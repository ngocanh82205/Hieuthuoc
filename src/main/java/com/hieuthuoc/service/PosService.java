package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.OrderRepository;
import com.hieuthuoc.repository.ProductRepository;
import com.hieuthuoc.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Bán hàng tại quầy: dùng chung tồn kho với kênh online, xuất kho FEFO ngay, cộng điểm cho khách thành viên. */
@Service
@RequiredArgsConstructor
@Transactional
public class PosService {
    public static final String WALK_IN_EMAIL = "khachle@vinapharma.local";

    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final OrderRepository orderRepo;
    private final StockService stockService;
    private final OrderService orderService;
    private final NotificationService notifications;
    private final SettingService settings;
    private final PasswordEncoder encoder;

    @Getter
    public static class Line {
        private final String key;
        private final Product product;
        private final UnitOption unit;
        private final int quantity;
        private String error;

        Line(String key, Product product, UnitOption unit, int quantity) {
            this.key = key;
            this.product = product;
            this.unit = unit;
            this.quantity = quantity;
        }

        public long getLineTotal() {
            return unit.price() * quantity;
        }
    }

    @Getter
    public static class View {
        private final List<Line> lines = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private long total;
        private boolean rxRequired;
        private User customer;
    }

    @Getter
    @Setter
    public static class CheckoutForm {
        private PaymentMethod paymentMethod = PaymentMethod.CASH;
        private Long cashReceived;
        private String patientName;
        private String doctorName;
        private String clinic;
        private LocalDate rxDate;
        private String note;
    }

    public View view(PosCart cart) {
        View v = new View();
        if (cart.getCustomerId() != null) v.customer = userRepo.findById(cart.getCustomerId()).orElse(null);
        Map<Long, Long> baseTotals = new HashMap<>();
        for (Map.Entry<String, Integer> e : cart.getItems().entrySet()) {
            Product p = productRepo.findById(Cart.productIdOf(e.getKey())).orElse(null);
            if (p == null) continue;
            UnitOption u = p.findUnit(Cart.unitIdOf(e.getKey()));
            if (u == null) continue;
            Line l = new Line(e.getKey(), p, u, e.getValue());
            v.lines.add(l);
            v.total += l.getLineTotal();
            baseTotals.merge(p.getId(), (long) u.factor() * e.getValue(), Long::sum);
            if (p.getDrugType() == DrugType.ETC || p.getDrugType() == DrugType.SPECIAL) v.rxRequired = true;
        }
        Set<Long> reported = new HashSet<>();
        for (Line l : v.lines) {
            stockService.fill(l.product);
            if (!l.product.isActive()) l.error = "Sản phẩm đã ngừng kinh doanh";
            else if (baseTotals.get(l.product.getId()) > l.product.getAvailable()) {
                l.error = "Chỉ còn " + l.product.getAvailable() + " " + l.product.getUnit();
            }
            if (l.error != null && reported.add(l.product.getId())) v.errors.add(l.product.getName() + ": " + l.error);
        }
        return v;
    }

    public void add(PosCart cart, Long productId, Long unitId, int qty) {
        Product p = productRepo.findById(productId).orElseThrow(() -> new BusinessException("Sản phẩm không tồn tại."));
        if (p.findUnit(unitId) == null) throw new BusinessException("Đơn vị tính không hợp lệ.");
        cart.getItems().merge(Cart.key(productId, unitId), Math.max(1, qty), Integer::sum);
    }

    public User findCustomer(String phone) {
        List<User> list = userRepo.findByPhone(Texts.trim(phone));
        return list.stream().filter(u -> u.getRole() == Role.CUSTOMER).findFirst()
                .orElseThrow(() -> new BusinessException("Không tìm thấy khách hàng có số điện thoại này."));
    }

    /** Tài khoản "Khách lẻ" dùng cho hóa đơn không gắn thành viên (bị khóa đăng nhập). */
    private User walkIn() {
        return userRepo.findByEmailIgnoreCase(WALK_IN_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setRole(Role.CUSTOMER);
            u.setFullName("Khách lẻ tại quầy");
            u.setEmail(WALK_IN_EMAIL);
            u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            u.setLocked(true);
            return userRepo.save(u);
        });
    }

    public Order checkout(PosCart cart, User staff, CheckoutForm f) {
        if (!staff.hasPermission(StaffPermission.POS)) throw new BusinessException("Bạn chưa được cấp quyền bán hàng tại quầy.");
        View v = view(cart);
        if (v.lines.isEmpty()) throw new BusinessException("Hóa đơn chưa có sản phẩm.");
        if (!v.errors.isEmpty()) throw new BusinessException(String.join(" ", v.errors));
        PaymentMethod pm = f.getPaymentMethod() == PaymentMethod.BANK_TRANSFER ? PaymentMethod.BANK_TRANSFER : PaymentMethod.CASH;
        if (pm == PaymentMethod.CASH && f.getCashReceived() != null && f.getCashReceived() < v.total) {
            throw new BusinessException("Tiền khách đưa chưa đủ.");
        }
        if (v.rxRequired) {
            if (Texts.isBlank(f.getPatientName()) || Texts.isBlank(f.getDoctorName()) || f.getRxDate() == null) {
                throw new BusinessException("Hóa đơn có thuốc kê đơn - nhập tên bệnh nhân, bác sĩ kê đơn và ngày kê (ghi sổ bán thuốc kê đơn).");
            }
            long days = settings.getLong("rx_valid_days");
            if (f.getRxDate().isAfter(LocalDate.now()) || f.getRxDate().plusDays(days).isBefore(LocalDate.now())) {
                throw new BusinessException("Đơn thuốc không còn hiệu lực (quá " + days + " ngày kể từ ngày kê).");
            }
        }
        User customer = v.customer != null ? v.customer : walkIn();
        Order o = new Order();
        o.setCode(orderService.newCode());
        o.setChannel("POS");
        o.setUser(customer);
        o.setRecipient(customer.getFullName());
        o.setPhone(customer.getPhone() == null ? "" : customer.getPhone());
        o.setShippingMethod(ShippingMethod.PICKUP);
        o.setPaymentMethod(pm);
        o.setNeedsPrescription(v.rxRequired);
        o.setNote(Texts.emptyToNull(Texts.trim(f.getNote(), 500)));
        for (Line l : v.lines) {
            OrderItem it = new OrderItem();
            it.setOrder(o);
            it.setProduct(l.product);
            it.setProductName(l.product.getName());
            it.setUnit(l.unit.name());
            it.setUnitFactor(l.unit.factor());
            it.setDrugType(l.product.getDrugType());
            it.setPrice(l.unit.price());
            it.setQuantity(l.quantity);
            o.getItems().add(it);
        }
        o.setSubtotal(v.total);
        o.setTotal(v.total);
        o.setStatus(OrderStatus.PREPARING);
        stockService.allocateFefo(o);
        o.setStatus(OrderStatus.COMPLETED);
        o.setPaymentStatus(PaymentStatus.PAID);
        o.setCompletedAt(LocalDateTime.now());
        o.setHandledBy(staff);
        if (v.customer != null) {
            MemberTier tier = MemberTier.of(orderRepo.totalSpent(customer));
            int earned = (int) Math.floor(o.getTotal() / (double) settings.getLong("points_per_amount") * tier.getPointMultiplier());
            o.setPointsEarned(earned);
            customer.setPoints(customer.getPoints() + earned);
        }
        if (v.rxRequired) {
            Prescription rx = new Prescription();
            rx.setOrder(o);
            rx.setUser(customer);
            rx.setStatus(ApprovalStatus.APPROVED);
            rx.setPharmacist(staff);
            rx.setPatientName(Texts.trim(f.getPatientName(), 100));
            rx.setDoctorName(Texts.trim(f.getDoctorName(), 100));
            rx.setClinic(Texts.emptyToNull(Texts.trim(f.getClinic(), 200)));
            rx.setRxDate(f.getRxDate());
            rx.setChecklist("pos");
            rx.setPharmacistNote("Bán tại quầy - dược sĩ kiểm tra đơn giấy trực tiếp");
            rx.setReviewedAt(LocalDateTime.now());
            o.getPrescriptions().add(rx);
        }
        orderService.addHistory(o, OrderStatus.COMPLETED, "Bán tại quầy - " + pm.getLabel()
                + (f.getCashReceived() != null && pm == PaymentMethod.CASH ? " (khách đưa " + f.getCashReceived() + ", thối " + (f.getCashReceived() - v.total) + ")" : ""), staff);
        orderRepo.save(o);
        notifications.log(staff, "pos.sale", o.getCode() + " - " + v.total);
        if (v.customer != null && o.getPointsEarnedValue() > 0) {
            notifications.notify(customer, "Cảm ơn bạn đã mua hàng tại quầy. Bạn được cộng " + o.getPointsEarnedValue() + " điểm.", "/account/orders/" + o.getCode());
        }
        cart.clear();
        return o;
    }
}
