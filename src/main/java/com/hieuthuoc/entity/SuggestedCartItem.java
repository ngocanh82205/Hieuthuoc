package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "suggested_cart_items")
@Getter
@Setter
@NoArgsConstructor
public class SuggestedCartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggested_cart_id")
    private SuggestedCart suggestedCart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Đơn vị quy đổi (null / 0 = đơn vị gốc). */
    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "unit_name", length = 30)
    private String unitName;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int quantity;
}
