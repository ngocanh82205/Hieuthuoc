package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Phân ca: nhân viên làm ca nào vào ngày nào, kèm chấm công vào / ra ca. */
@Entity
@Table(name = "shift_assignments", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "work_date", "shift_id"}),
        indexes = @Index(columnList = "work_date"))
@Getter
@Setter
@NoArgsConstructor
public class ShiftAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private WorkShift shift;

    @Column(length = 200)
    private String note;

    private LocalDateTime checkInAt;

    private LocalDateTime checkOutAt;

    /** Quản trị viên sửa công thủ công (ghi chú lý do). */
    @Column(length = 200)
    private String adjustNote;

    @ManyToOne(fetch = FetchType.LAZY)
    private User createdBy;

    public ShiftAssignment(User user, LocalDate date, WorkShift shift) {
        this.user = user;
        this.workDate = date;
        this.shift = shift;
    }

    public LocalDateTime getStartAt() {
        return workDate.atTime(shift.getStartTime());
    }

    public LocalDateTime getEndAt() {
        LocalDateTime end = workDate.atTime(shift.getEndTime());
        return shift.isOvernight() ? end.plusDays(1) : end;
    }

    /** Số phút đi muộn so với giờ bắt đầu ca. */
    public long getLateMinutes() {
        if (checkInAt == null || !checkInAt.isAfter(getStartAt())) return 0;
        return java.time.Duration.between(getStartAt(), checkInAt).toMinutes();
    }

    /** Số phút về sớm so với giờ kết thúc ca. */
    public long getEarlyMinutes() {
        if (checkOutAt == null || !checkOutAt.isBefore(getEndAt())) return 0;
        return java.time.Duration.between(checkOutAt, getEndAt()).toMinutes();
    }

    /** Số phút được tính công: thời gian có mặt trong khung ca, trừ giờ nghỉ (nếu làm quá 4 tiếng), tối đa bằng công của cả ca. */
    public long getPaidMinutes() {
        if (checkInAt == null || checkOutAt == null) return 0;
        LocalDateTime from = checkInAt.isAfter(getStartAt()) ? checkInAt : getStartAt();
        LocalDateTime to = checkOutAt.isBefore(getEndAt()) ? checkOutAt : getEndAt();
        long overlap = Math.max(0, java.time.Duration.between(from, to).toMinutes());
        long paid = overlap - (overlap > 240 ? shift.getBreakMinutes() : 0);
        return Math.max(0, Math.min(paid, shift.getPaidMinutes()));
    }

    public boolean isCompleted() {
        return checkInAt != null && checkOutAt != null;
    }

    /** Trạng thái chấm công hiển thị: [nhãn, màu]. */
    public String[] getAttendance() {
        LocalDateTime now = LocalDateTime.now();
        if (checkInAt == null) {
            if (now.isBefore(getStartAt())) return new String[]{"Sắp tới", "secondary"};
            if (now.isBefore(getEndAt())) return new String[]{"Chưa vào ca", "warning"};
            return new String[]{"Vắng", "danger"};
        }
        if (checkOutAt == null) {
            return now.isBefore(getEndAt().plusHours(4)) ? new String[]{"Đang làm", "info"} : new String[]{"Quên kết ca", "warning"};
        }
        long late = getLateMinutes();
        return late > 0 ? new String[]{"Muộn " + late + " phút", "warning"} : new String[]{"Hoàn thành", "success"};
    }
}
