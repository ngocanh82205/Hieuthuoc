package com.hieuthuoc.service;

import com.hieuthuoc.entity.MemberTier;
import com.hieuthuoc.entity.Product;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.entity.WishlistItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Chăm sóc khách hàng: yêu thích, hạng thành viên. */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerCareService {
    private final CustomerService customers;
    private final SettingService settings;

    @PersistenceContext
    private EntityManager em;

    public record TierInfo(long spent, MemberTier tier, MemberTier next, long toNext, int progress) {
    }

    public boolean toggleWishlist(User user, long productId) {
        Product p = em.find(Product.class, productId);
        if (p == null) throw BusinessException.notFound("Sản phẩm không tồn tại.");
        List<WishlistItem> l = em.createQuery("select w from WishlistItem w where w.userId = :u and w.product.id = :p", WishlistItem.class)
                .setParameter("u", user.getId()).setParameter("p", p.getId()).getResultList();
        if (!l.isEmpty()) {
            em.remove(l.get(0));
            return false;
        }
        WishlistItem w = new WishlistItem();
        w.setUserId(user.getId());
        w.setProduct(p);
        em.persist(w);
        return true;
    }

    /* ---------------- Hạng thành viên ---------------- */

    @Transactional(readOnly = true)
    public TierInfo tierOf(User user) {
        settings.all();
        long spent = customers.totalSpent(user);
        MemberTier tier = MemberTier.of(spent);
        MemberTier next = tier.next();
        return new TierInfo(spent, tier, next, next != null ? Math.max(0, next.getMinSpent() - spent) : 0,
                next != null ? (int) Math.round(100.0 * (spent - tier.getMinSpent()) / Math.max(1, next.getMinSpent() - tier.getMinSpent())) : 100);
    }
}
