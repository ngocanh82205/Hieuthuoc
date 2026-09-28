package com.hieuthuoc.entity;

import jakarta.persistence.*;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    /** Tên file ảnh (lưu ngoài thư mục public). */
    @Column(nullable = false, length = 200)
    private String image;

    @Column(length = 500)
    private String customerNote;

    @Enumerated(EnumType.STRING)
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

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
