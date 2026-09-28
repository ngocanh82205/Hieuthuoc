package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private SuggestedCart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    /** Id đơn vị bán (0 = đơn vị gốc). */
    private Long unitId;

    @Column(length = 30)
    private String unitName;

    private long price;

    private int quantity;
}
