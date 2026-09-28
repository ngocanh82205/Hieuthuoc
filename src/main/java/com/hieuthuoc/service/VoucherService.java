package com.hieuthuoc.service;

import com.hieuthuoc.entity.Voucher;
import com.hieuthuoc.entity.VoucherType;
import com.hieuthuoc.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VoucherService {
    private final VoucherRepository voucherRepo;

    public record Result(Voucher voucher, long discount, String error) {
    }

    /**
     * Kiểm tra mã giảm giá. discountable = giá trị hàng KHÔNG tính thuốc kê đơn
     * (quy định: không khuyến mại thuốc kê đơn).
     */
    public Result validate(String code, long discountable) {
        if (Texts.isBlank(code)) return new Result(null, 0, null);
        Voucher v = voucherRepo.findByCodeIgnoreCase(code.trim()).orElse(null);
        LocalDate today = LocalDate.now();
        if (v == null || !v.isActive()) return fail("Mã giảm giá không tồn tại hoặc đã ngừng áp dụng.");
        if (v.getStartDate() != null && v.getStartDate().isAfter(today)) return fail("Mã giảm giá chưa đến thời gian áp dụng.");
        if (v.getEndDate() != null && v.getEndDate().isBefore(today)) return fail("Mã giảm giá đã hết hạn.");
        if (v.getUsageLimit() != null && v.getUsedCount() >= v.getUsageLimit()) return fail("Mã giảm giá đã hết lượt sử dụng.");
        if (discountable <= 0) return fail("Mã giảm giá không áp dụng cho thuốc kê đơn.");
        if (discountable < v.getMinOrder()) {
            return fail("Đơn hàng (không tính thuốc kê đơn) cần tối thiểu "
                    + NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(v.getMinOrder()) + " ₫ để dùng mã này.");
        }
        long discount = v.getType() == VoucherType.PERCENT ? discountable * v.getValue() / 100 : v.getValue();
        if (v.getMaxDiscount() != null) discount = Math.min(discount, v.getMaxDiscount());
        discount = Math.min(discount, discountable);
        return new Result(v, discount, null);
    }

    private static Result fail(String msg) {
        return new Result(null, 0, msg);
    }
}
