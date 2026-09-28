package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Nhắc lịch uống thuốc (theo giờ mỗi ngày) hoặc nhắc mua lại (theo ngày). Gửi qua thông báo trong web. */
@Entity
@Table(name = "reminders")
@Getter
@Setter
@NoArgsConstructor
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ReminderType type;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Giờ uống trong ngày, cách nhau dấu phẩy. VD: "08:00,20:00". */
    @Column(length = 100)
    private String times;

    private LocalDate startDate;

    private LocalDate endDate;

    /** Ngày nhắc mua lại. */
    private LocalDate remindDate;

    @Column(length = 300)
    private String note;

    private boolean active = true;

    private LocalDateTime lastFiredAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public java.util.List<String> getTimeList() {
        if (times == null || times.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(times.split(",")).map(String::trim).filter(t -> !t.isEmpty()).sorted().toList();
    }
}
