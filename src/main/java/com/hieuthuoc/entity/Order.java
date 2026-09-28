package com.hieuthuoc.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {@Index(columnList = "status"), @Index(columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String recipient;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 300)
    private String address;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ShippingMethod shippingMethod = ShippingMethod.DELIVERY;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    private long subtotal;

    private long discount;

    private long shippingFee;

    private long total;

    @Column(length = 30)
    private String voucherCode;

    /** Điểm tích lũy khách dùng để trừ tiền. */
    private Integer pointsUsed;

    private Long pointsDiscount;

    /** Điểm khách được cộng khi đơn hoàn thành. */
    private Integer pointsEarned;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    private boolean needsPrescription;

    @Column(length = 500)
    private String note;

    @Column(length = 500)
    private String cancelReason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private ReturnStatus returnStatus;

    @Column(length = 1000)
    private String returnReason;

    /** Nhân viên phụ trách xử lý đơn. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User handledBy;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderHistory> history = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id DESC")
    private List<Prescription> prescriptions = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int getPointsUsedValue() {
        return pointsUsed == null ? 0 : pointsUsed;
    }

    public long getPointsDiscountValue() {
        return pointsDiscount == null ? 0 : pointsDiscount;
    }

    public int getPointsEarnedValue() {
        return pointsEarned == null ? 0 : pointsEarned;
    }

    public int getItemCount() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    public Prescription getLatestPrescription() {
        return prescriptions.isEmpty() ? null : prescriptions.get(0);
    }
}
