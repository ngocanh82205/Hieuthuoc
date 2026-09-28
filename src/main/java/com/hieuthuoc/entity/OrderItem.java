package com.hieuthuoc.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private DrugType drugType;

    private long price;

    /** Số lượng theo đơn vị đã chọn (unit). */
    private int quantity;

    /** Hệ số quy đổi của đơn vị đã chọn ra đơn vị gốc (VD: 1 Hộp = 10 Vỉ -> 10). */
    private Integer unitFactor;

    public int getFactor() {
        return unitFactor == null ? 1 : unitFactor;
    }

    /** Số lượng quy về đơn vị gốc (dùng cho tồn kho). */
    public int getBaseQuantity() {
        return quantity * getFactor();
    }

    /** Các lô đã xuất cho dòng hàng này (FEFO) - phục vụ truy vết thu hồi. */
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemBatch> allocations = new ArrayList<>();

    public long getLineTotal() {
        return price * quantity;
    }
}
