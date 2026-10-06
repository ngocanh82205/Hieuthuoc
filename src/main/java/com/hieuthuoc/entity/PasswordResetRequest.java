package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Khách quên mật khẩu: admin gọi điện xác minh rồi cấp mật khẩu tạm. */
@Entity
@Table(name = "password_reset_requests")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetRequest extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 150)
    private String identifier;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(length = 300)
    private String note;

    @Column(nullable = false)
    private boolean handled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handler;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;
}
