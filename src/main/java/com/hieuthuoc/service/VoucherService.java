package com.hieuthuoc.service;

import com.hieuthuoc.entity.MemberTier;
import com.hieuthuoc.entity.OrderStatus;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.entity.Voucher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherService {
    private static final List<OrderStatus> INVALID = List.of(OrderStatus.CANCELLED, OrderStatus.RX_REJECTED);

    private final CategoryService categories;
    private final CustomerService customers;

    @PersistenceContext
    private EntityManager em;

    public record Result(Voucher voucher, long discount, String error) {
        static Result fail(String m) {
            return new Result(null, 0, m);
        }
    }

    /**
     * Kiểm tra mã giảm giá. discountable = giá trị hàng KHÔNG tính thuốc kê đơn.
     * byCategory: giá trị hàng không kê đơn theo id danh mục (mã giới hạn danh mục).
     */
    public Result validate(String code, long discountable, User user, Map<Long, Long> byCategory) {
        if (code == null || code.isBlank()) return new Result(null, 0, null);
        List<Voucher> found = em.createQuery("select v from Voucher v left join fetch v.category where lower(v.code) = :c", Voucher.class)
                .setParameter("c", code.trim().toLowerCase()).getResultList();
        Voucher v = found.isEmpty() ? null : found.get(0);
        LocalDate today = LocalDate.now();
        if (v == null || !v.isActive()) return Result.fail("Mã giảm giá không tồn tại hoặc đã ngừng áp dụng.");
        if (v.getStartDate() != null && v.getStartDate().isAfter(today)) return Result.fail("Mã giảm giá chưa đến thời gian áp dụng.");
        if (v.getEndDate() != null && v.getEndDate().isBefore(today)) return Result.fail("Mã giảm giá đã hết hạn.");
        if (v.getUsageLimit() != null && v.getUsedCount() >= v.getUsageLimit()) return Result.fail("Mã giảm giá đã hết lượt sử dụng.");
        boolean targeted = v.getMinTier() != null || v.isNewCustomerOnly() || v.getPerUserLimit() != null;
        if (targeted && (user == null || !user.isCustomer())) return Result.fail("Vui lòng đăng nhập để dùng mã giảm giá này.");
        if (v.getMinTier() != null) {
            MemberTier tier = customers.tier(user);
            if (tier.ordinal() < v.getMinTier().ordinal()) {
                return Result.fail("Mã chỉ dành cho thành viên hạng " + v.getMinTier().getLabel() + " trở lên.");
            }
        }
        if (v.isNewCustomerOnly() && countOrders(user, null) > 0) return Result.fail("Mã chỉ dành cho khách hàng mua lần đầu.");
        if (v.getPerUserLimit() != null && countOrders(user, v.getCode()) >= v.getPerUserLimit()) {
            return Result.fail("Bạn đã dùng hết số lần sử dụng mã này (" + v.getPerUserLimit() + " lần).");
        }
        Result r = discountFor(v, discountable, byCategory);
        return r.error() != null ? Result.fail(r.error()) : new Result(v, r.discount(), null);
    }

    private long countOrders(User user, String voucherCode) {
        String jpql = "select count(o) from Order o where o.user.id = :u and o.status not in :st" + (voucherCode != null ? " and o.voucherCode = :c" : "");
        var q = em.createQuery(jpql, Long.class).setParameter("u", user.getId()).setParameter("st", INVALID);
        if (voucherCode != null) q.setParameter("c", voucherCode);
        return q.getSingleResult();
    }

    /**
     * Tiền giảm của mã theo giá trị hàng (điều kiện danh mục, đơn tối thiểu, mức giảm tối đa) - không kiểm tra thời hạn / lượt dùng.
     * Dùng khi đặt hàng và khi tính lại đơn sau điều chỉnh của dược sĩ.
     */
    public Result discountFor(Voucher v, long discountable, Map<Long, Long> byCategory) {
        long base = discountable;
        if (v.getCategory() != null) {
            base = 0;
            for (Long id : categories.descendantIds(v.getCategory())) base += byCategory.getOrDefault(id, 0L);
            if (base <= 0) return Result.fail("Mã chỉ áp dụng cho sản phẩm thuộc danh mục \"" + v.getCategory().getName() + "\".");
        }
        if (discountable <= 0) return Result.fail("Mã giảm giá không áp dụng cho thuốc kê đơn.");
        if (base < v.getMinOrder()) {
            return Result.fail((v.getCategory() != null ? "Sản phẩm thuộc danh mục \"" + v.getCategory().getName() + "\"" : "Đơn hàng (không tính thuốc kê đơn)")
                    + " cần tối thiểu " + PromotionService.money(v.getMinOrder()) + " để dùng mã này.");
        }
        long discount = v.isPercent() ? base * v.getDiscountValue() / 100 : v.getDiscountValue();
        if (v.getMaxDiscount() != null && v.getMaxDiscount() > 0) discount = Math.min(discount, v.getMaxDiscount());
        return new Result(v, Math.min(discount, Math.min(base, discountable)), null);
    }
}
