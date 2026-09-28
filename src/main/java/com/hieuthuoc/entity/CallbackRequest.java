package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Yêu cầu dược sĩ gọi điện tư vấn. */
@Entity
@Table(name = "callback_requests")
@Getter
@Setter
@NoArgsConstructor
public class CallbackRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    /** Khung giờ mong muốn, VD: "Sáng (8h-11h)". */
    @Column(length = 100)
    private String preferredTime;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private boolean done;

    @ManyToOne(fetch = FetchType.LAZY)
    private User handledBy;

    private LocalDateTime handledAt;

    @Column(length = 500)
    private String result;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
