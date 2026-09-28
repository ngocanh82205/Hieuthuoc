package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Phiếu hủy thuốc / điều chỉnh kiểm kê trên một lô. */
@Entity
@Table(name = "stock_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class StockAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Batch batch;

    private int quantity;

    @Column(length = 300)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    /** Loại phiếu: WRITE_OFF (hủy thuốc), STOCKTAKE (điều chỉnh kiểm kê), RETURN_SCRAP (hàng trả chuyển kho hủy), MANUAL. */
    @Column(length = 20)
    private String type = "MANUAL";

    /** Trạng thái duyệt; null = phiếu cũ (coi như đã duyệt). Phiếu chờ duyệt chưa làm thay đổi tồn kho. */
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(length = 20)
    private ApprovalStatus status = ApprovalStatus.APPROVED;

    @ManyToOne(fetch = FetchType.LAZY)
    private User approvedBy;

    private LocalDateTime approvedAt;

    @Column(length = 300)
    private String rejectReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ApprovalStatus getStatusValue() {
        return status == null ? ApprovalStatus.APPROVED : status;
    }

    public String getTypeLabel() {
        return switch (type == null ? "MANUAL" : type) {
            case "WRITE_OFF" -> "Phiếu hủy";
            case "STOCKTAKE" -> "Điều chỉnh kiểm kê";
            case "RETURN_SCRAP" -> "Hàng trả - kho hủy";
            default -> "Điều chỉnh";
        };
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
