package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(length = 200)
    private String activeIngredient;

    @Column(length = 100)
    private String strength;

    @Column(length = 100)
    private String dosageForm;

    @Column(length = 150)
    private String packaging;

    @Column(length = 100)
    private String registrationNo;

    @Column(length = 150)
    private String manufacturer;

    @Column(length = 80)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DrugType drugType = DrugType.OTC;

    /** Đơn vị bán (Hộp, Vỉ, Chai, Tuýp...). Tồn kho tính theo đơn vị này. */
    @Column(nullable = false, length = 30)
    private String unit = "Hộp";

    private long price;

    private Long oldPrice;

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String usageInstruction;

    @Column(length = 1000)
    private String contraindications;

    @Column(length = 1000)
    private String sideEffects;

    @Column(length = 300)
    private String image;

    /** Giới hạn số lượng mua mỗi đơn (null = không giới hạn). */
    private Integer maxPerOrder;

    /** Định mức tồn tối thiểu - dưới mức này sẽ cảnh báo. */
    private int minStock = 10;

    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /* ---- Thông tin tính toán (không lưu DB), được StockService/ProductService điền vào ---- */

    /** Tồn thực tế: tổng các lô còn hạn, không bị khóa. */
    @Transient
    private long onHand;

    /** Số lượng đang giữ chỗ cho các đơn chưa xuất kho. */
    @Transient
    private long reserved;

    /** Có thể bán = tồn thực tế - giữ chỗ. */
    @Transient
    private long available;

    @Transient
    private Double avgRating;

    @Transient
    private long sold;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isLowStock() {
        return onHand < minStock;
    }

    public boolean isOnSale() {
        return oldPrice != null && oldPrice > price;
    }

    public int getDiscountPercent() {
        return isOnSale() ? (int) Math.round(100.0 * (oldPrice - price) / oldPrice) : 0;
    }
}
