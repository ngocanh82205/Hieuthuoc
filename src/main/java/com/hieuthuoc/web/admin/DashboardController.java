package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Bảng điều khiển quản trị: chỉ số trong ngày / tháng, biểu đồ 14 ngày, việc cần xử lý. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class DashboardController {
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private final ReportService reports;
    private final SettingService settings;
    private final StockService stock;

    @PersistenceContext
    private EntityManager em;

    private long count(String jpql, Object... params) {
        var q = em.createQuery(jpql, Long.class);
        for (int i = 0; i + 1 < params.length; i += 2) q.setParameter((String) params[i], params[i + 1]);
        return q.getSingleResult();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String index(Model model) {
        LocalDate today = LocalDate.now();
        int near = settings.getInt("near_expiry_days");
        Map<String, long[]> days = reports.lastDays(14);
        List<Product> products = new ArrayList<>(em.createQuery("select p from Product p where p.active = true order by p.name", Product.class).getResultList());
        stock.fill(products, false);
        ReportService.Report r30 = reports.build(today.minusDays(29), today, near);
        long[] last = new ArrayList<>(days.values()).get(days.size() - 1);
        BigDecimal paid = em.createQuery("select coalesce(sum(t.amount), 0) from PaymentTransaction t where t.status = 'SUCCESS' and t.type = 'PAYMENT'"
                + " and t.paidAt >= :a and t.paidAt < :b", BigDecimal.class).setParameter("a", today.atStartOfDay()).setParameter("b", today.plusDays(1).atStartOfDay()).getSingleResult();
        Map<String, Long> statusCounts = new HashMap<>();
        for (Object[] r : em.createQuery("select o.status, count(o) from Order o group by o.status", Object[].class).getResultList()) {
            statusCounts.put(((OrderStatus) r[0]).name(), (Long) r[1]);
        }
        model.addAttribute("title", "Bảng điều khiển");
        model.addAttribute("revenueToday", last[1]);
        model.addAttribute("revenueMonth", reports.netRevenueSince(today.withDayOfMonth(1).atStartOfDay()));
        model.addAttribute("ordersToday", count("select count(o) from Order o where o.createdAt >= :a", "a", today.atStartOfDay()));
        model.addAttribute("processing", count("select count(o) from Order o where o.status in :s", "s",
                List.of(OrderStatus.PENDING_RX, OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.PACKED, OrderStatus.SHIPPING)));
        model.addAttribute("customers", count("select count(u) from User u where u.role = :r", "r", Role.CUSTOMER));
        model.addAttribute("newCustomers", count("select count(u) from User u where u.role = :r and u.createdAt >= :d", "r", Role.CUSTOMER, "d", today.minusDays(30).atStartOfDay()));
        model.addAttribute("inventoryValue", reports.inventoryValue(near)[0]);
        model.addAttribute("pendingReceipts", count("select count(r) from Receipt r where r.status = :s", "s", ApprovalStatus.PENDING));
        model.addAttribute("pendingRx", count("select count(p) from Prescription p where p.status = :s", "s", ApprovalStatus.PENDING));
        model.addAttribute("pendingResets", count("select count(p) from PasswordResetRequest p where p.handled = false"));
        model.addAttribute("paidOnlineToday", paid.longValue());
        model.addAttribute("chartLabels", days.keySet().stream().map(d -> LocalDate.parse(d).format(DM)).toList());
        model.addAttribute("chartRevenue", days.values().stream().map(v -> v[1]).toList());
        model.addAttribute("chartOrders", days.values().stream().map(v -> v[0]).toList());
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("top", r30.getTopProducts().stream().limit(5).toList());
        model.addAttribute("byPayment", r30.getByPayment());
        model.addAttribute("lowStock", products.stream().filter(Product::isLowStock).limit(6).toList());
        return "admin/dashboard";
    }
}
