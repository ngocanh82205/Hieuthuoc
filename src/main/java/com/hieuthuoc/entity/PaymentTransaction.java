package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Giao dịch thanh toán / hoàn tiền của đơn (cổng PayOS, VNPay, chuyển khoản, COD, tiền mặt). */
@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction extends Timestamped {
    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String REFUNDED = "REFUNDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false, length = 20)
    private String gateway;

    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    /** PAYMENT hoặc REFUND. */
    @Column(nullable = false, length = 10)
    private String type = "PAYMENT";

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "response_code", length = 20)
    private String responseCode;

    @Column(length = 300)
    private String message;

    /** Dữ liệu thô từ cổng thanh toán: chuỗi JSON hợp lệ hoặc null (cột có ràng buộc json_valid). */
    @Column(columnDefinition = "longtext")
    private String payload;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User creator;

    public long amountValue() {
        return amount == null ? 0 : amount.longValue();
    }

    public String gatewayLabel() {
        PaymentMethod m = PaymentMethod.tryFrom(gateway);
        return m != null ? m.getShortLabel() : gateway;
    }

    public String statusLabel() {
        return switch (status == null ? "" : status) {
            case SUCCESS -> "REFUND".equals(type) ? "Đã hoàn" : "Thành công";
            case FAILED -> "Thất bại";
            case REFUNDED -> "Đã hoàn tiền";
            default -> "Đang chờ";
        };
    }

    public String statusColor() {
        return switch (status == null ? "" : status) {
            case SUCCESS -> "success";
            case FAILED -> "danger";
            case REFUNDED -> "secondary";
            default -> "warning";
        };
    }
}
