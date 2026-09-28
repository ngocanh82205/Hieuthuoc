package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
    private final OrderRepository orderRepo;
    private final PrescriptionRepository prescriptionRepo;
    private final MessageRepository messageRepo;
    private final UserRepository userRepo;
    private final BatchRepository batchRepo;
    private final ProductRepository productRepo;
    private final StockService stockService;
    private final ConversationRepository conversationRepo;

    public record DayRow(LocalDate day, long orders, long revenue) {
    }

    public record NameValue(String name, long value) {
    }

    public record ProductRow(String name, String unit, long quantity, long revenue) {
    }

    public record PharmacistRow(String name, String licenseNo, long approved, long rejected, Long avgMinutes, long messages, long orders,
                                long consultations, long revenue) {
        public double getRejectRate() {
            long total = approved + rejected;
            return total == 0 ? 0 : Math.round(1000.0 * rejected / total) / 10.0;
        }
    }

    public record ChannelRow(String name, long orders, long revenue) {
    }

    public record ExpiryRow(Batch batch, long days, long value) {
    }

    @Getter
    public static class Report {
        private LocalDate from;
        private LocalDate to;
        private long orders;
        private long revenue;
        private long discount;
        private long cost;
        private long created;
        private long cancelled;
        private long cancelledOnly;
        private long returned;
        private long customersOrdered;
        private long newCustomers;
        private long returningCustomers;
        private long registrations;
        private final List<NameValue> byMonth = new ArrayList<>();
        private final List<ChannelRow> byChannel = new ArrayList<>();
        private final List<ChannelRow> byStaff = new ArrayList<>();
        private final List<ExpiryRow> nearExpiry = new ArrayList<>();
        private final List<ExpiryRow> expired = new ArrayList<>();
        private final List<DayRow> byDay = new ArrayList<>();
        private final List<NameValue> byCategory = new ArrayList<>();
        private final List<ProductRow> topProducts = new ArrayList<>();
        private final List<Product> slowProducts = new ArrayList<>();
        private final List<PharmacistRow> pharmacists = new ArrayList<>();

        public long getAov() {
            return orders == 0 ? 0 : revenue / orders;
        }

        public long getGrossProfit() {
            return revenue - cost;
        }

        public double getCancelRate() {
            return created == 0 ? 0 : Math.round(1000.0 * cancelled / created) / 10.0;
        }

        public double getCancelOnlyRate() {
            return created == 0 ? 0 : Math.round(1000.0 * cancelledOnly / created) / 10.0;
        }

        public double getReturnRate() {
            return created == 0 ? 0 : Math.round(1000.0 * returned / created) / 10.0;
        }

        public double getReturningRate() {
            return customersOrdered == 0 ? 0 : Math.round(1000.0 * returningCustomers / customersOrdered) / 10.0;
        }

        public double getMarginRate() {
            return revenue == 0 ? 0 : Math.round(1000.0 * (revenue - cost) / revenue) / 10.0;
        }
    }

    public List<Order> completedBetween(LocalDate from, LocalDate to) {
        return orderRepo.findByStatusAndCompletedAtBetween(OrderStatus.COMPLETED, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    public Report build(LocalDate from, LocalDate to) {
        return build(from, to, 90);
    }

    public Report build(LocalDate from, LocalDate to, long nearExpiryDays) {
        Report r = new Report();
        r.from = from;
        r.to = to;
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<Order> orders = completedBetween(from, to);

        Map<LocalDate, long[]> days = new TreeMap<>();
        Map<String, Long> months = new TreeMap<>();
        Map<String, long[]> channels = new LinkedHashMap<>();
        channels.put("Online", new long[2]);
        channels.put("Tại quầy (POS)", new long[2]);
        Map<String, long[]> staff = new TreeMap<>();
        Map<String, Long> categories = new HashMap<>();
        Map<Long, ProductRow> products = new HashMap<>();
        Set<Long> soldIds = new HashSet<>();
        for (Order o : orders) {
            r.orders++;
            r.revenue += o.getTotal();
            r.discount += o.getDiscount();
            long[] d = days.computeIfAbsent(o.getCompletedAt().toLocalDate(), k -> new long[2]);
            d[0]++;
            d[1] += o.getTotal();
            months.merge(o.getCompletedAt().format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy")), o.getTotal(), Long::sum);
            long[] ch = channels.get(o.isPos() ? "Tại quầy (POS)" : "Online");
            ch[0]++;
            ch[1] += o.getTotal();
            User handler = o.getAssignedTo() != null ? o.getAssignedTo() : o.getHandledBy();
            long[] st = staff.computeIfAbsent(handler == null ? "(Chưa ghi nhận)" : handler.getFullName(), k -> new long[2]);
            st[0]++;
            st[1] += o.getTotal();
            for (OrderItem it : o.getItems()) {
                Product p = it.getProduct();
                soldIds.add(p.getId());
                String cat = p.getCategory() == null ? "Khác" : p.getCategory().getName();
                categories.merge(cat, it.getLineTotal(), Long::sum);
                products.merge(p.getId(), new ProductRow(p.getName(), p.getUnit(), it.getBaseQuantity(), it.getLineTotal()),
                        (a, b) -> new ProductRow(a.name(), a.unit(), a.quantity() + b.quantity(), a.revenue() + b.revenue()));
                for (OrderItemBatch a : it.getAllocations()) r.cost += a.getQuantity() * a.getBatch().getImportPrice();
            }
        }
        days.forEach((k, v) -> r.byDay.add(new DayRow(k, v[0], v[1])));
        months.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().substring(3) + e.getKey().substring(0, 2)))
                .forEach(e -> r.byMonth.add(new NameValue(e.getKey(), e.getValue())));
        channels.forEach((k, v) -> r.byChannel.add(new ChannelRow(k, v[0], v[1])));
        staff.entrySet().stream().sorted(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[1]).reversed())
                .forEach(e -> r.byStaff.add(new ChannelRow(e.getKey(), e.getValue()[0], e.getValue()[1])));
        categories.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> r.byCategory.add(new NameValue(e.getKey(), e.getValue())));
        products.values().stream().sorted(Comparator.comparingLong(ProductRow::quantity).reversed()).limit(10).forEach(r.topProducts::add);

        List<Product> active = stockService.fill(new ArrayList<>(productRepo.findByActiveTrueOrderByNameAsc()));
        active.stream().filter(p -> p.getOnHand() > 0 && !soldIds.contains(p.getId()))
                .sorted(Comparator.comparingLong(Product::getOnHand).reversed()).limit(10).forEach(r.slowProducts::add);

        r.created = orderRepo.countByCreatedAtBetween(start, end);
        r.cancelled = orderRepo.countByStatusInAndCreatedAtBetween(EnumSet.of(OrderStatus.CANCELLED, OrderStatus.RETURNED), start, end);
        r.cancelledOnly = orderRepo.countByStatusInAndCreatedAtBetween(EnumSet.of(OrderStatus.CANCELLED), start, end);
        r.returned = r.cancelled - r.cancelledOnly;

        // Khách hàng mới / quay lại: trong số khách có đơn hoàn thành trong kỳ
        Set<Long> seen = new HashSet<>();
        for (Order o : orders) {
            if (o.isPos() && o.getUser().isLocked()) continue; // khách lẻ tại quầy
            if (!seen.add(o.getUser().getId())) continue;
            r.customersOrdered++;
            if (orderRepo.countByUserAndStatusAndCompletedAtBefore(o.getUser(), OrderStatus.COMPLETED, start) > 0) r.returningCustomers++;
            else r.newCustomers++;
        }
        r.registrations = userRepo.countByRoleAndCreatedAtBetween(Role.CUSTOMER, start, end);

        // Hàng cận hạn / hết hạn (giá trị theo giá nhập)
        LocalDate today = LocalDate.now();
        for (Batch b : batchRepo.findNearExpiry(today, today.plusDays(nearExpiryDays))) {
            r.nearExpiry.add(new ExpiryRow(b, java.time.temporal.ChronoUnit.DAYS.between(today, b.getExpDate()), (long) b.getQuantity() * b.getImportPrice()));
        }
        for (Batch b : batchRepo.findExpired(today)) {
            r.expired.add(new ExpiryRow(b, java.time.temporal.ChronoUnit.DAYS.between(b.getExpDate(), today), (long) b.getQuantity() * b.getImportPrice()));
        }

        List<Prescription> reviewed = prescriptionRepo.findByReviewedAtBetween(start, end);
        for (User u : userRepo.findByRoleInOrderByRoleAscFullNameAsc(List.of(Role.PHARMACIST, Role.ADMIN))) {
            List<Prescription> mine = reviewed.stream().filter(p -> p.getPharmacist() != null && p.getPharmacist().getId().equals(u.getId())).toList();
            long approved = mine.stream().filter(p -> p.getStatus() == ApprovalStatus.APPROVED).count();
            long rejected = mine.stream().filter(p -> p.getStatus() == ApprovalStatus.REJECTED).count();
            Long avg = mine.isEmpty() ? null
                    : Math.round(mine.stream().mapToLong(p -> Duration.between(p.getCreatedAt(), p.getReviewedAt()).toMinutes()).average().orElse(0));
            long handled = orders.stream().filter(o -> o.getHandledBy() != null && o.getHandledBy().getId().equals(u.getId())).count();
            long revenue = orders.stream().filter(o -> o.getHandledBy() != null && o.getHandledBy().getId().equals(u.getId())).mapToLong(Order::getTotal).sum();
            r.pharmacists.add(new PharmacistRow(u.getFullName(), u.getLicenseNo(), approved, rejected, avg,
                    messageRepo.countBySenderAndCreatedAtBetween(u, start, end), handled,
                    conversationRepo.countByPharmacistAndUpdatedAtBetween(u, start, end), revenue));
        }
        r.pharmacists.sort(Comparator.comparingLong(PharmacistRow::approved).reversed());
        return r;
    }

    /** Doanh thu & số đơn hoàn thành theo từng ngày trong n ngày gần nhất (cho biểu đồ). */
    public List<DayRow> lastDays(int n) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(n - 1L);
        Map<LocalDate, long[]> days = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) days.put(d, new long[2]);
        for (Order o : completedBetween(from, to)) {
            long[] v = days.get(o.getCompletedAt().toLocalDate());
            if (v != null) {
                v[0]++;
                v[1] += o.getTotal();
            }
        }
        List<DayRow> rows = new ArrayList<>();
        days.forEach((k, v) -> rows.add(new DayRow(k, v[0], v[1])));
        return rows;
    }

    /** Giá trị tồn kho theo giá nhập: [còn hạn, cận hạn, hết hạn]. */
    public long[] inventoryValue(long nearDays) {
        LocalDate today = LocalDate.now();
        LocalDate far = LocalDate.of(9999, 12, 31);
        return new long[]{
                batchRepo.stockValueBetween(today, far),
                batchRepo.stockValueBetween(today, today.plusDays(nearDays)),
                batchRepo.stockValueBetween(LocalDate.of(1900, 1, 1), today.minusDays(1)),
        };
    }
}
