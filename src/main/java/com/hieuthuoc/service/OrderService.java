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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    }

    /** Lỗi kiểm tra form thanh toán (nhiều lỗi cùng lúc). */
    @Getter
    public static class CheckoutException extends RuntimeException {
        private final List<String> errors;

        public CheckoutException(List<String> errors) {
            super(String.join(" ", errors));
            this.errors = errors;
        }
    }

    public Order placeOrder(User user, Cart cart, CheckoutForm f, MultipartFile rxFile) {
        if (f.getShippingMethod() == null) f.setShippingMethod(ShippingMethod.DELIVERY);
        if (f.getPaymentMethod() == null) f.setPaymentMethod(PaymentMethod.COD);
        CartService.View cv = cartService.build(cart, f.getShippingMethod());

        List<String> errors = new ArrayList<>(cv.getErrors());
        if (cv.isEmpty()) errors.add("Giỏ hàng đang trống.");
        if (cv.getVoucherError() != null) errors.add(cv.getVoucherError());
        if (Texts.trim(f.getRecipient()).length() < 2) errors.add("Vui lòng nhập tên người nhận.");
        if (!Texts.isPhone(Texts.trim(f.getPhone()))) errors.add("Số điện thoại người nhận không hợp lệ (10-11 số, bắt đầu bằng 0).");
        if (f.getShippingMethod() == ShippingMethod.DELIVERY && Texts.trim(f.getAddress()).length() < 10) {
            errors.add("Vui lòng nhập địa chỉ giao hàng đầy đủ.");
        }
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
        o.setCode(Texts.code("DH"));
        o.setUser(user);
        o.setRecipient(Texts.trim(f.getRecipient(), 100));
        o.setPhone(Texts.trim(f.getPhone()));
        o.setAddress(f.getShippingMethod() == ShippingMethod.PICKUP ? null : Texts.trim(f.getAddress(), 300));
        o.setShippingMethod(f.getShippingMethod());
        o.setPaymentMethod(f.getPaymentMethod());
        o.setSubtotal(cv.getSubtotal());
        o.setDiscount(cv.getDiscount());
        o.setShippingFee(cv.getShippingFee());
        o.setTotal(cv.getTotal());
        o.setVoucherCode(cv.getVoucher() == null ? null : cv.getVoucher().getCode());
        o.setNeedsPrescription(cv.isRxRequired());
        o.setStatus(cv.isRxRequired() ? OrderStatus.PENDING_RX : OrderStatus.PENDING);
        o.setNote(Texts.emptyToNull(Texts.trim(f.getNote(), 500)));

        for (CartService.Line line : cv.getLines()) {
            Product p = line.getProduct();
            OrderItem it = new OrderItem();
            it.setOrder(o);
            it.setProduct(p);
            it.setProductName(p.getName());
            it.setUnit(p.getUnit());
            it.setDrugType(p.getDrugType());
            it.setPrice(p.getPrice());
            it.setQuantity(line.getQuantity());
            o.getItems().add(it);
        }
        if (rxImage != null) {
            Prescription rx = new Prescription();
            rx.setOrder(o);
            rx.setUser(user);
            rx.setImage(rxImage);
            rx.setCustomerNote(Texts.emptyToNull(Texts.trim(f.getRxNote(), 500)));
            o.getPrescriptions().add(rx);
        }
        if (cv.getVoucher() != null) cv.getVoucher().setUsedCount(cv.getVoucher().getUsedCount() + 1);

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

        addHistory(o, o.getStatus(), cv.isRxRequired() ? "Khách đặt hàng kèm đơn thuốc" : "Khách đặt hàng", user);
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
                if (o.getPaymentMethod() == PaymentMethod.ONLINE && o.getPaymentStatus() != PaymentStatus.PAID) {
                    throw new BusinessException("Đơn thanh toán online chưa được khách thanh toán.");
                }
            }
            case PREPARING -> stockService.allocateFefo(o);
            case CANCELLED -> {
                if (note == null) throw new BusinessException("Vui lòng nhập lý do hủy đơn.");
                if (from == OrderStatus.PREPARING || from == OrderStatus.SHIPPING) stockService.restore(o);
                if (o.getPaymentStatus() == PaymentStatus.PAID) o.setPaymentStatus(PaymentStatus.REFUNDED);
                releaseVoucher(o);
                o.setCancelReason(note);
            }
            case COMPLETED -> {
                o.setPaymentStatus(PaymentStatus.PAID);
                o.setCompletedAt(LocalDateTime.now());
                User c = o.getUser();
                c.setPoints(c.getPoints() + (int) (o.getTotal() / settings.getLong("points_per_amount")));
            }
            default -> {
            }
        }
        if (o.getHandledBy() == null) o.setHandledBy(staff);
        o.setStatus(to);
        addHistory(o, to, note, staff);
        notifications.notify(o.getUser(), "Đơn hàng " + o.getCode() + ": " + to.getLabel() + (note != null ? " - " + note : ""),
                "/account/orders/" + o.getCode());
        notifications.log(staff, "order.status", o.getCode() + ": " + from + " → " + to);
        return o;
    }

    public void markPaid(Long orderId, User staff) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
        if (o.getPaymentStatus() != PaymentStatus.UNPAID) throw new BusinessException("Đơn hàng không ở trạng thái chưa thanh toán.");
        o.setPaymentStatus(PaymentStatus.PAID);
        addHistory(o, o.getStatus(), "Xác nhận đã nhận thanh toán", staff);
        notifications.log(staff, "order.paid", o.getCode());
    }

    /* ============================ Khách hàng ============================ */

    public void cancelByCustomer(Order o, User user, String reason) {
        if (!o.getStatus().isCustomerCancellable()) {
            throw new BusinessException("Đơn hàng đã được xử lý, không thể hủy. Vui lòng liên hệ nhà thuốc.");
        }
        reason = Texts.isBlank(reason) ? "Khách hàng hủy" : Texts.trim(reason, 500);
        if (o.getPaymentStatus() == PaymentStatus.PAID) o.setPaymentStatus(PaymentStatus.REFUNDED);
        for (Prescription rx : o.getPrescriptions()) {
            if (rx.getStatus() == ApprovalStatus.PENDING) {
                rx.setStatus(ApprovalStatus.REJECTED);
                rx.setRejectReason("Khách hủy đơn");
            }
        }
        releaseVoucher(o);
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelReason(reason);
        addHistory(o, OrderStatus.CANCELLED, reason, user);
        notifications.notifyStaff("Khách đã hủy đơn " + o.getCode(), "/staff/orders/" + o.getId());
    }

    public boolean canPay(Order o) {
        return o.getPaymentMethod() == PaymentMethod.ONLINE && o.getPaymentStatus() == PaymentStatus.UNPAID
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

    public boolean canRequestReturn(Order o) {
        return o.getStatus() == OrderStatus.COMPLETED && o.getReturnStatus() == null && o.getCompletedAt() != null
                && ChronoUnit.DAYS.between(o.getCompletedAt().toLocalDate(), LocalDate.now()) <= settings.getLong("return_days");
    }

    public void requestReturn(Order o, User user, String reason) {
        if (!canRequestReturn(o)) {
            throw new BusinessException("Đơn hàng không thể yêu cầu đổi/trả (chỉ áp dụng trong " + settings.get("return_days") + " ngày sau khi nhận hàng).");
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
        note = Texts.trim(note, 500);
        if (approve) {
            if (restock) stockService.restore(o);
            User c = o.getUser();
            c.setPoints(Math.max(0, c.getPoints() - (int) (o.getTotal() / settings.getLong("points_per_amount"))));
            o.setStatus(OrderStatus.RETURNED);
            o.setReturnStatus(ReturnStatus.APPROVED);
            o.setPaymentStatus(PaymentStatus.REFUNDED);
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

    /* ============================ Duyệt đơn thuốc (dược sĩ) ============================ */

    @Getter
    @Setter
    public static class RxApproval {
        private String patientName;
        private String doctorName;
        private String clinic;
        private LocalDate rxDate;
        private String pharmacistNote;
        /** orderItemId -> số lượng sau điều chỉnh (chỉ được giảm). */
        private Map<Long, Integer> qty = new java.util.HashMap<>();
    }

    private Prescription pendingRx(Long rxId) {
        Prescription rx = prescriptionRepo.findById(rxId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn thuốc."));
        if (rx.getStatus() != ApprovalStatus.PENDING || rx.getOrder().getStatus() != OrderStatus.PENDING_RX) {
            throw new BusinessException("Đơn thuốc này đã được xử lý.");
        }
        return rx;
    }

    public Order approvePrescription(Long rxId, User pharmacist, RxApproval form) {
        Prescription rx = pendingRx(rxId);
        Order o = rx.getOrder();
        if (Texts.isBlank(form.getPatientName()) || Texts.isBlank(form.getDoctorName())) {
            throw new BusinessException("Vui lòng ghi nhận tên bệnh nhân và bác sĩ kê đơn (sổ bán thuốc kê đơn).");
        }
        List<String> changes = new ArrayList<>();
        int remaining = 0;
        for (OrderItem it : new ArrayList<>(o.getItems())) {
            Integer q = form.getQty().get(it.getId());
            int newQty = q == null ? it.getQuantity() : q;
            if (newQty < 0 || newQty > it.getQuantity()) {
                throw new BusinessException("Số lượng \"" + it.getProductName() + "\" chỉ được điều chỉnh giảm (0 - " + it.getQuantity() + ").");
            }
            if (newQty != it.getQuantity()) {
                changes.add(it.getProductName() + ": " + it.getQuantity() + " → " + newQty);
                if (newQty == 0) o.getItems().remove(it);
                else it.setQuantity(newQty);
            }
            remaining += newQty;
        }
        if (remaining == 0) throw new BusinessException("Đơn hàng phải còn ít nhất 1 sản phẩm. Nếu không bán được, hãy từ chối đơn thuốc.");
        if (!changes.isEmpty()) recalc(o);

        rx.setStatus(ApprovalStatus.APPROVED);
        rx.setPharmacist(pharmacist);
        rx.setPatientName(Texts.trim(form.getPatientName(), 100));
        rx.setDoctorName(Texts.trim(form.getDoctorName(), 100));
        rx.setClinic(Texts.emptyToNull(Texts.trim(form.getClinic(), 200)));
        rx.setRxDate(form.getRxDate());
        rx.setPharmacistNote(Texts.emptyToNull(Texts.trim(form.getPharmacistNote(), 1000)));
        rx.setReviewedAt(LocalDateTime.now());

        o.setStatus(OrderStatus.PENDING);
        o.setHandledBy(pharmacist);
        String note = "Dược sĩ đã duyệt đơn thuốc" + (changes.isEmpty() ? "" : " (điều chỉnh: " + String.join("; ", changes) + ")");
        addHistory(o, OrderStatus.PENDING, note, pharmacist);
        String payNote = o.getPaymentMethod() == PaymentMethod.ONLINE ? " Vui lòng thanh toán để nhà thuốc xử lý đơn." : "";
        notifications.notify(o.getUser(), "Đơn thuốc của đơn " + o.getCode() + " đã được dược sĩ duyệt." + payNote, "/account/orders/" + o.getCode());
        notifications.log(pharmacist, "rx.approve", "Đơn thuốc #" + rx.getId() + " - " + o.getCode() + (changes.isEmpty() ? "" : " - " + String.join("; ", changes)));
        return o;
    }

    public Order rejectPrescription(Long rxId, User pharmacist, String reason) {
        Prescription rx = pendingRx(rxId);
        reason = Texts.trim(reason, 500);
        if (reason.length() < 5) throw new BusinessException("Vui lòng nhập lý do từ chối.");
        Order o = rx.getOrder();
        rx.setStatus(ApprovalStatus.REJECTED);
        rx.setPharmacist(pharmacist);
        rx.setRejectReason(reason);
        rx.setReviewedAt(LocalDateTime.now());
        o.setStatus(OrderStatus.RX_REJECTED);
        addHistory(o, OrderStatus.RX_REJECTED, reason, pharmacist);
        notifications.notify(o.getUser(), "Đơn thuốc của đơn " + o.getCode() + " bị từ chối: " + reason + ". Bạn có thể tải lại đơn thuốc.",
                "/account/orders/" + o.getCode());
        notifications.log(pharmacist, "rx.reject", "Đơn thuốc #" + rx.getId() + " - " + o.getCode() + ": " + reason);
        return o;
    }

    /* ============================ Tiện ích ============================ */

    /** Tính lại tiền sau khi điều chỉnh số lượng; giảm giá chỉ áp trên phần không phải thuốc kê đơn. */
    public void recalc(Order o) {
        long subtotal = 0;
        long discountable = 0;
        for (OrderItem it : o.getItems()) {
            subtotal += it.getLineTotal();
            if (it.getDrugType() != DrugType.ETC) discountable += it.getLineTotal();
        }
        long discount = Math.min(o.getDiscount(), discountable);
        long ship = settings.shippingFee(o.getShippingMethod(), subtotal - discount);
        o.setSubtotal(subtotal);
        o.setDiscount(discount);
        o.setShippingFee(ship);
        o.setTotal(subtotal - discount + ship);
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
            if (p == null || !p.isActive() || !p.getDrugType().isSellableOnline()) {
                skipped.add(it.getProductName());
                continue;
            }
            stockService.fill(p);
            long qty = Math.min(it.getQuantity(), p.getAvailable());
            if (p.getMaxPerOrder() != null) qty = Math.min(qty, p.getMaxPerOrder());
            if (qty <= 0) {
                skipped.add(it.getProductName());
                continue;
            }
            cart.getItems().put(p.getId(), (int) qty);
        }
        return skipped;
    }
}
