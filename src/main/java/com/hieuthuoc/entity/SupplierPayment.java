package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Phiếu chi trả công nợ nhà cung cấp. */
@Entity
@Table(name = "supplier_payments")
@Getter
@Setter
@NoArgsConstructor
public class SupplierPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Supplier supplier;

    private long amount;

    @Column(nullable = false)
    private LocalDate paidDate;

    /** Tiền mặt / Chuyển khoản */
    @Column(length = 30)
    private String method;

    @Column(length = 300)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
