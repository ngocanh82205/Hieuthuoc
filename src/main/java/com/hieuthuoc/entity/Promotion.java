package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chương trình khuyến mãi cho OTC / TPCN (không áp dụng thuốc kê đơn):
 * FLASH_SALE (giá sốc có thời hạn, giới hạn suất), COMBO (mua đủ bộ giảm tiền), GIFT (mua X tặng Y).
 */
@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
public class Promotion {
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

    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    /** FLASH_SALE: sản phẩm + giá sốc (theo đơn vị gốc) + số suất. GIFT: sản phẩm mua (X). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private Long salePrice;

    /** FLASH_SALE: số lượng tối đa bán giá sốc (đơn vị gốc, null = không giới hạn). */
    private Integer quantityLimit;

    private int soldCount;

    /** GIFT: mua đủ buyQuantity (đơn vị gốc) sản phẩm X được tặng giftQuantity sản phẩm Y. */
    private Integer buyQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product giftProduct;

    private Integer giftQuantity;

    /** COMBO: số tiền giảm cho mỗi bộ đủ sản phẩm. */
    private Long comboDiscount;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PromotionItem> items = new ArrayList<>();

    public boolean isRunning() {
        LocalDateTime now = LocalDateTime.now();
        return active && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public String getTypeLabel() {
        return switch (type) {
            case FLASH_SALE -> "Flash sale";
            case COMBO -> "Combo";
            case GIFT -> "Mua X tặng Y";
            default -> type;
        };
    }

    public Integer getRemaining() {
        return quantityLimit == null ? null : Math.max(0, quantityLimit - soldCount);
    }

    public String getStatusLabel() {
        LocalDateTime now = LocalDateTime.now();
        if (!active) return "Tạm dừng";
        if (now.isBefore(startAt)) return "Sắp diễn ra";
        if (!now.isBefore(endAt)) return "Đã kết thúc";
        if (FLASH_SALE.equals(type) && quantityLimit != null && soldCount >= quantityLimit) return "Hết suất";
        return "Đang chạy";
    }
}
