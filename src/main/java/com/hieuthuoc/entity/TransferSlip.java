package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Phiếu chuyển kho giữa các kho / chi nhánh (giữ nguyên số lô, hạn dùng để truy vết). */
@Entity
@Table(name = "transfer_slips")
@Getter
@Setter
@NoArgsConstructor
public class TransferSlip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Warehouse fromWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Warehouse toWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    private User createdBy;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "slip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<TransferItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public int getTotalQuantity() {
        return items.stream().mapToInt(TransferItem::getQuantity).sum();
    }
}
