package com.hieuthuoc.web.staff;

import com.fasterxml.jackson.databind.JsonNode;
import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Jpql;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Xử lý đơn hàng cho dược sĩ / nhân viên / admin. */
@Controller
@RequestMapping("/staff/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orders;
    private final SettingService settings;
    private final SafetyService safety;
    private final GhnService ghn;
    private final StockService stock;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private static <E extends Enum<E>> E parse(Class<E> cls, String v) {
        try {
            return v == null || v.isBlank() ? null : Enum.valueOf(cls, v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Order order(Long id) {
        return Web.found(em.find(Order.class, id));
    }

    private String show(Order o) {
        return "redirect:/staff/orders/" + o.getId();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String index(@RequestParam Map<String, String> in, Model model) {
        OrderStatus status = parse(OrderStatus.class, in.get("status"));
        PaymentMethod payment = parse(PaymentMethod.class, in.get("payment"));
        String q = Texts.trim(in.get("q"));
        String from = in.get("from"), to = in.get("to"), channel = in.get("channel");
        Jpql j = new Jpql("Order o", "o")
                .when(status != null, "o.status = :st", "st", status)
                .when(payment != null, "o.paymentMethod = :pm", "pm", payment)
                .when("1".equals(in.get("returns")) || "true".equals(in.get("returns")), "o.returnStatus = :rs", "rs", ReturnStatus.REQUESTED)
                .when(from != null && !from.isBlank(), "o.createdAt >= :from", "from", from != null && !from.isBlank() ? LocalDate.parse(from).atStartOfDay() : null)
                .when(to != null && !to.isBlank(), "o.createdAt < :to", "to", to != null && !to.isBlank() ? LocalDate.parse(to).plusDays(1).atStartOfDay() : null)
                .when("POS".equals(channel) || "ONLINE".equals(channel), "o.channel = :ch", "ch", channel)
                .search(q, "o.code", "o.recipient", "o.phone", "coalesce(o.trackingCode, '')");
        Map<String, Long> counts = new HashMap<>();
        for (Object[] r : em.createQuery("select o.status, count(o) from Order o group by o.status", Object[].class).getResultList()) {
            counts.put(((OrderStatus) r[0]).name(), (Long) r[1]);
        }
        int page = Math.max(1, parseInt(in.get("page")));
        model.addAttribute("title", "Quản lý đơn hàng");
        model.addAttribute("orders", j.page(em, Order.class, "o.createdAt desc, o.id desc", 20, page));
        model.addAttribute("status", status);
        model.addAttribute("payment", payment);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("counts", counts);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("payments", PaymentMethod.values());
        model.addAttribute("quick", List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.PACKED, OrderStatus.SHIPPING));
        model.addAttribute("returnCount", em.createQuery("select count(o) from Order o where o.returnStatus = :r", Long.class)
                .setParameter("r", ReturnStatus.REQUESTED).getSingleResult());
        return "staff/orders";
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 1 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String show(@PathVariable Long id, Model model) {
        Order o = order(id);
        List<SafetyService.Warning> warnings = o.isPos() ? List.of()
                : safety.check(o.getUser(), o.getItems().stream().map(OrderItem::getProduct).toList(), safety.recentProducts(o.getUser()));
        List<OrderStatus> transitions = o.getStatus().staffTransitions();
        model.addAttribute("title", "Đơn hàng " + o.getCode());
        model.addAttribute("order", o);
        model.addAttribute("transitions", transitions);
        model.addAttribute("buttons", transitions.stream().filter(t -> !EnumSet.of(OrderStatus.PACKED, OrderStatus.SHIPPING, OrderStatus.CANCELLED, OrderStatus.RETURNED).contains(t)).toList());
        model.addAttribute("canCancel", transitions.contains(OrderStatus.CANCELLED));
        model.addAttribute("canFail", transitions.contains(OrderStatus.RETURNED));
        model.addAttribute("warnings", warnings);
        model.addAttribute("hasDanger", warnings.stream().anyMatch(SafetyService.Warning::isDanger));
        model.addAttribute("carriers", settings.lines("carriers"));
        model.addAttribute("ghnEnabled", ghn.enabled());
        model.addAttribute("isGhn", o.getCarrier() != null && o.getCarrier().toLowerCase().contains("ghn"));
        model.addAttribute("returnBlockedByRx", orders.returnBlockedByRx(o));
        model.addAttribute("lockedAllocations", EnumSet.of(OrderStatus.PREPARING, OrderStatus.PACKED).contains(o.getStatus()) ? stock.lockedAllocations(o) : List.of());
        return "staff/order";
    }

    @PostMapping("/{id}/status")
    @Transactional
    public String changeStatus(@PathVariable Long id, @RequestParam(required = false) String to, @RequestParam(required = false) String note,
                               @RequestParam(name = "safety_ack", required = false) String ack, RedirectAttributes ra) {
        Order o = order(id);
        OrderStatus t = parse(OrderStatus.class, to);
        if (t == null) throw new BusinessException("Trạng thái không hợp lệ.");
        orders.changeStatus(o, t, currentUser.get(), note, ack != null);
        Web.success(ra, "Đơn " + o.getCode() + ": đã chuyển sang \"" + t.getLabel() + "\".");
        return show(o);
    }

    @PostMapping("/{id}/pack")
    @Transactional
    public String pack(@PathVariable Long id, @RequestParam(required = false) String note, RedirectAttributes ra) {
        Order o = order(id);
        orders.changeStatus(o, OrderStatus.PACKED, currentUser.get(), note != null && !note.isBlank() ? note : "Đã đóng gói, chờ bàn giao vận chuyển", false);
        Web.success(ra, "Đơn " + o.getCode() + " đã đóng gói.");
        return show(o);
    }

    /** Bàn giao vận chuyển thủ công (nhập đơn vị + mã vận đơn) hoặc khách nhận tại quầy. */
    @PostMapping("/{id}/ship")
    @Transactional
    public String ship(@PathVariable Long id, @RequestParam(required = false) String carrier,
                       @RequestParam(name = "tracking_code", required = false) String tracking, RedirectAttributes ra) {
        Order o = order(id);
        carrier = Texts.emptyToNull(Texts.trim(carrier));
        tracking = Texts.emptyToNull(Texts.trim(tracking));
        if (o.getShippingMethod() == ShippingMethod.DELIVERY || carrier != null) {
            orders.setShipping(o, carrier != null ? carrier : o.getCarrier(), tracking != null ? tracking : o.getTrackingCode());
        }
        orders.changeStatus(o, OrderStatus.SHIPPING, currentUser.get(), o.getCarrier() == null ? "Khách nhận tại quầy"
                : "Giao cho " + o.getCarrier() + (o.getTrackingCode() != null ? " - mã vận đơn " + o.getTrackingCode() : ""), false);
        Web.success(ra, "Đơn " + o.getCode() + " đã chuyển sang Đang vận chuyển.");
        return show(o);
    }

    /** Tạo vận đơn GHN tự động. */
    @PostMapping("/{id}/ghn")
    @Transactional
    public String createGhn(@PathVariable Long id, RedirectAttributes ra) {
        Order o = order(id);
        if (o.getStatus() != OrderStatus.PACKED && o.getStatus() != OrderStatus.PREPARING) {
            throw new BusinessException("Chỉ tạo vận đơn cho đơn đã soạn / đóng gói.");
        }
        if (o.getTrackingCode() != null) throw new BusinessException("Đơn đã có mã vận đơn " + o.getTrackingCode() + ".");
        JsonNode d = ghn.createOrder(o);
        String code = d.path("order_code").asText();
        o.setCarrier(GhnService.CARRIER);
        o.setTrackingCode(code);
        o.setShippingStatus("ready_to_pick");
        orders.addHistory(o, o.getStatus(), "Tạo vận đơn GHN " + code + (d.has("total_fee") ? " (phí " + OrderService.money(d.get("total_fee").asLong()) + ")" : ""), currentUser.get());
        Web.success(ra, "Đã tạo vận đơn GHN: " + code + ". Bấm \"Bàn giao vận chuyển\" khi shipper lấy hàng.");
        return show(o);
    }

    @PostMapping("/{id}/ghn/sync")
    @Transactional
    public String syncGhn(@PathVariable Long id, RedirectAttributes ra) {
        Order o = order(id);
        if (o.getTrackingCode() == null) throw new BusinessException("Đơn chưa có mã vận đơn.");
        JsonNode d = ghn.detail(o.getTrackingCode());
        if (d == null) throw new BusinessException("Không lấy được trạng thái từ GHN.");
        String st = d.hasNonNull("status") ? d.get("status").asText() : null;
        if (st != null && !st.equals(o.getShippingStatus())) {
            o.setShippingStatus(st);
            orders.addHistory(o, o.getStatus(), "GHN: " + GhnService.statusLabel(st), currentUser.get());
            if ("delivered".equals(st) && o.getStatus() == OrderStatus.SHIPPING) {
                orders.changeStatus(o, OrderStatus.COMPLETED, currentUser.get(), "GHN xác nhận giao hàng thành công", false);
            }
        }
        String label = GhnService.statusLabel(st);
        Web.info(ra, "Trạng thái GHN: " + (label != null ? label : "không rõ"));
        return show(o);
    }

    /** Đơn có hàng thuộc lô vừa bị khóa: trả về lô khóa, xuất lại từ lô khác. */
    @PostMapping("/{id}/swap-batches")
    @Transactional
    public String swapBatches(@PathVariable Long id, RedirectAttributes ra) {
        Order o = order(id);
        List<String> changes = orders.swapLockedBatches(o, currentUser.get());
        Web.success(ra, "Đã đổi lô: " + String.join("; ", changes) + ". Hãy soạn / đóng gói lại hàng.");
        return show(o);
    }

    @PostMapping("/{id}/paid")
    @Transactional
    public String markPaid(@PathVariable Long id, @RequestParam(required = false) String reference, RedirectAttributes ra) {
        Order o = order(id);
        orders.markPaid(o, currentUser.get(), reference);
        Web.success(ra, "Đã xác nhận thanh toán.");
        return show(o);
    }

    @PostMapping("/{id}/refund")
    @Transactional
    public String refund(@PathVariable Long id, @RequestParam(required = false, defaultValue = "0") long amount, @RequestParam(required = false) String note,
                         @RequestParam(required = false) String back, RedirectAttributes ra) {
        Order o = order(id);
        orders.approveRefund(o, currentUser.get(), amount, note);
        Web.success(ra, "Đã ghi nhận hoàn tiền.");
        return "refunds".equals(back) ? "redirect:/admin/refunds" : show(o);
    }

    @PostMapping("/{id}/return")
    @Transactional
    public String handleReturn(@PathVariable Long id, @RequestParam(required = false) String decision, @RequestParam(required = false) String restock,
                               @RequestParam(required = false) String note, RedirectAttributes ra) {
        Order o = order(id);
        orders.handleReturn(o, currentUser.get(), "approve".equals(decision), restock != null, note);
        Web.success(ra, "Đã xử lý yêu cầu đổi/trả.");
        return show(o);
    }

    @PostMapping("/{id}/einvoice")
    @Transactional
    public String einvoice(@PathVariable Long id, @RequestParam(name = "einvoice_no", required = false) String no0, RedirectAttributes ra) {
        Order o = order(id);
        String no = Texts.emptyToNull(Texts.trim(no0));
        if (no == null) throw new BusinessException("Nhập số hóa đơn điện tử.");
        if (no.length() > 50) no = no.substring(0, 50);
        User me = currentUser.get();
        o.setEinvoiceNo(no);
        orders.addHistory(o, o.getStatus(), "Đã xuất hóa đơn điện tử số " + no, me);
        notifications.log(me, "order.einvoice", o.getCode() + ": " + no);
        Web.success(ra, "Đã lưu số hóa đơn điện tử.");
        return show(o);
    }

    @GetMapping("/{id}/invoice")
    @Transactional(readOnly = true)
    public String invoice(@PathVariable Long id, Model model) {
        Order o = order(id);
        int rate = settings.getInt("vat_rate");
        long vat = Math.round(o.getTotal() * rate / (100.0 + rate));
        model.addAttribute("title", "Hóa đơn " + o.getCode());
        model.addAttribute("order", o);
        model.addAttribute("vatRate", rate);
        model.addAttribute("vatAmount", vat);
        model.addAttribute("preVat", o.getTotal() - vat);
        return "staff/invoice";
    }

    @GetMapping("/{id}/shipping-label")
    @Transactional(readOnly = true)
    public String shippingLabel(@PathVariable Long id, Model model) {
        Order o = order(id);
        model.addAttribute("title", "Phiếu giao hàng " + o.getCode());
        model.addAttribute("order", o);
        return "staff/shipping-label";
    }

    @GetMapping("/{id}/usage")
    @Transactional(readOnly = true)
    public String usageGuide(@PathVariable Long id, Model model) {
        Order o = order(id);
        model.addAttribute("title", "Hướng dẫn sử dụng " + o.getCode());
        model.addAttribute("order", o);
        return "staff/usage";
    }
}
