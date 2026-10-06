package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

/** Mã giảm giá (type PERCENT / AMOUNT). */
@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
public class Voucher extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(length = 300)
    private String description;

    @Column(nullable = false, length = 10)
    private String type = "PERCENT";

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order", nullable = false)
    private long minOrder;

    @Column(name = "max_discount")
    private Long maxDiscount;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "min_tier", length = 20)
    private MemberTier minTier;

    @Column(name = "new_customer_only", nullable = false)
    private boolean newCustomerOnly;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "per_user_limit")
    private Integer perUserLimit;

    @Column(name = "show_in_wallet", nullable = false)
    private boolean showInWallet = true;

    public boolean isRunning() {
        LocalDate today = LocalDate.now();
        return active
                && (startDate == null || !today.isBefore(startDate))
                && (endDate == null || !today.isAfter(endDate))
                && (usageLimit == null || usedCount < usageLimit);
    }

    public boolean isPercent() {
        return "PERCENT".equals(type);
    }

    public String typeLabel() {
        return isPercent() ? "Giảm theo %" : "Giảm số tiền cố định";
    }

    public String valueLabel() {
        return isPercent()
                ? "Giảm " + discountValue + "%" + (maxDiscount != null && maxDiscount > 0 ? " (tối đa " + money(maxDiscount) + ")" : "")
                : "Giảm " + money(discountValue);
    }

    public long discountFor(long amount) {
        if (amount < minOrder) return 0;
        long d = isPercent() ? amount * discountValue / 100 : discountValue;
        if (maxDiscount != null && maxDiscount > 0) d = Math.min(d, maxDiscount);
        return Math.min(d, amount);
    }

    private static String money(long n) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(n) + " ₫";
    }
}
