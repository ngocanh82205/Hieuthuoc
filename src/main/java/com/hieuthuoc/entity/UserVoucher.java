package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Voucher khách đã lưu vào kho. */
@Entity
@Table(name = "user_vouchers", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "voucher_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserVoucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Voucher voucher;

    @Column(nullable = false)
    private LocalDateTime savedAt;

    @PrePersist
    void prePersist() {
        if (savedAt == null) savedAt = LocalDateTime.now();
    }
}
