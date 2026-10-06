package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Phân bổ xuất kho của một dòng đơn theo lô (FEFO). */
@Entity
@Table(name = "order_item_batches")
@Getter
@Setter
@NoArgsConstructor
public class OrderItemBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(name = "batch_id", insertable = false, updatable = false)
    private Long batchId;

    @Column(nullable = false)
    private int quantity;
}
