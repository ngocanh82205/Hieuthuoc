package com.hieuthuoc.service;

import com.hieuthuoc.entity.Product;
import com.hieuthuoc.entity.Promotion;
import com.hieuthuoc.entity.PromotionItem;
import com.hieuthuoc.entity.UnitOption;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Khuyến mãi: flash sale (giá sốc), combo (giảm tiền khi mua đủ bộ), mua X tặng Y.
 * Chỉ áp dụng cho sản phẩm không kê đơn (quy định không khuyến mại thuốc kê đơn).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {
    @PersistenceContext
    private EntityManager em;

    /** Quà tặng: sản phẩm, số lượng (đơn vị gốc), tên chương trình. */
    public record Gift(Product product, int quantity, String promotion) {
    }

    /** Dòng hàng đưa vào tính khuyến mãi. */
    public record Line(String key, Product product, UnitOption unit, int base) {
    }

    /** Kết quả: flash (key dòng => [giá, id chương trình]), combo (tiền giảm), ghi chú, quà tặng. */
    public static class Evaluation {
        public final Map<String, long[]> flash = new LinkedHashMap<>();
        public long combo;
        public final List<String> notes = new ArrayList<>();
        public final List<Gift> gifts = new ArrayList<>();
    }

    public static boolean eligible(Product p) {
        return p != null && p.getDrugType().isPromotable();
    }

    public List<Promotion> running() {
        LocalDateTime now = LocalDateTime.now();
        return em.createQuery("select distinct p from Promotion p left join fetch p.items where p.active = true and p.startAt <= :now and p.endAt > :now",
                        Promotion.class).setParameter("now", now).getResultList()
                .stream().filter(Promotion::isRunning).toList();
    }

    private static Promotion flashOf(List<Promotion> running, Long productId) {
        for (Promotion p : running) {
            Integer rem = p.remaining();
            if (Promotion.FLASH_SALE.equals(p.getType()) && Objects.equals(p.getProductId(), productId) && (rem == null || rem > 0)) return p;
        }
        return null;
    }

    /** Gắn giá flash sale / nhãn khuyến mãi để hiển thị. */
    public <T extends Collection<Product>> T decorate(T products) {
        List<Promotion> running = running();
        if (running.isEmpty()) return products;
        for (Product p : products) {
            p.setFlashPrice(null);
            p.setPromoLabels(new ArrayList<>());
            if (!eligible(p)) continue;
            Promotion f = flashOf(running, p.getId());
            if (f != null && f.getSalePrice() != null && f.getSalePrice() < p.getPrice()) {
                p.setFlashPrice(f.getSalePrice());
                p.setFlashEndsAt(f.getEndAt());
                p.setFlashRemaining(f.remaining());
            }
            for (Promotion pr : running) {
                if (Promotion.GIFT.equals(pr.getType()) && Objects.equals(pr.getProductId(), p.getId()) && pr.getGiftProduct() != null) {
                    p.getPromoLabels().add("Mua " + pr.getBuyQuantity() + " " + p.getUnit() + " tặng " + pr.getGiftQuantity() + " "
                            + pr.getGiftProduct().getUnit() + " " + pr.getGiftProduct().getName());
                }
                if (Promotion.COMBO.equals(pr.getType()) && pr.getItems().stream().anyMatch(i -> Objects.equals(i.getProductId(), p.getId()))) {
                    p.getPromoLabels().add("Combo \"" + pr.getName() + "\": giảm " + money(pr.getComboDiscount()) + "/bộ");
                }
            }
        }
        return products;
    }

    public Product decorate(Product p) {
        decorate(List.of(p));
        return p;
    }

    /** Áp khuyến mãi cho các dòng giỏ hàng. */
    public Evaluation evaluate(List<Line> lines) {
        Evaluation ev = new Evaluation();
        List<Promotion> running = running();
        if (running.isEmpty() || lines.isEmpty()) return ev;
        Map<Long, Integer> baseQty = new HashMap<>();
        for (Line l : lines) if (eligible(l.product())) baseQty.merge(l.product().getId(), l.base(), Integer::sum);
        for (Line l : lines) {
            Product p = l.product();
            if (!eligible(p)) continue;
            Promotion f = flashOf(running, p.getId());
            if (f == null || f.getSalePrice() == null || f.getSalePrice() >= p.getPrice()) continue;
            int total = baseQty.getOrDefault(p.getId(), 0);
            if (f.remaining() != null && total > f.remaining()) {
                ev.notes.add("Flash sale " + p.getName() + " chỉ còn " + f.remaining() + " " + p.getUnit() + " giá sốc - giảm số lượng để hưởng giá flash sale.");
                continue;
            }
            ev.flash.put(l.key(), new long[]{Math.min(l.unit().price(), f.getSalePrice() * l.unit().factor()), f.getId()});
        }
        Evaluation cg = combosAndGifts(baseQty, running, ev.notes);
        ev.combo = cg.combo;
        ev.gifts.addAll(cg.gifts);
        ev.notes.clear();
        ev.notes.addAll(cg.notes);
        return ev;
    }

    /** Các chương trình đang chạy tại thời điểm at (kể cả đã tắt / hết suất sau đó) - để tính lại khuyến mãi cho đơn đã đặt. */
    public List<Promotion> runningAt(LocalDateTime at) {
        return em.createQuery("select distinct p from Promotion p left join fetch p.items where p.startAt <= :at and p.endAt > :at", Promotion.class)
                .setParameter("at", at).getResultList();
    }

    /** Giảm combo và quà tặng theo số lượng (đơn vị cơ bản) từng sản phẩm được khuyến mãi. */
    public Evaluation combosAndGifts(Map<Long, Integer> baseQty, List<Promotion> promotions, List<String> notes) {
        Evaluation ev = new Evaluation();
        if (notes != null) ev.notes.addAll(notes);
        for (Promotion pr : promotions) {
            if (!Promotion.COMBO.equals(pr.getType()) || pr.getItems().isEmpty() || pr.getComboDiscount() == null || pr.getComboDiscount() == 0) continue;
            long sets = Long.MAX_VALUE;
            for (PromotionItem it : pr.getItems()) {
                sets = Math.min(sets, baseQty.getOrDefault(it.getProductId(), 0) / Math.max(1, it.getQuantity()));
            }
            if (sets > 0 && sets != Long.MAX_VALUE) {
                ev.combo += sets * pr.getComboDiscount();
                ev.notes.add("Combo \"" + pr.getName() + "\" x" + sets);
            }
        }
        for (Promotion pr : promotions) {
            if (!Promotion.GIFT.equals(pr.getType()) || pr.getProductId() == null || pr.getGiftProduct() == null
                    || pr.getBuyQuantity() == null || pr.getBuyQuantity() <= 0) continue;
            int times = baseQty.getOrDefault(pr.getProductId(), 0) / pr.getBuyQuantity();
            if (times > 0) ev.gifts.add(new Gift(pr.getGiftProduct(), times * Objects.requireNonNullElse(pr.getGiftQuantity(), 0), pr.getName()));
        }
        return ev;
    }

    static String money(Long n) {
        return java.text.NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(n == null ? 0 : n) + " ₫";
    }
}
