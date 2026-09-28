package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transfer_items")
@Getter
@Setter
@NoArgsConstructor
public class TransferItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TransferSlip slip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Batch sourceBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Batch targetBatch;

    private int quantity;
}
