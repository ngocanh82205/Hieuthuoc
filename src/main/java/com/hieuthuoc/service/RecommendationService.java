package com.hieuthuoc.service;

import com.hieuthuoc.entity.DrugType;
import com.hieuthuoc.entity.Product;
import com.hieuthuoc.entity.ProductView;
import com.hieuthuoc.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Gợi ý sản phẩm:
 * - "Có thể bạn quan tâm": theo danh mục / hoạt chất khách đã xem và đã mua gần đây.
 * - "Thường được mua cùng": sản phẩm xuất hiện chung đơn hàng.
 * - "Sản phẩm tương tự": cùng danh mục, cùng hoạt chất.
 * Không gợi ý thuốc kê đơn / kiểm soát đặc biệt (không quảng cáo thuốc kê đơn).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {
    private static final List<DrugType> NOT_PROMOTABLE = List.of(DrugType.ETC, DrugType.SPECIAL);

    private final ProductService products;
    private final Sql sql;

    @PersistenceContext
    private EntityManager em;

    /** Ghi lượt xem sản phẩm của khách; bỏ qua nhân viên, không ghi lặp trong 10 phút. */
    @Transactional
    public void recordView(Product p, User user, String sessionId) {
        if (user != null && user.isStaff()) return;
        String jpql = "select count(v) from ProductView v where v.productId = :p and v.viewedAt >= :t and "
                + (user != null ? "v.userId = :u" : "v.sessionId = :u");
        long recent = em.createQuery(jpql, Long.class).setParameter("p", p.getId()).setParameter("t", LocalDateTime.now().minusMinutes(10))
                .setParameter("u", user != null ? user.getId() : sessionId).getSingleResult();
        if (recent == 0) {
            ProductView v = new ProductView();
            v.setUserId(user != null ? user.getId() : null);
            v.setSessionId(sessionId);
            v.setProduct(p);
            em.persist(v);
        }
    }

    private List<Product> byIdsOrdered(List<Long> ids, boolean promotableOnly) {
        if (ids.isEmpty()) return new ArrayList<>();
        String jpql = "select p from Product p where p.active = true and p.id in :ids" + (promotableOnly ? " and p.drugType not in :np" : "");
        var q = em.createQuery(jpql, Product.class).setParameter("ids", ids);
        if (promotableOnly) q.setParameter("np", NOT_PROMOTABLE);
        List<Product> list = new ArrayList<>(q.getResultList());
        list.sort(Comparator.comparingInt(p -> ids.indexOf(p.getId())));
        return list;
    }

    /** Sản phẩm đã xem gần đây (mới nhất trước). */
    public List<Product> recentlyViewed(User user, String sessionId, int limit, Long exceptId) {
        Map<String, Object> p = new HashMap<>();
        p.put("u", user != null ? user.getId() : sessionId);
        String where = user != null ? "user_id = :u" : "session_id = :u";
        if (exceptId != null) {
            where += " and product_id <> :ex";
            p.put("ex", exceptId);
        }
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> r : sql.rows("select product_id, max(viewed_at) as last_view from product_views where " + where
                + " group by product_id order by last_view desc limit " + limit, p)) ids.add(Sql.toLong(r.get("product_id")));
        return products.enrich(byIdsOrdered(ids, false));
    }

    public List<Product> forUser(User user, String sessionId, int limit) {
        List<Long> viewedIds = em.createQuery("select v.productId from ProductView v where " + (user != null ? "v.userId = :u" : "v.sessionId = :u")
                        + " and v.viewedAt >= :t", Long.class)
                .setParameter("u", user != null ? user.getId() : sessionId).setParameter("t", LocalDateTime.now().minusDays(60)).getResultList();
        List<Long> boughtIds = user == null ? List.of() : sql.rows("select oi.product_id from order_items oi join orders o on o.id = oi.order_id"
                        + " where o.user_id = :u and o.status not in ('CANCELLED','RX_REJECTED')", Map.of("u", user.getId()))
                .stream().map(r -> Sql.toLong(r.get("product_id"))).toList();
        Set<Long> seed = new LinkedHashSet<>(viewedIds);
        seed.addAll(boughtIds);
        if (seed.isEmpty()) return products.bestSellers(limit);
        List<Product> seedProducts = em.createQuery("select p from Product p where p.id in :ids", Product.class).setParameter("ids", seed).getResultList();
        Map<Long, Integer> cats = new HashMap<>();
        Set<String> ings = new HashSet<>();
        for (Product sp : seedProducts) {
            if (sp.getCategoryId() != null) cats.merge(sp.getCategoryId(), 1, Integer::sum);
            for (String i : Texts.splitIngredients(sp.getActiveIngredient())) ings.add(i.toLowerCase());
        }
        Map<Long, Long> together = boughtTogetherIds(new ArrayList<>(seed), 20);
        String jpql = "select p from Product p where p.active = true and p.drugType not in :np" + (boughtIds.isEmpty() ? "" : " and p.id not in :bought");
        var q = em.createQuery(jpql, Product.class).setParameter("np", NOT_PROMOTABLE);
        if (!boughtIds.isEmpty()) q.setParameter("bought", boughtIds);
        List<Object[]> scoredList = new ArrayList<>();
        for (Product p : q.getResultList()) {
            int score = cats.getOrDefault(p.getCategoryId(), 0) * 3;
            for (String i : Texts.splitIngredients(p.getActiveIngredient())) if (ings.contains(i.toLowerCase())) score += 4;
            score += (int) (together.getOrDefault(p.getId(), 0L) * 5);
            if (viewedIds.contains(p.getId())) score -= 2; // đã xem rồi: ưu tiên sản phẩm mới hơn
            if (score > 0) scoredList.add(new Object[]{p, score});
        }
        scoredList.sort((a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
        List<Product> scored = new ArrayList<>(scoredList.stream().limit(limit * 2L).map(x -> (Product) x[0]).toList());
        products.enrich(scored);
        List<Product> list = new ArrayList<>(scored.stream().filter(p -> p.getAvailable() > 0).limit(limit).toList());
        if (list.size() < limit) {
            for (Product m : products.bestSellers(limit * 2)) {
                if (list.size() >= limit) break;
                if (list.stream().noneMatch(x -> x.getId().equals(m.getId())) && !boughtIds.contains(m.getId())) list.add(m);
            }
        }
        return list;
    }

    /** product_id => số đơn mua chung với các sản phẩm ids. */
    private Map<Long, Long> boughtTogetherIds(List<Long> ids, int limit) {
        if (ids.isEmpty()) return new LinkedHashMap<>();
        List<Long> orderIds = sql.rows("select distinct order_id from order_items where product_id in (:ids) limit 500", Map.of("ids", ids))
                .stream().map(r -> Sql.toLong(r.get("order_id"))).toList();
        if (orderIds.isEmpty()) return new LinkedHashMap<>();
        Map<Long, Long> out = new LinkedHashMap<>();
        for (Map<String, Object> r : sql.rows("select product_id, count(distinct order_id) as cnt from order_items where order_id in (:o)"
                + " and product_id not in (:ids) and is_gift = 0 group by product_id order by cnt desc limit " + limit, Map.of("o", orderIds, "ids", ids))) {
            out.put(Sql.toLong(r.get("product_id")), Sql.toLong(r.get("cnt")));
        }
        return out;
    }

    public List<Product> boughtTogether(Product p, int limit) {
        List<Long> ids = new ArrayList<>(boughtTogetherIds(List.of(p.getId()), limit * 2).keySet());
        if (ids.isEmpty()) return new ArrayList<>();
        List<Product> list = byIdsOrdered(ids, true);
        return products.enrich(new ArrayList<>(list.subList(0, Math.min(limit, list.size()))));
    }

    public List<Product> similar(Product p, int limit) {
        String jpql = "select x from Product x where x.active = true and x.id <> :id and x.drugType <> :sp and (x.category.id = :cat"
                + (p.getActiveIngredient() != null && !p.getActiveIngredient().isEmpty() ? " or x.activeIngredient = :ai" : "") + ")";
        var q = em.createQuery(jpql, Product.class).setParameter("id", p.getId()).setParameter("sp", DrugType.SPECIAL)
                .setParameter("cat", p.getCategoryId() == null ? -1L : p.getCategoryId());
        if (p.getActiveIngredient() != null && !p.getActiveIngredient().isEmpty()) q.setParameter("ai", p.getActiveIngredient());
        return products.enrich(new ArrayList<>(q.setMaxResults(limit).getResultList()));
    }
}
