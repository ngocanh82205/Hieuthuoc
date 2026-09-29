package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalTime;

/** Loại ca làm việc (VD: Ca sáng 7h-15h). */
@Entity
@Table(name = "work_shifts")
@Getter
@Setter
@NoArgsConstructor
public class WorkShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    /** Thời gian nghỉ giữa ca (phút) - không tính công. */
    private int breakMinutes;

    /** Phụ cấp mỗi ca (VD phụ cấp ca tối). */
    private long allowance;

    /** Màu hiển thị trên lịch: primary | success | warning | info | danger | secondary */
    @Column(length = 20)
    private String color = "primary";

    private boolean active = true;

    public WorkShift(String name, LocalTime start, LocalTime end, int breakMinutes, long allowance, String color) {
        this.name = name;
        this.startTime = start;
        this.endTime = end;
        this.breakMinutes = breakMinutes;
        this.allowance = allowance;
        this.color = color;
    }

    /** Ca qua đêm nếu giờ kết thúc <= giờ bắt đầu. */
    public boolean isOvernight() {
        return !endTime.isAfter(startTime);
    }

    public long getLengthMinutes() {
        long m = Duration.between(startTime, endTime).toMinutes();
        return m <= 0 ? m + 24 * 60 : m;
    }

    /** Số phút được tính công của cả ca (trừ nghỉ). */
    public long getPaidMinutes() {
        return Math.max(0, getLengthMinutes() - breakMinutes);
    }

    public String getTimeRange() {
        return String.format("%02d:%02d - %02d:%02d", startTime.getHour(), startTime.getMinute(), endTime.getHour(), endTime.getMinute());
    }
}
