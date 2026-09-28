package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.OrderItemRepository;
import com.hieuthuoc.repository.ProductRepository;
import com.hieuthuoc.repository.ReviewRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepo;
    private final ReviewRepository reviewRepo;
    private final OrderItemRepository orderItemRepo;
    private final StockService stockService;

    public record Filter(String q, Category category, DrugType type, Long min, Long max, boolean inStock, String sort, boolean activeOnly,
                         String brand, String country, String dosageForm) {
        public Filter(String q, Category category, DrugType type, Long min, Long max, boolean inStock, String sort, boolean activeOnly) {
            this(q, category, type, min, max, inStock, sort, activeOnly, null, null, null);
        }
    }

    /** Điền tồn kho, điểm đánh giá, số lượng đã bán. */
    public <T extends Collection<Product>> T enrich(T products) {
        stockService.fill(products);
        Map<Long, Double> ratings = new HashMap<>();
        for (Object[] r : reviewRepo.averageByProduct()) ratings.put((Long) r[0], ((Number) r[1]).doubleValue());
        Map<Long, Long> sold = new HashMap<>();
        for (Object[] r : orderItemRepo.sumQuantityByProduct(EnumSet.of(OrderStatus.COMPLETED))) sold.put((Long) r[0], ((Number) r[1]).longValue());
        for (Product p : products) {
            Double avg = ratings.get(p.getId());
            p.setAvgRating(avg == null ? null : Math.round(avg * 10) / 10.0);
            p.setSold(sold.getOrDefault(p.getId(), 0L));
        }
        return products;
    }

    public Product enrich(Product p) {
        enrich(List.of(p));
        return p;
    }

    public List<Product> search(Filter f) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.activeOnly()) ps.add(cb.isTrue(root.get("active")));
            if (!Texts.isBlank(f.q())) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("activeIngredient")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("usageInstruction")), like),
                        cb.like(cb.lower(root.get("manufacturer")), like),
                        cb.like(cb.lower(root.get("registrationNo")), like)));
            }
            if (f.category() != null) ps.add(cb.equal(root.get("category"), f.category()));
            if (f.type() != null) ps.add(cb.equal(root.get("drugType"), f.type()));
            if (!Texts.isBlank(f.brand())) ps.add(cb.equal(root.get("manufacturer"), f.brand()));
            if (!Texts.isBlank(f.country())) ps.add(cb.equal(root.get("country"), f.country()));
            if (!Texts.isBlank(f.dosageForm())) ps.add(cb.equal(root.get("dosageForm"), f.dosageForm()));
            if (f.min() != null) ps.add(cb.ge(root.get("price"), f.min()));
            if (f.max() != null) ps.add(cb.le(root.get("price"), f.max()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<Product> list = enrich(new ArrayList<>(productRepo.findAll(spec)));
        if (f.inStock()) list.removeIf(p -> p.getAvailable() <= 0);
        Comparator<Product> cmp = switch (f.sort() == null ? "" : f.sort()) {
            case "price_asc" -> Comparator.comparingLong(Product::getPrice);
            case "price_desc" -> Comparator.comparingLong(Product::getPrice).reversed();
            case "name" -> Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER);
            case "newest" -> Comparator.comparing(Product::getId).reversed();
            default -> Comparator.comparingLong(Product::getSold).reversed().thenComparing(Product::getId);
        };
        list.sort(cmp);
        return list;
    }

    public static <T> Page<T> page(List<T> list, int page, int size) {
        int total = list.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) size));
        int p = Math.min(Math.max(page, 1), pages) - 1;
        int from = p * size;
        return new PageImpl<>(list.subList(from, Math.min(from + size, total)), PageRequest.of(p, size), total);
    }
}
