package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_items", indexes = @Index(columnList = "product_id"))
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    /** Lưu lại tên, đơn vị, loại thuốc, giá tại thời điểm đặt hàng. */
    @Column(nullable = false, length = 200)
    private String productName;

    @Column(length = 30)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DrugType drugType;

    private long price;

    private int quantity;

    /** Các lô đã xuất cho dòng hàng này (FEFO) - phục vụ truy vết thu hồi. */
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemBatch> allocations = new ArrayList<>();

    public long getLineTotal() {
        return price * quantity;
    }
}
