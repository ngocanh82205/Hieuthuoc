package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Yêu cầu quên mật khẩu: admin xác minh qua điện thoại rồi cấp mật khẩu tạm. */
@Entity
@Table(name = "password_reset_requests")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    /** Email hoặc SĐT khách nhập. */
    @Column(nullable = false, length = 150)
    private String identifier;

    @Column(length = 20)
    private String contactPhone;

    @Column(length = 300)
    private String note;

    private boolean handled;

    @ManyToOne(fetch = FetchType.LAZY)
    private User handledBy;

    private LocalDateTime handledAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
