package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Báo cáo: doanh thu theo ngày/tháng/năm/kênh/danh mục/phương thức thanh toán/nhân viên, bán chạy, tồn kho, dược sĩ... */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
    private final StockService stock;
    private final Sql sql;

    @PersistenceContext
    private EntityManager em;

    public record ProductRow(String name, String unit, long quantity, long revenue) {
    }

    public record ExpiryRow(Batch batch, long days, long value) {
    }

    public record PharmacistRow(String name, String license, long approved, long rejected, double rejectRate, Long avgMinutes,
                                long messages, long consultations, long orders, long revenue) {
    }

    /** Kết quả báo cáo; map [số đơn, doanh thu] giữ thứ tự hiển thị. */
    @Getter
    public static class Report {
        private LocalDate from;
        private LocalDate to;
        private long orders;
        private long revenue;
        private long grossSales;
        private long returnsValue;
        private long returnsCount;
        private long discount;
        private long cost;
        private long shipping;
        private long created;
        private long cancelled;
        private long cancelledOnly;
        private long returned;
        private long customersOrdered;
        private long newCustomers;
        private long returningCustomers;
        private long registrations;
        private long aov;
        private long grossProfit;
        private double marginRate;
        private double cancelRate;
        private double cancelOnlyRate;
        private double returnRate;
        private double returningRate;
        private final Map<String, long[]> byDay = new TreeMap<>();
        private final Map<String, Long> byMonth = new TreeMap<>();
        private final Map<String, Long> byYear = new TreeMap<>();
        private final Map<String, long[]> byChannel = new LinkedHashMap<>();
        private Map<String, long[]> byPayment = new LinkedHashMap<>();
        private Map<String, long[]> byStaff = new LinkedHashMap<>();
        private Map<String, Long> byCategory = new LinkedHashMap<>();
        private final List<ProductRow> topProducts = new ArrayList<>();
        private final List<Product> slowProducts = new ArrayList<>();
        private final List<PharmacistRow> pharmacists = new ArrayList<>();
        private final List<ExpiryRow> nearExpiry = new ArrayList<>();
        private final List<ExpiryRow> expired = new ArrayList<>();
    }

    private static LocalDateTime start(LocalDate d) {
        return d.atStartOfDay();
    }

    private static LocalDateTime end(LocalDate d) {
        return d.atTime(23, 59, 59);
    }

    /** Đơn đang ở trạng thái hoàn thành trong kỳ (file liên thông Dược Quốc gia: bán ra theo lô còn hiệu lực). */
    public List<Order> completedBetween(LocalDate from, LocalDate to) {
        return em.createQuery("select distinct o from Order o left join fetch o.items where o.status = :s and o.completedAt between :a and :b order by o.completedAt", Order.class)
                .setParameter("s", OrderStatus.COMPLETED).setParameter("a", start(from)).setParameter("b", end(to)).getResultList();
    }

    /** Doanh số ghi nhận trong kỳ: mọi đơn hoàn thành trong kỳ, kể cả đơn sau đó bị trả / hủy hóa đơn (phần trả ghi giảm ở kỳ trả). */
    public List<Order> salesBetween(LocalDate from, LocalDate to) {
        return em.createQuery("select distinct o from Order o left join fetch o.items where o.completedAt is not null and o.completedAt between :a and :b", Order.class)
                .setParameter("a", start(from)).setParameter("b", end(to)).getResultList();
    }

    /** Hàng bán bị trả lại / hóa đơn bị hủy trong kỳ (ghi giảm doanh thu vào ngày trả). */
    public List<Order> returnsBetween(LocalDate from, LocalDate to) {
        return em.createQuery("select distinct o from Order o left join fetch o.items where o.returnedAt is not null and o.returnedAt between :a and :b", Order.class)
                .setParameter("a", start(from)).setParameter("b", end(to)).getResultList();
    }

    /** Doanh thu tiền hàng của đơn: không gồm phí giao hàng (thu hộ, trả cho đơn vị vận chuyển). */
    public static long goodsValue(Order o) {
        return o.getTotal() - o.getShippingFee();
    }

    public Report build(LocalDate from, LocalDate to) {
        return build(from, to, 90);
    }

    /**
     * Doanh thu thuần = tiền hàng các đơn hoàn thành trong kỳ - tiền hàng bị trả trong kỳ (không gồm phí ship).
     * Giá vốn = giá vốn đơn hoàn thành - giá vốn hàng trả được nhập lại kho.
     */
    public Report build(LocalDate from, LocalDate to, long nearDays) {
        Report r = new Report();
        r.from = from;
        r.to = to;
        LocalDateTime start = start(from);
        LocalDateTime end = end(to);
        List<Order> orders = salesBetween(from, to);
        List<Order> returns = returnsBetween(from, to);
        r.byChannel.put("Online", new long[2]);
        r.byChannel.put("Tại quầy (POS)", new long[2]);
        Map<String, long[]> byPayment = new HashMap<>();
        Map<String, long[]> byStaff = new HashMap<>();
        Map<String, Long> byCategory = new HashMap<>();
        Map<Long, long[]> products = new LinkedHashMap<>();
        Map<Long, String[]> productNames = new HashMap<>();
        Set<Long> sold = new HashSet<>();
        Map<Long, long[]> handled = new HashMap<>();

        for (Order o : orders) {
            r.orders++;
            r.grossSales += goodsValue(o);
            r.discount += o.getDiscount() + o.getPromoDiscount() + o.getPointsDiscount();
            r.shipping += o.getShippingFee();
            r.cost += o.getCostAmount();
        }
        for (Order o : returns) {
            r.returnsCount++;
            r.returnsValue += goodsValue(o);
            r.cost -= o.getReturnCost();
        }
        // sign = 1: bán ra (ghi vào ngày hoàn thành); -1: trả lại (ghi vào ngày trả)
        List<Object[]> entries = new ArrayList<>();
        for (Order o : orders) entries.add(new Object[]{o, 1, o.getCompletedAt()});
        for (Order o : returns) entries.add(new Object[]{o, -1, o.getReturnedAt()});
        for (Object[] e : entries) {
            Order o = (Order) e[0];
            int sign = (int) e[1];
            LocalDateTime at = (LocalDateTime) e[2];
            long value = sign * goodsValue(o);
            int n = sign > 0 ? 1 : 0;
            long[] d = r.byDay.computeIfAbsent(at.toLocalDate().toString(), k -> new long[2]);
            d[0] += n;
            d[1] += value;
            r.byMonth.merge(at.format(DateTimeFormatter.ofPattern("yyyy-MM")), value, Long::sum);
            r.byYear.merge(String.valueOf(at.getYear()), value, Long::sum);
            long[] ch = r.byChannel.get(o.isPos() ? "Tại quầy (POS)" : "Online");
            ch[0] += n;
            ch[1] += value;
            long[] pm = byPayment.computeIfAbsent(o.getPaymentMethod().getShortLabel(), k -> new long[2]);
            pm[0] += n;
            pm[1] += value;
            long[] st = byStaff.computeIfAbsent(o.getHandler() != null ? o.getHandler().getFullName() : "(Chưa ghi nhận)", k -> new long[2]);
            st[0] += n;
            st[1] += value;
            if (o.getHandledBy() != null) {
                long[] h = handled.computeIfAbsent(o.getHandledBy(), k -> new long[2]);
                h[0] += n;
                h[1] += value;
            }
            for (OrderItem it : o.getItems()) {
                Product p = it.getProduct();
                if (p == null) continue;
                if (sign > 0) sold.add(p.getId());
                String cat = p.getCategory() != null ? p.getCategory().getName() : "Khác";
                byCategory.merge(cat, sign * it.lineTotal(), Long::sum);
                productNames.putIfAbsent(p.getId(), new String[]{p.getName(), p.getUnit()});
                long[] pr = products.computeIfAbsent(p.getId(), k -> new long[2]);
                pr[0] += (long) sign * it.baseQuantity();
                pr[1] += sign * it.lineTotal();
            }
        }
        r.revenue = r.grossSales - r.returnsValue;
        byCategory.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> r.byCategory.put(e.getKey(), e.getValue()));
        byStaff.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .forEach(e -> r.byStaff.put(e.getKey(), e.getValue()));
        byPayment.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .forEach(e -> r.byPayment.put(e.getKey(), e.getValue()));
        products.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0])).limit(10)
                .forEach(e -> r.topProducts.add(new ProductRow(productNames.get(e.getKey())[0], productNames.get(e.getKey())[1], e.getValue()[0], e.getValue()[1])));

        List<Product> active = new ArrayList<>(em.createQuery("select p from Product p where p.active = true order by p.name", Product.class).getResultList());
        stock.fill(active, false);
        active.stream().filter(p -> p.getOnHand() > 0 && !sold.contains(p.getId()))
                .sorted(Comparator.comparingLong(Product::getOnHand).reversed()).limit(10).forEach(r.slowProducts::add);

        r.created = count("select count(o) from Order o where o.createdAt between :a and :b", start, end, null);
        r.cancelledOnly = count("select count(o) from Order o where o.createdAt between :a and :b and o.status = :s", start, end, OrderStatus.CANCELLED);
        r.returned = count("select count(o) from Order o where o.createdAt between :a and :b and o.status = :s", start, end, OrderStatus.RETURNED);
        r.cancelled = r.cancelledOnly + r.returned;

        // Khách mới / quay lại trong số khách có đơn hoàn thành trong kỳ
        Set<Long> seen = new HashSet<>();
        for (Order o : orders) {
            User u = o.getUser();
            if (u == null || (u.isLocked() && u.getEmail() == null) || !seen.add(u.getId())) continue;
            r.customersOrdered++;
            long before = em.createQuery("select count(o) from Order o where o.user.id = :u and o.status = :s and o.completedAt < :a", Long.class)
                    .setParameter("u", u.getId()).setParameter("s", OrderStatus.COMPLETED).setParameter("a", start).getSingleResult();
            if (before > 0) r.returningCustomers++;
            else r.newCustomers++;
        }
        r.registrations = em.createQuery("select count(u) from User u where u.role = :r and u.createdAt between :a and :b", Long.class)
                .setParameter("r", Role.CUSTOMER).setParameter("a", start).setParameter("b", end).getSingleResult();

        LocalDate today = LocalDate.now();
        for (Batch b : em.createQuery("select b from Batch b join fetch b.product where b.quantity > 0 and b.expDate between :a and :b order by b.expDate", Batch.class)
                .setParameter("a", today).setParameter("b", today.plusDays(nearDays)).getResultList()) {
            r.nearExpiry.add(new ExpiryRow(b, b.daysLeft(), (long) b.getQuantity() * b.getImportPrice()));
        }
        for (Batch b : em.createQuery("select b from Batch b join fetch b.product where b.quantity > 0 and b.expDate < :a order by b.expDate", Batch.class)
                .setParameter("a", today).getResultList()) {
            r.expired.add(new ExpiryRow(b, -b.daysLeft(), (long) b.getQuantity() * b.getImportPrice()));
        }

        List<Prescription> reviewed = em.createQuery("select p from Prescription p where p.reviewedAt between :a and :b", Prescription.class)
                .setParameter("a", start).setParameter("b", end).getResultList();
        List<User> staff = em.createQuery("select u from User u where u.role in :r order by u.fullName", User.class)
                .setParameter("r", List.of(Role.PHARMACIST, Role.ADMIN)).getResultList();
        for (User u : staff) {
            List<Prescription> mine = reviewed.stream().filter(p -> p.getPharmacist() != null && p.getPharmacist().getId().equals(u.getId())).toList();
            long approved = mine.stream().filter(p -> p.getStatus() == ApprovalStatus.APPROVED).count();
            long rejected = mine.stream().filter(p -> p.getStatus() == ApprovalStatus.REJECTED).count();
            Long avg = mine.isEmpty() ? null : Math.round(mine.stream()
                    .mapToLong(p -> Duration.between(p.getCreatedAt(), p.getReviewedAt()).toMinutes()).average().orElse(0));
            long messages = em.createQuery("select count(m) from Message m where m.sender.id = :u and m.createdAt between :a and :b", Long.class)
                    .setParameter("u", u.getId()).setParameter("a", start).setParameter("b", end).getSingleResult();
            long consultations = em.createQuery("select count(c) from Conversation c where c.pharmacist.id = :u and c.updatedAt between :a and :b", Long.class)
                    .setParameter("u", u.getId()).setParameter("a", start).setParameter("b", end).getSingleResult();
            long[] h = handled.getOrDefault(u.getId(), new long[2]);
            r.pharmacists.add(new PharmacistRow(u.getFullName(), u.getLicenseNo(), approved, rejected,
                    approved + rejected > 0 ? Math.round(1000.0 * rejected / (approved + rejected)) / 10.0 : 0, avg, messages, consultations, h[0], h[1]));
        }
        r.pharmacists.sort(Comparator.comparingLong(PharmacistRow::approved).reversed());

        r.aov = r.orders > 0 ? r.grossSales / r.orders : 0;
        r.grossProfit = r.revenue - r.cost;
        r.marginRate = r.revenue != 0 ? Math.round(1000.0 * r.grossProfit / r.revenue) / 10.0 : 0;
        r.cancelRate = rate(r.cancelled, r.created);
        r.cancelOnlyRate = rate(r.cancelledOnly, r.created);
        r.returnRate = rate(r.returned, r.created);
        r.returningRate = rate(r.returningCustomers, r.customersOrdered);
        return r;
    }

    private static double rate(long a, long b) {
        return b > 0 ? Math.round(1000.0 * a / b) / 10.0 : 0;
    }

    private long count(String jpql, LocalDateTime a, LocalDateTime b, OrderStatus s) {
        var q = em.createQuery(jpql, Long.class).setParameter("a", a).setParameter("b", b);
        if (s != null) q.setParameter("s", s);
        return q.getSingleResult();
    }

    /** Doanh thu thuần (tiền hàng, đã trừ hàng trả) & số đơn hoàn thành theo ngày trong n ngày gần nhất (biểu đồ). */
    public Map<String, long[]> lastDays(int n) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(n - 1L);
        Map<String, long[]> days = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) days.put(d.toString(), new long[2]);
        for (Order o : em.createQuery("select o from Order o where o.completedAt between :a and :b", Order.class)
                .setParameter("a", start(from)).setParameter("b", end(to)).getResultList()) {
            long[] v = days.get(o.getCompletedAt().toLocalDate().toString());
            if (v != null) {
                v[0]++;
                v[1] += goodsValue(o);
            }
        }
        for (Order o : em.createQuery("select o from Order o where o.returnedAt between :a and :b", Order.class)
                .setParameter("a", start(from)).setParameter("b", end(to)).getResultList()) {
            long[] v = days.get(o.getReturnedAt().toLocalDate().toString());
            if (v != null) v[1] -= goodsValue(o);
        }
        return days;
    }

    /** Doanh thu thuần (tiền hàng, đã trừ hàng trả) từ from đến hiện tại. */
    public long netRevenueSince(LocalDateTime from) {
        long sales = sql.scalar("select coalesce(sum(total - shipping_fee), 0) from orders where completed_at is not null and completed_at >= :f", Map.of("f", from));
        long returns = sql.scalar("select coalesce(sum(total - shipping_fee), 0) from orders where returned_at is not null and returned_at >= :f", Map.of("f", from));
        return sales - returns;
    }

    /** Giá trị tồn kho theo giá nhập: [còn hạn, cận hạn, hết hạn]. */
    public long[] inventoryValue(long nearDays) {
        LocalDate today = LocalDate.now();
        String base = "select coalesce(sum(quantity * import_price), 0) from batches where quantity > 0 and ";
        return new long[]{
                sql.scalar(base + "exp_date >= :t", Map.of("t", today)),
                sql.scalar(base + "exp_date between :t and :n", Map.of("t", today, "n", today.plusDays(nearDays))),
                sql.scalar(base + "exp_date < :t", Map.of("t", today)),
        };
    }

    /** Khoảng ngày báo cáo: mặc định 30 ngày gần nhất; đảo lại nếu nhập ngược. */
    public static LocalDate[] range(String from, String to) {
        LocalDate t = parse(to, LocalDate.now());
        LocalDate f = parse(from, t.minusDays(29));
        return f.isAfter(t) ? new LocalDate[]{t, f} : new LocalDate[]{f, t};
    }

    private static LocalDate parse(String s, LocalDate def) {
        try {
            return s == null || s.isBlank() ? def : LocalDate.parse(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }
}
