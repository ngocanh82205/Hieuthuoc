package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dòng lương của một nhân viên trong bảng lương tháng. */
@Entity
@Table(name = "payroll_lines")
@Getter
@Setter
@NoArgsConstructor
public class PayrollLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Payroll payroll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    /** Hình thức lương tại thời điểm tính: MONTHLY | HOURLY */
    @Column(length = 10)
    private String salaryType;

    /** Lương cơ bản tháng hoặc đơn giá giờ tại thời điểm tính. */
    private long rate;

    private int scheduledShifts;
    private int shiftsWorked;
    private int daysWorked;
    private long workedMinutes;
    private int absentShifts;
    private int lateCount;
    private int missingCheckout;

    private long baseAmount;
    private long shiftAllowance;
    private long fixedAllowance;
    private long bonus;
    private long latePenalty;
    private long insurance;
    private long otherDeduction;
    private long total;

    @Column(length = 300)
    private String note;

    public double getWorkedHours() {
        return Math.round(workedMinutes / 6.0) / 10.0;
    }

    public long getGross() {
        return baseAmount + shiftAllowance + fixedAllowance + bonus;
    }

    public long getDeductions() {
        return latePenalty + insurance + otherDeduction;
    }

    public void recomputeTotal() {
        total = Math.max(0, getGross() - getDeductions());
    }
}
