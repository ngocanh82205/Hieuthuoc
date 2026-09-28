package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Lô hàng trong kho: quản lý theo số lô và hạn dùng để xuất FEFO & truy vết thu hồi. */
@Entity
@Table(name = "batches", indexes = @Index(columnList = "product_id, expDate"))
@Getter
@Setter
@NoArgsConstructor
public class Batch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String batchNo;

    private LocalDate mfgDate;

    @Column(nullable = false)
    private LocalDate expDate;

    private int quantity;

    private long importPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    private Receipt receipt;

    /** Kho / chi nhánh chứa lô (null = kho chính). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Warehouse warehouse;

    /** Lô bị khóa (thu hồi / nghi ngờ chất lượng) sẽ không được xuất bán. */
    private boolean locked;

    @Column(length = 300)
    private String lockReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expDate.isBefore(LocalDate.now());
    }
}
