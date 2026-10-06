package com.hieuthuoc.api;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Dữ liệu công khai: thông tin nhà thuốc, danh mục, sản phẩm, đánh giá, bài viết, câu hỏi thường gặp. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogApi {
    private final ProductService products;
    private final CategoryService categories;
    private final CatalogService catalog;
    private final SettingService settings;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/store")
    public Map<String, Object> store() {
        Map<String, Object> s = new LinkedHashMap<>();
        for (String k : List.of("store_name", "store_address", "store_phone", "store_email", "store_hours")) s.put(k, settings.get(k));
        s.put("shippingFee", settings.getLong("shipping_fee"));
        s.put("freeShipThreshold", settings.getLong("free_ship_threshold"));
        s.put("paymentMethods", settings.enabledPaymentMethods().stream().map(m -> Map.of("code", m.name(), "label", m.getLabel())).toList());
        s.put("provinces", SettingService.PROVINCES);
        return Api.ok(s);
    }

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public Map<String, Object> categories() {
        return Api.ok(categories.tree().stream().map(Dto.CategoryDto::of).toList());
    }

    /**
     * Tìm kiếm / lọc sản phẩm đang bán.
     * Tham số: q, category (slug), type (OTC|ETC|SUPPLEMENT|DEVICE|COSMETIC), min, max, inStock, sort (bestseller|price_asc|price_desc|newest|name), page, perPage.
     */
    @GetMapping("/products")
    @Transactional(readOnly = true)
    public Map<String, Object> products(@RequestParam Map<String, String> in) {
        ProductService.Filter f = new ProductService.Filter();
        f.q = Texts.trim(in.get("q"));
        String slug = Texts.trim(in.get("category"));
        f.category = slug.isEmpty() ? null : em.createQuery("select c from Category c where c.slug = :s", Category.class).setParameter("s", slug)
                .getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Danh mục không tồn tại."));
        DrugType type = DrugType.tryFrom(in.get("type"));
        f.type = type != null ? type.name() : null;
        f.min = Texts.toLong(in.get("min"));
        f.max = Texts.toLong(in.get("max"));
        f.inStock = "1".equals(in.get("inStock")) || "true".equals(in.get("inStock"));
        f.sort = in.getOrDefault("sort", "bestseller");
        int perPage = Math.min(50, Math.max(1, Texts.toInt(in.get("perPage"), 12)));
        return Api.page(Page.of(products.search(f), perPage, Texts.toInt(in.get("page"), 1)), Dto.ProductDto::of);
    }

    private Product bySlug(String slug) {
        return em.createQuery("select p from Product p where p.slug = :s and p.active = true", Product.class).setParameter("s", slug)
                .getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Sản phẩm không tồn tại hoặc đã ngừng kinh doanh."));
    }

    @GetMapping("/products/{slug}")
    @Transactional(readOnly = true)
    public Map<String, Object> product(@PathVariable String slug) {
        Product p = products.enrich(bySlug(slug));
        List<Product> related = new ArrayList<>(catalog.equivalents(p).stream().filter(e -> e.getDrugType().isSellableOnline()).limit(6).toList());
        products.enrich(related);
        return Api.ok(new Dto.ProductDetailDto(Dto.ProductDto.of(p), p.unitOptions().stream().map(Dto.UnitDto::of).toList(), p.getDosageForm(), p.getPackaging(),
                p.getRegistrationNo(), p.getManufacturer(), p.getCountry(), p.getDescription(), p.getUsageInstruction(), p.getContraindications(),
                p.getSideEffects(), p.getMaxPerOrder(), p.getDrugType().isSellableOnline(), p.getPromoLabels(), related.stream().map(Dto.ProductDto::of).toList()));
    }

    @GetMapping("/products/{slug}/reviews")
    @Transactional(readOnly = true)
    public Map<String, Object> reviews(@PathVariable String slug, @RequestParam(defaultValue = "1") int page) {
        Product p = bySlug(slug);
        List<Review> list = em.createQuery("select r from Review r join fetch r.user where r.product.id = :p and r.hidden = false order by r.createdAt desc", Review.class)
                .setParameter("p", p.getId()).getResultList();
        return Api.page(Page.of(list, 10, page), Dto.ReviewDto::of);
    }

    @GetMapping("/posts")
    @Transactional(readOnly = true)
    public Map<String, Object> posts(@RequestParam(defaultValue = "1") int page) {
        List<Post> list = em.createQuery("select p from Post p where p.published = true order by p.createdAt desc", Post.class).getResultList();
        return Api.page(Page.of(list, 9, page), p -> Dto.PostDto.of(p, false));
    }

    @GetMapping("/posts/{slug}")
    @Transactional(readOnly = true)
    public Map<String, Object> post(@PathVariable String slug) {
        Post p = em.createQuery("select p from Post p where p.slug = :s and p.published = true", Post.class).setParameter("s", slug)
                .getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Bài viết không tồn tại."));
        return Api.ok(Dto.PostDto.of(p, true));
    }

    @GetMapping("/faqs")
    @Transactional(readOnly = true)
    public Map<String, Object> faqs() {
        return Api.ok(em.createQuery("select f from Faq f where f.active = true order by f.sortOrder, f.id", Faq.class).getResultList().stream().map(Dto.FaqDto::of).toList());
    }
}
