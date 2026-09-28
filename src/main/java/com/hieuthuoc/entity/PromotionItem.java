package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sản phẩm trong combo (số lượng theo đơn vị gốc). */
@Entity
@Table(name = "promotion_items")
@Getter
@Setter
@NoArgsConstructor
public class PromotionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    private int quantity = 1;

    public PromotionItem(Promotion promotion, Product product, int quantity) {
        this.promotion = promotion;
        this.product = product;
        this.quantity = quantity;
    }
}
