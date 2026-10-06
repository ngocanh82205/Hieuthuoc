package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "category_id", insertable = false, updatable = false)
    private Long categoryId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "active_ingredient", length = 200)
    private String activeIngredient;

    @Column(length = 100)
    private String strength;

    @Column(name = "dosage_form", length = 100)
    private String dosageForm;

    @Column(length = 150)
    private String packaging;

    @Column(name = "registration_no", length = 100)
    private String registrationNo;

    @Column(length = 150)
    private String manufacturer;

    @Column(length = 80)
    private String country;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drug_type", nullable = false, length = 20)
    private DrugType drugType = DrugType.OTC;

    /** Đơn vị bán gốc (Hộp, Vỉ, Chai...). Tồn kho tính theo đơn vị này. */
    @Column(nullable = false, length = 30)
    private String unit = "Hộp";

    @Column(nullable = false)
    private long price;

    @Column(name = "old_price")
    private Long oldPrice;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "usage_instruction", columnDefinition = "text")
    private String usageInstruction;

    @Column(columnDefinition = "text")
    private String contraindications;

    @Column(name = "side_effects", columnDefinition = "text")
    private String sideEffects;

    @Column(length = 300)
    private String image;

    /** Giới hạn số lượng mua mỗi đơn (null = không giới hạn). */
    @Column(name = "max_per_order")
    private Integer maxPerOrder;

    /** Định mức tồn tối thiểu - dưới mức này sẽ cảnh báo. */
    @Column(name = "min_stock", nullable = false)
    private int minStock = 10;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "weight_gram", nullable = false)
    private int weightGram = 200;

    @Column(name = "meta_title", length = 150)
    private String metaTitle;

    @Column(name = "meta_description", length = 300)
    private String metaDescription;

    @ManyToMany
    @JoinTable(name = "product_equivalents", joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "equivalent_id"))
    private Set<Product> equivalents = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("factor ASC")
    private List<ProductUnit> units = new ArrayList<>();

    /* ---- Thông tin tính toán (không lưu DB) do StockService / CatalogService / PromotionService điền vào ---- */

    @Transient
    private long onHand;

    @Transient
    private long reserved;

    @Transient
    private long available;

    @Transient
    private Double avgRating;

    @Transient
    private long reviewCount;

    @Transient
    private long sold;

    @Transient
    private Long flashPrice;

    @Transient
    private LocalDateTime flashEndsAt;

    @Transient
    private Integer flashRemaining;

    @Transient
    private List<String> promoLabels = new ArrayList<>();

    public String imageUrl() {
        if (image == null || image.isEmpty()) return null;
        return image.startsWith("http") || image.startsWith("/") ? image : "/storage/" + image;
    }

    /** Đơn vị tính: đơn vị gốc (id = 0) + các đơn vị quy đổi (Vỉ, Hộp = 10 vỉ...). */
    public List<UnitOption> unitOptions() {
        List<UnitOption> list = new ArrayList<>();
        list.add(new UnitOption(0L, unit, 1, price));
        for (ProductUnit u : units) list.add(new UnitOption(u.getId(), u.getName(), u.getFactor(), u.getPrice()));
        return list;
    }

    /** Đơn vị theo id; không có thì trả về đơn vị gốc. */
    public UnitOption findUnit(Long unitId) {
        long id = unitId == null ? 0L : unitId;
        for (UnitOption o : unitOptions()) if (o.id() == id) return o;
        return unitOptions().get(0);
    }

    public UnitOption findUnitByFactor(int factor) {
        for (UnitOption o : unitOptions()) if (o.factor() == factor) return o;
        return null;
    }

    public boolean isFlashSale() {
        return flashPrice != null && flashPrice < price;
    }

    public int flashPercent() {
        return isFlashSale() && price > 0 ? (int) Math.round(100 - flashPrice * 100.0 / price) : 0;
    }

    public boolean isOnSale() {
        return oldPrice != null && oldPrice > price;
    }

    public int discountPercent() {
        return isOnSale() ? (int) Math.round(100 - price * 100.0 / oldPrice) : 0;
    }

    public long effectivePrice() {
        return isFlashSale() ? flashPrice : price;
    }

    public boolean isLowStock() {
        return available < minStock;
    }

    public List<String> ingredientList() {
        if (activeIngredient == null || activeIngredient.isBlank()) return List.of();
        return Arrays.stream(activeIngredient.split("[,;+]")).map(s -> s.trim().toLowerCase()).filter(s -> !s.isEmpty()).toList();
    }
}
