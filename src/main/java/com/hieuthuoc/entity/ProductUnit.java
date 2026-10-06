package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Đơn vị quy đổi lớn hơn đơn vị gốc (VD: Hộp = 10 Vỉ). */
@Entity
@Table(name = "product_units")
@Getter
@Setter
@NoArgsConstructor
public class ProductUnit extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false)
    private int factor;

    @Column(nullable = false)
    private long price;
}
