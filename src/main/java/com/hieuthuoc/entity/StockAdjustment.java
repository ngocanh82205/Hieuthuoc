package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Phiếu điều chỉnh / hủy hàng của một lô (quantity âm = giảm). */
@Entity
@Table(name = "stock_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class StockAdjustment extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 300)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** MANUAL, WRITE_OFF, STOCKTAKE, RETURN_SCRAP, RECALL. */
    @Column(nullable = false, length = 20)
    private String type = "MANUAL";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.APPROVED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approver;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "reject_reason", length = 300)
    private String rejectReason;

    public String typeLabel() {
        return switch (type == null ? "" : type) {
            case "WRITE_OFF" -> "Phiếu hủy";
            case "STOCKTAKE" -> "Điều chỉnh kiểm kê";
            case "RETURN_SCRAP" -> "Hàng trả - kho hủy";
            case "RECALL" -> "Thu hồi";
            default -> "Điều chỉnh";
        };
    }
}
