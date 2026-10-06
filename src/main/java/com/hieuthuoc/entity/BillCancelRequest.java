package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Phiếu yêu cầu hủy hóa đơn bán tại quầy (quản trị viên duyệt, hoàn tiền về tài khoản khách). */
@Entity
@Table(name = "bill_cancel_requests")
@Getter
@Setter
@NoArgsConstructor
public class BillCancelRequest extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requester;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone = "";

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "bank_account", nullable = false, length = 30)
    private String bankAccount;

    @Column(name = "account_holder", nullable = false, length = 100)
    private String accountHolder;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decider;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    /** Tài khoản nhận hoàn tiền, VD: "Vietcombank - 0123456789 - NGUYEN VAN A". */
    public String bankLabel() {
        return bankName + " - " + bankAccount + " - " + accountHolder;
    }
}
