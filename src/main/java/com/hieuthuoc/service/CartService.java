package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Tính giỏ hàng: dòng hàng, khuyến mãi, mã giảm giá, điểm, phí ship, tổng tiền. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {
    private final StockService stock;
    private final VoucherService vouchers;
    private final SettingService settings;
    private final PromotionService promotions;

    @PersistenceContext
    private EntityManager em;

    /** Một dòng giỏ hàng. */
    @Getter
    @Setter
    public static class Line {
        private final String key;
        private final Product product;
        private final UnitOption unit;
        private final int quantity;
        private final int base;
        private Long promoPrice;
        private Long flashPromotionId;
        private String error;
        private long unitPrice;
        private long listPrice;
        private boolean flash;
        private long lineTotal;
        private boolean selected = true;

        Line(String key, Product product, UnitOption unit, int quantity) {
            this.key = key;
            this.product = product;
            this.unit = unit;
            this.quantity = quantity;
            this.base = unit.factor() * quantity;
        }
    }

    /** Kết quả tính giỏ hàng. */
    @Getter
    @Setter
    public static class View {
        private final List<Line> lines = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private long subtotal;
        private long discountable;
        private boolean rxRequired;
        private long promoDiscount;
        private List<String> promoNotes = new ArrayList<>();
        private final List<PromotionService.Gift> gifts = new ArrayList<>();
        private Voucher voucher;
        private String voucherError;
        private long discount;
        private int pointsAvailable;
        private int pointsUsed;
        private long pointsDiscount;
        private int pointValue;
        private boolean usePoints;
        private long afterDiscount;
        private long shippingFee;
        private long total;
        private long weight;
        private final List<Line> rows = new ArrayList<>();

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    private int baseInCart(Cart cart, Product p) {
        int total = 0;
        for (Map.Entry<String, Integer> e : cart.items().entrySet()) {
            if (Cart.productIdOf(e.getKey()) == p.getId()) total += p.findUnit(Cart.unitIdOf(e.getKey())).factor() * e.getValue();
        }
        return total;
    }

    public static String stockError(Product p, long baseQty) {
        if (!p.getDrugType().isSellableOnline()) return "Thuốc kiểm soát đặc biệt không bán online. Vui lòng đến trực tiếp nhà thuốc.";
        if (p.getAvailable() <= 0) return p.getName() + " đang tạm hết hàng.";
        if (baseQty > p.getAvailable()) return "Chỉ còn " + p.getAvailable() + " " + p.getUnit() + " " + p.getName() + " trong kho.";
        if (p.getMaxPerOrder() != null && p.getMaxPerOrder() > 0 && baseQty > p.getMaxPerOrder()) {
            return "Mỗi đơn chỉ được mua tối đa " + p.getMaxPerOrder() + " " + p.getUnit() + " " + p.getName() + ".";
        }
        return null;
    }

    /** Kiểm tra khả năng thêm vào giỏ; trả về lỗi hoặc null. */
    public String checkAdd(Cart cart, Product p, UnitOption unit, int addQty) {
        if (p == null || !p.isActive()) return "Sản phẩm không tồn tại.";
        stock.fill(p);
        return stockError(p, baseInCart(cart, p) + (long) unit.factor() * addQty);
    }

    public View build(Cart cart, ShippingMethod method, User user) {
        View v = new View();
        v.pointValue = settings.getInt("point_value");
        v.usePoints = cart.usePoints();
        Map<String, Integer> items = cart.items();
        Set<Long> ids = new LinkedHashSet<>();
        for (String k : items.keySet()) ids.add(Cart.productIdOf(k));
        Map<Long, Product> products = new HashMap<>();
        if (!ids.isEmpty()) {
            List<Product> list = em.createQuery("select p from Product p where p.id in :ids", Product.class).setParameter("ids", ids).getResultList();
            stock.fill(list);
            promotions.decorate(list);
            for (Product p : list) products.put(p.getId(), p);
        }
        Map<Long, Integer> baseTotals = new HashMap<>();
        List<Line> others = new ArrayList<>();
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            Product p = products.get(Cart.productIdOf(e.getKey()));
            if (p == null || !p.isActive()) continue;
            UnitOption u = p.findUnit(Cart.unitIdOf(e.getKey()));
            Line line = new Line(e.getKey(), p, u, e.getValue());
            // Chỉ dòng được tích chọn mới tính tiền / khuyến mãi / tồn kho và được đặt hàng
            if (!cart.isSelected(e.getKey())) {
                others.add(line);
                continue;
            }
            v.lines.add(line);
            baseTotals.merge(p.getId(), line.base, Integer::sum);
        }
        PromotionService.Evaluation ev = promotions.evaluate(toPromoLines(v.lines));
        Map<Long, Long> byCategory = new HashMap<>();
        for (Line line : v.lines) {
            long[] f = ev.flash.get(line.key);
            if (f != null) {
                line.promoPrice = f[0];
                line.flashPromotionId = f[1];
            }
            line.unitPrice = line.promoPrice != null ? line.promoPrice : line.unit.price();
            line.listPrice = line.unit.price();
            line.flash = line.promoPrice != null && line.promoPrice < line.unit.price();
            line.lineTotal = line.unitPrice * line.quantity;
            Product p = line.product;
            v.subtotal += line.lineTotal;
            v.weight += (long) (p.getWeightGram() > 0 ? p.getWeightGram() : 200) * line.quantity;
            if (p.getDrugType().isPrescription()) {
                v.rxRequired = true;
            } else {
                v.discountable += line.lineTotal;
                if (p.getCategoryId() != null) byCategory.merge(p.getCategoryId(), line.lineTotal, Long::sum);
            }
        }
        v.promoDiscount = Math.min(ev.combo, v.discountable);
        v.promoNotes = new ArrayList<>(ev.notes);
        for (PromotionService.Gift g : ev.gifts) {
            Product gp = stock.fill(g.product());
            if (gp.getAvailable() >= g.quantity() + baseTotals.getOrDefault(gp.getId(), 0)) v.gifts.add(g);
            else v.promoNotes.add("Quà tặng " + gp.getName() + " (" + g.promotion() + ") tạm hết hàng.");
        }
        Set<Long> reported = new HashSet<>();
        for (Line line : v.lines) {
            line.error = stockError(line.product, baseTotals.get(line.product.getId()));
            if (line.error != null && reported.add(line.product.getId())) v.errors.add(line.error);
        }
        VoucherService.Result r = vouchers.validate(cart.voucherCode(), v.discountable - v.promoDiscount, user, byCategory);
        v.voucher = r.voucher();
        v.voucherError = r.error();
        v.discount = r.discount();
        // Điểm tích lũy: chỉ trừ trên phần hàng không kê đơn (sau mã giảm giá)
        if (user != null) {
            v.pointsAvailable = user.getPoints();
            if (cart.usePoints() && v.pointValue > 0) {
                long maxByAmount = Math.max(0, v.discountable - v.promoDiscount - v.discount) / v.pointValue;
                v.pointsUsed = (int) Math.min(user.getPoints(), maxByAmount);
                v.pointsDiscount = (long) v.pointsUsed * v.pointValue;
            }
        }
        long after = v.subtotal - v.promoDiscount - v.discount - v.pointsDiscount;
        v.afterDiscount = after;
        v.shippingFee = v.lines.isEmpty() ? 0 : settings.shippingFee(method, after);
        v.total = after + v.shippingFee;

        // Toàn bộ giỏ theo thứ tự thêm vào (trang giỏ hàng hiển thị cả dòng chưa chọn)
        Map<String, long[]> flash = promotions.evaluate(toPromoLines(others)).flash;
        Map<String, Line> rows = new HashMap<>();
        for (Line l : v.lines) rows.put(l.key, l);
        for (Line o : others) {
            long[] f = flash.get(o.key);
            o.unitPrice = f != null ? f[0] : o.unit.price();
            o.listPrice = o.unit.price();
            o.flash = o.unitPrice < o.listPrice;
            o.lineTotal = o.unitPrice * o.quantity;
            o.selected = false;
            rows.put(o.key, o);
        }
        for (String key : items.keySet()) if (rows.containsKey(key)) v.rows.add(rows.get(key));
        return v;
    }

    private static List<PromotionService.Line> toPromoLines(List<Line> lines) {
        return lines.stream().map(l -> new PromotionService.Line(l.key, l.product, l.unit, l.base)).toList();
    }
}
