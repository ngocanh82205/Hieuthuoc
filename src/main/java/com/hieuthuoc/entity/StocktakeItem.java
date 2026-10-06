package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "stocktake_items")
@Getter
@Setter
@NoArgsConstructor
public class StocktakeItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktake_id")
    private Stocktake stocktake;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(length = 30)
    private String unit;

    @Column(name = "batch_no", nullable = false, length = 50)
    private String batchNo;

    @Column(name = "exp_date")
    private LocalDate expDate;

    @Column(name = "book_qty", nullable = false)
    private int bookQty;

    /** Hàng đã xuất cho đơn đang soạn / đóng gói (còn nằm trên kệ, chưa giao đi). */
    @Column(name = "pending_out_qty", nullable = false)
    private int pendingOutQty;

    @Column(name = "counted_qty", nullable = false)
    private int countedQty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_id")
    private StockAdjustment adjustment;

    /** Số phải có trên kệ = tồn sổ sách + hàng đã xuất cho đơn đang soạn / đóng gói (chưa giao đi). */
    public int expectedQty() {
        return bookQty + pendingOutQty;
    }

    public int diff() {
        return countedQty - expectedQty();
    }
}
