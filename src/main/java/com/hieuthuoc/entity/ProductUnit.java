package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Đơn vị bán quy đổi, VD: 1 Hộp = 10 Vỉ (đơn vị gốc), giá riêng. */
@Entity
@Table(name = "product_units")
@Getter
@Setter
@NoArgsConstructor
public class ProductUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(nullable = false, length = 30)
    private String name;

    private int factor;

    private long price;

    public ProductUnit(Product product, String name, int factor, long price) {
        this.product = product;
        this.name = name;
        this.factor = factor;
        this.price = price;
    }
}
