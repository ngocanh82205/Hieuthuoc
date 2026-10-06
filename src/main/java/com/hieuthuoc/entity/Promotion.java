package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Chương trình khuyến mãi: FLASH_SALE (giá sốc có giới hạn suất), COMBO (mua đủ bộ giảm tiền), GIFT (mua X tặng Y). */
@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
public class Promotion extends Timestamped {
    public static final String FLASH_SALE = "FLASH_SALE";
    public static final String COMBO = "COMBO";
    public static final String GIFT = "GIFT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_id", insertable = false, updatable = false)
    private Long productId;

    @Column(name = "sale_price")
    private Long salePrice;

    @Column(name = "quantity_limit")
    private Integer quantityLimit;

    @Column(name = "sold_count", nullable = false)
    private int soldCount;

    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gift_product_id")
    private Product giftProduct;

    @Column(name = "gift_quantity")
    private Integer giftQuantity;

    @Column(name = "combo_discount")
    private Long comboDiscount;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PromotionItem> items = new ArrayList<>();

    public Integer remaining() {
        return quantityLimit == null ? null : Math.max(0, quantityLimit - soldCount);
    }

    public boolean isRunning() {
        return isRunningAt(LocalDateTime.now());
    }

    public boolean isRunningAt(LocalDateTime at) {
        Integer rem = remaining();
        return active && !at.isBefore(startAt) && at.isBefore(endAt)
                && (!FLASH_SALE.equals(type) || rem == null || rem > 0);
    }

    public String typeLabel() {
        return switch (type == null ? "" : type) {
            case FLASH_SALE -> "Flash sale";
            case COMBO -> "Combo";
            case GIFT -> "Mua X tặng Y";
            default -> type;
        };
    }

    public String statusLabel() {
        LocalDateTime now = LocalDateTime.now();
        if (!active) return "Tạm dừng";
        if (now.isBefore(startAt)) return "Sắp diễn ra";
        if (!now.isBefore(endAt)) return "Đã kết thúc";
        Integer rem = remaining();
        return rem != null && rem == 0 ? "Hết suất" : "Đang chạy";
    }
}
