package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String recipient;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 300)
    private String address;

    @Column(length = 60)
    private String province;

    @Column(name = "ghn_district_id")
    private Integer ghnDistrictId;

    @Column(name = "ghn_ward_code", length = 20)
    private String ghnWardCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "shipping_method", nullable = false, length = 20)
    private ShippingMethod shippingMethod = ShippingMethod.DELIVERY;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(nullable = false)
    private long subtotal;

    @Column(nullable = false)
    private long discount;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(nullable = false)
    private long total;

    /** Giá vốn chốt lúc hoàn thành đơn (lô xuất bị xóa khi nhận trả hàng nên không tính lại được về sau). */
    @Column(name = "cost_amount", nullable = false)
    private long costAmount;

    @Column(name = "voucher_code", length = 30)
    private String voucherCode;

    @Column(name = "points_used", nullable = false)
    private int pointsUsed;

    @Column(name = "points_discount", nullable = false)
    private long pointsDiscount;

    @Column(name = "points_earned", nullable = false)
    private int pointsEarned;

    @Column(name = "promo_discount", nullable = false)
    private long promoDiscount;

    @Column(name = "promo_note", length = 500)
    private String promoNote;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "needs_prescription", nullable = false)
    private boolean needsPrescription;

    @Column(length = 500)
    private String note;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "return_status", length = 20)
    private ReturnStatus returnStatus;

    @Column(name = "return_reason", length = 1000)
    private String returnReason;

    /** Kênh bán: ONLINE hoặc POS (bán tại quầy). */
    @Column(nullable = false, length = 10)
    private String channel = "ONLINE";

    /** Nhân viên xử lý đơn. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handler;

    @Column(name = "handled_by", insertable = false, updatable = false)
    private Long handledBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Ngày nhận hàng trả (báo cáo ghi giảm doanh thu vào ngày này). */
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    /** Giá vốn hoàn lại khi hàng trả được nhập lại kho. */
    @Column(name = "return_cost", nullable = false)
    private long returnCost;

    @Column(length = 50)
    private String carrier;

    @Column(name = "tracking_code", length = 60)
    private String trackingCode;

    /** Trạng thái vận đơn bên GHN (ready_to_pick, delivering, delivered, cancel...). */
    @Column(name = "shipping_status", length = 40)
    private String shippingStatus;

    @Column(name = "refund_amount")
    private Long refundAmount;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by")
    private User refundedBy;

    @Column(name = "refund_note", length = 300)
    private String refundNote;

    @Column(name = "vat_company", length = 200)
    private String vatCompany;

    @Column(name = "vat_tax_code", length = 20)
    private String vatTaxCode;

    @Column(name = "vat_address", length = 300)
    private String vatAddress;

    @Column(name = "vat_email", length = 150)
    private String vatEmail;

    @Column(name = "einvoice_no", length = 50)
    private String einvoiceNo;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order")
    @OrderBy("id ASC")
    private List<OrderHistory> history = new ArrayList<>();

    @OneToMany(mappedBy = "order")
    @OrderBy("id DESC")
    private List<Prescription> prescriptions = new ArrayList<>();

    @OneToMany(mappedBy = "order")
    @OrderBy("id DESC")
    private List<PaymentTransaction> paymentTransactions = new ArrayList<>();

    @OneToMany(mappedBy = "order")
    @OrderBy("id DESC")
    private List<BillCancelRequest> cancelRequests = new ArrayList<>();

    public boolean isPos() {
        return "POS".equals(channel);
    }

    public boolean isVatRequested() {
        return vatTaxCode != null && !vatTaxCode.isEmpty();
    }

    public int itemCount() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    /** Đã giao thành công -> khách được đánh giá. */
    public boolean isCompleted() {
        return status == OrderStatus.COMPLETED;
    }

    public Prescription latestPrescription() {
        return prescriptions.isEmpty() ? null : prescriptions.get(0);
    }

    /** Hạn cuối lập phiếu hủy hóa đơn bán tại quầy (null = không giới hạn). */
    public LocalDateTime posCancelDeadline(int days) {
        if (days <= 0) return null;
        LocalDateTime base = completedAt != null ? completedAt : getCreatedAt();
        return base.toLocalDate().plusDays(days).atTime(23, 59, 59);
    }

    public boolean isPosCancelExpired(int days) {
        LocalDateTime d = posCancelDeadline(days);
        return d != null && d.isBefore(LocalDateTime.now());
    }

    /** Nhân viên được ghi nhận đã thu tiền: chưa thu, giá đã chốt (sau bước duyệt / khách xác nhận) và đơn còn hiệu lực. */
    public boolean canMarkPaid() {
        return paymentStatus == PaymentStatus.UNPAID && EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
                OrderStatus.PREPARING, OrderStatus.PACKED, OrderStatus.SHIPPING).contains(status);
    }

    public boolean canPayOnline() {
        return paymentStatus == PaymentStatus.UNPAID && paymentMethod != null && paymentMethod.isPrepaid()
                && (status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED);
    }
}
