package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Dược sĩ / nhân viên: tổng quan, duyệt đơn thuốc, xử lý đơn hàng. */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {
    private final PrescriptionRepository prescriptionRepo;
    private final OrderRepository orderRepo;
    private final ConversationRepository conversationRepo;
    private final ProductRepository productRepo;
    private final BatchRepository batchRepo;
    private final OrderService orderService;
    private final StockService stockService;
    private final SettingService settings;
    private final CurrentUser currentUser;
    private final SafetyService safety;
    private final CatalogService catalogService;
    private final UserRepository userRepo;
    private final NotificationService notifications;
    private final ProductQuestionRepository questionRepo;
    private final CallbackRequestRepository callbackRepo;

    @GetMapping
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        List<Product> products = stockService.fill(new ArrayList<>(productRepo.findByActiveTrueOrderByNameAsc()));
        model.addAttribute("pendingRx", prescriptionRepo.countByStatus(ApprovalStatus.PENDING));
        model.addAttribute("pendingOrders", orderRepo.countByStatusIn(EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED)));
        model.addAttribute("inProgress", orderRepo.countByStatusIn(EnumSet.of(OrderStatus.PREPARING, OrderStatus.SHIPPING)));
        model.addAttribute("returns", orderRepo.countByReturnStatus(ReturnStatus.REQUESTED));
        model.addAttribute("openChats", conversationRepo.countByClosedFalse());
        model.addAttribute("openQuestions", questionRepo.countByAnswerIsNullAndHiddenFalse());
        model.addAttribute("openCallbacks", callbackRepo.countByDoneFalse());
        model.addAttribute("lowStock", products.stream().filter(Product::isLowStock).count());
        model.addAttribute("nearExpiry", batchRepo.findNearExpiry(today, today.plusDays(settings.getLong("near_expiry_days"))).size());
        model.addAttribute("expired", batchRepo.findExpired(today).size());
        model.addAttribute("rxQueue", prescriptionRepo.findTop5ByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING));
        model.addAttribute("recentOrders", orderRepo.findTop8ByStatusInOrderByCreatedAtAsc(
                EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING)));
        model.addAttribute("title", "Tổng quan");
        return "staff/dashboard";
    }

    /* ---------------- Duyệt đơn thuốc ---------------- */

    @GetMapping("/prescriptions")
    public String prescriptions(@RequestParam(defaultValue = "PENDING") ApprovalStatus status, Model model) {
        model.addAttribute("list", status == ApprovalStatus.PENDING
                ? prescriptionRepo.findByStatusOrderByCreatedAtAsc(status)
                : prescriptionRepo.findTop200ByStatusOrderByReviewedAtDesc(status));
        model.addAttribute("status", status);
        model.addAttribute("title", "Duyệt đơn thuốc");
        return "staff/prescriptions";
    }

    @GetMapping("/prescriptions/{id}")
    public String prescription(@PathVariable Long id, Model model) {
        Prescription rx = prescriptionRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn thuốc."));
        Order o = rx.getOrder();
        Map<Long, Long> available = new HashMap<>();
        Map<Long, List<Product>> equivalents = new HashMap<>();
        if (o != null) {
            for (OrderItem it : o.getItems()) {
                available.put(it.getId(), stockService.fill(it.getProduct()).getAvailable());
                // Thuốc thay thế: cùng hoạt chất + cùng hàm lượng, hoặc được admin cấu hình tương đương
                equivalents.put(it.getId(), stockService.fill(new ArrayList<>(catalogService.equivalents(it.getProduct(), true))));
            }
        } else {
            model.addAttribute("products", stockService.fill(new ArrayList<>(productRepo.findSellable())));
        }
        List<Product> current = o == null ? List.of() : o.getItems().stream().map(OrderItem::getProduct).toList();
        model.addAttribute("warnings", safety.check(rx.getUser(), current, safety.recentProducts(rx.getUser())));
        model.addAttribute("rxChecks", OrderService.RX_CHECKS);
        model.addAttribute("rxValidDays", settings.getLong("rx_valid_days"));
        model.addAttribute("rx", rx);
        model.addAttribute("order", o);
        model.addAttribute("available", available);
        model.addAttribute("equivalents", equivalents);
        model.addAttribute("previous", o == null ? List.of() : o.getPrescriptions().stream().filter(p -> !p.getId().equals(rx.getId())).toList());
        model.addAttribute("title", "Đơn thuốc #" + rx.getId());
        return "staff/prescription";
    }

    @PostMapping("/prescriptions/{id}/approve")
    @Transactional
    public String approve(@PathVariable Long id, @ModelAttribute OrderService.RxApproval form, RedirectAttributes ra) {
        Order o = orderService.approvePrescription(id, currentUser.get(), form);
        Flash.success(ra, o.getStatus() == OrderStatus.AWAITING_CUSTOMER
                ? "Đã duyệt/lên đơn " + o.getCode() + ". Đang chờ khách xác nhận."
                : "Đã duyệt đơn thuốc cho đơn hàng " + o.getCode() + ".");
        return "redirect:/staff/prescriptions";
    }

    @PostMapping("/prescriptions/{id}/reject")
    @Transactional
    public String reject(@PathVariable Long id, @RequestParam String rejectReason, RedirectAttributes ra) {
        orderService.rejectPrescription(id, currentUser.get(), rejectReason);
        Flash.info(ra, "Đã từ chối đơn thuốc và thông báo cho khách hàng.");
        return "redirect:/staff/prescriptions";
    }

    /** Sổ bán thuốc kê đơn. */
    @GetMapping("/rx-log")
    public String rxLog(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        Model model) {
        if (to == null) to = LocalDate.now();
        if (from == null) from = to.minusDays(30);
        model.addAttribute("rows", prescriptionRepo.findByStatusAndReviewedAtBetweenOrderByReviewedAtDesc(
                ApprovalStatus.APPROVED, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("title", "Sổ bán thuốc kê đơn");
        return "staff/rx-log";
    }

    /* ---------------- Đơn hàng ---------------- */

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) OrderStatus status,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) PaymentMethod payment,
                         @RequestParam(required = false) String channel,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(defaultValue = "false") boolean returns,
                         @RequestParam(defaultValue = "false") boolean mine,
                         @RequestParam(defaultValue = "1") int page,
                         Model model) {
        Long meId = currentUser.get().getId();
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (payment != null) ps.add(cb.equal(root.get("paymentMethod"), payment));
            if (returns) ps.add(cb.equal(root.get("returnStatus"), ReturnStatus.REQUESTED));
            if (mine) ps.add(cb.equal(root.get("assignedTo").get("id"), meId));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null) ps.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            if ("POS".equals(channel)) ps.add(cb.equal(root.get("channel"), "POS"));
            if ("ONLINE".equals(channel)) ps.add(cb.or(cb.isNull(root.get("channel")), cb.notEqual(root.get("channel"), "POS")));
            if (!Texts.isBlank(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("code")), like), cb.like(cb.lower(root.get("recipient")), like),
                        cb.like(root.get("phone"), like), cb.like(cb.lower(root.get("user").get("email")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Order> p = orderRepo.findAll(spec, PageRequest.of(Math.max(page, 1) - 1, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<String, Long> counts = new HashMap<>();
        for (Object[] r : orderRepo.countGroupByStatus()) counts.put(((OrderStatus) r[0]).name(), (Long) r[1]);
        model.addAttribute("page", p);
        model.addAttribute("counts", counts);
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        model.addAttribute("payment", payment);
        model.addAttribute("returns", returns);
        model.addAttribute("mine", mine);
        model.addAttribute("mineCount", orderRepo.countByAssignedToIdAndStatusIn(meId, EnumSet.of(OrderStatus.PENDING_RX, OrderStatus.AWAITING_CUSTOMER,
                OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.SHIPPING)));
        model.addAttribute("channel", channel);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("returnCount", orderRepo.countByReturnStatus(ReturnStatus.REQUESTED));
        model.addAttribute("title", "Quản lý đơn hàng");
        return "staff/orders";
    }

    private Order order(Long id) {
        return orderRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable Long id, Model model) {
        Order o = order(id);
        model.addAttribute("order", o);
        model.addAttribute("transitions", o.getStatus().staffTransitions());
        model.addAttribute("warnings", o.isPos() ? List.of() : safety.check(o.getUser(), o.getItems().stream().map(OrderItem::getProduct).toList(),
                safety.recentProducts(o.getUser())));
        model.addAttribute("needsVerify", orderService.needsVerifyCall(o));
        model.addAttribute("carriers", settings.lines("carriers"));
        model.addAttribute("assignable", userRepo.findByRoleInOrderByRoleAscFullNameAsc(List.of(Role.PHARMACIST, Role.ADMIN)).stream()
                .filter(u -> !u.isLocked() && u.hasPermission(StaffPermission.ORDER)).toList());
        model.addAttribute("returnBlockedByRx", orderService.returnBlockedByRx(o));
        model.addAttribute("title", "Đơn hàng " + o.getCode());
        return "staff/order";
    }

    @PostMapping("/orders/{id}/assign")
    @Transactional
    public String assign(@PathVariable Long id, @RequestParam(required = false) Long staffId, RedirectAttributes ra) {
        User me = currentUser.get();
        if (me.getRole() != Role.ADMIN) throw new BusinessException("Chỉ quản trị viên được phân công xử lý đơn.", 403);
        Order o = orderService.assign(id, me, staffId);
        Flash.success(ra, o.getAssignedTo() == null ? "Đã bỏ phân công." : "Đã phân công " + o.getAssignedTo().getFullName() + " xử lý đơn.");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/orders/{id}/refund")
    @Transactional
    public String refund(@PathVariable Long id, @RequestParam long amount, @RequestParam String note,
                         @RequestParam(defaultValue = "") String back, RedirectAttributes ra) {
        orderService.approveRefund(id, currentUser.get(), amount, note);
        Flash.success(ra, "Đã ghi nhận hoàn tiền.");
        return "redirect:" + ("/admin/refunds".equals(back) ? back : "/staff/orders/" + id);
    }

    @PostMapping("/orders/{id}/einvoice")
    @Transactional
    public String einvoice(@PathVariable Long id, @RequestParam String einvoiceNo, RedirectAttributes ra) {
        Order o = order(id);
        String no = Texts.trim(einvoiceNo, 50);
        if (no.isEmpty()) throw new BusinessException("Nhập số hóa đơn điện tử.");
        o.setEinvoiceNo(no);
        orderService.addHistory(o, o.getStatus(), "Đã xuất hóa đơn điện tử số " + no, currentUser.get());
        notifications.log(currentUser.get(), "order.einvoice", o.getCode() + ": " + no);
        Flash.success(ra, "Đã lưu số hóa đơn điện tử.");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/orders/{id}/verify")
    @Transactional
    public String verify(@PathVariable Long id, @RequestParam String note, RedirectAttributes ra) {
        orderService.verifyCall(id, currentUser.get(), note);
        Flash.success(ra, "Đã ghi nhận cuộc gọi xác minh.");
        return "redirect:/staff/orders/" + id;
    }

    /** Soạn hàng: hệ thống gợi ý lô theo FEFO, nhân viên có thể chọn lô khác hoặc quét mã lô. */
    @GetMapping("/orders/{id}/pick")
    public String pick(@PathVariable Long id, Model model) {
        Order o = order(id);
        Map<Long, List<Batch>> lots = new HashMap<>();
        for (OrderItem it : o.getItems()) lots.put(it.getId(), batchRepo.findSellableFefo(it.getProduct().getId(), LocalDate.now()));
        model.addAttribute("order", o);
        model.addAttribute("lots", lots);
        model.addAttribute("title", "Soạn hàng " + o.getCode());
        return "staff/pick";
    }

    @PostMapping("/orders/{id}/pick")
    @Transactional
    public String doPick(@PathVariable Long id, @RequestParam Map<String, String> params, RedirectAttributes ra) {
        Map<Long, String> inputs = new HashMap<>();
        params.forEach((k, v) -> {
            // scan_<itemId> (quét mã lô) ưu tiên hơn lot_<itemId> (chọn trong danh sách)
            if (k.startsWith("lot_") && !Texts.isBlank(v)) inputs.putIfAbsent(Long.valueOf(k.substring(4)), v);
        });
        params.forEach((k, v) -> {
            if (k.startsWith("scan_") && !Texts.isBlank(v)) inputs.put(Long.valueOf(k.substring(5)), v.trim());
        });
        Order o = orderService.prepare(id, currentUser.get(), inputs);
        Flash.success(ra, "Đã soạn hàng đơn " + o.getCode() + ". Có thể in phiếu giao hàng và hướng dẫn sử dụng.");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/orders/{id}/ship")
    @Transactional
    public String ship(@PathVariable Long id, @RequestParam(required = false) String carrier, @RequestParam(required = false) String trackingCode,
                       RedirectAttributes ra) {
        orderService.setShipping(id, carrier, trackingCode);
        Order o = orderService.changeStatus(id, OrderStatus.SHIPPING, currentUser.get(),
                Texts.isBlank(carrier) ? "Khách nhận tại quầy" : "Giao cho " + carrier + (Texts.isBlank(trackingCode) ? "" : " - mã vận đơn " + trackingCode));
        Flash.success(ra, "Đơn " + o.getCode() + " đã chuyển sang Đang giao hàng.");
        return "redirect:/staff/orders/" + id;
    }

    @GetMapping("/orders/{id}/shipping-label")
    public String shippingLabel(@PathVariable Long id, Model model) {
        Order o = order(id);
        model.addAttribute("order", o);
        model.addAttribute("title", "Phiếu giao hàng " + o.getCode());
        return "staff/shipping-label";
    }

    @GetMapping("/orders/{id}/usage")
    public String usageGuide(@PathVariable Long id, Model model) {
        Order o = order(id);
        model.addAttribute("order", o);
        model.addAttribute("title", "Hướng dẫn sử dụng " + o.getCode());
        return "staff/usage";
    }

    @PostMapping("/orders/{id}/status")
    @Transactional
    public String changeStatus(@PathVariable Long id, @RequestParam OrderStatus to, @RequestParam(required = false) String note,
                               RedirectAttributes ra) {
        Order o = orderService.changeStatus(id, to, currentUser.get(), note);
        Flash.success(ra, "Đơn " + o.getCode() + ": đã chuyển sang \"" + to.getLabel() + "\".");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/orders/{id}/paid")
    @Transactional
    public String markPaid(@PathVariable Long id, RedirectAttributes ra) {
        orderService.markPaid(id, currentUser.get());
        Flash.success(ra, "Đã xác nhận thanh toán.");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/orders/{id}/return")
    @Transactional
    public String handleReturn(@PathVariable Long id, @RequestParam String decision,
                               @RequestParam(defaultValue = "false") boolean restock,
                               @RequestParam(required = false) String note, RedirectAttributes ra) {
        orderService.handleReturn(id, currentUser.get(), "approve".equals(decision), restock, note);
        Flash.success(ra, "Đã xử lý yêu cầu đổi/trả.");
        return "redirect:/staff/orders/" + id;
    }

    @GetMapping("/orders/{id}/invoice")
    public String invoice(@PathVariable Long id, Model model) {
        Order o = order(id);
        model.addAttribute("order", o);
        long rate = settings.getLong("vat_rate");
        long vat = Math.round(o.getTotal() * rate / (100.0 + rate));
        model.addAttribute("vatRate", rate);
        model.addAttribute("vatAmount", vat);
        model.addAttribute("preVat", o.getTotal() - vat);
        model.addAttribute("title", "Hóa đơn " + o.getCode());
        return "staff/invoice";
    }
}
