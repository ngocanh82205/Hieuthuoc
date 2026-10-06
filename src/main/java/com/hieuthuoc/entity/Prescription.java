package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Đơn thuốc khách tải lên (kèm đơn hàng hoặc gửi riêng để dược sĩ lên đơn). */
@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
public class Prescription extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    /** Gửi riêng (chưa chọn sản phẩm) - dược sĩ lên đơn từ đơn thuốc. */
    @Column(nullable = false)
    private boolean standalone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 200)
    private String image;

    @Column(length = 300)
    private String checklist;

    @Column(name = "customer_note", length = 500)
    private String customerNote;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacist_id")
    private User pharmacist;

    @Column(name = "patient_name", length = 100)
    private String patientName;

    @Column(name = "doctor_name", length = 100)
    private String doctorName;

    @Column(length = 200)
    private String clinic;

    @Column(name = "rx_date")
    private LocalDate rxDate;

    @Column(name = "pharmacist_note", length = 1000)
    private String pharmacistNote;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public boolean hasCheck(String key) {
        return checklist != null && java.util.Arrays.asList(checklist.split(",")).contains(key);
    }
}
