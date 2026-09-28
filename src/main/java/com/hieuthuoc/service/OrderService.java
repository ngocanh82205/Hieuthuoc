package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final PrescriptionRepository prescriptionRepo;
    private final VoucherRepository voucherRepo;
    private final AddressRepository addressRepo;
    private final CartService cartService;
    private final StockService stockService;
    private final FileStorageService files;
    private final NotificationService notifications;
    private final SettingService settings;
    private final com.hieuthuoc.repository.BatchRepository batchRepo;
    private final com.hieuthuoc.repository.StockAdjustmentRepository adjustmentRepo;
    private final CatalogService catalogService;
    private final UserRepository userRepo;
    private final PromotionRepository promotionRepo;

    /* ============================ Đặt hàng ============================ */

    @Getter
    @Setter
    public static class CheckoutForm {
        private String recipient;
        private String phone;
        private String address;
        private ShippingMethod shippingMethod = ShippingMethod.DELIVERY;
        private PaymentMethod paymentMethod = PaymentMethod.COD;
        private String note;
        private String rxNote;
        private boolean saveAddress;
        private String province;
        /** Yêu cầu xuất hóa đơn VAT */
        private boolean requestVat;
        private String vatCompany;
        private String vatTaxCode;
        private String vatAddress;
        private String vatEmail;
    }

    /** Lỗi kiểm tra form (nhiều lỗi cùng lúc). */
    @Getter
    public static class CheckoutException extends RuntimeException {
        private final List<String> errors;

        public CheckoutException(List<String> errors) {
            super(String.join(" ", errors));
            this.errors = errors;
        }
    }

    private static List<String> validateDelivery(String recipient, String phone, ShippingMethod method, String address) {
        List<String> errors = new ArrayList<>();
        if (Texts.trim(recipient).length() < 2) errors.add("Vui lòng nhập tên người nhận.");
        if (!Texts.isPhone(Texts.trim(phone))) errors.add("Số điện thoại người nhận không hợp lệ (10-11 số, bắt đầu bằng 0).");
        if (method == ShippingMethod.DELIVERY && Texts.trim(address).length() < 10) errors.add("Vui lòng nhập địa chỉ giao hàng đầy đủ.");
        return errors;
    }

    static List<String> validateVat(boolean request, String company, String taxCode, String address, String email) {
        List<String> errors = new ArrayList<>();
        if (!request) return errors;
        if (Texts.trim(company).length() < 3) errors.add("Nhập tên công ty / đơn vị để xuất hóa đơn VAT.");
        if (!Texts.trim(taxCode).matches("\\d{10}(-\\d{3})?")) errors.add("Mã số thuế không hợp lệ (10 số hoặc 10 số-3 số).");
        if (Texts.trim(address).length() < 10) errors.add("Nhập địa chỉ xuất hóa đơn.");
        if (!Texts.isBlank(email) && !Texts.isEmail(email.trim())) errors.add("Email nhận hóa đơn không hợp lệ.");
        return errors;
    }

    private static OrderItem newItem(Order o, Product p, UnitOption unit, int qty) {
        OrderItem it = new OrderItem();
        it.setOrder(o);
        it.setProduct(p);
        it.setProductName(p.getName());
        it.setUnit(unit.name());
        it.setUnitFactor(unit.factor());
        it.setDrugType(p.getDrugType());
        it.setPrice(unit.price());
        it.setQuantity(qty);
        return it;
    }

    public Order placeOrder(User user, Cart cart, CheckoutForm f, MultipartFile rxFile) {
        if (f.getShippingMethod() == null) f.setShippingMethod(ShippingMethod.DELIVERY);
        if (f.getPaymentMethod() == null || !PaymentMethod.ONLINE_METHODS.contains(f.getPaymentMethod())) f.setPaymentMethod(PaymentMethod.COD);
        cart.setProvince(f.getShippingMethod() == ShippingMethod.DELIVERY ? Texts.emptyToNull(f.getProvince()) : null);
        CartService.View cv = cartService.build(cart, f.getShippingMethod(), user);

        List<String> errors = new ArrayList<>(cv.getErrors());
        if (!settings.enabledPaymentMethods().contains(f.getPaymentMethod())) errors.add("Phương thức thanh toán này đang tạm ngừng, vui lòng chọn cách khác.");
        if (f.getShippingMethod() == ShippingMethod.DELIVERY) {
            if (Texts.isBlank(f.getProvince()) || !ShippingZone.PROVINCES.contains(f.getProvince())) errors.add("Vui lòng chọn tỉnh/thành phố giao hàng.");
            else if (!settings.deliversTo(f.getProvince())) errors.add("Nhà thuốc chưa giao hàng tới " + f.getProvince() + ". Bạn có thể chọn nhận tại nhà thuốc.");
        }
        errors.addAll(validateVat(f.isRequestVat(), f.getVatCompany(), f.getVatTaxCode(), f.getVatAddress(), f.getVatEmail()));
        if (cv.isEmpty()) errors.add("Giỏ hàng đang trống.");
        if (cv.getVoucherError() != null) errors.add(cv.getVoucherError());
        errors.addAll(validateDelivery(f.getRecipient(), f.getPhone(), f.getShippingMethod(), f.getAddress()));
        if (cv.isRxRequired() && !files.isPresent(rxFile)) {
            errors.add("Giỏ hàng có thuốc kê đơn - vui lòng tải lên ảnh đơn thuốc hợp lệ.");
        }
        if (!errors.isEmpty()) throw new CheckoutException(errors);

        String rxImage = null;
        if (cv.isRxRequired()) {
            try {
                rxImage = files.store(FileStorageService.Kind.PRESCRIPTIONS, rxFile);
            } catch (BusinessException e) {
                throw new CheckoutException(List.of(e.getMessage()));
            }
        }

        Order o = new Order();
        o.setCode(newCode());
        o.setUser(user);
        o.setRecipient(Texts.trim(f.getRecipient(), 100));
        o.setPhone(Texts.trim(f.getPhone()));
        o.setAddress(f.getShippingMethod() == ShippingMethod.PICKUP ? null : Texts.trim(f.getAddress(), 300));
        o.setShippingMethod(f.getShippingMethod());
        o.setProvince(cart.getProvince());
        o.setPaymentMethod(f.getPaymentMethod());
        if (f.isRequestVat()) {
            o.setVatCompany(Texts.trim(f.getVatCompany(), 200));
            o.setVatTaxCode(Texts.trim(f.getVatTaxCode(), 20));
            o.setVatAddress(Texts.trim(f.getVatAddress(), 300));
            o.setVatEmail(Texts.emptyToNull(Texts.trim(f.getVatEmail(), 150)));
        }
        o.setSubtotal(cv.getSubtotal());
        o.setDiscount(cv.getDiscount());
        o.setPointsUsed(cv.getPointsUsed());
        o.setPointsDiscount(cv.getPointsDiscount());
        o.setShippingFee(cv.getShippingFee());
        o.setTotal(cv.getTotal());
        o.setVoucherCode(cv.getVoucher() == null ? null : cv.getVoucher().getCode());
        o.setNeedsPrescription(cv.isRxRequired());
        o.setStatus(cv.isRxRequired() ? OrderStatus.PENDING_RX : OrderStatus.PENDING);
        o.setNote(Texts.emptyToNull(Texts.trim(f.getNote(), 500)));

        for (CartService.Line line : cv.getLines()) {
            OrderItem it = newItem(o, line.getProduct(), line.getUnit(), line.getQuantity());
            it.setPrice(line.getUnitPrice());
            o.getItems().add(it);
            // Trừ suất flash sale
            if (line.getFlashPromotionId() != null) {
                promotionRepo.findById(line.getFlashPromotionId()).ifPresent(pr -> pr.setSoldCount(pr.getSoldCount() + line.getBaseQuantity()));
            }
        }
        // Quà tặng kèm (mua X tặng Y): dòng giá 0, được giữ chỗ và xuất kho như hàng bán
        List<String> promoNotes = new ArrayList<>(cv.getPromoNotes().stream().filter(n -> n.startsWith("Combo")).toList());
        for (PromotionService.Gift g : cv.getGifts()) {
            Product gp = g.product();
            OrderItem gift = newItem(o, gp, gp.getUnitOptions().get(0), g.quantity());
            gift.setPrice(0);
            gift.setProductName(gp.getName() + " (quà tặng)");
            o.getItems().add(gift);
            promoNotes.add("Tặng " + g.quantity() + " " + gp.getUnit() + " " + gp.getName());
        }
        o.setPromoDiscount(cv.getPromoDiscount() > 0 ? cv.getPromoDiscount() : null);
        o.setPromoNote(promoNotes.isEmpty() ? null : Texts.trim(String.join("; ", promoNotes), 500));
        if (rxImage != null) {
            Prescription rx = new Prescription();
            rx.setOrder(o);
            rx.setUser(user);
            rx.setImage(rxImage);
            rx.setCustomerNote(Texts.emptyToNull(Texts.trim(f.getRxNote(), 500)));
            o.getPrescriptions().add(rx);
        }
        if (cv.getVoucher() != null) cv.getVoucher().setUsedCount(cv.getVoucher().getUsedCount() + 1);
        if (cv.getPointsUsed() > 0) user.setPoints(user.getPoints() - cv.getPointsUsed());

        if (f.isSaveAddress() && f.getShippingMethod() == ShippingMethod.DELIVERY
                && !addressRepo.existsByUserAndAddressLine(user, o.getAddress())) {
            Address a = new Address();
            a.setUser(user);
            a.setRecipient(o.getRecipient());
            a.setPhone(o.getPhone());
            a.setAddressLine(o.getAddress());
            a.setDefaultAddress(!addressRepo.existsByUser(user));
            addressRepo.save(a);
        }

        addHistory(o, o.getStatus(), (cv.isRxRequired() ? "Khách đặt hàng kèm đơn thuốc" : "Khách đặt hàng")
                + (cv.getPointsUsed() > 0 ? " (dùng " + cv.getPointsUsed() + " điểm)" : ""), user);
        orderRepo.save(o);

        notifications.notifyStaff(
                cv.isRxRequired() ? "Đơn " + o.getCode() + " có thuốc kê đơn cần duyệt" : "Đơn hàng mới " + o.getCode(),
                cv.isRxRequired() ? "/staff/prescriptions" : "/staff/orders/" + o.getId());
        notifications.log(user, "order.create", o.getCode());
        cart.clear();
        return o;
    }

    /* ============================ Chuyển trạng thái (nhân viên) ============================ */

    public Order changeStatus(Long orderId, OrderStatus to, User staff, String note) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
        OrderStatus from = o.getStatus();
        if (to == null || !from.staffTransitions().contains(to)) {
            throw new BusinessException("Không thể chuyển từ \"" + from.getLabel() + "\" sang \"" + (to == null ? "?" : to.getLabel()) + "\".");
        }
        note = Texts.emptyToNull(Texts.trim(note, 500));
        switch (to) {
            case CONFIRMED -> {
                if (o.getPaymentMethod().isPrepaid() && o.getPaymentStatus() != PaymentStatus.PAID) {
                    throw new BusinessException("Đơn " + o.getPaymentMethod().getLabel().toLowerCase() + " chưa nhận được tiền của khách.");
                }
                if (needsVerifyCall(o) && o.getVerifiedAt() == null) {
                    throw new BusinessException("Đơn COD giá trị lớn (từ " + String.format("%,d", settings.getLong("cod_verify_threshold")).replace(',', '.')
                            + " đ) - vui lòng gọi điện xác minh với khách trước khi xác nhận.");
                }
            }
            case PREPARING -> stockService.allocateFefo(o);
            case SHIPPING -> {
                if (o.getShippingMethod() == ShippingMethod.DELIVERY && Texts.isBlank(o.getCarrier())) {
                    throw new BusinessException("Vui lòng chọn đơn vị vận chuyển trước khi giao hàng.");
                }
            }
            case CANCELLED -> {
                if (note == null) throw new BusinessException("Vui lòng nhập lý do hủy đơn.");
                if (o.getPaymentStatus() == PaymentStatus.PAID && !staff.hasPermission(StaffPermission.REFUND)) {
                    throw new BusinessException("Đơn đã thanh toán - bạn chưa được cấp quyền hủy đơn & hoàn tiền.");
                }
                releaseResources(o);
                o.setCancelReason(note);
            }
            case COMPLETED -> {
                o.setPaymentStatus(PaymentStatus.PAID);
                o.setCompletedAt(LocalDateTime.now());
                User c = o.getUser();
                // Điểm cộng = tổng tiền / số tiền mỗi điểm x hệ số hạng thành viên
                MemberTier tier = MemberTier.of(orderRepo.totalSpent(c));
                int earned = (int) Math.floor(o.getTotal() / (double) settings.getLong("points_per_amount") * tier.getPointMultiplier());
                o.setPointsEarned(earned);
                c.setPoints(c.getPoints() + earned);
            }
            default -> {
            }
        }
        if (o.getHandledBy() == null) o.setHandledBy(staff);
        o.setStatus(to);
        addHistory(o, to, note, staff);
        Map<String, String> vars = new HashMap<>();
        vars.put("code", o.getCode());
        vars.put("status", to.getLabel());
        vars.put("note", note != null ? (to == OrderStatus.CANCELLED ? note : " - " + note) : "");
        vars.put("carrier", o.getCarrier() == null ? "nhà thuốc" : o.getCarrier());
        vars.put("tracking", o.getTrackingCode() == null ? "" : " (mã vận đơn " + o.getTrackingCode() + ")");
        vars.put("points", String.valueOf(o.getPointsEarnedValue()));
        String tpl = switch (to) {
            case SHIPPING -> "tpl_order_shipping";
            case COMPLETED -> "tpl_order_completed";
            case CANCELLED -> "tpl_order_cancelled";
            default -> "tpl_order_status";
        };
        if (!o.isPos()) notifications.notify(o.getUser(), settings.render(tpl, vars), "/account/orders/" + o.getCode());
        notifications.log(staff, "order.status", o.getCode() + ": " + from + " → " + to);
        return o;
    }

    /** Hủy đơn: hoàn kho (nếu đã xuất), hoàn tiền, hoàn điểm, trả lượt dùng voucher. */
    private void releaseResources(Order o) {
        if (o.getStatus() == OrderStatus.PREPARING || o.getStatus() == OrderStatus.SHIPPING) stockService.restore(o);
        if (o.getPaymentStatus() == PaymentStatus.PAID) requestRefund(o, o.getTotal());
        if (o.getPointsUsedValue() > 0) o.getUser().setPoints(o.getUser().getPoints() + o.getPointsUsedValue());
        releaseVoucher(o);
    }

    /** Đơn đã thu tiền bị hủy / trả hàng: chuyển sang "Chờ hoàn tiền" để admin / người có quyền duyệt hoàn tiền. */
    private void requestRefund(Order o, long amount) {
        o.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        o.setRefundAmount(amount);
        notifications.notifyAdmins("Đơn " + o.getCode() + " cần duyệt hoàn tiền " + String.format("%,d", amount).replace(',', '.') + " đ", "/admin/refunds");
    }

    /** Duyệt hoàn tiền (admin hoặc nhân viên có quyền REFUND): ghi nhận số tiền, cách hoàn, mã giao dịch. */
    public Order approveRefund(Long orderId, User approver, long amount, String note) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
        if (!approver.hasPermission(StaffPermission.REFUND)) throw new BusinessException("Bạn chưa được cấp quyền duyệt hoàn tiền.", 403);
        if (o.getPaymentStatus() != PaymentStatus.REFUND_PENDING) throw new BusinessException("Đơn không ở trạng thái chờ hoàn tiền.");
        if (amount <= 0 || amount > o.getTotal()) throw new BusinessException("Số tiền hoàn phải từ 1 đến " + o.getTotal() + " đ.");
        note = Texts.trim(note, 300);
        if (note.isEmpty()) throw new BusinessException("Vui lòng ghi hình thức / mã giao dịch hoàn tiền.");
        o.setPaymentStatus(PaymentStatus.REFUNDED);
        o.setRefundAmount(amount);
        o.setRefundedAt(LocalDateTime.now());
        o.setRefundedBy(approver);
        o.setRefundNote(note);
        addHistory(o, o.getStatus(), "Đã hoàn tiền " + String.format("%,d", amount).replace(',', '.') + " đ: " + note, approver);
        notifications.notify(o.getUser(), "Nhà thuốc đã hoàn " + String.format("%,d", amount).replace(',', '.') + " đ cho đơn " + o.getCode() + " (" + note + ").",
                "/account/orders/" + o.getCode());
        notifications.log(approver, "refund.approve", o.getCode() + ": " + amount + " - " + note);
        return o;
    }

    /** Admin phân công nhân viên / dược sĩ xử lý đơn. */
    public Order assign(Long orderId, User admin, Long staffId) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
        User staff = staffId == null ? null : userRepo.findById(staffId).orElseThrow(() -> new BusinessException("Nhân viên không tồn tại."));
        if (staff != null && (!staff.isStaff() || staff.isLocked() || !staff.hasPermission(StaffPermission.ORDER))) {
            throw new BusinessException("Nhân viên được phân công phải có quyền xử lý đơn hàng.");
        }
        o.setAssignedTo(staff);
        addHistory(o, o.getStatus(), staff == null ? "Bỏ phân công" : "Phân công cho " + staff.getFullName(), admin);
        if (staff != null && !staff.getId().equals(admin.getId())) notifications.notify(staff, "Bạn được phân công xử lý đơn " + o.getCode(), "/staff/orders/" + o.getId());
        notifications.log(admin, "order.assign", o.getCode() + " → " + (staff == null ? "(không)" : staff.getFullName()));
        return o;
    }

    /** Đơn COD có giá trị từ ngưỡng cấu hình trở lên phải gọi điện xác minh trước khi xác nhận. */
    public boolean needsVerifyCall(Order o) {
        return !o.isPos() && o.getPaymentMethod() == PaymentMethod.COD && o.getTotal() >= settings.getLong("cod_verify_threshold");
    }

    public void verifyCall(Long orderId, User staff, String note) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
        note = Texts.trim(note, 300);
        if (note.isEmpty()) throw new BusinessException("Vui lòng ghi kết quả cuộc gọi xác minh.");
        o.setVerifiedAt(LocalDateTime.now());
        o.setVerifiedBy(staff);
        o.setVerifyNote(note);
        addHistory(o, o.getStatus(), "Đã gọi xác minh: " + note, staff);
        notifications.log(staff, "order.verify_call", o.getCode() + ": " + note);
    }

    /** Soạn hàng: nhân viên chọn hoặc quét số lô cho từng dòng (bỏ trống = theo gợi ý FEFO). */
    public Order prepare(Long orderId, User staff, Map<Long, String> batchInputs) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
        if (o.getStatus() != OrderStatus.CONFIRMED) throw new BusinessException("Chỉ soạn hàng cho đơn đã xác nhận.");
        Map<Long, Batch> preferred = new HashMap<>();
        for (OrderItem it : o.getItems()) {
            String in = Texts.trim(batchInputs.get(it.getId()));
            if (in.isEmpty()) continue;
            Batch b = in.matches("\\d+") ? batchRepo.findById(Long.parseLong(in)).orElse(null) : null;
            if (b == null || !b.getProduct().getId().equals(it.getProduct().getId())) {
                b = batchRepo.findFirstByProductAndBatchNoIgnoreCase(it.getProduct(), in).orElse(null);
            }
            if (b == null) throw new BusinessException("Không tìm thấy lô \"" + in + "\" của sản phẩm " + it.getProductName() + ".");
            preferred.put(it.getId(), b);
        }
        stockService.allocate(o, preferred);
        o.setStatus(OrderStatus.PREPARING);
        if (o.getHandledBy() == null) o.setHandledBy(staff);
        List<String> lots = new ArrayList<>();
        for (OrderItem it : o.getItems()) {
            for (OrderItemBatch a : it.getAllocations()) lots.add(it.getProductName() + ": lô " + a.getBatch().getBatchNo() + " x" + a.getQuantity());
        }
        addHistory(o, OrderStatus.PREPARING, "Soạn hàng - " + String.join("; ", lots), staff);
        notifications.notify(o.getUser(), "Đơn hàng " + o.getCode() + ": " + OrderStatus.PREPARING.getLabel(), "/account/orders/" + o.getCode());
        notifications.log(staff, "order.status", o.getCode() + ": CONFIRMED → PREPARING");
        return o;
    }

    /** Ghi đơn vị vận chuyển + mã vận đơn (VD GHN/GHTK) trước khi chuyển sang Đang giao. */
    public void setShipping(Long orderId, String carrier, String trackingCode) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
        o.setCarrier(Texts.emptyToNull(Texts.trim(carrier, 50)));
        o.setTrackingCode(Texts.emptyToNull(Texts.trim(trackingCode, 60)));
    }

    public void markPaid(Long orderId, User staff) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
        if (o.getPaymentStatus() != PaymentStatus.UNPAID) throw new BusinessException("Đơn hàng không ở trạng thái chưa thanh toán.");
        o.setPaymentStatus(PaymentStatus.PAID);
        addHistory(o, o.getStatus(), "Xác nhận đã nhận thanh toán", staff);
        notifications.notify(o.getUser(), "Nhà thuốc đã nhận được thanh toán cho đơn " + o.getCode() + ".", "/account/orders/" + o.getCode());
        notifications.log(staff, "order.paid", o.getCode());
    }

    /* ============================ Khách hàng ============================ */

    public void cancelByCustomer(Order o, User user, String reason) {
        if (!o.getStatus().isCustomerCancellable()) {
            throw new BusinessException("Đơn hàng đang giao hoặc đã hoàn tất, không thể hủy. Vui lòng liên hệ nhà thuốc.");
        }
        reason = Texts.isBlank(reason) ? "Khách hàng hủy" : Texts.trim(reason, 500);
        for (Prescription rx : o.getPrescriptions()) {
            if (rx.getStatus() == ApprovalStatus.PENDING) {
                rx.setStatus(ApprovalStatus.REJECTED);
                rx.setRejectReason("Khách hủy đơn");
            }
        }
        releaseResources(o);
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelReason(reason);
        addHistory(o, OrderStatus.CANCELLED, reason, user);
        notifications.notifyStaff("Khách đã hủy đơn " + o.getCode(), "/staff/orders/" + o.getId());
    }

    public boolean canPay(Order o) {
        return o.getPaymentMethod() == PaymentMethod.ONLINE && o.getPaymentStatus() == PaymentStatus.UNPAID
                && (o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.CONFIRMED);
    }

    /** Hiện hướng dẫn chuyển khoản khi đơn đã được chấp nhận xử lý mà chưa nhận tiền. */
    public boolean showBankTransfer(Order o) {
        return o.getPaymentMethod() == PaymentMethod.BANK_TRANSFER && o.getPaymentStatus() == PaymentStatus.UNPAID
                && (o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.CONFIRMED);
    }

    /** Cổng thanh toán giả lập (demo). Khi triển khai thật: tích hợp VNPay/MoMo và xử lý IPN. */
    public void pay(Order o, User user, boolean success) {
        if (!canPay(o)) throw new BusinessException("Đơn hàng không ở trạng thái có thể thanh toán.");
        if (!success) throw new BusinessException("Thanh toán thất bại hoặc đã bị hủy. Bạn có thể thử lại.");
        o.setPaymentStatus(PaymentStatus.PAID);
        addHistory(o, o.getStatus(), "Khách thanh toán online thành công", user);
    }

    public void reuploadPrescription(Order o, User user, MultipartFile file, String note) {
        if (o.getStatus() != OrderStatus.RX_REJECTED) throw new BusinessException("Đơn hàng không cần tải lại đơn thuốc.");
        Prescription rx = new Prescription();
        rx.setOrder(o);
        rx.setUser(user);
        rx.setImage(files.store(FileStorageService.Kind.PRESCRIPTIONS, file));
        rx.setCustomerNote(Texts.emptyToNull(Texts.trim(note, 500)));
        o.getPrescriptions().add(0, rx);
        o.setStatus(OrderStatus.PENDING_RX);
        addHistory(o, OrderStatus.PENDING_RX, "Khách tải lại đơn thuốc", user);
        notifications.notifyStaff("Đơn " + o.getCode() + ": khách đã tải lại đơn thuốc", "/staff/prescriptions");
    }

    @Getter
    @Setter
    public static class ConfirmForm {
        private String recipient;
        private String phone;
        private String address;
        private ShippingMethod shippingMethod = ShippingMethod.DELIVERY;
        private PaymentMethod paymentMethod = PaymentMethod.COD;
        private String province;
    }

    /** Khách xác nhận đơn do dược sĩ lên (hoặc đã điều chỉnh/thay thuốc): chọn giao hàng, thanh toán. */
    public void confirmByCustomer(Order o, User user, ConfirmForm f) {
        if (o.getStatus() != OrderStatus.AWAITING_CUSTOMER) throw new BusinessException("Đơn hàng không ở trạng thái chờ xác nhận.");
        if (f.getShippingMethod() == null) f.setShippingMethod(ShippingMethod.DELIVERY);
        if (f.getPaymentMethod() == null || !PaymentMethod.ONLINE_METHODS.contains(f.getPaymentMethod())) f.setPaymentMethod(PaymentMethod.COD);
        List<String> errors = validateDelivery(f.getRecipient(), f.getPhone(), f.getShippingMethod(), f.getAddress());
        if (!errors.isEmpty()) throw new BusinessException(String.join(" ", errors));
        o.setRecipient(Texts.trim(f.getRecipient(), 100));
        o.setPhone(Texts.trim(f.getPhone()));
        o.setShippingMethod(f.getShippingMethod());
        o.setAddress(f.getShippingMethod() == ShippingMethod.PICKUP ? null : Texts.trim(f.getAddress(), 300));
        String province = Texts.emptyToNull(f.getProvince());
        if (province == null) province = ShippingZone.detect(f.getAddress());
        if (f.getShippingMethod() == ShippingMethod.DELIVERY && province != null && !settings.deliversTo(province)) {
            throw new BusinessException("Nhà thuốc chưa giao hàng tới " + province + ". Bạn có thể chọn nhận tại nhà thuốc.");
        }
        o.setProvince(f.getShippingMethod() == ShippingMethod.PICKUP ? null : province);
        o.setPaymentMethod(settings.enabledPaymentMethods().contains(f.getPaymentMethod()) ? f.getPaymentMethod() : PaymentMethod.COD);
        recalc(o);
        o.setStatus(OrderStatus.PENDING);
        addHistory(o, OrderStatus.PENDING, "Khách xác nhận đơn hàng", user);
        notifications.notifyStaff("Khách đã xác nhận đơn " + o.getCode(), "/staff/orders/" + o.getId());
    }

    /** Thuốc kê đơn không được đổi trả nếu cấu hình không cho phép (mặc định: không). */
    public boolean returnBlockedByRx(Order o) {
        return settings.getLong("return_rx_allowed") == 0 && o.getItems().stream().anyMatch(i -> i.getDrugType() != null && i.getDrugType().isPrescription());
    }

    public boolean canRequestReturn(Order o) {
        return !returnBlockedByRx(o) && o.getStatus() == OrderStatus.COMPLETED && o.getReturnStatus() == null && o.getCompletedAt() != null
                && ChronoUnit.DAYS.between(o.getCompletedAt().toLocalDate(), LocalDate.now()) <= settings.getLong("return_days");
    }

    public void requestReturn(Order o, User user, String reason) {
        if (!canRequestReturn(o)) {
            throw new BusinessException(returnBlockedByRx(o) ? "Theo chính sách, đơn có thuốc kê đơn không áp dụng đổi/trả (trừ lỗi từ nhà thuốc - vui lòng liên hệ hotline)."
                    : "Đơn hàng không thể yêu cầu đổi/trả (chỉ áp dụng trong " + settings.get("return_days") + " ngày sau khi nhận hàng).");
        }
        reason = Texts.trim(reason, 1000);
        if (reason.length() < 10) throw new BusinessException("Vui lòng mô tả lý do đổi/trả (tối thiểu 10 ký tự).");
        o.setReturnStatus(ReturnStatus.REQUESTED);
        o.setReturnReason(reason);
        addHistory(o, o.getStatus(), "Khách yêu cầu đổi/trả: " + reason, user);
        notifications.notifyStaff("Đơn " + o.getCode() + " có yêu cầu đổi/trả", "/staff/orders/" + o.getId());
    }

    public void handleReturn(Long orderId, User staff, boolean approve, boolean restock, String note) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
        if (o.getReturnStatus() != ReturnStatus.REQUESTED) throw new BusinessException("Không có yêu cầu đổi/trả cần xử lý.");
        if (!staff.hasPermission(StaffPermission.REFUND)) throw new BusinessException("Bạn chưa được cấp quyền xử lý đổi trả & hoàn tiền.");
        note = Texts.trim(note, 500);
        if (approve) {
            if (restock) {
                stockService.restore(o);
            } else {
                // Hàng trả lại không đạt chất lượng: ghi nhận nhập về rồi chuyển sang kho hủy để truy vết
                for (OrderItem it : o.getItems()) {
                    for (OrderItemBatch a : it.getAllocations()) {
                        StockAdjustment adj = new StockAdjustment();
                        adj.setBatch(a.getBatch());
                        adj.setQuantity(0);
                        adj.setType("RETURN_SCRAP");
                        adj.setApprovedBy(staff);
                        adj.setApprovedAt(LocalDateTime.now());
                        adj.setReason("Hàng trả lại đơn " + o.getCode() + " - chuyển kho hủy " + a.getQuantity() + " " + it.getProduct().getUnit());
                        adj.setUser(staff);
                        adjustmentRepo.save(adj);
                    }
                }
            }
            User c = o.getUser();
            c.setPoints(Math.max(0, c.getPoints() - o.getPointsEarnedValue() + o.getPointsUsedValue()));
            o.setStatus(OrderStatus.RETURNED);
            o.setReturnStatus(ReturnStatus.APPROVED);
            if (o.getPaymentStatus() == PaymentStatus.PAID) requestRefund(o, o.getTotal());
            addHistory(o, OrderStatus.RETURNED, "Chấp nhận đổi/trả" + (restock ? " (nhập lại kho)" : " (không nhập lại kho)")
                    + (note.isEmpty() ? "" : ": " + note), staff);
            notifications.notify(c, "Yêu cầu đổi/trả đơn " + o.getCode() + " đã được chấp nhận. Nhà thuốc sẽ hoàn tiền cho bạn.",
                    "/account/orders/" + o.getCode());
        } else {
            if (note.isEmpty()) throw new BusinessException("Vui lòng nhập lý do từ chối.");
            o.setReturnStatus(ReturnStatus.REJECTED);
            addHistory(o, o.getStatus(), "Từ chối đổi/trả: " + note, staff);
            notifications.notify(o.getUser(), "Yêu cầu đổi/trả đơn " + o.getCode() + " bị từ chối: " + note, "/account/orders/" + o.getCode());
        }
        notifications.log(staff, "order.return", o.getCode() + ": " + (approve ? "approve" : "reject"));
    }

    /* ============================ Đơn thuốc ============================ */

    /** Khách gửi đơn thuốc mà chưa chọn sản phẩm - dược sĩ đọc đơn và lên đơn hàng. */
    public Prescription submitStandalonePrescription(User user, MultipartFile file, String note) {
        Prescription rx = new Prescription();
        rx.setUser(user);
        rx.setStandalone(true);
        rx.setImage(files.store(FileStorageService.Kind.PRESCRIPTIONS, file));
        rx.setCustomerNote(Texts.emptyToNull(Texts.trim(note, 500)));
        prescriptionRepo.save(rx);
        notifications.notifyStaff("Khách " + user.getFullName() + " gửi đơn thuốc nhờ lên đơn", "/staff/prescriptions/" + rx.getId());
        notifications.log(user, "rx.submit", "Đơn thuốc #" + rx.getId());
        return rx;
    }

    @Getter
    @Setter
    public static class RxApproval {
        private String patientName;
        private String doctorName;
        private String clinic;
        private LocalDate rxDate;
        private String pharmacistNote;
        /** Các mục kiểm tra đơn thuốc dược sĩ đã đánh dấu (xem RX_CHECKS). */
        private List<String> checks = new ArrayList<>();
        /** orderItemId -> số lượng sau điều chỉnh (chỉ được giảm, trừ khi thay thuốc). */
        private Map<Long, Integer> qty = new HashMap<>();
        /** orderItemId -> id sản phẩm thay thế (cùng hoạt chất). */
        private Map<Long, Long> sub = new HashMap<>();
        /** Lên đơn cho đơn thuốc gửi riêng: danh sách sản phẩm + số lượng (đơn vị gốc). */
        private List<Long> productIds = new ArrayList<>();
        private List<Integer> quantities = new ArrayList<>();
    }

    private Prescription pendingRx(Long rxId) {
        Prescription rx = prescriptionRepo.findById(rxId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn thuốc."));
        boolean pendingOrder = rx.getOrder() == null ? rx.isStandalone() : rx.getOrder().getStatus() == OrderStatus.PENDING_RX;
        if (rx.getStatus() != ApprovalStatus.PENDING || !pendingOrder) throw new BusinessException("Đơn thuốc này đã được xử lý.");
        return rx;
    }

    public static final Map<String, String> RX_CHECKS = new LinkedHashMap<>();

    static {
        RX_CHECKS.put("valid", "Đơn thuốc hợp lệ (đủ thông tin bệnh nhân, cơ sở khám bệnh)");
        RX_CHECKS.put("date", "Còn hiệu lực (trong thời hạn kể từ ngày kê)");
        RX_CHECKS.put("sign", "Có chữ ký bác sĩ và dấu cơ sở khám bệnh");
        RX_CHECKS.put("match", "Thuốc và liều dùng khớp với đơn");
    }

    void recordRx(Prescription rx, User pharmacist, RxApproval form) {
        if (!pharmacist.hasPermission(StaffPermission.RX_REVIEW)) throw new BusinessException("Bạn chưa được cấp quyền duyệt đơn thuốc.", 403);
        if (Texts.isBlank(form.getPatientName()) || Texts.isBlank(form.getDoctorName())) {
            throw new BusinessException("Vui lòng ghi nhận tên bệnh nhân và bác sĩ kê đơn (sổ bán thuốc kê đơn).");
        }
        if (form.getChecks() == null || !form.getChecks().containsAll(RX_CHECKS.keySet())) {
            throw new BusinessException("Vui lòng kiểm tra và đánh dấu đủ các mục: hợp lệ, hiệu lực, chữ ký, khớp thuốc/liều.");
        }
        if (form.getRxDate() == null) throw new BusinessException("Vui lòng nhập ngày kê đơn.");
        long validDays = settings.getLong("rx_valid_days");
        if (form.getRxDate().isAfter(LocalDate.now())) throw new BusinessException("Ngày kê đơn không hợp lệ.");
        if (form.getRxDate().plusDays(validDays).isBefore(LocalDate.now())) {
            throw new BusinessException("Đơn thuốc đã hết hiệu lực (quá " + validDays + " ngày kể từ ngày kê) - hãy từ chối đơn.");
        }
        rx.setChecklist(String.join(",", form.getChecks()));
        rx.setStatus(ApprovalStatus.APPROVED);
        rx.setPharmacist(pharmacist);
        rx.setPatientName(Texts.trim(form.getPatientName(), 100));
        rx.setDoctorName(Texts.trim(form.getDoctorName(), 100));
        rx.setClinic(Texts.emptyToNull(Texts.trim(form.getClinic(), 200)));
        rx.setRxDate(form.getRxDate());
        rx.setPharmacistNote(Texts.emptyToNull(Texts.trim(form.getPharmacistNote(), 1000)));
        rx.setReviewedAt(LocalDateTime.now());
    }

    /** Kiểm tra tồn kho khả dụng cho một sản phẩm khi dược sĩ thêm/thay thuốc (đơn hàng hiện tại chưa tính). */
    private void checkStock(Product p, long baseQty, long alreadyReservedHere) {
        stockService.fill(p);
        if (!p.getDrugType().isSellableOnline()) throw new BusinessException(p.getName() + " không được bán online.");
        if (baseQty > p.getAvailable() + alreadyReservedHere) {
            throw new BusinessException("\"" + p.getName() + "\" chỉ còn " + (p.getAvailable() + alreadyReservedHere) + " " + p.getUnit() + ".");
        }
    }

    public Order approvePrescription(Long rxId, User pharmacist, RxApproval form) {
        Prescription rx = pendingRx(rxId);
        if (rx.getOrder() == null) return createQuote(rx, pharmacist, form);
        Order o = rx.getOrder();
        List<String> changes = new ArrayList<>();
        boolean substituted = false;
        int remaining = 0;
        for (OrderItem it : new ArrayList<>(o.getItems())) {
            Integer q = form.getQty().get(it.getId());
            int newQty = q == null ? it.getQuantity() : q;
            Long subId = form.getSub().get(it.getId());
            if (subId != null && subId > 0 && !subId.equals(it.getProduct().getId())) {
                // Thay bằng thuốc cùng hoạt chất (đơn vị gốc của thuốc mới)
                Product np = productRepo.findById(subId).orElseThrow(() -> new BusinessException("Thuốc thay thế không tồn tại."));
                if (!catalogService.isSubstitutable(it.getProduct(), np)) {
                    throw new BusinessException("Chỉ được thay bằng thuốc cùng hoạt chất và cùng hàm lượng"
                            + (it.getProduct().getStrength() != null ? " (" + it.getProduct().getStrength() + ")" : "")
                            + ", hoặc thuốc được cấu hình tương đương.");
                }
                if (newQty <= 0) throw new BusinessException("Nhập số lượng cho thuốc thay thế \"" + np.getName() + "\".");
                checkStock(np, newQty, 0);
                changes.add("thay " + it.getProductName() + " bằng " + np.getName() + " x" + newQty + " " + np.getUnit());
                it.setProduct(np);
                it.setProductName(np.getName());
                it.setDrugType(np.getDrugType());
                it.setUnit(np.getUnit());
                it.setUnitFactor(1);
                it.setPrice(np.getPrice());
                it.setQuantity(newQty);
                substituted = true;
            } else {
                if (newQty < 0 || newQty > it.getQuantity()) {
                    throw new BusinessException("Số lượng \"" + it.getProductName() + "\" chỉ được điều chỉnh giảm (0 - " + it.getQuantity() + ").");
                }
                if (newQty != it.getQuantity()) {
                    changes.add(it.getProductName() + ": " + it.getQuantity() + " → " + newQty);
                    if (newQty == 0) o.getItems().remove(it);
                    else it.setQuantity(newQty);
                }
            }
            remaining += newQty;
        }
        if (remaining == 0) throw new BusinessException("Đơn hàng phải còn ít nhất 1 sản phẩm. Nếu không bán được, hãy từ chối đơn thuốc.");
        if (!changes.isEmpty()) recalc(o);
        recordRx(rx, pharmacist, form);

        // Có thay thuốc / điều chỉnh -> khách phải xác nhận lại; không thay đổi -> chờ nhà thuốc xác nhận
        OrderStatus next = substituted || !changes.isEmpty() ? OrderStatus.AWAITING_CUSTOMER : OrderStatus.PENDING;
        o.setStatus(next);
        o.setHandledBy(pharmacist);
        String note = "Dược sĩ đã duyệt đơn thuốc" + (changes.isEmpty() ? "" : " (điều chỉnh: " + String.join("; ", changes) + ")");
        addHistory(o, next, note, pharmacist);
        String msg = next == OrderStatus.AWAITING_CUSTOMER
                ? "Dược sĩ đã duyệt đơn thuốc của đơn " + o.getCode() + " và điều chỉnh sản phẩm. Vui lòng xem lại và xác nhận đơn hàng."
                : "Đơn thuốc của đơn " + o.getCode() + " đã được dược sĩ duyệt."
                + (o.getPaymentMethod().isPrepaid() ? " Vui lòng thanh toán để nhà thuốc xử lý đơn." : "");
        notifications.notify(o.getUser(), msg, "/account/orders/" + o.getCode());
        notifications.log(pharmacist, "rx.approve", "Đơn thuốc #" + rx.getId() + " - " + o.getCode() + (changes.isEmpty() ? "" : " - " + String.join("; ", changes)));
        return o;
    }

    /** Dược sĩ lên đơn hàng từ đơn thuốc khách gửi riêng; khách xác nhận địa chỉ và thanh toán sau. */
    private Order createQuote(Prescription rx, User pharmacist, RxApproval form) {
        User customer = rx.getUser();
        Order o = new Order();
        o.setCode(newCode());
        o.setUser(customer);
        List<Address> addresses = addressRepo.findByUserOrderByDefaultAddressDescIdAsc(customer);
        if (addresses.isEmpty()) {
            o.setRecipient(customer.getFullName());
            o.setPhone(customer.getPhone() == null ? "" : customer.getPhone());
            o.setShippingMethod(ShippingMethod.PICKUP);
        } else {
            Address a = addresses.get(0);
            o.setRecipient(a.getRecipient());
            o.setPhone(a.getPhone());
            o.setAddress(a.getAddressLine());
            o.setProvince(ShippingZone.detect(a.getAddressLine()));
            o.setShippingMethod(ShippingMethod.DELIVERY);
        }
        o.setPaymentMethod(PaymentMethod.COD);
        o.setNeedsPrescription(true);
        o.setStatus(OrderStatus.AWAITING_CUSTOMER);
        o.setHandledBy(pharmacist);

        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (int i = 0; i < form.getProductIds().size(); i++) {
            Long pid = form.getProductIds().get(i);
            Integer q = i < form.getQuantities().size() ? form.getQuantities().get(i) : null;
            if (pid == null || q == null || q <= 0) continue;
            wanted.merge(pid, q, Integer::sum);
        }
        if (wanted.isEmpty()) throw new BusinessException("Vui lòng chọn ít nhất 1 sản phẩm và số lượng để lên đơn.");
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product p = productRepo.findById(e.getKey()).orElseThrow(() -> new BusinessException("Sản phẩm không tồn tại."));
            checkStock(p, e.getValue(), 0);
            o.getItems().add(newItem(o, p, p.getUnitOptions().get(0), e.getValue()));
            lines.add(p.getName() + " x" + e.getValue());
        }
        recalc(o);
        addHistory(o, OrderStatus.AWAITING_CUSTOMER, "Dược sĩ lên đơn từ đơn thuốc: " + String.join("; ", lines), pharmacist);
        orderRepo.save(o);
        recordRx(rx, pharmacist, form);
        rx.setOrder(o);
        o.getPrescriptions().add(rx);
        notifications.notify(customer, "Dược sĩ đã lên đơn " + o.getCode() + " từ đơn thuốc bạn gửi. Vui lòng xem báo giá và xác nhận.",
                "/account/orders/" + o.getCode());
        notifications.log(pharmacist, "rx.quote", "Đơn thuốc #" + rx.getId() + " → " + o.getCode());
        return o;
    }

    public Prescription rejectPrescription(Long rxId, User pharmacist, String reason) {
        Prescription rx = pendingRx(rxId);
        if (!pharmacist.hasPermission(StaffPermission.RX_REVIEW)) throw new BusinessException("Bạn chưa được cấp quyền duyệt đơn thuốc.", 403);
        reason = Texts.trim(reason, 500);
        if (reason.length() < 5) throw new BusinessException("Vui lòng nhập lý do từ chối.");
        rx.setStatus(ApprovalStatus.REJECTED);
        rx.setPharmacist(pharmacist);
        rx.setRejectReason(reason);
        rx.setReviewedAt(LocalDateTime.now());
        Order o = rx.getOrder();
        if (o != null) {
            o.setStatus(OrderStatus.RX_REJECTED);
            addHistory(o, OrderStatus.RX_REJECTED, reason, pharmacist);
            notifications.notify(o.getUser(), "Đơn thuốc của đơn " + o.getCode() + " bị từ chối: " + reason + ". Bạn có thể tải lại đơn thuốc.",
                    "/account/orders/" + o.getCode());
        } else {
            notifications.notify(rx.getUser(), "Đơn thuốc bạn gửi bị từ chối: " + reason + ". Bạn có thể gửi lại ảnh rõ hơn.",
                    "/account/prescriptions");
        }
        notifications.log(pharmacist, "rx.reject", "Đơn thuốc #" + rx.getId() + (o != null ? " - " + o.getCode() : "") + ": " + reason);
        return rx;
    }

    /* ============================ Tiện ích ============================ */

    /** Tính lại tiền sau khi điều chỉnh; giảm giá và điểm chỉ áp trên phần không phải thuốc kê đơn. */
    public void recalc(Order o) {
        long subtotal = 0;
        long discountable = 0;
        for (OrderItem it : o.getItems()) {
            subtotal += it.getLineTotal();
            if (it.getDrugType() != DrugType.ETC) discountable += it.getLineTotal();
        }
        long promo = Math.min(o.getPromoDiscountValue(), discountable);
        if (o.getPromoDiscount() != null) o.setPromoDiscount(promo);
        long discount = Math.min(o.getDiscount(), discountable - promo);
        long pointsDiscount = o.getPointsDiscountValue();
        long pointValue = Math.max(1, settings.getLong("point_value"));
        if (pointsDiscount > discountable - promo - discount) {
            // Trả lại phần điểm không dùng được nữa
            long allowed = Math.max(0, discountable - promo - discount) / pointValue;
            int refund = (int) (o.getPointsUsedValue() - allowed);
            if (refund > 0) o.getUser().setPoints(o.getUser().getPoints() + refund);
            o.setPointsUsed((int) allowed);
            pointsDiscount = allowed * pointValue;
            o.setPointsDiscount(pointsDiscount);
        }
        long ship = shippingFee(o, subtotal - promo - discount - pointsDiscount);
        o.setSubtotal(subtotal);
        o.setDiscount(discount);
        o.setShippingFee(ship);
        o.setTotal(subtotal - promo - discount - pointsDiscount + ship);
    }

    /** Phí ship theo khu vực giao hàng của đơn (nếu có cấu hình), không thì theo biểu phí chung. */
    private long shippingFee(Order o, long amount) {
        return settings.shippingFee(o.getShippingMethod(), amount, o.getProvince());
    }

    /** Sinh mã đơn hàng không trùng. */
    public String newCode() {
        String code;
        do {
            code = Texts.code("DH");
        } while (orderRepo.existsByCode(code));
        return code;
    }

    private void releaseVoucher(Order o) {
        if (o.getVoucherCode() == null) return;
        voucherRepo.findByCodeIgnoreCase(o.getVoucherCode()).ifPresent(v -> v.setUsedCount(Math.max(0, v.getUsedCount() - 1)));
    }

    public void addHistory(Order o, OrderStatus status, String note, User user) {
        OrderHistory h = new OrderHistory();
        h.setOrder(o);
        h.setStatus(status);
        h.setNote(note);
        h.setUser(user);
        o.getHistory().add(h);
    }

    /** Thêm lại các sản phẩm của đơn cũ vào giỏ (mua lại nhanh). Trả về danh sách sản phẩm không thêm được. */
    public List<String> reorder(Order o, Cart cart) {
        List<String> skipped = new ArrayList<>();
        for (OrderItem it : o.getItems()) {
            Product p = productRepo.findById(it.getProduct().getId()).orElse(null);
            UnitOption unit = p == null ? null : Optional.ofNullable(p.findUnitByFactor(it.getFactor())).orElse(p.getUnitOptions().get(0));
            if (p == null || cartService.checkAdd(cart, p, unit, it.getQuantity()) != null) {
                skipped.add(it.getProductName());
                continue;
            }
            cart.add(p.getId(), unit.id(), it.getQuantity());
        }
        return skipped;
    }
}
