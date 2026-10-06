package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Lô hàng: tồn kho theo số lô / hạn dùng (xuất FEFO). */
@Entity
@Table(name = "batches")
@Getter
@Setter
@NoArgsConstructor
public class Batch extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "batch_no", nullable = false, length = 50)
    private String batchNo;

    @Column(name = "mfg_date")
    private LocalDate mfgDate;

    @Column(name = "exp_date", nullable = false)
    private LocalDate expDate;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "import_price", nullable = false)
    private long importPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "lock_reason", length = 300)
    private String lockReason;

    @Column(name = "product_id", insertable = false, updatable = false)
    private Long productId;

    public boolean isExpired() {
        return expDate.isBefore(LocalDate.now());
    }

    public long daysLeft() {
        return ChronoUnit.DAYS.between(LocalDate.now(), expDate);
    }
}
