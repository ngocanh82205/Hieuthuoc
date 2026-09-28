package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {
    private final ProductRepository productRepo;
    private final StockService stockService;
    private final VoucherService voucherService;
    private final SettingService settings;

    @Getter
    public static class Line {
        private final String key;
        private final Product product;
        private final UnitOption unit;
        private final int quantity;
        private String error;

        Line(String key, Product product, UnitOption unit, int quantity) {
            this.key = key;
            this.product = product;
            this.unit = unit;
            this.quantity = quantity;
        }

        public long getUnitPrice() {
            return unit.price();
        }

        public long getLineTotal() {
            return unit.price() * quantity;
        }

        public int getBaseQuantity() {
            return unit.factor() * quantity;
        }
    }

    @Getter
    public static class View {
        private final List<Line> lines = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private long subtotal;
        private long discountable;
        private boolean rxRequired;
        private Voucher voucher;
        private String voucherError;
        private long discount;
        /** Điểm hiện có của khách, điểm dùng cho đơn này, số tiền được trừ. */
        private int pointsAvailable;
        private int pointsUsed;
        private long pointsDiscount;
        private long pointValue;
        private boolean usePoints;
        private long shippingFee;
        private long total;

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    /** Tổng số lượng (quy về đơn vị gốc) của một sản phẩm trong giỏ, cộng thêm phần sắp thêm. */
    private long baseInCart(Cart cart, Product p) {
        long total = 0;
        for (Map.Entry<String, Integer> e : cart.getItems().entrySet()) {
            if (Cart.productIdOf(e.getKey()) != p.getId()) continue;
            UnitOption u = p.findUnit(Cart.unitIdOf(e.getKey()));
            if (u != null) total += (long) u.factor() * e.getValue();
        }
        return total;
    }

    private static String stockError(Product p, long baseQty) {
        if (!p.getDrugType().isSellableOnline()) return "Thuốc kiểm soát đặc biệt không bán online. Vui lòng đến trực tiếp nhà thuốc.";
        if (p.getAvailable() <= 0) return p.getName() + " đang tạm hết hàng.";
        if (baseQty > p.getAvailable()) return "Chỉ còn " + p.getAvailable() + " " + p.getUnit() + " " + p.getName() + " trong kho.";
        if (p.getMaxPerOrder() != null && baseQty > p.getMaxPerOrder()) {
            return "Mỗi đơn chỉ được mua tối đa " + p.getMaxPerOrder() + " " + p.getUnit() + " " + p.getName() + ".";
        }
        return null;
    }

    /** Kiểm tra khả năng thêm vào giỏ; trả về thông báo lỗi hoặc null nếu hợp lệ. */
    public String checkAdd(Cart cart, Product p, UnitOption unit, int addQty) {
        if (p == null || !p.isActive()) return "Sản phẩm không tồn tại.";
        if (unit == null) return "Đơn vị tính không hợp lệ.";
        stockService.fill(p);
        return stockError(p, baseInCart(cart, p) + (long) unit.factor() * addQty);
    }

    public View build(Cart cart, ShippingMethod shippingMethod, User user) {
        View v = new View();
        Set<Long> ids = new HashSet<>();
        for (String k : cart.getItems().keySet()) ids.add(Cart.productIdOf(k));
        Map<Long, Product> products = new HashMap<>();
        for (Product p : stockService.fill(new ArrayList<>(productRepo.findAllById(ids)))) products.put(p.getId(), p);

        Map<Long, Long> baseTotals = new HashMap<>();
        for (Map.Entry<String, Integer> e : cart.getItems().entrySet()) {
            Product p = products.get(Cart.productIdOf(e.getKey()));
            if (p == null || !p.isActive()) continue;
            UnitOption u = p.findUnit(Cart.unitIdOf(e.getKey()));
            if (u == null) continue;
            Line line = new Line(e.getKey(), p, u, e.getValue());
            v.lines.add(line);
            baseTotals.merge(p.getId(), (long) line.getBaseQuantity(), Long::sum);
            v.subtotal += line.getLineTotal();
            if (p.getDrugType().isPrescription()) v.rxRequired = true;
            else v.discountable += line.getLineTotal();
        }
        Set<Long> reported = new HashSet<>();
        for (Line line : v.lines) {
            line.error = stockError(line.product, baseTotals.get(line.product.getId()));
            if (line.error != null && reported.add(line.product.getId())) v.errors.add(line.error);
        }

        VoucherService.Result r = voucherService.validate(cart.getVoucherCode(), v.discountable);
        v.voucher = r.voucher();
        v.voucherError = r.error();
        v.discount = r.discount();

        // Điểm tích lũy: chỉ trừ trên phần hàng không kê đơn (sau mã giảm giá)
        v.pointValue = settings.getLong("point_value");
        v.usePoints = cart.isUsePoints();
        if (user != null) {
            v.pointsAvailable = user.getPoints();
            if (cart.isUsePoints() && v.pointValue > 0) {
                long maxByAmount = Math.max(0, v.discountable - v.discount) / v.pointValue;
                v.pointsUsed = (int) Math.min(user.getPoints(), maxByAmount);
                v.pointsDiscount = v.pointsUsed * v.pointValue;
            }
        }
        long afterDiscount = v.subtotal - v.discount - v.pointsDiscount;
        v.shippingFee = v.lines.isEmpty() ? 0 : settings.shippingFee(shippingMethod, afterDiscount);
        v.total = afterDiscount + v.shippingFee;
        return v;
    }
}
