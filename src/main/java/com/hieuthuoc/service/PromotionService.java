package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Tính khuyến mãi: flash sale (giá sốc), combo (giảm tiền khi mua đủ bộ), mua X tặng Y.
 * Chỉ áp dụng cho sản phẩm không kê đơn (quy định không khuyến mại thuốc kê đơn).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {
    private final PromotionRepository promotionRepo;

    public static boolean eligible(Product p) {
        return p != null && !p.getDrugType().isPrescription() && p.getDrugType().isSellableOnline();
    }

    public List<Promotion> running() {
        return promotionRepo.findByActiveTrue().stream().filter(Promotion::isRunning).toList();
    }

    private static Promotion flashOf(Long productId, List<Promotion> running) {
        for (Promotion pr : running) {
            if (Promotion.FLASH_SALE.equals(pr.getType()) && pr.getProduct() != null && pr.getProduct().getId().equals(productId)
                    && (pr.getRemaining() == null || pr.getRemaining() > 0)) return pr;
        }
        return null;
    }

    /** Gắn thông tin khuyến mãi để hiển thị (giá flash sale, nhãn "Mua X tặng Y", combo). */
    public <T extends Collection<Product>> T decorate(T products) {
        List<Promotion> running = running();
        if (running.isEmpty()) return products;
        for (Product p : products) {
            p.setFlashPrice(null);
            p.getPromoLabels().clear();
            if (!eligible(p)) continue;
            Promotion f = flashOf(p.getId(), running);
            if (f != null && f.getSalePrice() < p.getPrice()) {
                p.setFlashPrice(f.getSalePrice());
                p.setFlashEndsAt(f.getEndAt());
                p.setFlashRemaining(f.getRemaining());
            }
            for (Promotion pr : running) {
                if (Promotion.GIFT.equals(pr.getType()) && pr.getProduct() != null && pr.getProduct().getId().equals(p.getId())) {
                    p.getPromoLabels().add("Mua " + pr.getBuyQuantity() + " " + p.getUnit() + " tặng " + pr.getGiftQuantity() + " " + pr.getGiftProduct().getUnit()
                            + " " + pr.getGiftProduct().getName());
                }
                if (Promotion.COMBO.equals(pr.getType()) && pr.getItems().stream().anyMatch(i -> i.getProduct().getId().equals(p.getId()))) {
                    p.getPromoLabels().add("Combo \"" + pr.getName() + "\": giảm " + String.format("%,d", pr.getComboDiscount()).replace(',', '.') + " đ/bộ");
                }
            }
        }
        return products;
    }

    public record Gift(Product product, int quantity, String promotion) {
    }

    /** Kết quả áp khuyến mãi cho giỏ hàng. */
    public static class Evaluation {
        /** key dòng giỏ -> [giá đơn vị sau flash sale, id chương trình] */
        public final Map<String, long[]> flash = new HashMap<>();
        public long comboDiscount;
        public final List<String> notes = new ArrayList<>();
        public final List<Gift> gifts = new ArrayList<>();
    }

    /** lines: key -> (sản phẩm, đơn vị, số lượng). */
    public Evaluation evaluate(List<CartService.Line> lines) {
        Evaluation ev = new Evaluation();
        List<Promotion> running = running();
        if (running.isEmpty() || lines.isEmpty()) return ev;
        Map<Long, Long> baseQty = new HashMap<>();
        Map<Long, Product> products = new HashMap<>();
        for (CartService.Line l : lines) {
            if (!eligible(l.getProduct())) continue;
            baseQty.merge(l.getProduct().getId(), (long) l.getBaseQuantity(), Long::sum);
            products.put(l.getProduct().getId(), l.getProduct());
        }
        // Flash sale
        for (CartService.Line l : lines) {
            Product p = l.getProduct();
            if (!eligible(p)) continue;
            Promotion f = flashOf(p.getId(), running);
            if (f == null || f.getSalePrice() >= p.getPrice()) continue;
            long total = baseQty.getOrDefault(p.getId(), 0L);
            if (f.getRemaining() != null && total > f.getRemaining()) {
                ev.notes.add("Flash sale " + p.getName() + " chỉ còn " + f.getRemaining() + " " + p.getUnit() + " giá sốc - giảm số lượng để hưởng giá flash sale.");
                continue;
            }
            long unitPrice = Math.min(l.getUnit().price(), f.getSalePrice() * l.getUnit().factor());
            ev.flash.put(l.getKey(), new long[]{unitPrice, f.getId()});
        }
        // Combo
        for (Promotion pr : running) {
            if (!Promotion.COMBO.equals(pr.getType()) || pr.getItems().isEmpty() || pr.getComboDiscount() == null) continue;
            long sets = Long.MAX_VALUE;
            for (PromotionItem it : pr.getItems()) sets = Math.min(sets, baseQty.getOrDefault(it.getProduct().getId(), 0L) / Math.max(1, it.getQuantity()));
            if (sets > 0 && sets != Long.MAX_VALUE) {
                ev.comboDiscount += sets * pr.getComboDiscount();
                ev.notes.add("Combo \"" + pr.getName() + "\" x" + sets);
            }
        }
        // Mua X tặng Y
        for (Promotion pr : running) {
            if (!Promotion.GIFT.equals(pr.getType()) || pr.getProduct() == null || pr.getGiftProduct() == null) continue;
            long bought = baseQty.getOrDefault(pr.getProduct().getId(), 0L);
            long times = pr.getBuyQuantity() == null || pr.getBuyQuantity() <= 0 ? 0 : bought / pr.getBuyQuantity();
            if (times > 0) ev.gifts.add(new Gift(pr.getGiftProduct(), (int) (times * pr.getGiftQuantity()), pr.getName()));
        }
        return ev;
    }
}
