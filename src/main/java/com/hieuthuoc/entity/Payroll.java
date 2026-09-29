package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Bảng lương theo tháng. DRAFT (nháp, tính lại được) -> APPROVED (đã chốt) -> PAID (đã trả). */
@Entity
@Table(name = "payrolls")
@Getter
@Setter
@NoArgsConstructor
public class Payroll {
    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String PAID = "PAID";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tháng lương dạng yyyy-MM */
    @Column(name = "pay_month", nullable = false, unique = true, length = 7)
    private String month;

    @Column(nullable = false, length = 10)
    private String status = DRAFT;

    @Column(length = 300)
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime calculatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private User approvedBy;

    private LocalDateTime approvedAt;

    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "payroll", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PayrollLine> lines = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public long getTotal() {
        return lines.stream().mapToLong(PayrollLine::getTotal).sum();
    }

    public String getStatusLabel() {
        return switch (status) {
            case APPROVED -> "Đã chốt";
            case PAID -> "Đã trả lương";
            default -> "Nháp";
        };
    }

    public String getStatusColor() {
        return switch (status) {
            case APPROVED -> "primary";
            case PAID -> "success";
            default -> "secondary";
        };
    }

    public String getMonthLabel() {
        return month.substring(5) + "/" + month.substring(0, 4);
    }
}
