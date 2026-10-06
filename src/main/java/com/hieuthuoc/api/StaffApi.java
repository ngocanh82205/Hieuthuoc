package com.hieuthuoc.api;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Jpql;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Nghiệp vụ nhân viên nhà thuốc (dược sĩ / quản trị): tổng quan, duyệt đơn thuốc, xử lý đơn hàng, tra cứu tồn kho.
 * Quyền chi tiết (RX_REVIEW, ORDER, INVENTORY) được kiểm tra ở từng endpoint.
 */
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffApi {
    private final OrderService orders;
    private final StockService stock;
    private final SafetyService safety;
    private final SettingService settings;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private User need(StaffPermission p) {
        User u = currentUser.get();
        if (!u.hasPermission(p)) throw BusinessException.forbidden("Bạn chưa được cấp quyền \"" + p.getShortLabel() + "\".");
        return u;
    }

    private long count(String jpql, Object... params) {
        var q = em.createQuery(jpql, Long.class);
        for (int i = 0; i + 1 < params.length; i += 2) q.setParameter((String) params[i], params[i + 1]);
        return q.getSingleResult();
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("pendingPrescriptions", count("select count(p) from Prescription p where p.status = :s", "s", ApprovalStatus.PENDING));
        d.put("pendingOrders", count("select count(o) from Order o where o.status in :s", "s", List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED)));
        d.put("inProgressOrders", count("select count(o) from Order o where o.status in :s", "s", List.of(OrderStatus.PREPARING, OrderStatus.PACKED, OrderStatus.SHIPPING)));
        d.put("returnRequests", count("select count(o) from Order o where o.returnStatus = :s", "s", ReturnStatus.REQUESTED));
        d.put("openConsultations", count("select count(c) from Conversation c where c.closed = false and c.mode = 'HUMAN'"));
        d.put("nearExpiryBatches", count("select count(b) from Batch b where b.quantity > 0 and b.expDate between :a and :b",
                "a", today, "b", today.plusDays(settings.getInt("near_expiry_days"))));
        d.put("expiredBatches", count("select count(b) from Batch b where b.quantity > 0 and b.expDate < :a", "a", today));
        return Api.ok(d);
    }

    /* ---------------- Duyệt đơn thuốc ---------------- */

    /** Tham số: status = PENDING (mặc định, cũ nhất trước) | APPROVED | REJECTED. */
    @GetMapping("/prescriptions")
    @Transactional(readOnly = true)
    public Map<String, Object> prescriptions(@RequestParam(defaultValue = "PENDING") String status, @RequestParam(defaultValue = "1") int page) {
        need(StaffPermission.RX_REVIEW);
        ApprovalStatus st;
        try {
            st = ApprovalStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Trạng thái không hợp lệ: " + status);
        }
        Page<Prescription> p = new Jpql("Prescription p", "p").where("p.status = :s", "s", st)
                .page(em, Prescription.class, st == ApprovalStatus.PENDING ? "p.createdAt" : "p.reviewedAt desc", 20, page);
        return Api.page(p, Dto.PrescriptionDto::of);
    }

    private Prescription rx(Long id) {
        Prescription p = em.find(Prescription.class, id);
        if (p == null) throw BusinessException.notFound("Không tìm thấy đơn thuốc.");
        return p;
    }

    /** Chi tiết đơn thuốc kèm đơn hàng, cảnh báo an toàn (dị ứng, tương tác) và danh mục kiểm tra bắt buộc. */
    @GetMapping("/prescriptions/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> prescription(@PathVariable Long id) {
        need(StaffPermission.RX_REVIEW);
        Prescription p = rx(id);
        Order o = p.getOrder();
        List<Product> current = o != null ? o.getItems().stream().map(OrderItem::getProduct).toList() : List.of();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("prescription", Dto.PrescriptionDto.of(p));
        d.put("order", o != null ? Dto.OrderDto.of(o, true) : null);
        d.put("customer", Dto.UserDto.of(p.getUser()));
        d.put("warnings", safety.check(p.getUser(), current, safety.recentProducts(p.getUser())).stream()
                .map(w -> Map.of("level", w.level(), "message", w.message())).toList());
        d.put("checks", OrderService.RX_CHECKS);
        d.put("validDays", settings.getInt("rx_valid_days"));
        return Api.ok(d);
    }

    /**
     * Duyệt đơn thuốc. Body: {"patientName", "doctorName", "clinic", "rxDate": "yyyy-MM-dd", "checks": ["valid","date","sign","match"],
     * "pharmacistNote", "qty": {"orderItemId": số lượng}, "sub": {"orderItemId": productId thay thế}} hoặc với đơn nhờ lên đơn:
     * "productIds": [...], "quantities": [...]; "reuseConfirmed": true khi được hỏi xác nhận đơn dùng lại.
     */
    @PostMapping("/prescriptions/{id}/approve")
    @Transactional
    public Map<String, Object> approve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User me = need(StaffPermission.RX_REVIEW);
        Map<String, Object> b = new LinkedHashMap<>();
        Map<String, String> rename = Map.of("patientName", "patient_name", "doctorName", "doctor_name", "rxDate", "rx_date", "pharmacistNote", "pharmacist_note",
                "productIds", "product_ids", "quantities", "quantities", "reuseConfirmed", "reuse_confirmed");
        body.forEach((k, v) -> b.put(rename.getOrDefault(k, k), v));
        Order o = orders.approvePrescription(rx(id), me, Api.form(b));
        return Api.ok(Dto.OrderDto.of(o, true), o.getStatus() == OrderStatus.AWAITING_CUSTOMER
                ? "Đã duyệt/lên đơn " + o.getCode() + ". Đang chờ khách xác nhận." : "Đã duyệt đơn thuốc cho đơn hàng " + o.getCode() + ".");
    }

    /** Body: {"reason": "..."} */
    @PostMapping("/prescriptions/{id}/reject")
    @Transactional
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User me = need(StaffPermission.RX_REVIEW);
        Prescription p = orders.rejectPrescription(rx(id), me, Api.form(body).get("reason"));
        return Api.ok(Dto.PrescriptionDto.of(p), "Đã từ chối đơn thuốc và thông báo cho khách hàng.");
    }

    /* ---------------- Đơn hàng ---------------- */

    /** Tham số: status, q (mã đơn / tên / SĐT), channel (ONLINE|POS), page. */
    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public Map<String, Object> orders(@RequestParam Map<String, String> in) {
        need(StaffPermission.ORDER);
        OrderStatus st = null;
        try {
            if (!Texts.isBlank(in.get("status"))) st = OrderStatus.valueOf(in.get("status"));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Trạng thái không hợp lệ: " + in.get("status"));
        }
        String ch = in.get("channel");
        Page<Order> p = new Jpql("Order o", "o").when(st != null, "o.status = :s", "s", st)
                .when("POS".equals(ch) || "ONLINE".equals(ch), "o.channel = :c", "c", ch)
                .search(Texts.trim(in.get("q")), "o.code", "o.recipient", "o.phone")
                .page(em, Order.class, "o.createdAt desc, o.id desc", 20, Texts.toInt(in.get("page"), 1));
        return Api.page(p, o -> Dto.OrderDto.of(o, false));
    }

    private Order findOrder(Long id) {
        Order o = em.find(Order.class, id);
        if (o == null) throw BusinessException.notFound("Không tìm thấy đơn hàng.");
        return o;
    }

    @GetMapping("/orders/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> order(@PathVariable Long id) {
        need(StaffPermission.ORDER);
        Order o = findOrder(id);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("order", Dto.OrderDto.of(o, true));
        d.put("customer", Dto.UserDto.of(o.getUser()));
        d.put("transitions", o.getStatus().staffTransitions().stream().map(Dto.Code::of).toList());
        return Api.ok(d);
    }

    /** Chuyển trạng thái đơn. Body: {"to": "CONFIRMED|PREPARING|PACKED|SHIPPING|COMPLETED|CANCELLED|RETURNED", "note", "safetyAck": true} */
    @PostMapping("/orders/{id}/status")
    @Transactional
    public Map<String, Object> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User me = need(StaffPermission.ORDER);
        Form f = Api.form(body);
        OrderStatus to;
        try {
            to = OrderStatus.valueOf(Texts.trim(f.get("to")));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Trạng thái không hợp lệ.");
        }
        Order o = findOrder(id);
        orders.changeStatus(o, to, me, f.get("note"), f.bool("safetyAck"));
        return Api.ok(Dto.OrderDto.of(o, true), "Đơn " + o.getCode() + ": đã chuyển sang \"" + to.getLabel() + "\".");
    }

    /* ---------------- Kho ---------------- */

    /** Tồn kho theo sản phẩm. Tham số: q, filter = low | out. */
    @GetMapping("/inventory")
    @Transactional(readOnly = true)
    public Map<String, Object> inventory(@RequestParam(required = false) String q, @RequestParam(required = false) String filter) {
        need(StaffPermission.INVENTORY);
        List<Product> list = new ArrayList<>(new Jpql("Product p", "p").search(Texts.trim(q), "p.name", "coalesce(p.activeIngredient, '')")
                .list(em, Product.class, "p.name", 0));
        stock.fill(list, false);
        return Api.ok(list.stream().filter(p -> !"low".equals(filter) || p.isLowStock()).filter(p -> !"out".equals(filter) || p.getAvailable() <= 0)
                .map(Dto.StockDto::of).toList());
    }

    /** Cảnh báo kho: lô hết hạn, cận hạn, đang khóa. */
    @GetMapping("/inventory/alerts")
    @Transactional(readOnly = true)
    public Map<String, Object> alerts() {
        need(StaffPermission.INVENTORY);
        LocalDate today = LocalDate.now();
        int near = settings.getInt("near_expiry_days");
        String base = "select b from Batch b join fetch b.product where b.quantity > 0 and ";
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("nearExpiryDays", near);
        d.put("expired", em.createQuery(base + "b.expDate < :t order by b.expDate", Batch.class).setParameter("t", today).getResultList().stream().map(Dto.BatchDto::of).toList());
        d.put("nearExpiry", em.createQuery(base + "b.expDate between :a and :b order by b.expDate", Batch.class).setParameter("a", today)
                .setParameter("b", today.plusDays(near)).getResultList().stream().map(Dto.BatchDto::of).toList());
        d.put("locked", em.createQuery(base + "b.locked = true order by b.expDate", Batch.class).getResultList().stream().map(Dto.BatchDto::of).toList());
        return Api.ok(d);
    }
}
