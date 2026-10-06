package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_id", insertable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(length = 30)
    private String unit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drug_type", length = 20)
    private DrugType drugType;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_factor", nullable = false)
    private int unitFactor = 1;

    @Column(name = "is_gift", nullable = false)
    private boolean gift;

    /** Suất flash sale đã giữ cho dòng này (trả lại khi hủy / giảm số lượng). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flash_promotion_id")
    private Promotion flashPromotion;

    @Column(name = "flash_promotion_id", insertable = false, updatable = false)
    private Long flashPromotionId;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItemBatch> allocations = new ArrayList<>();

    public int factor() {
        return Math.max(1, unitFactor);
    }

    public int baseQuantity() {
        return quantity * factor();
    }

    public long lineTotal() {
        return price * quantity;
    }

    public boolean isPrescription() {
        return drugType != null && drugType.isPrescription();
    }
}
