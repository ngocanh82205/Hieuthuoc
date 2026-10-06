package com.hieuthuoc.service;

import com.hieuthuoc.entity.Category;
import com.hieuthuoc.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final StockService stock;
    private final CategoryService categories;
    private final PromotionService promotions;
    private final Sql sql;

    @PersistenceContext
    private EntityManager em;

    /** Bộ lọc tìm kiếm. */
    public static class Filter {
        public String q;
        public Category category;
        public String type;
        public Long min;
        public Long max;
        public boolean inStock;
        public String sort;
        public String brand;
        public String country;
        public String form;
        public boolean activeOnly = true;
    }

    /** Điền tồn kho, điểm đánh giá, số lượng đã bán, khuyến mãi. */
    public <T extends Collection<Product>> T enrich(T products) {
        if (products.isEmpty()) return products;
        List<Long> ids = products.stream().map(Product::getId).toList();
        stock.fill(products);
        Map<Long, double[]> ratings = new HashMap<>();
        for (Map<String, Object> r : sql.rows("select product_id as pid, avg(rating) as avg_rating, count(*) as cnt from reviews where hidden = 0"
                + " and product_id in (:ids) group by product_id", Map.of("ids", ids))) {
            ratings.put(Sql.toLong(r.get("pid")), new double[]{Sql.toDouble(r.get("avg_rating")), Sql.toDouble(r.get("cnt"))});
        }
        Map<Long, Long> sold = sql.longMap("select oi.product_id as pid, sum(oi.quantity * oi.unit_factor) as qty from order_items oi"
                + " join orders o on o.id = oi.order_id where o.status = 'COMPLETED' and oi.product_id in (:ids) group by oi.product_id", Map.of("ids", ids));
        for (Product p : products) {
            double[] r = ratings.get(p.getId());
            p.setAvgRating(r != null ? Math.round(r[0] * 10) / 10.0 : null);
            p.setReviewCount(r != null ? (long) r[1] : 0);
            p.setSold(sold.getOrDefault(p.getId(), 0L));
        }
        promotions.decorate(products);
        return products;
    }

    public Product enrich(Product p) {
        enrich(new ArrayList<>(List.of(p)));
        return p;
    }

    /** Tìm kiếm & lọc sản phẩm. */
    public List<Product> search(Filter f) {
        StringBuilder jpql = new StringBuilder("select p from Product p where 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (f.activeOnly) jpql.append(" and p.active = true");
        String kw = Texts.trim(f.q);
        if (!kw.isEmpty()) {
            jpql.append(" and (lower(p.name) like :kw or lower(p.activeIngredient) like :kw or lower(p.description) like :kw"
                    + " or lower(p.usageInstruction) like :kw or lower(p.manufacturer) like :kw or lower(p.registrationNo) like :kw)");
            params.put("kw", "%" + kw.toLowerCase() + "%");
        }
        if (f.category != null) {
            jpql.append(" and p.category.id in :cats");
            params.put("cats", categories.descendantIds(f.category));
        }
        if (f.type != null && !f.type.isEmpty()) {
            jpql.append(" and p.drugType = :type");
            params.put("type", com.hieuthuoc.entity.DrugType.tryFrom(f.type));
        }
        if (f.brand != null && !f.brand.isEmpty()) {
            jpql.append(" and p.manufacturer = :brand");
            params.put("brand", f.brand);
        }
        if (f.country != null && !f.country.isEmpty()) {
            jpql.append(" and p.country = :country");
            params.put("country", f.country);
        }
        if (f.form != null && !f.form.isEmpty()) {
            jpql.append(" and p.dosageForm = :form");
            params.put("form", f.form);
        }
        if (f.min != null) {
            jpql.append(" and p.price >= :min");
            params.put("min", f.min);
        }
        if (f.max != null) {
            jpql.append(" and p.price <= :max");
            params.put("max", f.max);
        }
        var q = em.createQuery(jpql.toString(), Product.class);
        params.forEach(q::setParameter);
        List<Product> list = new ArrayList<>(q.getResultList());
        // Tìm không dấu: "thuoc ha sot" vẫn ra "thuốc hạ sốt"
        if (!kw.isEmpty() && list.isEmpty()) {
            String needle = Texts.vnAscii(kw);
            List<Product> all = em.createQuery("select p from Product p" + (f.activeOnly ? " where p.active = true" : ""), Product.class).getResultList();
            for (Product p : all) {
                String hay = Texts.vnAscii(String.join(" ", Objects.requireNonNullElse(p.getName(), ""), Objects.requireNonNullElse(p.getActiveIngredient(), ""),
                        Objects.requireNonNullElse(p.getDescription(), ""), Objects.requireNonNullElse(p.getUsageInstruction(), ""),
                        Objects.requireNonNullElse(p.getManufacturer(), ""), p.getCategory() != null ? p.getCategory().getName() : ""));
                boolean ok = true;
                for (String w : needle.split("\\s+")) if (!w.isEmpty() && !hay.contains(w)) ok = false;
                if (ok) list.add(p);
            }
        }
        enrich(list);
        if (f.inStock) list.removeIf(p -> p.getAvailable() <= 0);
        String sort = f.sort == null ? "" : f.sort;
        switch (sort) {
            case "price_asc" -> list.sort(Comparator.comparingLong(Product::effectivePrice));
            case "price_desc" -> list.sort(Comparator.comparingLong(Product::effectivePrice).reversed());
            case "name" -> list.sort(Comparator.comparing(p -> p.getName().toLowerCase()));
            case "newest" -> list.sort(Comparator.comparing(Product::getId).reversed());
            case "rating" -> list.sort(Comparator.comparing((Product p) -> p.getAvgRating() == null ? 0 : p.getAvgRating())
                    .thenComparingLong(Product::getReviewCount).reversed());
            default -> list.sort(Comparator.comparingLong(Product::getSold).reversed().thenComparing(Product::getId));
        }
        return list;
    }

    /** Sản phẩm bán chạy. */
    public List<Product> bestSellers(int limit) {
        Filter f = new Filter();
        f.sort = "bestseller";
        return search(f).stream().filter(p -> p.getDrugType().isSellableOnline()).limit(limit).toList();
    }
}
