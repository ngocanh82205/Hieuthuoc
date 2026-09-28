package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Banner trang chủ. */
@Entity
@Table(name = "banners")
@Getter
@Setter
@NoArgsConstructor
public class Banner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 300)
    private String subtitle;

    /** Ảnh nền (tùy chọn); không có ảnh thì dùng nền gradient theo màu. */
    @Column(length = 300)
    private String image;

    @Column(length = 300)
    private String link;

    @Column(length = 50)
    private String buttonText;

    /** Tông màu nền: ocean | teal | violet | sunset */
    @Column(length = 20)
    private String theme = "ocean";

    private int sortOrder;

    private boolean active = true;

    private LocalDate startDate;

    private LocalDate endDate;

    public boolean isShowing() {
        LocalDate today = LocalDate.now();
        return active && (startDate == null || !today.isBefore(startDate)) && (endDate == null || !today.isAfter(endDate));
    }
}
