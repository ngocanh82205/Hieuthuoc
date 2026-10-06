package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    public static final Map<String, String> RX_CHECKS = new LinkedHashMap<>();

    static {
        RX_CHECKS.put("valid", "Đơn thuốc hợp lệ (đủ thông tin bệnh nhân, cơ sở khám bệnh)");
        RX_CHECKS.put("date", "Còn hiệu lực (trong thời hạn kể từ ngày kê)");
        RX_CHECKS.put("sign", "Có chữ ký bác sĩ và dấu cơ sở khám bệnh");
        RX_CHECKS.put("match", "Thuốc và liều dùng khớp với đơn");
    }

    private final CartService carts;
    private final StockService stock;
    private final FileStorageService files;
    private final NotificationService notifications;
    private final SettingService settings;
    private final CatalogService catalog;
    private final MailService mail;
    private final GhnService ghn;
    private final PromotionService promotions;
    private final VoucherService vouchers;
    private final SafetyService safety;
    private final CustomerService customers;
    private final Sql sql;

    @PersistenceContext
    private EntityManager em;

    /* ============================ Đặt hàng ============================ */

    public static List<String> validateDelivery(String recipient, String phone, ShippingMethod method, String address) {
        List<String> errors = new ArrayList<>();
        if (Texts.mbLen(Texts.trim(recipient)) < 2) errors.add("Vui lòng nhập tên người nhận.");
        if (!Texts.isPhone(phone)) errors.add("Số điện thoại người nhận không hợp lệ (10-11 số, bắt đầu bằng 0).");
        if (method == ShippingMethod.DELIVERY && Texts.mbLen(Texts.trim(address)) < 10) errors.add("Vui lòng nhập địa chỉ giao hàng đầy đủ.");
        return errors;
    }

    public static List<String> validateVat(boolean request, String company, String taxCode, String address, String email) {
        List<String> errors = new ArrayList<>();
        if (!request) return errors;
        if (Texts.mbLen(Texts.trim(company)) < 3) errors.add("Nhập tên công ty / đơn vị để xuất hóa đơn VAT.");
        if (!Texts.trim(taxCode).matches("\\d{10}(-\\d{3})?")) errors.add("Mã số thuế không hợp lệ (10 số hoặc 10 số-3 số).");
        if (Texts.mbLen(Texts.trim(address)) < 10) errors.add("Nhập địa chỉ xuất hóa đơn.");
        if (email != null && !email.isEmpty() && !Texts.isEmail(email.trim())) errors.add("Email nhận hóa đơn không hợp lệ.");
        return errors;
    }

    /** Phí giao hàng: GHN (nếu đã cấu hình và có mã địa giới) hoặc phí cố định; đủ ngưỡng thì miễn phí. */
    public long shippingFeeFor(ShippingMethod method, long amount, String province, Integer districtId, String wardCode, long weight, long insurance) {
        if (method == ShippingMethod.PICKUP) return 0;
        if (ghn.enabled() && districtId != null && districtId > 0 && wardCode != null && !wardCode.isEmpty()) {
            if (amount >= settings.getLong("free_ship_threshold")) return 0;
            Long fee = ghn.fee(districtId, wardCode, weight, insurance);
            if (fee != null) return fee;
        }
        return settings.shippingFee(method, amount);
    }

    private static ShippingMethod shippingOf(String s) {
        try {
            return s == null ? ShippingMethod.DELIVERY : ShippingMethod.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ShippingMethod.DELIVERY;
        }
    }

    private static PaymentMethod paymentOf(String s) {
        PaymentMethod m = PaymentMethod.tryFrom(s);
        return m == null ? PaymentMethod.COD : m;
    }

    /**
     * Đặt hàng từ giỏ.
     * f: recipient, phone, address, province, ghn_province_id, ghn_district_id, ghn_ward_code, shipping_method, payment_method,
     * note, rx_note, save_address, request_vat, vat_company, vat_tax_code, vat_address, vat_email
     */
    public Order placeOrder(User user, Cart cart, Form f, MultipartFile rxFile) {
        ShippingMethod method = shippingOf(f.get("shipping_method"));
        PaymentMethod pay = paymentOf(f.get("payment_method"));
        if (!PaymentMethod.ONLINE_METHODS.contains(pay)) pay = PaymentMethod.COD;
        String province = method == ShippingMethod.DELIVERY ? Texts.emptyToNull(f.get("province")) : null;
        cart.setProvince(province);
        CartService.View cv = carts.build(cart, method, user);

        List<String> errors = new ArrayList<>(cv.getErrors());
        if (!settings.enabledPaymentMethods().contains(pay)) errors.add("Phương thức thanh toán này đang tạm ngừng, vui lòng chọn cách khác.");
        if (method == ShippingMethod.DELIVERY) {
            if (ghn.enabled()) {
                // Địa giới theo danh mục của GHN
                if (province == null || Texts.isBlank(f.get("ghn_district_id")) || Texts.isBlank(f.get("ghn_ward_code"))) {
                    errors.add("Vui lòng chọn tỉnh/thành, quận/huyện và phường/xã để tính phí giao hàng.");
                }
            } else if (!settings.isProvince(province)) {
                errors.add("Vui lòng chọn tỉnh/thành phố giao hàng.");
            }
        }
        boolean wantVat = f.bool("request_vat");
        errors.addAll(validateVat(wantVat, f.get("vat_company"), f.get("vat_tax_code"), f.get("vat_address"), f.get("vat_email")));
        if (cv.getLines().isEmpty()) errors.add("Giỏ hàng đang trống.");
        if (cv.getVoucherError() != null) errors.add(cv.getVoucherError());
        errors.addAll(validateDelivery(f.get("recipient"), f.get("phone"), method, f.get("address")));
        if (cv.isRxRequired() && !files.isPresent(rxFile)) errors.add("Giỏ hàng có thuốc kê đơn - vui lòng tải lên ảnh đơn thuốc hợp lệ.");
        if (!errors.isEmpty()) throw new ValidationException(new ArrayList<>(new LinkedHashSet<>(errors)));
        String rxImage = cv.isRxRequired() ? files.store("prescriptions", rxFile) : null;

        Integer districtId = method == ShippingMethod.DELIVERY && !Texts.isBlank(f.get("ghn_district_id")) ? Texts.toInt(f.get("ghn_district_id"), 0) : null;
        String wardCode = method == ShippingMethod.DELIVERY && !Texts.isBlank(f.get("ghn_ward_code")) ? f.get("ghn_ward_code") : null;
        long shippingFee = cv.getLines().isEmpty() ? 0
                : shippingFeeFor(method, cv.getAfterDiscount(), province, districtId, wardCode, cv.getWeight(), cv.getSubtotal());

        lockAndRecheck(user, cv);
        Order o = new Order();
        o.setCode(newCode("DH"));
        o.setUser(user);
        o.setRecipient(Texts.trim(f.get("recipient"), 100));
        o.setPhone(Texts.trim(f.get("phone")));
        o.setAddress(method == ShippingMethod.PICKUP ? null : Texts.trim(f.get("address"), 300));
        o.setProvince(province);
        o.setGhnDistrictId(districtId);
        o.setGhnWardCode(wardCode);
        o.setShippingMethod(method);
        o.setPaymentMethod(pay);
        o.setPaymentStatus(PaymentStatus.UNPAID);
        o.setVatCompany(wantVat ? Texts.trim(f.get("vat_company"), 200) : null);
        o.setVatTaxCode(wantVat ? Texts.trim(f.get("vat_tax_code")) : null);
        o.setVatAddress(wantVat ? Texts.trim(f.get("vat_address"), 300) : null);
        o.setVatEmail(wantVat && !Texts.isBlank(f.get("vat_email")) ? f.get("vat_email").trim() : null);
        o.setSubtotal(cv.getSubtotal());
        o.setDiscount(cv.getDiscount());
        o.setPromoDiscount(cv.getPromoDiscount());
        o.setPointsUsed(cv.getPointsUsed());
        o.setPointsDiscount(cv.getPointsDiscount());
        o.setShippingFee(shippingFee);
        o.setTotal(cv.getAfterDiscount() + shippingFee);
        o.setVoucherCode(cv.getVoucher() != null ? cv.getVoucher().getCode() : null);
        o.setNeedsPrescription(cv.isRxRequired());
        o.setStatus(cv.isRxRequired() ? OrderStatus.PENDING_RX : OrderStatus.PENDING);
        o.setNote(!Texts.isBlank(f.get("note")) ? Texts.trim(f.get("note"), 500) : null);
        o.setChannel("ONLINE");
        em.persist(o);
        for (CartService.Line line : cv.getLines()) {
            OrderItem it = newItem(o, line.getProduct(), line.getUnit(), line.getQuantity(), line.getUnitPrice());
            if (line.getFlashPromotionId() != null) {
                it.setFlashPromotion(em.getReference(Promotion.class, line.getFlashPromotionId()));
                sql.update("update promotions set sold_count = sold_count + :n where id = :id", Map.of("n", line.getBase(), "id", line.getFlashPromotionId()));
            }
        }
        // Quà tặng kèm (mua X tặng Y): dòng giá 0, được giữ chỗ và xuất kho như hàng bán
        List<String> notes = new ArrayList<>(cv.getPromoNotes().stream().filter(n -> n.startsWith("Combo")).toList());
        for (PromotionService.Gift g : cv.getGifts()) {
            Product gp = g.product();
            OrderItem it = newItem(o, gp, gp.unitOptions().get(0), g.quantity(), 0L);
            it.setProductName(gp.getName() + " (quà tặng)");
            it.setGift(true);
            notes.add("Tặng " + g.quantity() + " " + gp.getUnit() + " " + gp.getName());
        }
        o.setPromoNote(notes.isEmpty() ? null : Texts.trim(String.join("; ", notes), 500));
        if (rxImage != null) {
            Prescription rx = new Prescription();
            rx.setOrder(o);
            rx.setUser(user);
            rx.setImage(rxImage);
            rx.setCustomerNote(!Texts.isBlank(f.get("rx_note")) ? Texts.trim(f.get("rx_note"), 500) : null);
            em.persist(rx);
        }
        if (cv.getVoucher() != null) {
            sql.update("update vouchers set used_count = used_count + 1 where id = :id", Map.of("id", cv.getVoucher().getId()));
        }
        if (cv.getPointsUsed() > 0) addPoints(user, -cv.getPointsUsed());
        if (f.bool("save_address") && method == ShippingMethod.DELIVERY) {
            long exists = em.createQuery("select count(a) from Address a where a.user.id = :u and a.addressLine = :l", Long.class)
                    .setParameter("u", user.getId()).setParameter("l", o.getAddress()).getSingleResult();
            if (exists == 0) {
                long any = em.createQuery("select count(a) from Address a where a.user.id = :u", Long.class).setParameter("u", user.getId()).getSingleResult();
                Address a = new Address();
                a.setUser(user);
                a.setRecipient(o.getRecipient());
                a.setPhone(o.getPhone());
                a.setAddressLine(o.getAddress());
                a.setProvince(province);
                a.setGhnDistrictId(districtId);
                a.setGhnWardCode(wardCode);
                a.setGhnProvinceId(!Texts.isBlank(f.get("ghn_province_id")) ? Texts.toInt(f.get("ghn_province_id"), 0) : null);
                a.setDefault(any == 0);
                em.persist(a);
            }
        }
        addHistory(o, o.getStatus(), (cv.isRxRequired() ? "Khách đặt hàng kèm đơn thuốc" : "Khách đặt hàng")
                + (cv.getPointsUsed() > 0 ? " (dùng " + cv.getPointsUsed() + " điểm)" : ""), user);

        notifications.notifyStaff(cv.isRxRequired() ? "Đơn " + o.getCode() + " có thuốc kê đơn cần duyệt" : "Đơn hàng mới " + o.getCode(),
                cv.isRxRequired() ? "/staff/prescriptions" : "/staff/orders/" + o.getId());
        notifications.log(user, "order.create", o.getCode());
        mail.orderPlaced(o);
        cart.removeSelected();
        return o;
    }

    /**
     * Khóa các dòng dữ liệu bị giới hạn (điểm, tồn kho, suất flash sale, lượt voucher) rồi kiểm tra lại trong transaction:
     * giỏ được tính trước khi khóa nên 2 đơn đặt cùng lúc có thể cùng qua kiểm tra và vượt giới hạn.
     */
    private void lockAndRecheck(User user, CartService.View cv) {
        User u = lockUser(user.getId());
        if (cv.getPointsUsed() > u.getPoints()) {
            throw new BusinessException("Điểm tích lũy vừa thay đổi (còn " + num(u.getPoints()) + " điểm). Vui lòng kiểm tra lại giỏ hàng.");
        }
        user.setPoints(u.getPoints());

        // Tồn kho: khóa các lô của sản phẩm trong đơn (cùng thứ tự để tránh deadlock) rồi tính lại số khả dụng
        TreeMap<Long, Long> need = new TreeMap<>();
        for (CartService.Line l : cv.getLines()) need.merge(l.getProduct().getId(), (long) l.getBase(), Long::sum);
        for (PromotionService.Gift g : cv.getGifts()) need.merge(g.product().getId(), (long) g.quantity(), Long::sum);
        if (!need.isEmpty()) {
            em.createQuery("select b from Batch b where b.product.id in :ids order by b.product.id, b.id", Batch.class)
                    .setParameter("ids", need.keySet()).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            List<Product> products = em.createQuery("select p from Product p where p.id in :ids", Product.class).setParameter("ids", need.keySet()).getResultList();
            stock.fill(products);
            for (Product p : products) {
                if (need.get(p.getId()) > p.getAvailable()) {
                    throw new BusinessException("\"" + p.getName() + "\" vừa có khách khác đặt, hiện chỉ còn " + Math.max(0, p.getAvailable()) + " " + p.getUnit()
                            + ". Vui lòng cập nhật giỏ hàng.");
                }
            }
        }

        TreeMap<Long, Integer> flash = new TreeMap<>();
        for (CartService.Line l : cv.getLines()) if (l.getFlashPromotionId() != null) flash.merge(l.getFlashPromotionId(), l.getBase(), Integer::sum);
        for (Map.Entry<Long, Integer> e : flash.entrySet()) {
            Promotion promo = em.find(Promotion.class, e.getKey(), LockModeType.PESSIMISTIC_WRITE);
            if (promo != null) em.refresh(promo);
            if (promo == null || (promo.getQuantityLimit() != null && promo.getSoldCount() + e.getValue() > promo.getQuantityLimit())) {
                throw new BusinessException("Flash sale \"" + (promo != null ? promo.getName() : "") + "\" vừa hết suất"
                        + (promo != null ? " (còn " + promo.remaining() + ")" : "") + ". Vui lòng cập nhật giỏ hàng.");
            }
        }

        if (cv.getVoucher() != null) {
            Voucher v = em.find(Voucher.class, cv.getVoucher().getId(), LockModeType.PESSIMISTIC_WRITE);
            em.refresh(v);
            if (v.getUsageLimit() != null && v.getUsedCount() >= v.getUsageLimit()) {
                throw new BusinessException("Mã giảm giá " + v.getCode() + " vừa hết lượt sử dụng.");
            }
            // Giới hạn theo khách: tài khoản đã bị khóa ở trên nên đếm lại là chính xác
            long used = em.createQuery("select count(o) from Order o where o.user.id = :u and o.voucherCode = :c and o.status not in :st", Long.class)
                    .setParameter("u", user.getId()).setParameter("c", v.getCode())
                    .setParameter("st", List.of(OrderStatus.CANCELLED, OrderStatus.RX_REJECTED)).getSingleResult();
            if (v.getPerUserLimit() != null && used >= v.getPerUserLimit()) {
                throw new BusinessException("Bạn đã dùng hết số lần sử dụng mã " + v.getCode() + ".");
            }
        }
    }

    /** Khóa và đọc lại tài khoản (điểm tích lũy mới nhất). */
    private User lockUser(Long id) {
        User u = em.find(User.class, id, LockModeType.PESSIMISTIC_WRITE);
        em.refresh(u);
        return u;
    }

    /** Cộng / trừ điểm tích lũy (khóa dòng tài khoản trước). */
    public void addPoints(User user, int delta) {
        if (delta == 0) return;
        User u = lockUser(user.getId());
        u.setPoints(u.getPoints() + delta);
        if (u != user) user.setPoints(u.getPoints());
    }

    /** Trả lại suất flash sale đã giữ cho một dòng hàng (baseQty theo đơn vị cơ bản). */
    private void releaseFlash(OrderItem it, int baseQty) {
        if (it.getFlashPromotionId() == null && it.getFlashPromotion() == null || baseQty <= 0) return;
        Long id = it.getFlashPromotion() != null ? it.getFlashPromotion().getId() : it.getFlashPromotionId();
        Promotion promo = em.find(Promotion.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (promo != null) {
            em.refresh(promo);
            promo.setSoldCount(Math.max(0, promo.getSoldCount() - baseQty));
        }
    }

    private OrderItem newItem(Order o, Product p, UnitOption unit, int qty, Long price) {
        OrderItem it = new OrderItem();
        it.setOrder(o);
        it.setProduct(p);
        it.setProductName(p.getName());
        it.setUnit(unit.name());
        it.setUnitFactor(unit.factor());
        it.setDrugType(p.getDrugType());
        it.setPrice(price != null ? price : unit.price());
        it.setQuantity(qty);
        o.getItems().add(it);
        em.persist(it);
        return it;
    }

    /* ============================ Chuyển trạng thái (nhân viên) ============================ */

    /**
     * Khóa đơn và nạp lại dữ liệu mới nhất: thao tác bấm 2 lần / 2 người cùng xử lý sẽ chạy tuần tự,
     * request sau thấy trạng thái đã đổi và bị chặn bởi bước kiểm tra.
     */
    public void lockOrder(Order o) {
        em.flush();
        em.refresh(o, LockModeType.PESSIMISTIC_WRITE);
    }

    /** @param safetyAck dược sĩ đã kiểm tra / tư vấn các cảnh báo an toàn nguy hiểm của đơn (bắt buộc khi xác nhận đơn có cảnh báo) */
    public Order changeStatus(Order o, OrderStatus to, User staff, String note0, boolean safetyAck) {
        String note = note0 != null && !note0.trim().isEmpty() ? Texts.trim(note0, 500) : null;
        lockOrder(o);
        OrderStatus from = o.getStatus();
        if (!from.staffTransitions().contains(to)) {
            throw new BusinessException("Không thể chuyển từ \"" + from.getLabel() + "\" sang \"" + to.getLabel() + "\""
                    + (from == OrderStatus.SHIPPING && to == OrderStatus.CANCELLED ? " (đơn đang vận chuyển không được hủy)." : "."));
        }
        switch (to) {
            case CONFIRMED -> {
                if (o.getPaymentMethod().isPrepaid() && o.getPaymentStatus() != PaymentStatus.PAID) {
                    throw new BusinessException("Đơn " + o.getPaymentMethod().getLabel().toLowerCase() + " chưa nhận được tiền của khách.");
                }
                List<String> danger = dangerWarnings(o);
                if (!danger.isEmpty()) {
                    if (!safetyAck) {
                        throw new BusinessException("Đơn có cảnh báo an toàn nguy hiểm: " + String.join(" ", danger)
                                + " Hãy liên hệ tư vấn cho khách, sau đó đánh dấu \"Đã kiểm tra cảnh báo an toàn\" để xác nhận đơn (hoặc hủy đơn).");
                    }
                    addHistory(o, o.getStatus(), "Dược sĩ đã kiểm tra cảnh báo an toàn: " + String.join(" ", danger), staff);
                    notifications.log(staff, "safety.ack", o.getCode() + ": " + String.join(" ", danger));
                }
            }
            case PREPARING -> stock.allocate(o);
            case SHIPPING -> {
                if (o.getShippingMethod() == ShippingMethod.DELIVERY && Texts.isBlank(o.getCarrier())) {
                    throw new BusinessException("Vui lòng chọn đơn vị vận chuyển (hoặc tạo vận đơn GHN) trước khi giao hàng.");
                }
                // Lô bị khóa (thu hồi / nghi ngờ chất lượng) sau khi đã soạn: không được giao đi
                List<OrderItemBatch> locked = stock.lockedAllocations(o);
                if (!locked.isEmpty()) {
                    String list = locked.stream().map(a -> a.getOrderItem().getProductName() + " - lô " + a.getBatch().getBatchNo()
                            + (a.getBatch().getLockReason() != null ? " (" + a.getBatch().getLockReason() + ")" : "")).distinct().collect(Collectors.joining("; "));
                    throw new BusinessException("Đơn có hàng thuộc lô đã bị khóa: " + list + ". Bấm \"Đổi sang lô khác\" và soạn lại hàng trước khi giao.");
                }
            }
            case CANCELLED -> {
                if (note == null) throw new BusinessException("Vui lòng nhập lý do hủy đơn.");
                if (o.getPaymentStatus() == PaymentStatus.PAID && !staff.hasPermission(StaffPermission.REFUND)) {
                    throw new BusinessException("Đơn đã thanh toán - bạn chưa được cấp quyền hủy đơn & hoàn tiền.");
                }
                cancelShipment(o, staff);
                releaseResources(o);
                o.setCancelReason(note);
            }
            case COMPLETED -> completeOrder(o, staff);
            case RETURNED -> {
                // Giao thất bại (khách không nhận / hàng hoàn về): nhập lại kho, trả điểm & lượt voucher, hoàn tiền nếu đã thu
                if (note == null) throw new BusinessException("Vui lòng ghi lý do giao không thành công.");
                releaseResources(o);
                o.setCancelReason(note);
                note = "Giao không thành công, hàng hoàn về kho: " + note;
            }
            default -> {
            }
        }
        if (o.getHandler() == null) o.setHandler(staff);
        o.setStatus(to);
        addHistory(o, to, note, staff);
        notifyStatus(o, to, note);
        notifications.log(staff, "order.status", o.getCode() + ": " + from.name() + " → " + to.name());
        return o;
    }

    /** Hoàn tất đơn: ghi nhận thu tiền COD, cộng điểm theo hạng thành viên. */
    public void completeOrder(Order o, User by) {
        if (o.getPaymentStatus() == PaymentStatus.UNPAID) {
            o.setPaymentStatus(PaymentStatus.PAID);
            recordPayment(o, o.getPaymentMethod().name(), o.getTotal(), PaymentTransaction.SUCCESS,
                    o.getPaymentMethod() == PaymentMethod.COD ? "Thu tiền khi giao hàng" : "Xác nhận đã thanh toán", by, "PAYMENT", null, null);
        }
        o.setCompletedAt(LocalDateTime.now().withNano(0));
        // Giá vốn chốt lúc hoàn thành: lô xuất bị xóa khi nhận trả hàng nên không tính lại được về sau
        o.setCostAmount(stock.costOf(o));
        if (!o.isPos()) {
            User c = o.getUser();
            MemberTier tier = customers.tier(c);
            long per = Math.max(1, settings.getLong("points_per_amount"));
            // Điểm tính trên tiền hàng khách trả, không tính phí giao hàng
            int earned = (int) Math.floor(Math.max(0, o.getTotal() - o.getShippingFee()) / (double) per * tier.getMultiplier());
            o.setPointsEarned(earned);
            addPoints(c, earned);
        }
    }

    /** Thông báo trong web + email theo mẫu. */
    public void notifyStatus(Order o, OrderStatus to, String note) {
        if (o.isPos()) return;
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("code", o.getCode());
        vars.put("status", to.getLabel());
        vars.put("note", note != null ? (to == OrderStatus.CANCELLED ? note : " - " + note) : "");
        vars.put("carrier", !Texts.isBlank(o.getCarrier()) ? o.getCarrier() : "nhà thuốc");
        vars.put("tracking", !Texts.isBlank(o.getTrackingCode()) ? " (mã vận đơn " + o.getTrackingCode() + ")" : "");
        vars.put("points", String.valueOf(o.getPointsEarned()));
        String tpl = switch (to) {
            case SHIPPING -> "Đơn hàng {code} đang được giao bởi {carrier}{tracking}. Vui lòng để ý điện thoại nhé!";
            case COMPLETED -> "Cảm ơn bạn đã mua hàng tại {store}! Đơn {code} đã hoàn thành, bạn được cộng {points} điểm.";
            case CANCELLED -> "Đơn hàng {code} đã bị hủy: {note}. Liên hệ {phone} nếu cần hỗ trợ.";
            default -> "Đơn hàng {code}: {status}{note}";
        };
        String msg = settings.render(tpl, vars);
        notifications.notify(o.getUser(), msg, "/account/orders/" + o.getCode());
        mail.orderStatus(o, msg);
    }

    /** Hủy đơn đã có vận đơn GHN: hủy vận đơn bên GHN trước (lỗi thì giữ nguyên đơn), ghi lịch sử. */
    private void cancelShipment(Order o, User by) {
        if (!GhnService.hasActiveShipment(o)) return;
        ghn.cancelOrder(o.getTrackingCode());
        o.setShippingStatus("cancel");
        addHistory(o, o.getStatus(), "Đã hủy vận đơn GHN " + o.getTrackingCode(), by);
    }

    /** Hủy đơn: hoàn kho (nếu đã xuất), hoàn tiền, hoàn điểm, trả lượt dùng voucher. */
    private void releaseResources(Order o) {
        if (o.getStatus().isAllocated()) stock.restore(o);
        if (o.getPaymentStatus() == PaymentStatus.PAID) requestRefund(o, o.getTotal());
        if (o.getPointsUsed() > 0) addPoints(o.getUser(), o.getPointsUsed());
        if (o.getVoucherCode() != null) {
            sql.update("update vouchers set used_count = used_count - 1 where code = :c and used_count > 0", Map.of("c", o.getVoucherCode()));
        }
        // Trả lại suất flash sale đã giữ cho đơn
        for (OrderItem it : o.getItems()) if (it.getFlashPromotionId() != null || it.getFlashPromotion() != null) releaseFlash(it, it.baseQuantity());
        // Hủy các giao dịch online đang chờ
        for (PaymentTransaction t : em.createQuery("select t from PaymentTransaction t where t.order.id = :o and t.status = :s", PaymentTransaction.class)
                .setParameter("o", o.getId()).setParameter("s", PaymentTransaction.PENDING).getResultList()) {
            t.setStatus(PaymentTransaction.FAILED);
            t.setMessage("Đơn đã hủy");
        }
    }

    /** Đơn đã thu tiền bị hủy / trả: chuyển "Chờ hoàn tiền" để người có quyền duyệt. */
    private void requestRefund(Order o, long amount) {
        o.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        o.setRefundAmount(amount);
        notifications.notifyAdmins("Đơn " + o.getCode() + " cần duyệt hoàn tiền " + money(amount), "/admin/refunds");
    }

    public Order approveRefund(Order o, User approver, long amount, String note0) {
        if (!approver.hasPermission(StaffPermission.REFUND)) throw BusinessException.forbidden("Bạn chưa được cấp quyền duyệt hoàn tiền.");
        String note = Texts.trim(note0, 300);
        if (note.isEmpty()) throw new BusinessException("Vui lòng ghi hình thức / mã giao dịch hoàn tiền.");
        lockOrder(o);
        if (o.getPaymentStatus() != PaymentStatus.REFUND_PENDING) throw new BusinessException("Đơn không ở trạng thái chờ hoàn tiền.");
        if (amount <= 0 || amount > o.getTotal()) throw new BusinessException("Số tiền hoàn phải từ 1 đến " + money(o.getTotal()) + ".");
        o.setPaymentStatus(PaymentStatus.REFUNDED);
        o.setRefundAmount(amount);
        o.setRefundedAt(LocalDateTime.now().withNano(0));
        o.setRefundedBy(approver);
        o.setRefundNote(note);
        recordPayment(o, o.getPaymentMethod().name(), amount, PaymentTransaction.SUCCESS, "Hoàn tiền: " + note, approver, "REFUND", null, null);
        for (PaymentTransaction t : em.createQuery("select t from PaymentTransaction t where t.order.id = :o and t.type = 'PAYMENT' and t.status = :s",
                PaymentTransaction.class).setParameter("o", o.getId()).setParameter("s", PaymentTransaction.SUCCESS).getResultList()) {
            t.setStatus(PaymentTransaction.REFUNDED);
        }
        addHistory(o, o.getStatus(), "Đã hoàn tiền " + money(amount) + ": " + note, approver);
        String msg = "Nhà thuốc đã hoàn " + money(amount) + " cho đơn " + o.getCode() + " (" + note + ").";
        notifications.notify(o.getUser(), msg, "/account/orders/" + o.getCode());
        mail.send(o.getUser(), "Hoàn tiền đơn hàng " + o.getCode(), List.of(msg), "Xem đơn hàng", mail.url("/account/orders/" + o.getCode()));
        notifications.log(approver, "refund.approve", o.getCode() + ": " + amount + " - " + note);
        return o;
    }

    /** Cảnh báo an toàn mức nguy hiểm (dị ứng, chống chỉ định, thai kỳ, tương tác nặng) của đơn với hồ sơ sức khỏe khách. */
    public List<String> dangerWarnings(Order o) {
        if (o.isPos() || o.getUser() == null) return List.of();
        List<Product> products = o.getItems().stream().map(OrderItem::getProduct).filter(Objects::nonNull).toList();
        return safety.check(o.getUser(), products, safety.recentProducts(o.getUser())).stream()
                .filter(SafetyService.Warning::isDanger).map(SafetyService.Warning::message).toList();
    }

    /** Đổi phần hàng soạn từ lô bị khóa sang lô hợp lệ khác (đơn đang soạn / đã đóng gói). */
    public List<String> swapLockedBatches(Order o, User staff) {
        lockOrder(o);
        if (o.getStatus() != OrderStatus.PREPARING && o.getStatus() != OrderStatus.PACKED) {
            throw new BusinessException("Chỉ đổi lô cho đơn đang soạn hàng hoặc đã đóng gói.");
        }
        List<String> changes = stock.swapLockedBatches(o);
        if (changes.isEmpty()) throw new BusinessException("Đơn không có hàng thuộc lô bị khóa.");
        addHistory(o, o.getStatus(), "Đổi lô bị khóa: " + String.join("; ", changes) + ". Cần soạn / đóng gói lại hàng.", staff);
        notifications.log(staff, "order.swap_batches", o.getCode() + ": " + String.join("; ", changes));
        return changes;
    }

    public void setShipping(Order o, String carrier, String tracking) {
        o.setCarrier(carrier != null && !carrier.isBlank() ? Texts.trim(carrier, 50) : null);
        o.setTrackingCode(tracking != null && !tracking.isBlank() ? Texts.trim(tracking, 60) : null);
    }

    /**
     * Xác nhận đã nhận tiền (chuyển khoản / thu COD trước). Chỉ khi giá đã chốt (từ "Chờ xác nhận" trở đi):
     * trước đó dược sĩ / khách còn điều chỉnh được đơn, tổng tiền thay đổi sau khi đã thu sẽ lệch với số tiền khách trả.
     */
    public void markPaid(Order o, User staff, String reference) {
        lockOrder(o);
        if (o.getPaymentStatus() != PaymentStatus.UNPAID) throw new BusinessException("Đơn hàng không ở trạng thái chưa thanh toán.");
        if (o.getStatus() == OrderStatus.PENDING_RX || o.getStatus() == OrderStatus.AWAITING_CUSTOMER) {
            throw new BusinessException("Đơn " + o.getCode() + " đang \"" + o.getStatus().getLabel() + "\" - tổng tiền chưa chốt, chưa ghi nhận thu tiền.");
        }
        if (!o.canMarkPaid()) throw new BusinessException("Đơn " + o.getCode() + " đã " + o.getStatus().getLabel().toLowerCase() + " - không ghi nhận thu tiền.");
        o.setPaymentStatus(PaymentStatus.PAID);
        recordPayment(o, o.getPaymentMethod().name(), o.getTotal(), PaymentTransaction.SUCCESS,
                "Nhân viên xác nhận đã nhận tiền" + (reference != null && !reference.isEmpty() ? " (" + reference + ")" : ""), staff, "PAYMENT", reference, null);
        addHistory(o, o.getStatus(), "Xác nhận đã nhận thanh toán", staff);
        notifications.notify(o.getUser(), "Nhà thuốc đã nhận được thanh toán cho đơn " + o.getCode() + ".", "/account/orders/" + o.getCode());
        notifications.log(staff, "order.paid", o.getCode());
    }

    public PaymentTransaction recordPayment(Order o, String gateway, long amount, String status, String message, User by,
                                            String type, String transactionId, String payload) {
        PaymentTransaction t = new PaymentTransaction();
        t.setOrder(o);
        t.setGateway(gateway);
        t.setAmount(BigDecimal.valueOf(amount));
        t.setType(type);
        t.setStatus(status);
        t.setGatewayOrderId(o.getCode());
        t.setTransactionId(transactionId);
        t.setMessage(message != null ? Texts.limit(message, 297) : null);
        t.setPayload(payload);
        t.setPaidAt(PaymentTransaction.SUCCESS.equals(status) ? LocalDateTime.now().withNano(0) : null);
        t.setCreator(by);
        em.persist(t);
        return t;
    }

    /* ============================ Khách hàng ============================ */

    public void cancelByCustomer(Order o, User user, String reason0) {
        String reason = reason0 != null && !reason0.trim().isEmpty() ? Texts.trim(reason0, 500) : "Khách hàng hủy";
        lockOrder(o);
        if (!o.getStatus().isCustomerCancellable()) {
            throw new BusinessException("Đơn hàng đang giao hoặc đã hoàn tất, không thể hủy. Vui lòng liên hệ nhà thuốc.");
        }
        for (Prescription rx : o.getPrescriptions()) {
            if (rx.getStatus() == ApprovalStatus.PENDING) {
                rx.setStatus(ApprovalStatus.REJECTED);
                rx.setRejectReason("Khách hủy đơn");
            }
        }
        if (GhnService.hasActiveShipment(o)) {
            try {
                cancelShipment(o, user);
            } catch (BusinessException e) {
                throw new BusinessException("Đơn " + o.getCode() + " đã được bàn giao cho đơn vị vận chuyển, không thể tự hủy. Vui lòng liên hệ nhà thuốc.");
            }
        }
        releaseResources(o);
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelReason(reason);
        addHistory(o, OrderStatus.CANCELLED, reason, user);
        notifications.notifyStaff("Khách đã hủy đơn " + o.getCode(), "/staff/orders/" + o.getId());
        notifications.log(user, "order.cancel", o.getCode());
    }

    /** Hiện hướng dẫn chuyển khoản khi đơn chưa nhận tiền. */
    public boolean showBankTransfer(Order o) {
        return o.getPaymentMethod() == PaymentMethod.BANK_TRANSFER && o.getPaymentStatus() == PaymentStatus.UNPAID
                && (o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.CONFIRMED);
    }

    public void reuploadPrescription(Order o, User user, MultipartFile file, String note) {
        if (o.getStatus() != OrderStatus.RX_REJECTED) throw new BusinessException("Đơn hàng không cần tải lại đơn thuốc.");
        String image = files.store("prescriptions", file);
        lockOrder(o);
        if (o.getStatus() != OrderStatus.RX_REJECTED) throw new BusinessException("Đơn hàng không cần tải lại đơn thuốc.");
        Prescription rx = new Prescription();
        rx.setOrder(o);
        rx.setUser(user);
        rx.setImage(image);
        rx.setCustomerNote(note != null && !note.isBlank() ? Texts.trim(note, 500) : null);
        em.persist(rx);
        o.setStatus(OrderStatus.PENDING_RX);
        addHistory(o, OrderStatus.PENDING_RX, "Khách tải lại đơn thuốc", user);
        notifications.notifyPermission("RX_REVIEW", "Đơn " + o.getCode() + ": khách đã tải lại đơn thuốc", "/staff/prescriptions");
    }

    /** Khách xác nhận đơn do dược sĩ lên / điều chỉnh: chọn giao hàng, thanh toán. */
    public void confirmByCustomer(Order o, User user, Form f) {
        ShippingMethod method = shippingOf(f.get("shipping_method"));
        PaymentMethod pay = paymentOf(f.get("payment_method"));
        List<String> errors = validateDelivery(f.get("recipient"), f.get("phone"), method, f.get("address"));
        if (!errors.isEmpty()) throw new BusinessException(String.join(" ", errors));
        String province = Texts.emptyToNull(f.get("province"));
        lockOrder(o);
        if (o.getStatus() != OrderStatus.AWAITING_CUSTOMER) throw new BusinessException("Đơn hàng không ở trạng thái chờ xác nhận.");
        String address = method == ShippingMethod.PICKUP ? null : Texts.trim(f.get("address"), 300);
        // Mã quận / phường GHN phải khớp địa chỉ giao: lấy theo địa chỉ đã lưu, đổi sang địa chỉ khác thì bỏ mã cũ
        Address saved = null;
        if (address != null) {
            List<Address> l = em.createQuery("select a from Address a where a.user.id = :u and a.addressLine = :l", Address.class)
                    .setParameter("u", user.getId()).setParameter("l", address).setMaxResults(1).getResultList();
            saved = l.isEmpty() ? null : l.get(0);
        }
        if (saved != null) {
            if (!Texts.isBlank(saved.getProvince())) province = saved.getProvince();
            o.setGhnDistrictId(saved.getGhnDistrictId());
            o.setGhnWardCode(saved.getGhnWardCode());
        } else if (address == null || !address.equals(o.getAddress()) || !Objects.equals(province, o.getProvince())) {
            o.setGhnDistrictId(null);
            o.setGhnWardCode(null);
        }
        o.setRecipient(Texts.trim(f.get("recipient"), 100));
        o.setPhone(Texts.trim(f.get("phone")));
        o.setShippingMethod(method);
        o.setAddress(address);
        o.setProvince(method == ShippingMethod.PICKUP ? null : province);
        o.setPaymentMethod(settings.enabledPaymentMethods().contains(pay) ? pay : PaymentMethod.COD);
        recalc(o);
        o.setStatus(OrderStatus.PENDING);
        addHistory(o, OrderStatus.PENDING, "Khách xác nhận đơn hàng", user);
        notifications.notifyStaff("Khách đã xác nhận đơn " + o.getCode(), "/staff/orders/" + o.getId());
    }

    /* ============================ Đổi / trả ============================ */

    public boolean returnBlockedByRx(Order o) {
        return settings.getInt("return_rx_allowed") == 0 && o.getItems().stream().anyMatch(OrderItem::isPrescription);
    }

    /** Đổi / trả online chỉ cho đơn đặt online; hóa đơn mua tại quầy xử lý tại quầy (phiếu hủy hóa đơn). */
    public boolean canRequestReturn(Order o) {
        return !o.isPos() && !returnBlockedByRx(o) && o.getStatus() == OrderStatus.COMPLETED && o.getReturnStatus() == null && o.getCompletedAt() != null
                && ChronoUnit.DAYS.between(o.getCompletedAt().toLocalDate(), LocalDate.now()) <= settings.getInt("return_days");
    }

    public void requestReturn(Order o, User user, String reason0) {
        if (o.isPos()) {
            throw new BusinessException("Hóa đơn " + o.getCode() + " mua tại quầy - vui lòng mang hóa đơn và sản phẩm đến nhà thuốc để được hỗ trợ đổi / trả.");
        }
        if (!canRequestReturn(o)) {
            throw new BusinessException(returnBlockedByRx(o)
                    ? "Theo chính sách, đơn có thuốc kê đơn không áp dụng đổi/trả (trừ lỗi từ nhà thuốc - vui lòng liên hệ hotline)."
                    : "Đơn hàng không thể yêu cầu đổi/trả (chỉ áp dụng trong " + settings.get("return_days") + " ngày sau khi nhận hàng).");
        }
        String reason = Texts.trim(reason0, 1000);
        if (Texts.mbLen(reason) < 10) throw new BusinessException("Vui lòng mô tả lý do đổi/trả (tối thiểu 10 ký tự).");
        o.setReturnStatus(ReturnStatus.REQUESTED);
        o.setReturnReason(reason);
        addHistory(o, o.getStatus(), "Khách yêu cầu đổi/trả: " + reason, user);
        notifications.notifyPermission("REFUND", "Đơn " + o.getCode() + " có yêu cầu đổi/trả", "/staff/orders/" + o.getId());
    }

    public void handleReturn(Order o, User staff, boolean approve, boolean restock, String note0) {
        if (!staff.hasPermission(StaffPermission.REFUND)) throw BusinessException.forbidden("Bạn chưa được cấp quyền xử lý đổi trả & hoàn tiền.");
        String note = Texts.trim(note0, 500);
        if (!approve && note.isEmpty()) throw new BusinessException("Vui lòng nhập lý do từ chối.");
        lockOrder(o);
        if (o.getReturnStatus() != ReturnStatus.REQUESTED) throw new BusinessException("Không có yêu cầu đổi/trả cần xử lý.");
        if (approve) {
            if (restock) {
                stock.restore(o);
            } else {
                // Hàng trả không đạt chất lượng: ghi nhận vào kho hủy để truy vết
                for (OrderItem it : o.getItems()) {
                    for (OrderItemBatch a : it.getAllocations()) {
                        StockAdjustment adj = new StockAdjustment();
                        adj.setBatch(a.getBatch());
                        adj.setQuantity(0);
                        adj.setType("RETURN_SCRAP");
                        adj.setStatus(ApprovalStatus.APPROVED);
                        adj.setApprover(staff);
                        adj.setApprovedAt(LocalDateTime.now().withNano(0));
                        adj.setUser(staff);
                        adj.setReason("Hàng trả lại đơn " + o.getCode() + " - chuyển kho hủy " + a.getQuantity() + " " + it.getProduct().getUnit());
                        em.persist(adj);
                    }
                }
            }
            User c = lockUser(o.getUser().getId());
            c.setPoints(Math.max(0, c.getPoints() - o.getPointsEarned() + o.getPointsUsed()));
            o.setStatus(OrderStatus.RETURNED);
            o.setReturnStatus(ReturnStatus.APPROVED);
            // Báo cáo ghi giảm doanh thu vào ngày nhận trả; giá vốn chỉ hoàn lại khi hàng được nhập lại kho
            o.setReturnedAt(LocalDateTime.now().withNano(0));
            o.setReturnCost(restock ? o.getCostAmount() : 0);
            if (o.getPaymentStatus() == PaymentStatus.PAID) requestRefund(o, o.getTotal());
            addHistory(o, OrderStatus.RETURNED, "Chấp nhận đổi/trả" + (restock ? " (nhập lại kho)" : " (không nhập lại kho)") + (!note.isEmpty() ? ": " + note : ""), staff);
        } else {
            o.setReturnStatus(ReturnStatus.REJECTED);
            addHistory(o, o.getStatus(), "Từ chối đổi/trả: " + note, staff);
        }
        String msg = approve ? "Yêu cầu đổi/trả đơn " + o.getCode() + " đã được chấp nhận. Nhà thuốc sẽ hoàn tiền cho bạn."
                : "Yêu cầu đổi/trả đơn " + o.getCode() + " bị từ chối: " + note;
        notifications.notify(o.getUser(), msg, "/account/orders/" + o.getCode());
        mail.send(o.getUser(), "Kết quả yêu cầu đổi/trả đơn " + o.getCode(), List.of(msg), "Xem đơn hàng", mail.url("/account/orders/" + o.getCode()));
        notifications.log(staff, "order.return", o.getCode() + ": " + (approve ? "chấp nhận" : "từ chối"));
    }

    /* ============================ Đơn thuốc ============================ */

    /** Khách gửi đơn thuốc mà chưa chọn sản phẩm - dược sĩ đọc đơn và lên đơn hàng. */
    public Prescription submitStandalonePrescription(User user, MultipartFile file, String note) {
        Prescription rx = new Prescription();
        rx.setUser(user);
        rx.setStandalone(true);
        rx.setImage(files.store("prescriptions", file));
        rx.setCustomerNote(note != null && !note.isBlank() ? Texts.trim(note, 500) : null);
        em.persist(rx);
        notifications.notifyPermission("RX_REVIEW", "Khách " + user.getFullName() + " gửi đơn thuốc nhờ lên đơn", "/staff/prescriptions/" + rx.getId());
        notifications.log(user, "rx.submit", "Đơn thuốc #" + rx.getId());
        return rx;
    }

    /** Khóa đơn thuốc (và đơn hàng đi kèm) rồi kiểm tra còn chờ duyệt: 2 dược sĩ bấm cùng lúc thì người sau bị chặn. */
    private void assertPending(Prescription rx) {
        em.flush();
        em.refresh(rx, LockModeType.PESSIMISTIC_WRITE);
        if (rx.getOrder() != null) lockOrder(rx.getOrder());
        boolean pendingOrder = rx.getOrder() != null ? rx.getOrder().getStatus() == OrderStatus.PENDING_RX : rx.isStandalone();
        if (rx.getStatus() != ApprovalStatus.PENDING || !pendingOrder) throw new BusinessException("Đơn thuốc này đã được xử lý.");
    }

    private static String normName(String s) {
        return Texts.trim(s).toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Một tờ đơn thuốc (cùng bệnh nhân + bác sĩ + ngày kê) đã được bán ở đơn khác (online hoặc tại quầy) chưa.
     * Chưa xác nhận -> chặn và liệt kê các lần đã bán; dược sĩ xác nhận (đơn còn được bán tiếp, VD: bán chia đợt)
     * -> trả về ghi chú để lưu vào sổ thuốc kê đơn.
     */
    public String checkRxReuse(String patient, String doctor, LocalDate rxDate, Long exceptRxId, boolean confirmed) {
        var q = em.createQuery("select p from Prescription p where p.status = :s and p.rxDate = :d"
                        + (exceptRxId != null ? " and p.id <> :ex" : "") + " order by p.id", Prescription.class)
                .setParameter("s", ApprovalStatus.APPROVED).setParameter("d", rxDate);
        if (exceptRxId != null) q.setParameter("ex", exceptRxId);
        List<Prescription> list = q.getResultList();
        List<Prescription> previous = list.stream().filter(p -> normName(p.getPatientName()).equals(normName(patient))
                && normName(p.getDoctorName()).equals(normName(doctor)) && p.getOrder() != null
                && p.getOrder().getStatus() != OrderStatus.CANCELLED && p.getOrder().getStatus() != OrderStatus.RX_REJECTED).toList();
        if (previous.isEmpty()) return null;
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String> lines = previous.stream().map(p -> p.getOrder().getCode() + " (" + (p.getReviewedAt() != null ? p.getReviewedAt().format(df) : "") + ", "
                + (p.getPharmacist() != null ? p.getPharmacist().getFullName() : "-") + "): "
                + p.getOrder().getItems().stream().filter(OrderItem::isPrescription).map(i -> i.getProductName() + " x" + i.getQuantity() + " " + i.getUnit())
                .collect(Collectors.joining(", "))).toList();
        if (!confirmed) {
            throw new RxReuseException("Đơn thuốc của bệnh nhân " + patient + " (BS. " + doctor + ", kê ngày " + rxDate.format(df) + ") đã được bán: "
                    + String.join("; ", lines) + ". Kiểm tra số lượng còn lại trên đơn rồi đánh dấu xác nhận để tiếp tục.");
        }
        return Texts.trim("Đơn thuốc đã bán trước đó ở " + String.join("; ", lines) + " - dược sĩ xác nhận còn được bán tiếp.", 500);
    }

    private void recordRx(Prescription rx, User pharmacist, Form form) {
        if (!pharmacist.hasPermission(StaffPermission.RX_REVIEW)) throw BusinessException.forbidden("Bạn chưa được cấp quyền duyệt đơn thuốc.");
        if (!pharmacist.isAdmin() && Texts.isBlank(pharmacist.getLicenseNo())) {
            throw BusinessException.forbidden("Người duyệt đơn thuốc phải có chứng chỉ hành nghề dược (CCHN).");
        }
        if (Texts.isBlank(form.get("patient_name")) || Texts.isBlank(form.get("doctor_name"))) {
            throw new BusinessException("Vui lòng ghi nhận tên bệnh nhân và bác sĩ kê đơn (sổ bán thuốc kê đơn).");
        }
        List<String> checks = form.list("checks");
        if (!checks.containsAll(RX_CHECKS.keySet())) {
            throw new BusinessException("Vui lòng kiểm tra và đánh dấu đủ các mục: hợp lệ, hiệu lực, chữ ký, khớp thuốc/liều.");
        }
        if (Texts.isBlank(form.get("rx_date"))) throw new BusinessException("Vui lòng nhập ngày kê đơn.");
        LocalDate date;
        try {
            date = LocalDate.parse(form.get("rx_date").trim().substring(0, 10));
        } catch (RuntimeException e) {
            throw new BusinessException("Ngày kê đơn không hợp lệ.");
        }
        int valid = settings.getInt("rx_valid_days");
        if (date.isAfter(LocalDate.now())) throw new BusinessException("Ngày kê đơn không hợp lệ.");
        if (date.plusDays(valid).isBefore(LocalDate.now())) {
            throw new BusinessException("Đơn thuốc đã hết hiệu lực (quá " + valid + " ngày kể từ ngày kê) - hãy từ chối đơn.");
        }
        String reuse = checkRxReuse(form.get("patient_name").trim(), form.get("doctor_name").trim(), date, rx.getId(), form.bool("reuse_confirmed"));
        if (reuse != null) notifications.log(pharmacist, "rx.reuse", "Đơn thuốc #" + rx.getId() + ": " + reuse);
        rx.setChecklist(RX_CHECKS.keySet().stream().filter(checks::contains).collect(Collectors.joining(",")) + (reuse != null ? ",reuse" : ""));
        rx.setStatus(ApprovalStatus.APPROVED);
        rx.setPharmacist(pharmacist);
        rx.setPatientName(Texts.trim(form.get("patient_name"), 100));
        rx.setDoctorName(Texts.trim(form.get("doctor_name"), 100));
        rx.setClinic(!Texts.isBlank(form.get("clinic")) ? Texts.trim(form.get("clinic"), 200) : null);
        rx.setRxDate(date);
        rx.setPharmacistNote(!Texts.isBlank(form.get("pharmacist_note")) ? Texts.trim(form.get("pharmacist_note"), 1000) : null);
        rx.setReviewedAt(LocalDateTime.now().withNano(0));
    }

    private void checkStock(Product p, long baseQty) {
        stock.fill(p);
        if (!p.getDrugType().isSellableOnline()) throw new BusinessException(p.getName() + " không được bán online.");
        if (baseQty > p.getAvailable()) throw new BusinessException("\"" + p.getName() + "\" chỉ còn " + p.getAvailable() + " " + p.getUnit() + ".");
    }

    /**
     * Dược sĩ duyệt đơn thuốc: có thể giảm số lượng hoặc thay thuốc cùng hoạt chất.
     * form: patient_name, doctor_name, clinic, rx_date, pharmacist_note, checks[], qty[itemId], sub[itemId], product_ids[], quantities[]
     */
    public Order approvePrescription(Prescription rx, User pharmacist, Form form) {
        assertPending(rx);
        if (rx.getOrder() == null) return createQuote(rx, pharmacist, form);
        Order o = rx.getOrder();
        List<String> changes = new ArrayList<>();
        boolean substituted = false;
        int remaining = 0;
        Map<String, String> qtyMap = form.map("qty");
        Map<String, String> subMap = form.map("sub");
        for (OrderItem it : new ArrayList<>(o.getItems())) {
            String q = qtyMap.get(String.valueOf(it.getId()));
            int newQty = q != null && !q.isEmpty() ? Texts.toInt(q, it.getQuantity()) : it.getQuantity();
            long subId = Texts.toInt(subMap.get(String.valueOf(it.getId())), 0);
            if (subId > 0 && subId != it.getProduct().getId()) {
                Product np = em.find(Product.class, subId);
                if (np == null) throw new BusinessException("Thuốc thay thế không tồn tại.");
                if (!np.isActive()) throw new BusinessException("\"" + np.getName() + "\" đã ngừng kinh doanh - không dùng làm thuốc thay thế.");
                Product old = it.getProduct();
                if (!catalog.isSubstitutable(old, np)) {
                    throw new BusinessException("Chỉ được thay bằng thuốc cùng hoạt chất và cùng hàm lượng"
                            + (!Texts.isBlank(old.getStrength()) ? " (" + old.getStrength() + ")" : "") + ", hoặc thuốc được cấu hình tương đương."
                            + (Texts.isBlank(old.getStrength()) || Texts.isBlank(np.getStrength()) ? " Thuốc chưa ghi hàm lượng thì phải được quản trị viên cấu hình là tương đương." : ""));
                }
                if (newQty <= 0) throw new BusinessException("Nhập số lượng cho thuốc thay thế \"" + np.getName() + "\".");
                // Đơn hiện tại đang giữ chỗ thuốc cũ, không ảnh hưởng thuốc mới
                checkStock(np, newQty);
                changes.add("thay " + it.getProductName() + " bằng " + np.getName() + " x" + newQty + " " + np.getUnit());
                // Suất flash sale giữ cho thuốc cũ không còn dùng: trả lại hết, dòng mới tính giá gốc
                releaseFlash(it, it.baseQuantity());
                it.setProduct(np);
                it.setProductName(np.getName());
                it.setDrugType(np.getDrugType());
                it.setUnit(np.getUnit());
                it.setUnitFactor(1);
                it.setPrice(np.getPrice());
                it.setQuantity(newQty);
                it.setFlashPromotion(null);
                it.setFlashPromotionId(null);
                substituted = true;
            } else {
                if (newQty < 0 || newQty > it.getQuantity()) {
                    throw new BusinessException("Số lượng \"" + it.getProductName() + "\" chỉ được điều chỉnh giảm (0 - " + it.getQuantity() + ").");
                }
                if (newQty != it.getQuantity()) {
                    releaseFlash(it, (it.getQuantity() - newQty) * it.factor());
                    changes.add(it.getProductName() + ": " + it.getQuantity() + " → " + newQty);
                    if (newQty == 0) o.getItems().remove(it);
                    else it.setQuantity(newQty);
                }
            }
            remaining += it.isGift() ? 0 : newQty;
        }
        if (remaining == 0) throw new BusinessException("Đơn hàng phải còn ít nhất 1 sản phẩm. Nếu không bán được, hãy từ chối đơn thuốc.");
        em.flush();
        if (!changes.isEmpty()) {
            changes.addAll(reapplyPromotions(o));
            recalc(o);
        }
        recordRx(rx, pharmacist, form);
        // Có điều chỉnh -> khách phải xác nhận lại; không -> chờ nhà thuốc xác nhận
        OrderStatus next = substituted || !changes.isEmpty() ? OrderStatus.AWAITING_CUSTOMER : OrderStatus.PENDING;
        o.setStatus(next);
        o.setHandler(pharmacist);
        addHistory(o, next, "Dược sĩ đã duyệt đơn thuốc" + (!changes.isEmpty() ? " (điều chỉnh: " + String.join("; ", changes) + ")" : ""), pharmacist);
        String msg = next == OrderStatus.AWAITING_CUSTOMER
                ? "Dược sĩ đã duyệt đơn thuốc của đơn " + o.getCode() + " và điều chỉnh sản phẩm. Vui lòng xem lại và xác nhận đơn hàng."
                : "Đơn thuốc của đơn " + o.getCode() + " đã được dược sĩ duyệt." + (o.getPaymentMethod().isPrepaid() ? " Vui lòng thanh toán để nhà thuốc xử lý đơn." : "");
        notifications.notify(o.getUser(), msg, "/account/orders/" + o.getCode());
        mail.send(o.getUser(), "Đơn thuốc đã được duyệt - " + o.getCode(), List.of(msg), "Xem đơn hàng", mail.url("/account/orders/" + o.getCode()));
        notifications.log(pharmacist, "rx.approve", "Đơn thuốc #" + rx.getId() + " - " + o.getCode() + (!changes.isEmpty() ? " - " + String.join("; ", changes) : ""));
        return o;
    }

    /** Dược sĩ lên đơn hàng từ đơn thuốc khách gửi riêng; khách xác nhận địa chỉ và thanh toán sau. */
    private Order createQuote(Prescription rx, User pharmacist, Form form) {
        User customer = rx.getUser();
        LinkedHashMap<Long, Integer> wanted = new LinkedHashMap<>();
        List<String> pids = form.list("product_ids");
        List<String> qtys = form.list("quantities");
        for (int i = 0; i < pids.size(); i++) {
            Long pid = Texts.toLong(pids.get(i));
            int q = i < qtys.size() ? Texts.toInt(qtys.get(i), 0) : 0;
            if (pid != null && pid > 0 && q > 0) wanted.merge(pid, q, Integer::sum);
        }
        if (wanted.isEmpty()) throw new BusinessException("Vui lòng chọn ít nhất 1 sản phẩm và số lượng để lên đơn.");
        List<Address> addrs = em.createQuery("select a from Address a where a.user.id = :u order by a.isDefault desc, a.id asc", Address.class)
                .setParameter("u", customer.getId()).setMaxResults(1).getResultList();
        Address a = addrs.isEmpty() ? null : addrs.get(0);
        Order o = new Order();
        o.setCode(newCode("DH"));
        o.setUser(customer);
        o.setRecipient(a != null ? a.getRecipient() : customer.getFullName());
        o.setPhone(a != null ? a.getPhone() : (customer.getPhone() != null ? customer.getPhone() : ""));
        o.setAddress(a != null ? a.getAddressLine() : null);
        o.setProvince(a != null ? a.getProvince() : null);
        o.setGhnDistrictId(a != null ? a.getGhnDistrictId() : null);
        o.setGhnWardCode(a != null ? a.getGhnWardCode() : null);
        o.setShippingMethod(a != null ? ShippingMethod.DELIVERY : ShippingMethod.PICKUP);
        o.setPaymentMethod(PaymentMethod.COD);
        o.setNeedsPrescription(true);
        o.setStatus(OrderStatus.AWAITING_CUSTOMER);
        o.setHandler(pharmacist);
        o.setChannel("ONLINE");
        em.persist(o);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product p = em.find(Product.class, e.getKey());
            if (p == null) throw new BusinessException("Sản phẩm không tồn tại.");
            checkStock(p, e.getValue());
            newItem(o, p, p.unitOptions().get(0), e.getValue(), null);
            lines.add(p.getName() + " x" + e.getValue());
        }
        em.flush();
        recalc(o);
        recordRx(rx, pharmacist, form);
        rx.setOrder(o);
        addHistory(o, OrderStatus.AWAITING_CUSTOMER, "Dược sĩ lên đơn từ đơn thuốc: " + String.join("; ", lines), pharmacist);
        String msg = "Dược sĩ đã lên đơn " + o.getCode() + " từ đơn thuốc bạn gửi. Vui lòng xem báo giá và xác nhận.";
        notifications.notify(customer, msg, "/account/orders/" + o.getCode());
        mail.send(customer, "Báo giá đơn thuốc - " + o.getCode(), List.of(msg), "Xem báo giá", mail.url("/account/orders/" + o.getCode()), o, null);
        notifications.log(pharmacist, "rx.quote", "Đơn thuốc #" + rx.getId() + " → " + o.getCode());
        return o;
    }

    public Prescription rejectPrescription(Prescription rx, User pharmacist, String reason0) {
        if (!pharmacist.hasPermission(StaffPermission.RX_REVIEW)) throw BusinessException.forbidden("Bạn chưa được cấp quyền duyệt đơn thuốc.");
        String reason = Texts.trim(reason0, 500);
        if (Texts.mbLen(reason) < 5) throw new BusinessException("Vui lòng nhập lý do từ chối.");
        assertPending(rx);
        rx.setStatus(ApprovalStatus.REJECTED);
        rx.setPharmacist(pharmacist);
        rx.setRejectReason(reason);
        rx.setReviewedAt(LocalDateTime.now().withNano(0));
        Order o = rx.getOrder();
        if (o != null) {
            o.setStatus(OrderStatus.RX_REJECTED);
            addHistory(o, OrderStatus.RX_REJECTED, reason, pharmacist);
            String msg = "Đơn thuốc của đơn " + o.getCode() + " bị từ chối: " + reason + ". Bạn có thể tải lại đơn thuốc trong "
                    + settings.getInt("rx_rejected_cancel_days") + " ngày, quá hạn đơn hàng sẽ tự hủy.";
            notifications.notify(o.getUser(), msg, "/account/orders/" + o.getCode());
            mail.send(o.getUser(), "Đơn thuốc bị từ chối - " + o.getCode(), List.of(msg), "Tải lại đơn thuốc", mail.url("/account/orders/" + o.getCode()));
        } else {
            notifications.notify(rx.getUser(), "Đơn thuốc bạn gửi bị từ chối: " + reason + ". Bạn có thể gửi lại ảnh rõ hơn.", "/account/prescriptions");
        }
        notifications.log(pharmacist, "rx.reject", "Đơn thuốc #" + rx.getId() + (o != null ? " - " + o.getCode() : "") + ": " + reason);
        return rx;
    }

    /* ============================ Tự hủy định kỳ ============================ */

    /** Đơn cần tự hủy: danh sách id + điều kiện kiểm tra lại sau khi khóa. */
    public List<Long> abandonedRxRejectedIds() {
        int days = Math.max(1, settings.getInt("rx_rejected_cancel_days"));
        return em.createQuery("select o.id from Order o where o.status = :s and o.updatedAt <= :t", Long.class)
                .setParameter("s", OrderStatus.RX_REJECTED).setParameter("t", LocalDateTime.now().minusDays(days)).getResultList();
    }

    /**
     * Tự hủy 1 đơn bị từ chối đơn thuốc mà khách không tải lại sau N ngày (rx_rejected_cancel_days):
     * trả điểm đã dùng, lượt voucher, suất flash sale. Chạy định kỳ.
     */
    public boolean cancelAbandonedRxRejected(Long id) {
        int days = Math.max(1, settings.getInt("rx_rejected_cancel_days"));
        return autoCancel(id, o -> o.getStatus() == OrderStatus.RX_REJECTED,
                "Tự hủy: đơn thuốc bị từ chối và khách không tải lại sau " + days + " ngày",
                o -> "Đơn " + o.getCode() + " đã tự hủy do chưa tải lại đơn thuốc hợp lệ.");
    }

    private static final List<PaymentMethod> GATEWAYS = List.of(PaymentMethod.PAYOS, PaymentMethod.VNPAY);

    /** Đơn PayOS / VNPay chưa thanh toán quá N giờ (unpaid_cancel_hours, 0 = tắt), không có giao dịch vừa mở trong 30 phút. */
    public List<Long> unpaidOnlineIds() {
        int hours = settings.getInt("unpaid_cancel_hours");
        if (hours <= 0) return List.of();
        return em.createQuery("select o.id from Order o where o.status = :s and o.paymentMethod in :g and o.paymentStatus = :ps and o.updatedAt <= :t"
                        + " and not exists (select t from PaymentTransaction t where t.order = o and t.status = 'PENDING' and t.createdAt > :recent)", Long.class)
                .setParameter("s", OrderStatus.PENDING).setParameter("g", GATEWAYS).setParameter("ps", PaymentStatus.UNPAID)
                .setParameter("t", LocalDateTime.now().minusHours(hours)).setParameter("recent", LocalDateTime.now().minusMinutes(30)).getResultList();
    }

    /**
     * Tự hủy đơn chọn PayOS / VNPay nhưng khách không thanh toán sau N giờ để nhả hàng đang giữ chỗ.
     * Bỏ qua đơn vừa mở cổng thanh toán (khách có thể đang trả tiền); tiền về sau khi đã hủy được PaymentService chuyển "chờ hoàn tiền".
     * Chuyển khoản thủ công không tự hủy vì nhân viên đối soát sao kê và xác nhận bằng tay.
     */
    public boolean cancelUnpaidOnline(Long id) {
        int hours = settings.getInt("unpaid_cancel_hours");
        if (hours <= 0) return false;
        return autoCancel(id, o -> o.getStatus() == OrderStatus.PENDING && GATEWAYS.contains(o.getPaymentMethod())
                        && o.getPaymentStatus() == PaymentStatus.UNPAID && o.getUpdatedAt() != null && !o.getUpdatedAt().isAfter(LocalDateTime.now().minusHours(hours))
                        && em.createQuery("select count(t) from PaymentTransaction t where t.order.id = :o and t.status = 'PENDING' and t.createdAt > :recent", Long.class)
                        .setParameter("o", o.getId()).setParameter("recent", LocalDateTime.now().minusMinutes(30)).getSingleResult() == 0,
                "Tự hủy: khách chưa thanh toán online sau " + hours + " giờ",
                o -> "Đơn " + o.getCode() + " đã tự hủy do chưa thanh toán sau " + hours + " giờ. Bạn có thể đặt lại bất cứ lúc nào.");
    }

    public List<Long> unconfirmedQuoteIds() {
        int days = settings.getInt("quote_cancel_days");
        if (days <= 0) return List.of();
        return em.createQuery("select o.id from Order o where o.status = :s and o.updatedAt <= :t", Long.class)
                .setParameter("s", OrderStatus.AWAITING_CUSTOMER).setParameter("t", LocalDateTime.now().minusDays(days)).getResultList();
    }

    /** Tự hủy báo giá / đơn dược sĩ đã điều chỉnh mà khách không xác nhận sau N ngày (quote_cancel_days, 0 = tắt). */
    public boolean cancelUnconfirmedQuote(Long id) {
        int days = settings.getInt("quote_cancel_days");
        if (days <= 0) return false;
        return autoCancel(id, o -> o.getStatus() == OrderStatus.AWAITING_CUSTOMER,
                "Tự hủy: khách không xác nhận đơn sau " + days + " ngày",
                o -> "Đơn " + o.getCode() + " đã tự hủy do chưa được xác nhận sau " + days + " ngày.");
    }

    /** Khóa đơn, kiểm tra lại điều kiện rồi trả tài nguyên (kho, điểm, voucher, flash sale) và hủy. */
    private boolean autoCancel(Long id, Predicate<Order> stillMatches, String reason, Function<Order, String> message) {
        Order o = em.find(Order.class, id);
        if (o == null) return false;
        lockOrder(o);
        if (!stillMatches.test(o)) return false;
        releaseResources(o);
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelReason(reason);
        addHistory(o, OrderStatus.CANCELLED, reason, null);
        notifications.notify(o.getUser(), message.apply(o) + (o.getPointsUsed() > 0 ? " Điểm đã dùng được hoàn lại." : ""), "/account/orders/" + o.getCode());
        return true;
    }

    /* ============================ Tiện ích ============================ */

    /**
     * Sau khi dược sĩ điều chỉnh đơn: kiểm tra lại điều kiện khuyến mãi theo các dòng hàng còn lại.
     * Chỉ giảm, không tăng (combo, quà tặng, mã giảm giá không vượt mức khách được hưởng lúc đặt);
     * chương trình lấy theo thời điểm đặt hàng để khách không mất ưu đãi vì chương trình đã kết thúc.
     *
     * @return mô tả thay đổi (ghi vào lịch sử đơn)
     */
    private List<String> reapplyPromotions(Order o) {
        List<String> changes = new ArrayList<>();
        Map<Long, Integer> baseQty = new HashMap<>();
        long discountable = 0;
        Map<Long, Long> byCategory = new HashMap<>();
        for (OrderItem it : o.getItems()) {
            if (it.isGift() || it.getProduct() == null) continue;
            if (PromotionService.eligible(it.getProduct())) baseQty.merge(it.getProduct().getId(), it.baseQuantity(), Integer::sum);
            if (!it.isPrescription()) {
                discountable += it.lineTotal();
                if (it.getProduct().getCategoryId() != null) byCategory.merge(it.getProduct().getCategoryId(), it.lineTotal(), Long::sum);
            }
        }
        PromotionService.Evaluation ev = promotions.combosAndGifts(baseQty,
                promotions.runningAt(o.getCreatedAt() != null ? o.getCreatedAt() : LocalDateTime.now()), List.of());

        // Combo
        long combo = Math.min(o.getPromoDiscount(), Math.min(ev.combo, discountable));
        if (combo < o.getPromoDiscount()) {
            changes.add("giảm giá combo " + money(o.getPromoDiscount()) + " → " + money(combo) + " (không còn đủ bộ)");
            o.setPromoDiscount(combo);
        }

        // Quà tặng: số lượng tặng tối đa theo số lượng mua còn lại
        Map<Long, Integer> allowed = new HashMap<>();
        for (PromotionService.Gift g : ev.gifts) allowed.merge(g.product().getId(), g.quantity(), Integer::sum);
        for (OrderItem it : new ArrayList<>(o.getItems())) {
            if (!it.isGift()) continue;
            Long pid = it.getProduct().getId();
            int keep = Math.min(it.getQuantity(), allowed.getOrDefault(pid, 0));
            allowed.put(pid, allowed.getOrDefault(pid, 0) - keep);
            if (keep < it.getQuantity()) {
                changes.add(keep == 0 ? "bỏ quà tặng " + it.getProductName() + " (không còn đủ điều kiện)" : it.getProductName() + ": " + it.getQuantity() + " → " + keep);
                if (keep == 0) o.getItems().remove(it);
                else it.setQuantity(keep);
            }
        }
        if (o.getPromoNote() != null && !o.getPromoNote().isEmpty() && !changes.isEmpty()) {
            o.setPromoNote(Texts.trim(o.getPromoNote() + "; Điều chỉnh: " + String.join("; ", changes), 500));
        }

        // Mã giảm giá: kiểm tra lại đơn tối thiểu / danh mục trên giá trị hàng còn lại
        if (o.getVoucherCode() != null && o.getDiscount() > 0) {
            List<Voucher> vs = em.createQuery("select v from Voucher v left join fetch v.category where v.code = :c", Voucher.class)
                    .setParameter("c", o.getVoucherCode()).getResultList();
            if (!vs.isEmpty()) {
                Voucher v = vs.get(0);
                VoucherService.Result r = vouchers.discountFor(v, Math.max(0, discountable - o.getPromoDiscount()), byCategory);
                if (r.error() != null) {
                    changes.add("bỏ mã giảm giá " + v.getCode() + " (" + r.error() + ")");
                    o.setDiscount(0);
                    o.setVoucherCode(null);
                    sql.update("update vouchers set used_count = used_count - 1 where id = :id and used_count > 0", Map.of("id", v.getId()));
                } else if (r.discount() < o.getDiscount()) {
                    changes.add("mã " + v.getCode() + " giảm " + money(o.getDiscount()) + " → " + money(r.discount()));
                    o.setDiscount(r.discount());
                }
            }
        }
        return changes;
    }

    /** Tính lại tiền sau khi điều chỉnh; giảm giá và điểm chỉ áp trên phần không phải thuốc kê đơn. */
    public void recalc(Order o) {
        long subtotal = 0, discountable = 0, weight = 0;
        for (OrderItem it : o.getItems()) {
            subtotal += it.lineTotal();
            if (!it.isPrescription()) discountable += it.lineTotal();
            int w = it.getProduct() != null && it.getProduct().getWeightGram() > 0 ? it.getProduct().getWeightGram() : 200;
            weight += (long) w * it.getQuantity();
        }
        long promo = Math.min(o.getPromoDiscount(), discountable);
        long discount = Math.min(o.getDiscount(), Math.max(0, discountable - promo));
        long pointsDiscount = o.getPointsDiscount();
        long pointValue = Math.max(1, settings.getLong("point_value"));
        if (pointsDiscount > discountable - promo - discount) {
            // Trả lại phần điểm không dùng được nữa
            int allowedPts = (int) (Math.max(0, discountable - promo - discount) / pointValue);
            int refund = o.getPointsUsed() - allowedPts;
            if (refund > 0) addPoints(o.getUser(), refund);
            o.setPointsUsed(allowedPts);
            pointsDiscount = allowedPts * pointValue;
            o.setPointsDiscount(pointsDiscount);
        }
        long after = subtotal - promo - discount - pointsDiscount;
        long ship = shippingFeeFor(o.getShippingMethod() != null ? o.getShippingMethod() : ShippingMethod.DELIVERY, after, o.getProvince(),
                o.getGhnDistrictId(), o.getGhnWardCode(), weight, subtotal);
        if (o.getPaymentStatus() != PaymentStatus.UNPAID && after + ship != o.getTotal()) {
            // Số tiền khách đã trả phải khớp tổng đơn; muốn đổi đơn đã thu tiền thì hủy và hoàn tiền
            throw new BusinessException("Đơn " + o.getCode() + " đã ghi nhận thanh toán " + money(o.getTotal()) + " - không thể điều chỉnh làm thay đổi tổng tiền.");
        }
        o.setSubtotal(subtotal);
        o.setPromoDiscount(promo);
        o.setDiscount(discount);
        o.setShippingFee(ship);
        o.setTotal(after + ship);
    }

    public String newCode(String prefix) {
        String code;
        do {
            code = Texts.code(prefix);
        } while (em.createQuery("select count(o) from Order o where o.code = :c", Long.class).setParameter("c", code).getSingleResult() > 0);
        return code;
    }

    public void addHistory(Order o, OrderStatus status, String note, User user) {
        OrderHistory h = new OrderHistory();
        h.setOrder(o);
        h.setStatus(status);
        h.setNote(note != null ? Texts.limit(note, 997) : null);
        h.setUser(user);
        em.persist(h);
        o.getHistory().add(h);
    }

    /** Thêm lại sản phẩm của đơn cũ vào giỏ (mua lại nhanh). Trả về danh sách không thêm được. */
    public List<String> reorder(Order o, Cart cart) {
        List<String> skipped = new ArrayList<>();
        for (OrderItem it : o.getItems()) {
            if (it.isGift()) continue;
            Product p = em.find(Product.class, it.getProduct().getId());
            UnitOption unit = p == null ? null : Optional.ofNullable(p.findUnitByFactor(it.factor())).orElse(p.unitOptions().get(0));
            if (p == null || carts.checkAdd(cart, p, unit, it.getQuantity()) != null) {
                skipped.add(it.getProductName());
                continue;
            }
            cart.add(p.getId(), unit.id(), it.getQuantity());
        }
        return skipped;
    }

    public static String money(long n) {
        return PromotionService.money(n);
    }

    public static String num(long n) {
        return java.text.NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(n);
    }
}
