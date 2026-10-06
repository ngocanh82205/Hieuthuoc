package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Bán hàng tại quầy: dùng chung tồn kho với kênh online, xuất kho FEFO ngay, cộng điểm cho khách thành viên. */
@Service
@RequiredArgsConstructor
@Transactional
public class PosService {
    public static final String WALK_IN_EMAIL = "khachle@vinapharma.local";

    private final PosCart cart;
    private final StockService stock;
    private final OrderService orders;
    private final NotificationService notifications;
    private final SettingService settings;
    private final SafetyService safety;
    private final CustomerService customers;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager em;

    @Getter
    public static class Line {
        private final String key;
        private final Product product;
        private final UnitOption unit;
        private final int quantity;
        private final long total;
        private String error;

        Line(String key, Product product, UnitOption unit, int quantity) {
            this.key = key;
            this.product = product;
            this.unit = unit;
            this.quantity = quantity;
            this.total = unit.price() * quantity;
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

    /* ---------------- Giỏ quầy (session) ---------------- */

    public void add(long productId, long unitId, int qty) {
        Product p = em.find(Product.class, productId);
        if (p == null) throw new BusinessException("Sản phẩm không tồn tại.");
        UnitOption u = p.findUnit(unitId);
        String k = Cart.key(p.getId(), u.id() == 0 ? null : u.id());
        cart.put(k, Math.min(9999, cart.get(k) + Math.max(1, qty)));
    }

    public void update(String key, int qty) {
        if (qty <= 0) cart.remove(key);
        else if (cart.has(key)) cart.put(key, Math.min(qty, 9999));
    }

    public void setCustomer(Long id) {
        cart.setCustomerId(id);
    }

    public void clear() {
        cart.clear();
    }

    @Transactional(readOnly = true)
    public User findCustomer(String phone) {
        List<User> l = em.createQuery("select u from User u where u.phone = :p and u.role = :r and u.locked = false", User.class)
                .setParameter("p", Texts.trim(phone)).setParameter("r", Role.CUSTOMER).setMaxResults(1).getResultList();
        if (l.isEmpty()) throw new BusinessException("Không tìm thấy khách hàng có số điện thoại này.");
        return l.get(0);
    }

    /** Hóa đơn đang lập: dòng hàng, tổng tiền, lỗi tồn kho, có thuốc kê đơn không. */
    @Transactional(readOnly = true)
    public View view() {
        View v = new View();
        Map<String, Integer> items = cart.items();
        v.customer = cart.customerId() != null ? em.find(User.class, cart.customerId()) : null;
        Set<Long> ids = new HashSet<>();
        for (String k : items.keySet()) ids.add(Cart.productIdOf(k));
        Map<Long, Product> products = new HashMap<>();
        if (!ids.isEmpty()) {
            List<Product> list = em.createQuery("select p from Product p where p.id in :ids", Product.class).setParameter("ids", ids).getResultList();
            stock.fill(list, false);
            for (Product p : list) products.put(p.getId(), p);
        }
        Map<Long, Integer> base = new HashMap<>();
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            Product p = products.get(Cart.productIdOf(e.getKey()));
            if (p == null) continue;
            UnitOption u = p.findUnit(Cart.unitIdOf(e.getKey()));
            Line l = new Line(e.getKey(), p, u, e.getValue());
            v.lines.add(l);
            v.total += l.total;
            base.merge(p.getId(), u.factor() * e.getValue(), Integer::sum);
            v.rxRequired = v.rxRequired || p.getDrugType().isPrescription();
        }
        Set<Long> reported = new HashSet<>();
        for (Line l : v.lines) {
            Product p = l.product;
            if (!p.isActive()) l.error = "Sản phẩm đã ngừng kinh doanh";
            else if (base.get(p.getId()) > p.getAvailable()) l.error = "Chỉ còn " + p.getAvailable() + " " + p.getUnit();
            if (l.error != null && reported.add(p.getId())) v.errors.add(p.getName() + ": " + l.error);
        }
        return v;
    }

    /** Tài khoản "Khách lẻ" dùng cho hóa đơn không gắn thành viên (bị khóa đăng nhập). */
    private User walkIn() {
        List<User> l = em.createQuery("select u from User u where u.email = :e", User.class).setParameter("e", WALK_IN_EMAIL).getResultList();
        if (!l.isEmpty()) return l.get(0);
        User u = new User();
        u.setEmail(WALK_IN_EMAIL);
        u.setRole(Role.CUSTOMER);
        u.setFullName("Khách lẻ tại quầy");
        u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID()));
        u.setLocked(true);
        em.persist(u);
        return u;
    }

    public Order checkout(User staff, Form f) {
        if (!staff.hasPermission(StaffPermission.POS)) throw BusinessException.forbidden("Bạn chưa được cấp quyền bán hàng tại quầy.");
        View v = view();
        if (v.lines.isEmpty()) throw new BusinessException("Hóa đơn chưa có sản phẩm.");
        if (!v.errors.isEmpty()) throw new BusinessException(String.join(" ", v.errors));
        PaymentMethod pm = "BANK_TRANSFER".equals(f.get("payment_method")) ? PaymentMethod.BANK_TRANSFER : PaymentMethod.CASH;
        LocalDate rxDate = null;
        String reuse = null;
        if (v.rxRequired) {
            if (Texts.trim(f.get("patient_name")).isEmpty() || Texts.trim(f.get("doctor_name")).isEmpty() || Texts.isBlank(f.get("rx_date"))) {
                throw new BusinessException("Hóa đơn có thuốc kê đơn - nhập tên bệnh nhân, bác sĩ kê đơn và ngày kê (ghi sổ bán thuốc kê đơn).");
            }
            if (staff.getRole() != Role.ADMIN && Texts.isBlank(staff.getLicenseNo())) {
                throw BusinessException.forbidden("Bán thuốc kê đơn phải do dược sĩ có chứng chỉ hành nghề (CCHN).");
            }
            try {
                rxDate = LocalDate.parse(f.get("rx_date").trim().substring(0, 10));
            } catch (RuntimeException e) {
                throw new BusinessException("Ngày kê đơn không hợp lệ.");
            }
            int days = settings.getInt("rx_valid_days");
            if (rxDate.isAfter(LocalDate.now()) || rxDate.plusDays(days).isBefore(LocalDate.now())) {
                throw new BusinessException("Đơn thuốc không còn hiệu lực (quá " + days + " ngày kể từ ngày kê).");
            }
            // Cùng tờ đơn đã bán ở hóa đơn / đơn hàng khác: dược sĩ phải xác nhận còn được bán tiếp
            reuse = orders.checkRxReuse(f.get("patient_name").trim(), f.get("doctor_name").trim(), rxDate, null, f.bool("reuse_confirmed"));
        }
        User member = v.customer;
        User customer = member != null ? member : walkIn();
        // Khách thành viên có hồ sơ sức khỏe: cảnh báo nguy hiểm phải được dược sĩ xác nhận đã tư vấn
        List<String> danger = member == null ? List.of() : safety.check(member, v.lines.stream().map(Line::getProduct).toList(), safety.recentProducts(member))
                .stream().filter(SafetyService.Warning::isDanger).map(SafetyService.Warning::message).toList();
        if (!danger.isEmpty() && !f.bool("safety_ack")) {
            throw new BusinessException("Có cảnh báo an toàn nguy hiểm: " + String.join(" ", danger)
                    + " Tư vấn cho khách rồi đánh dấu \"Đã kiểm tra cảnh báo an toàn\" để lập hóa đơn.");
        }
        Order o = new Order();
        o.setCode(orders.newCode("DH"));
        o.setChannel("POS");
        o.setUser(customer);
        o.setRecipient(customer.getFullName());
        o.setPhone(customer.getPhone() != null ? customer.getPhone() : "");
        o.setShippingMethod(ShippingMethod.PICKUP);
        o.setPaymentMethod(pm);
        o.setPaymentStatus(PaymentStatus.PAID);
        o.setNeedsPrescription(v.rxRequired);
        o.setNote(!Texts.isBlank(f.get("note")) ? Texts.trim(f.get("note"), 500) : null);
        o.setSubtotal(v.total);
        o.setTotal(v.total);
        o.setStatus(OrderStatus.COMPLETED);
        o.setCompletedAt(LocalDateTime.now().withNano(0));
        o.setHandler(staff);
        em.persist(o);
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
            em.persist(it);
        }
        stock.allocate(o);
        em.flush();
        o.setCostAmount(stock.costOf(o));
        if (member != null) {
            int earned = (int) Math.floor(o.getTotal() / (double) Math.max(1, settings.getLong("points_per_amount")) * customers.tier(member).getMultiplier());
            o.setPointsEarned(earned);
            orders.addPoints(member, earned);
        }
        if (v.rxRequired) {
            Prescription rx = new Prescription();
            rx.setOrder(o);
            rx.setUser(customer);
            rx.setStatus(ApprovalStatus.APPROVED);
            rx.setPharmacist(staff);
            rx.setPatientName(Texts.trim(f.get("patient_name"), 100));
            rx.setDoctorName(Texts.trim(f.get("doctor_name"), 100));
            rx.setClinic(!Texts.isBlank(f.get("clinic")) ? Texts.trim(f.get("clinic"), 200) : null);
            rx.setRxDate(rxDate);
            rx.setChecklist("pos" + (reuse != null ? ",reuse" : ""));
            rx.setPharmacistNote("Bán tại quầy - dược sĩ kiểm tra đơn giấy trực tiếp");
            rx.setReviewedAt(LocalDateTime.now().withNano(0));
            em.persist(rx);
        }
        orders.recordPayment(o, pm.name(), o.getTotal(), PaymentTransaction.SUCCESS, "Thanh toán tại quầy", staff, "PAYMENT", null, null);
        orders.addHistory(o, OrderStatus.COMPLETED, "Bán tại quầy - " + pm.getLabel(), staff);
        notifications.log(staff, "pos.sale", o.getCode() + " - " + o.getTotal());
        if (reuse != null) notifications.log(staff, "rx.reuse", o.getCode() + ": " + reuse);
        if (!danger.isEmpty()) notifications.log(staff, "safety.ack", o.getCode() + ": " + String.join(" ", danger));
        if (member != null && o.getPointsEarned() > 0) {
            notifications.notify(member, "Cảm ơn bạn đã mua hàng tại quầy. Bạn được cộng " + o.getPointsEarned() + " điểm.", "/account/orders/" + o.getCode());
        }
        clear();
        return o;
    }

    /* ======================= Hủy hóa đơn bán tại quầy ======================= */

    /** Nhân viên lập phiếu yêu cầu hủy hóa đơn, kèm tài khoản ngân hàng của khách để hoàn tiền; chờ admin duyệt. */
    public BillCancelRequest requestCancel(Order o, User staff, Form f) {
        if (!staff.hasPermission(StaffPermission.POS)) throw BusinessException.forbidden("Bạn chưa được cấp quyền bán hàng tại quầy.");
        if (!o.isPos() || o.getStatus() != OrderStatus.COMPLETED) throw new BusinessException("Chỉ hủy được hóa đơn bán tại quầy đã hoàn thành.");
        int days = settings.getInt("pos_cancel_days");
        if (o.isPosCancelExpired(days)) {
            throw new BusinessException("Hóa đơn " + o.getCode() + " đã quá thời hạn hủy (" + days + " ngày kể từ ngày bán, hết hạn "
                    + o.posCancelDeadline(days).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "). Xử lý theo quy trình đổi / trả hàng.");
        }
        long pending = em.createQuery("select count(r) from BillCancelRequest r where r.order.id = :o and r.status = :s", Long.class)
                .setParameter("o", o.getId()).setParameter("s", ApprovalStatus.PENDING).getSingleResult();
        if (pending > 0) throw new BusinessException("Hóa đơn " + o.getCode() + " đã có phiếu hủy đang chờ duyệt.");
        String reason = Texts.trim(f.get("reason"), 300);
        String bank = Texts.trim(f.get("bank_name"), 100);
        String account = Objects.requireNonNullElse(f.get("bank_account"), "").replaceAll("\\s+", "");
        String holder = Texts.limit(Texts.trim(f.get("account_holder")).toUpperCase(), 100, "");
        String phone = Objects.requireNonNullElse(f.get("customer_phone"), "").replaceAll("[\\s.\\-]+", "");
        if (Texts.mbLen(reason) < 5) throw new BusinessException("Vui lòng ghi lý do hủy hóa đơn.");
        if (!phone.matches("0\\d{9,10}")) throw new BusinessException("Vui lòng nhập số điện thoại của khách (10 - 11 số, bắt đầu bằng 0).");
        if (bank.isEmpty() || holder.isEmpty()) throw new BusinessException("Vui lòng nhập ngân hàng và tên chủ tài khoản của khách.");
        if (!account.matches("\\d{6,20}")) throw new BusinessException("Số tài khoản chỉ gồm chữ số (6 - 20 số).");
        BillCancelRequest r = new BillCancelRequest();
        r.setOrder(o);
        r.setRequester(staff);
        r.setReason(reason);
        r.setCustomerPhone(phone);
        r.setBankName(bank);
        r.setBankAccount(account);
        r.setAccountHolder(holder);
        r.setStatus(ApprovalStatus.PENDING);
        em.persist(r);
        orders.addHistory(o, o.getStatus(), "Yêu cầu hủy hóa đơn: " + reason, staff);
        notifications.notifyPermission("REFUND", "Yêu cầu hủy hóa đơn " + o.getCode() + " (" + OrderService.money(o.getTotal()) + ") chờ duyệt", "/staff/pos/history");
        notifications.log(staff, "pos.cancel_request", o.getCode() + ": " + reason);
        return r;
    }

    /**
     * Admin xác nhận hủy hóa đơn (nhân viên đã kiểm tra và nhận lại thuốc khi lập phiếu, nên không có bước từ chối):
     * hoàn hàng về lô, trừ điểm đã cộng, ghi hoàn tiền vào tài khoản khách, hóa đơn -> Đã hủy.
     */
    public BillCancelRequest approveCancel(BillCancelRequest r, User admin, String note0) {
        if (!admin.hasPermission(StaffPermission.REFUND)) throw BusinessException.forbidden("Chỉ quản trị viên được duyệt hủy hóa đơn.");
        if (r.getStatus() != ApprovalStatus.PENDING) throw new BusinessException("Phiếu hủy đã được xử lý.");
        String note = Texts.emptyToNull(Texts.trim(note0, 300));
        Order o = r.getOrder();
        orders.lockOrder(o);
        if (o.getStatus() != OrderStatus.COMPLETED) throw new BusinessException("Hóa đơn " + o.getCode() + " không còn ở trạng thái hoàn thành.");
        stock.restore(o);
        if (o.getPointsEarned() > 0 && o.getUser() != null && o.getUser().getRole() == Role.CUSTOMER) {
            User u = em.find(User.class, o.getUser().getId(), LockModeType.PESSIMISTIC_WRITE);
            em.refresh(u);
            u.setPoints(u.getPoints() - Math.min(u.getPoints(), o.getPointsEarned()));
        }
        String refund = "Hoàn tiền vào TK " + r.bankLabel() + (note != null ? " - mã GD " + note : "");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        o.setStatus(OrderStatus.CANCELLED);
        o.setReturnedAt(now);
        o.setReturnCost(o.getCostAmount());
        o.setPaymentStatus(PaymentStatus.REFUNDED);
        o.setRefundAmount(o.getTotal());
        o.setRefundedAt(now);
        o.setRefundedBy(admin);
        o.setRefundNote(Texts.limit(refund, 300, ""));
        for (PaymentTransaction t : em.createQuery("select t from PaymentTransaction t where t.order.id = :o and t.type = 'PAYMENT' and t.status = :s",
                PaymentTransaction.class).setParameter("o", o.getId()).setParameter("s", PaymentTransaction.SUCCESS).getResultList()) {
            t.setStatus(PaymentTransaction.REFUNDED);
        }
        orders.recordPayment(o, PaymentMethod.BANK_TRANSFER.name(), o.getTotal(), PaymentTransaction.SUCCESS, refund, admin, "REFUND", null, null);
        orders.addHistory(o, OrderStatus.CANCELLED, "Đã hủy hóa đơn: " + r.getReason() + ". " + refund, admin);
        r.setStatus(ApprovalStatus.APPROVED);
        r.setDecider(admin);
        r.setDecidedAt(now);
        r.setDecisionNote(note);
        if (r.getRequester() != null && !r.getRequester().getId().equals(admin.getId())) {
            notifications.notify(r.getRequester(), "Đã duyệt hủy hóa đơn " + o.getCode() + ".", "/staff/pos/history");
        }
        notifications.log(admin, "pos.cancel_approve", o.getCode() + (note != null ? ": " + note : ""));
        return r;
    }
}
