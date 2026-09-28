package com.hieuthuoc.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Đơn thuốc khách tải lên - dược sĩ duyệt/từ chối và ghi sổ bán thuốc kê đơn. */
@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
public class Prescription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Đơn hàng gắn với đơn thuốc. Null khi khách gửi đơn thuốc chưa chọn sản phẩm (chờ dược sĩ lên đơn). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;

    /** true = khách gửi đơn thuốc không kèm sản phẩm, nhờ dược sĩ lên đơn. */
    private Boolean standalone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    /** Tên file ảnh (lưu ngoài thư mục public). */
    /** Ảnh đơn thuốc (null khi bán tại quầy - dược sĩ xem đơn giấy trực tiếp). */
    @Column(length = 200)
    private String image;

    /** Các mục dược sĩ đã kiểm tra khi duyệt (hợp lệ, hiệu lực, chữ ký, khớp thuốc/liều). */
    @Column(length = 300)
    private String checklist;

    @Column(length = 500)
    private String customerNote;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    private User pharmacist;

    @Column(length = 100)
    private String patientName;

    @Column(length = 100)
    private String doctorName;

    @Column(length = 200)
    private String clinic;

    private LocalDate rxDate;

    @Column(length = 1000)
    private String pharmacistNote;

    @Column(length = 500)
    private String rejectReason;

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public boolean isStandalone() {
        return Boolean.TRUE.equals(standalone);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
