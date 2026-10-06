package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Jpql;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tài chính: thống kê dòng tiền và danh sách giao dịch (payment_transactions).
 * Mỗi đơn gắn 1 giao dịch đại diện (ưu tiên đã thu / đã hoàn, sau đó id lớn nhất) để không đếm trùng.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class FinanceController {
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final OrderService orders;
    private final ReportExportService export;
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

    private static LocalDate date(String v) {
        try {
            return v == null || v.isBlank() ? null : LocalDate.parse(v);
        } catch (Exception e) {
            return null;
        }
    }

    /** Lọc đơn: mã đơn, tên, SĐT, khoảng ngày, số tiền, phương thức, trạng thái thanh toán. */
    private Jpql filtered(Map<String, String> in) {
        LocalDate from = date(in.get("from")), to = date(in.get("to"));
        if (from != null && to != null && to.isBefore(from)) {
            throw new ValidationException(List.of("Ngày kết thúc phải sau ngày bắt đầu."));
        }
        Long min = Texts.toLong(in.get("min")), max = Texts.toLong(in.get("max"));
        PaymentMethod m = parse(PaymentMethod.class, in.get("method"));
        PaymentStatus s = parse(PaymentStatus.class, in.get("status"));
        return new Jpql("Order o", "o")
                .search(Texts.trim(in.get("q"), 100), "o.code", "o.recipient", "o.phone")
                .when(from != null, "o.createdAt >= :from", "from", from != null ? from.atStartOfDay() : null)
                .when(to != null, "o.createdAt < :to", "to", to != null ? to.plusDays(1).atStartOfDay() : null)
                .when(min != null, "o.total >= :min", "min", min)
                .when(max != null, "o.total <= :max", "max", max)
                .when(m != null, "o.paymentMethod = :m", "m", m)
                .when(s != null, "o.paymentStatus = :s", "s", s);
    }

    /** Giao dịch đại diện của đơn. */
    private static PaymentTransaction rep(Order o) {
        List<PaymentTransaction> pay = o.getPaymentTransactions().stream().filter(t -> "PAYMENT".equals(t.getType())).toList();
        return pay.stream().filter(t -> "SUCCESS".equals(t.getStatus()) || "REFUNDED".equals(t.getStatus())).max(Comparator.comparing(PaymentTransaction::getId))
                .orElseGet(() -> pay.stream().max(Comparator.comparing(PaymentTransaction::getId)).orElse(null));
    }

    private void filterModel(Map<String, String> in, Model model) {
        model.addAttribute("f", in);
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("payStatuses", PaymentStatus.values());
    }

    @GetMapping("/finance")
    @Transactional(readOnly = true)
    public String index(@RequestParam Map<String, String> in, Model model) {
        List<Order> base = filtered(in).where("o.status <> :rx", "rx", OrderStatus.RX_REJECTED).list(em, Order.class, null, 0);
        List<Order> paid = base.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PAID).toList();
        Set<OrderStatus> codOpen = EnumSet.of(OrderStatus.SHIPPING, OrderStatus.PACKED, OrderStatus.PREPARING, OrderStatus.CONFIRMED, OrderStatus.PENDING);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("orders", base.size());
        stats.put("gross", base.stream().mapToLong(Order::getTotal).sum());
        stats.put("paid", paid.stream().mapToLong(Order::getTotal).sum());
        stats.put("paid_count", paid.size());
        stats.put("unpaid", base.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.UNPAID && o.getStatus() != OrderStatus.CANCELLED).mapToLong(Order::getTotal).sum());
        stats.put("cod_pending", base.stream().filter(o -> o.getPaymentMethod() == PaymentMethod.COD && o.getPaymentStatus() == PaymentStatus.UNPAID && codOpen.contains(o.getStatus()))
                .mapToLong(Order::getTotal).sum());
        stats.put("refund_pending", base.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.REFUND_PENDING).mapToLong(o -> o.getRefundAmount() == null ? 0 : o.getRefundAmount()).sum());
        stats.put("refunded", base.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.REFUNDED).mapToLong(o -> o.getRefundAmount() == null ? 0 : o.getRefundAmount()).sum());

        LocalDate from = date(in.get("from")), to = date(in.get("to"));
        Jpql tx = new Jpql("PaymentTransaction t", "t")
                .when(from != null, "t.createdAt >= :from", "from", from != null ? from.atStartOfDay() : null)
                .when(to != null, "t.createdAt < :to", "to", to != null ? to.plusDays(1).atStartOfDay() : null);
        List<PaymentTransaction> txs = tx.list(em, PaymentTransaction.class, "t.id desc", 0);
        stats.put("tx_success", txs.stream().filter(t -> "SUCCESS".equals(t.getStatus()) && "PAYMENT".equals(t.getType())).count());
        stats.put("tx_failed", txs.stream().filter(t -> "FAILED".equals(t.getStatus())).count());

        Map<PaymentMethod, long[]> byMethod = new LinkedHashMap<>();
        for (Order o : paid) {
            long[] v = byMethod.computeIfAbsent(o.getPaymentMethod(), k -> new long[2]);
            v[0]++;
            v[1] += o.getTotal();
        }
        // Cổng -> trạng thái -> [số GD, số tiền]
        Map<String, Map<String, long[]>> byGateway = new TreeMap<>();
        Map<String, String> statusLabels = new HashMap<>();
        for (PaymentTransaction t : txs) {
            if (!"PAYMENT".equals(t.getType())) continue;
            long[] v = byGateway.computeIfAbsent(t.getGateway(), k -> new TreeMap<>()).computeIfAbsent(t.getStatus(), k -> new long[2]);
            v[0]++;
            v[1] += t.amountValue();
            statusLabels.put(t.getStatus(), t.statusLabel());
        }
        Map<String, String> gatewayLabels = new HashMap<>();
        for (String g : byGateway.keySet()) {
            PaymentMethod pm = parse(PaymentMethod.class, g);
            gatewayLabels.put(g, pm != null ? pm.getShortLabel() : g);
        }
        filterModel(in, model);
        model.addAttribute("title", "Tài chính & thanh toán");
        model.addAttribute("stats", stats);
        model.addAttribute("byMethod", byMethod);
        model.addAttribute("byGateway", byGateway);
        model.addAttribute("gatewayLabels", gatewayLabels);
        model.addAttribute("statusLabels", statusLabels);
        model.addAttribute("recent", txs.stream().limit(10).toList());
        model.addAttribute("isIndex", true);
        return "admin/finance/index";
    }

    @GetMapping("/finance/transactions")
    @Transactional(readOnly = true)
    public Object transactions(@RequestParam Map<String, String> in, @RequestParam(defaultValue = "1") int page, Model model) throws IOException {
        String sort = Set.of("total", "createdAt", "code").contains(in.getOrDefault("sort", "")) ? in.get("sort") : "createdAt";
        String dir = "asc".equals(in.get("dir")) ? "asc" : "desc";
        String order = "o." + sort + " " + dir + ", o.id desc";
        if ("xlsx".equals(in.get("export"))) {
            List<List<Object>> rows = new ArrayList<>();
            for (Order o : filtered(in).list(em, Order.class, order, 0)) {
                PaymentTransaction t = rep(o);
                rows.add(Arrays.asList(o.getCode(), o.getCreatedAt().format(DT), o.getRecipient(), o.getPhone(), o.getTotal(), o.getPaymentMethod().getShortLabel(),
                        o.getPaymentStatus().getLabel(), t != null ? t.getGateway() : "", t != null ? t.getTransactionId() : "",
                        t != null && t.getPaidAt() != null ? t.getPaidAt().format(DT) : "", o.getStatus().getLabel()));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            export.table("Giao dich", List.of("Mã đơn", "Ngày tạo", "Người nhận", "SĐT", "Tổng tiền", "Phương thức", "Trạng thái TT", "Cổng", "Mã GD",
                    "Thời điểm thu", "Trạng thái đơn"), rows, out);
            return Web.xlsx(out, "giao-dich-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ".xlsx");
        }
        Page<Order> orders = filtered(in).page(em, Order.class, order, 20, page);
        Map<Long, PaymentTransaction> reps = new HashMap<>();
        for (Order o : orders.getItems()) {
            PaymentTransaction t = rep(o);
            if (t != null) reps.put(o.getId(), t);
        }
        filterModel(in, model);
        model.addAttribute("title", "Danh sách giao dịch");
        model.addAttribute("orders", orders);
        model.addAttribute("reps", reps);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("isIndex", false);
        return "admin/finance/transactions";
    }

    /** Cập nhật thanh toán COD / chuyển khoản: xác nhận đã thu tiền. */
    @PostMapping("/finance/orders/{id}/cod")
    @Transactional
    public String confirmCod(@PathVariable Long id, @RequestParam(required = false) String reference, HttpServletRequest req, RedirectAttributes ra) {
        Order o = Web.found(em.find(Order.class, id));
        if (o.getPaymentMethod() != PaymentMethod.COD && o.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new BusinessException("Chỉ xác nhận thủ công cho đơn COD / chuyển khoản.");
        }
        orders.markPaid(o, currentUser.get(), reference);
        Web.success(ra, "Đã ghi nhận thu tiền đơn " + o.getCode() + ".");
        return Web.back(req, "/admin/finance/transactions");
    }

    @GetMapping("/refunds")
    @Transactional(readOnly = true)
    public String refunds(Model model) {
        model.addAttribute("title", "Duyệt hoàn tiền");
        model.addAttribute("pending", em.createQuery("select o from Order o join fetch o.user where o.paymentStatus = :s order by o.updatedAt", Order.class)
                .setParameter("s", PaymentStatus.REFUND_PENDING).getResultList());
        model.addAttribute("done", em.createQuery("select o from Order o join fetch o.user where o.paymentStatus = :s order by o.refundedAt desc", Order.class)
                .setParameter("s", PaymentStatus.REFUNDED).setMaxResults(50).getResultList());
        return "admin/refunds";
    }
}
