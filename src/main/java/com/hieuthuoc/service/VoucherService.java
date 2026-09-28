package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.OrderRepository;
import com.hieuthuoc.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VoucherService {
    private static final EnumSet<OrderStatus> INVALID = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.RX_REJECTED);

    private final VoucherRepository voucherRepo;
    private final OrderRepository orderRepo;
    private final CategoryService categoryService;

    public record Result(Voucher voucher, long discount, String error) {
    }

    public Result validate(String code, long discountable) {
        return validate(code, discountable, null, null);
    }

    /**
     * Kiểm tra mã giảm giá. discountable = giá trị hàng KHÔNG tính thuốc kê đơn (quy định: không khuyến mại thuốc kê đơn).
     * byCategory: giá trị hàng không kê đơn theo id danh mục (để áp mã giới hạn danh mục).
     */
    public Result validate(String code, long discountable, User user, Map<Long, Long> byCategory) {
        if (Texts.isBlank(code)) return new Result(null, 0, null);
        Voucher v = voucherRepo.findByCodeIgnoreCase(code.trim()).orElse(null);
        LocalDate today = LocalDate.now();
        if (v == null || !v.isActive()) return fail("Mã giảm giá không tồn tại hoặc đã ngừng áp dụng.");
        if (v.getStartDate() != null && v.getStartDate().isAfter(today)) return fail("Mã giảm giá chưa đến thời gian áp dụng.");
        if (v.getEndDate() != null && v.getEndDate().isBefore(today)) return fail("Mã giảm giá đã hết hạn.");
        if (v.getUsageLimit() != null && v.getUsedCount() >= v.getUsageLimit()) return fail("Mã giảm giá đã hết lượt sử dụng.");
        boolean targeted = v.getMinTier() != null || v.isNewOnly() || v.getPerUserLimit() != null;
        if (targeted && (user == null || user.getRole() != Role.CUSTOMER)) return fail("Vui lòng đăng nhập để dùng mã giảm giá này.");
        if (v.getMinTier() != null) {
            MemberTier tier = MemberTier.of(orderRepo.totalSpent(user));
            if (tier.ordinal() < v.getMinTier().ordinal()) return fail("Mã chỉ dành cho thành viên hạng " + v.getMinTier().getLabel() + " trở lên.");
        }
        if (v.isNewOnly() && orderRepo.countValidByUser(user, INVALID) > 0) return fail("Mã chỉ dành cho khách hàng mua lần đầu.");
        if (v.getPerUserLimit() != null && orderRepo.countVoucherUse(user, v.getCode(), INVALID) >= v.getPerUserLimit()) {
            return fail("Bạn đã dùng hết số lần sử dụng mã này (" + v.getPerUserLimit() + " lần).");
        }
        long base = discountable;
        if (v.getCategory() != null) {
            Set<Long> ids = categoryService.descendantIds(v.getCategory());
            base = byCategory == null ? 0 : byCategory.entrySet().stream().filter(e -> ids.contains(e.getKey())).mapToLong(Map.Entry::getValue).sum();
            if (base <= 0) return fail("Mã chỉ áp dụng cho sản phẩm thuộc danh mục \"" + v.getCategory().getName() + "\".");
        }
        if (discountable <= 0) return fail("Mã giảm giá không áp dụng cho thuốc kê đơn.");
        if (base < v.getMinOrder()) {
            return fail((v.getCategory() != null ? "Sản phẩm thuộc danh mục \"" + v.getCategory().getName() + "\"" : "Đơn hàng (không tính thuốc kê đơn)")
                    + " cần tối thiểu " + NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(v.getMinOrder()) + " ₫ để dùng mã này.");
        }
        long discount = v.getType() == VoucherType.PERCENT ? base * v.getValue() / 100 : v.getValue();
        if (v.getMaxDiscount() != null) discount = Math.min(discount, v.getMaxDiscount());
        discount = Math.min(discount, Math.min(base, discountable));
        return new Result(v, discount, null);
    }

    private static Result fail(String msg) {
        return new Result(null, 0, msg);
    }
}
