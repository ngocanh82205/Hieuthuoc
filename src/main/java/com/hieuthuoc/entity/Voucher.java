package com.hieuthuoc.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private VoucherType type = VoucherType.PERCENT;

    @Column(name = "discount_value")
    private long value;

    private long minOrder;

    private Long maxDiscount;

    private Integer usageLimit;

    private int usedCount;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean active = true;

    /* ---- Đối tượng áp dụng ---- */

    /** Hạng thành viên tối thiểu được dùng mã (null = mọi khách). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private MemberTier minTier;

    /** Chỉ khách hàng mới (chưa có đơn hàng thành công). */
    private Boolean newCustomerOnly;

    /** Chỉ áp dụng cho sản phẩm thuộc danh mục (kể cả danh mục con). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    /** Số lần mỗi khách được dùng (null = không giới hạn). */
    private Integer perUserLimit;

    public boolean isNewOnly() {
        return Boolean.TRUE.equals(newCustomerOnly);
    }

    /** Hiển thị trong "Kho voucher" để khách lưu. */
    private Boolean showInWallet;

    public boolean isInWallet() {
        return Boolean.TRUE.equals(showInWallet);
    }
}
