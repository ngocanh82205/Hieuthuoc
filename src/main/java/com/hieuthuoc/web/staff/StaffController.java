package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Khu vực nhân viên: tổng quan, duyệt đơn thuốc, sổ thuốc kê đơn, hồ sơ cá nhân. */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {
    private final OrderService orders;
    private final StockService stock;
    private final SettingService settings;
    private final SafetyService safety;
    private final CatalogService catalog;
    private final AccountService accounts;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private long count(String jpql, Object... params) {
        var q = em.createQuery(jpql, Long.class);
        for (int i = 0; i + 1 < params.length; i += 2) q.setParameter((String) params[i], params[i + 1]);
        return q.getSingleResult();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        List<Product> products = new ArrayList<>(em.createQuery("select p from Product p where p.active = true", Product.class).getResultList());
        stock.fill(products, false);
        model.addAttribute("title", "Tổng quan");
        model.addAttribute("pendingRx", count("select count(p) from Prescription p where p.status = :s", "s", ApprovalStatus.PENDING));
        model.addAttribute("pendingOrders", count("select count(o) from Order o where o.status in :s", "s", List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED)));
        model.addAttribute("inProgress", count("select count(o) from Order o where o.status in :s", "s", List.of(OrderStatus.PREPARING, OrderStatus.PACKED, OrderStatus.SHIPPING)));
        model.addAttribute("returns", count("select count(o) from Order o where o.returnStatus = :s", "s", ReturnStatus.REQUESTED));
        model.addAttribute("openChats", count("select count(c) from Conversation c where c.closed = false and c.mode = 'HUMAN'"));
        model.addAttribute("lowStock", products.stream().filter(Product::isLowStock).count());
        model.addAttribute("nearExpiry", count("select count(b) from Batch b where b.quantity > 0 and b.expDate between :a and :b",
                "a", today, "b", today.plusDays(settings.getInt("near_expiry_days"))));
        model.addAttribute("expired", count("select count(b) from Batch b where b.quantity > 0 and b.expDate < :a", "a", today));
        model.addAttribute("rxQueue", em.createQuery("select p from Prescription p join fetch p.user left join fetch p.order where p.status = :s order by p.createdAt",
                Prescription.class).setParameter("s", ApprovalStatus.PENDING).setMaxResults(5).getResultList());
        model.addAttribute("recentOrders", em.createQuery("select o from Order o where o.status in :s order by o.createdAt", Order.class)
                .setParameter("s", List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.PACKED)).setMaxResults(8).getResultList());
        return "staff/dashboard";
    }

    /* ---------------- Duyệt đơn thuốc ---------------- */

    @GetMapping("/prescriptions")
    @Transactional(readOnly = true)
    public String prescriptions(@RequestParam(defaultValue = "PENDING") String status, Model model) {
        ApprovalStatus st;
        try {
            st = ApprovalStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            st = ApprovalStatus.PENDING;
        }
        String base = "select p from Prescription p join fetch p.user left join fetch p.order left join fetch p.pharmacist where p.status = :s";
        var q = em.createQuery(base + (st == ApprovalStatus.PENDING ? " order by p.createdAt" : " order by p.reviewedAt desc"), Prescription.class).setParameter("s", st);
        if (st != ApprovalStatus.PENDING) q.setMaxResults(200);
        model.addAttribute("title", "Duyệt đơn thuốc");
        model.addAttribute("list", q.getResultList());
        model.addAttribute("status", st);
        model.addAttribute("statuses", ApprovalStatus.values());
        return "staff/prescriptions";
    }

    private Prescription rx(Long id) {
        return Web.found(em.find(Prescription.class, id));
    }

    @GetMapping("/prescriptions/{id}")
    @Transactional(readOnly = true)
    public String prescription(@PathVariable Long id, Model model) {
        Prescription rx = rx(id);
        Order o = rx.getOrder();
        Map<Long, Long> available = new HashMap<>();
        Map<Long, List<Product>> equivalents = new HashMap<>();
        List<Product> products = List.of();
        if (o != null) {
            for (OrderItem it : o.getItems()) {
                available.put(it.getId(), stock.fill(it.getProduct()).getAvailable());
                List<Product> eq = new ArrayList<>(catalog.equivalents(it.getProduct(), true));
                stock.fill(eq);
                equivalents.put(it.getId(), eq);
            }
        } else {
            products = new ArrayList<>(em.createQuery("select p from Product p where p.active = true and p.drugType <> :t order by p.name", Product.class)
                    .setParameter("t", DrugType.SPECIAL).getResultList());
            stock.fill(products);
        }
        List<Product> current = o != null ? o.getItems().stream().map(OrderItem::getProduct).toList() : List.of();
        model.addAttribute("title", "Đơn thuốc #" + rx.getId());
        model.addAttribute("rx", rx);
        model.addAttribute("order", o);
        model.addAttribute("available", available);
        model.addAttribute("equivalents", equivalents);
        model.addAttribute("products", products);
        model.addAttribute("rxChecks", OrderService.RX_CHECKS);
        model.addAttribute("rxValidDays", settings.getInt("rx_valid_days"));
        model.addAttribute("warnings", safety.check(rx.getUser(), current, safety.recentProducts(rx.getUser())));
        model.addAttribute("previous", o != null ? o.getPrescriptions().stream().filter(p -> !p.getId().equals(rx.getId())).toList() : List.of());
        return "staff/prescription";
    }

    @PostMapping("/prescriptions/{id}/approve")
    @Transactional
    public String approve(@PathVariable Long id, @RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        Order o = orders.approvePrescription(rx(id), currentUser.get(), new Form(params));
        Web.success(ra, o.getStatus() == OrderStatus.AWAITING_CUSTOMER
                ? "Đã duyệt/lên đơn " + o.getCode() + ". Đang chờ khách xác nhận." : "Đã duyệt đơn thuốc cho đơn hàng " + o.getCode() + ".");
        return "redirect:/staff/prescriptions";
    }

    @PostMapping("/prescriptions/{id}/reject")
    @Transactional
    public String reject(@PathVariable Long id, @RequestParam(name = "reject_reason", required = false) String reason, RedirectAttributes ra) {
        orders.rejectPrescription(rx(id), currentUser.get(), reason);
        Web.info(ra, "Đã từ chối đơn thuốc và thông báo cho khách hàng.");
        return "redirect:/staff/prescriptions";
    }

    /** Sổ bán thuốc kê đơn. */
    @GetMapping("/prescriptions/log")
    @Transactional(readOnly = true)
    public String rxLog(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        LocalDate t = to != null && !to.isBlank() ? LocalDate.parse(to) : LocalDate.now();
        LocalDate f = from != null && !from.isBlank() ? LocalDate.parse(from) : t.minusDays(30);
        List<Prescription> rows = em.createQuery("select p from Prescription p join fetch p.user left join fetch p.pharmacist left join p.order o "
                        + "where p.status = :s and (p.order is null or o.status <> :c) and p.reviewedAt between :a and :b order by p.reviewedAt desc", Prescription.class)
                .setParameter("s", ApprovalStatus.APPROVED).setParameter("c", OrderStatus.CANCELLED)
                .setParameter("a", f.atStartOfDay()).setParameter("b", t.atTime(23, 59, 59)).getResultList();
        model.addAttribute("title", "Duyệt đơn thuốc · Sổ thuốc kê đơn");
        model.addAttribute("rows", rows);
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("statuses", ApprovalStatus.values());
        return "staff/rx-log";
    }

    /* ---------------- Hồ sơ nhân viên ---------------- */

    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("title", "Tài khoản của tôi");
        model.addAttribute("me", currentUser.get());
        return "staff/profile";
    }

    @PostMapping("/profile/password")
    @Transactional
    public String changePassword(@RequestParam(required = false) String current, @RequestParam(required = false) String password,
                                 @RequestParam(name = "password_confirmation", required = false) String confirm, RedirectAttributes ra) {
        accounts.changePassword(currentUser.get(), current, password, confirm);
        Web.success(ra, "Đổi mật khẩu thành công.");
        return "redirect:/staff/profile";
    }
}
