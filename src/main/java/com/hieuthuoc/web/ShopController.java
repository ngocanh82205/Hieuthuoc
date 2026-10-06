package com.hieuthuoc.web;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

/** Cửa hàng (public): trang chủ, danh sách & chi tiết sản phẩm, bài viết, trang tĩnh, FAQ. */
@Controller
@RequiredArgsConstructor
public class ShopController {
    private final ProductService products;
    private final CategoryService categories;
    private final RecommendationService recommend;
    private final CatalogService catalog;
    private final CurrentUser currentUser;
    private final SettingService settings;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/")
    @Transactional(readOnly = true)
    public String home(HttpSession session, Model model) {
        ProductService.Filter f = new ProductService.Filter();
        f.sort = "bestseller";
        List<Product> all = products.search(f);
        List<Product> sellable = all.stream().filter(p -> p.getDrugType().isSellableOnline()).toList();
        List<Product> flash = sellable.stream().filter(Product::isFlashSale).sorted(Comparator.comparingInt(Product::flashPercent).reversed()).limit(8).toList();
        List<Product> onSale = !flash.isEmpty() ? flash : sellable.stream().filter(p -> p.isOnSale() && !p.getDrugType().isPrescription())
                .sorted(Comparator.comparingInt(Product::discountPercent).reversed()).limit(4).toList();
        // Đếm cả sản phẩm của danh mục con cháu
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Category> cats = new HashMap<>();
        for (Category c : em.createQuery("select c from Category c", Category.class).getResultList()) cats.put(c.getId(), c);
        for (Product p : all) {
            Set<Long> seen = new HashSet<>();
            for (Category c = p.getCategory() != null ? cats.get(p.getCategory().getId()) : null; c != null && seen.add(c.getId());
                 c = c.getParentId() != null ? cats.get(c.getParentId()) : null) {
                counts.merge(c.getId(), 1, Integer::sum);
            }
        }
        User user = currentUser.getOrNull();
        boolean staff = user != null && user.isStaff();
        model.addAttribute("title", "Trang chủ");
        model.addAttribute("metaDescription", "Nhà thuốc trực tuyến " + settings.get("store_name")
                + " đạt chuẩn GPP: thuốc chính hãng, dược sĩ tư vấn miễn phí, giao hàng nhanh toàn quốc.");
        model.addAttribute("bestSellers", sellable.stream().limit(8).toList());
        model.addAttribute("onSale", onSale);
        LocalDateTime flashEndsAt = flash.stream().map(Product::getFlashEndsAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        model.addAttribute("flashEndsAt", flashEndsAt);
        model.addAttribute("flashEnd", flashEndsAt != null ? flashEndsAt.withNano(0).toString() : null);
        model.addAttribute("newest", sellable.stream().sorted(Comparator.comparing(Product::getId).reversed()).limit(8).toList());
        model.addAttribute("categoryCounts", counts);
        model.addAttribute("posts", em.createQuery("select p from Post p where p.published = true order by p.createdAt desc", Post.class).setMaxResults(3).getResultList());
        model.addAttribute("recommended", staff ? List.of() : recommend.forUser(user, session.getId(), 8));
        List<Product> recent = staff ? List.of() : recommend.recentlyViewed(user, session.getId(), 8, null);
        model.addAttribute("recentlyViewed", recent.stream().limit(4).toList());
        return "shop/home";
    }

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public String products(@RequestParam MultiValueMap<String, String> params, Model model) {
        Form in = new Form(params);
        String catSlug = Texts.trim(in.get("category"));
        Category cat = catSlug.isEmpty() ? null : em.createQuery("select c from Category c where c.slug = :s", Category.class)
                .setParameter("s", catSlug).getResultStream().findFirst().orElse(null);
        DrugType type = DrugType.tryFrom(in.get("type"));
        String q = Texts.trim(in.get("q"));
        String sort = in.get("sort", "bestseller");
        ProductService.Filter f = new ProductService.Filter();
        f.q = q;
        f.category = cat;
        f.type = type != null ? type.name() : null;
        f.min = Texts.toLong(in.get("min"));
        f.max = Texts.toLong(in.get("max"));
        f.inStock = in.bool("instock");
        f.sort = sort;
        f.brand = in.get("brand");
        f.country = in.get("country");
        f.form = in.get("form");
        List<Product> list = products.search(f);
        model.addAttribute("title", cat != null ? cat.getName() : (!q.isEmpty() ? "Tìm kiếm: " + q : "Tất cả sản phẩm"));
        model.addAttribute("metaDescription", cat != null ? cat.getMetaDescription() : null);
        model.addAttribute("page", Page.of(list, 12, in.intVal("page", 1)));
        model.addAttribute("category", cat);
        model.addAttribute("categoryPath", cat != null ? categories.path(cat) : List.of());
        model.addAttribute("subCategories", cat != null ? categories.children(cat) : List.of());
        model.addAttribute("q", q);
        model.addAttribute("type", type);
        model.addAttribute("sort", sort);
        model.addAttribute("brands", distinct("manufacturer"));
        model.addAttribute("countries", distinct("country"));
        model.addAttribute("forms", distinct("dosageForm"));
        model.addAttribute("drugTypes", DrugType.values());
        return "shop/products";
    }

    private List<String> distinct(String field) {
        return em.createQuery("select distinct p." + field + " from Product p where p.active = true and p." + field + " is not null and p." + field
                + " <> '' order by p." + field, String.class).getResultList();
    }

    /** Gợi ý khi gõ ô tìm kiếm (JSON). */
    @GetMapping("/products/suggest")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<Map<String, String>> suggest(@RequestParam(required = false) String q) {
        String s = Texts.trim(q);
        if (Texts.mbLen(s) < 2) return List.of();
        String like = "%" + s.toLowerCase() + "%";
        return em.createQuery("select p from Product p where p.active = true and (lower(p.name) like :l or lower(p.activeIngredient) like :l) order by p.name", Product.class)
                .setParameter("l", like).setMaxResults(8).getResultList().stream()
                .map(p -> Map.of("name", p.getName(), "url", "/products/" + p.getSlug(), "price", OrderService.money(p.getPrice()) + "/" + p.getUnit()))
                .toList();
    }

    @GetMapping("/products/{slug}")
    @Transactional
    public String product(@PathVariable String slug, HttpSession session, Model model) {
        Product p = em.createQuery("select p from Product p where p.slug = :s and p.active = true", Product.class).setParameter("s", slug)
                .getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Sản phẩm không tồn tại hoặc đã ngừng kinh doanh."));
        products.enrich(p);
        User u = currentUser.getOrNull();
        recommend.recordView(p, u, session.getId());
        boolean isCustomer = u != null && u.isCustomer();
        List<Product> equivalents = new ArrayList<>(catalog.equivalents(p).stream().filter(e -> e.getDrugType().isSellableOnline()).limit(8).toList());
        products.enrich(equivalents);
        boolean canReview = isCustomer && hasPurchased(u.getId(), p.getId())
                && em.createQuery("select count(r) from Review r where r.product.id = :p and r.user.id = :u", Long.class)
                .setParameter("p", p.getId()).setParameter("u", u.getId()).getSingleResult() == 0;
        String desc = p.getName() + (p.getActiveIngredient() != null ? " (" + p.getActiveIngredient() + ")" : "") + ". " + Objects.toString(p.getDescription(), "");
        model.addAttribute("title", p.getMetaTitle() != null && !p.getMetaTitle().isBlank() ? p.getMetaTitle() : p.getName());
        model.addAttribute("metaDescription", p.getMetaDescription() != null && !p.getMetaDescription().isBlank() ? p.getMetaDescription() : Texts.limit(desc, 160, ""));
        model.addAttribute("ogImage", p.imageUrl());
        model.addAttribute("product", p);
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "Product");
        ld.put("name", p.getName());
        ld.put("description", p.getDescription());
        ld.put("brand", p.getManufacturer());
        ld.put("sku", p.getRegistrationNo());
        ld.put("image", p.imageUrl());
        ld.put("offers", Map.of("@type", "Offer", "priceCurrency", "VND", "price", p.effectivePrice(),
                "availability", p.getAvailable() > 0 ? "https://schema.org/InStock" : "https://schema.org/OutOfStock"));
        if (p.getAvgRating() != null) {
            ld.put("aggregateRating", Map.of("@type", "AggregateRating", "ratingValue", p.getAvgRating(), "reviewCount", p.getReviewCount()));
        }
        try {
            // Escape "</" để chuỗi JSON không thể đóng thẻ <script>
            model.addAttribute("jsonLd", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ld).replace("</", "<\\/"));
        } catch (Exception e) {
            model.addAttribute("jsonLd", "{}");
        }
        model.addAttribute("flashEnd", p.getFlashEndsAt() != null ? p.getFlashEndsAt().withNano(0).toString() : null);
        model.addAttribute("reviews", em.createQuery("select r from Review r join fetch r.user where r.product.id = :p and r.hidden = false order by r.createdAt desc", Review.class)
                .setParameter("p", p.getId()).getResultList());
        model.addAttribute("equivalents", equivalents);
        model.addAttribute("related", recommend.similar(p, 4));
        model.addAttribute("boughtTogether", recommend.boughtTogether(p, 4));
        model.addAttribute("recentlyViewed", u != null && u.isStaff() ? List.of() : recommend.recentlyViewed(u, session.getId(), 6, p.getId()));
        model.addAttribute("canReview", canReview);
        model.addAttribute("categoryPath", p.getCategory() != null ? categories.path(p.getCategory()) : List.of());
        return "shop/product";
    }

    private boolean hasPurchased(Long userId, Long productId) {
        return em.createQuery("select count(oi) from OrderItem oi where oi.order.user.id = :u and oi.product.id = :p and oi.order.status = :s", Long.class)
                .setParameter("u", userId).setParameter("p", productId).setParameter("s", OrderStatus.COMPLETED).getSingleResult() > 0;
    }

    /** Đánh giá: chỉ khách đã mua và đơn đã giao thành công. */
    @PostMapping("/products/{slug}/reviews")
    @Transactional
    public String review(@PathVariable String slug, @RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        Product p = em.createQuery("select p from Product p where p.slug = :s and p.active = true", Product.class).setParameter("s", slug)
                .getResultStream().findFirst().orElseThrow(BusinessException::notFound);
        User u = currentUser.get();
        if (!hasPurchased(u.getId(), p.getId())) {
            throw new BusinessException("Bạn chỉ có thể đánh giá sản phẩm đã mua và giao hàng thành công.");
        }
        Form f = new Form(params);
        Validator.of(f).required("rating", "Vui lòng chọn số sao từ 1 đến 5.").rule("rating", Texts.trim(f.get("rating")).matches("[1-5]"), "Vui lòng chọn số sao từ 1 đến 5.")
                .max("comment", 1000).check();
        if (em.createQuery("select count(r) from Review r where r.product.id = :p and r.user.id = :u", Long.class)
                .setParameter("p", p.getId()).setParameter("u", u.getId()).getSingleResult() > 0) {
            throw new BusinessException("Bạn đã đánh giá sản phẩm này.");
        }
        Long orderId = em.createQuery("select o.id from OrderItem oi join oi.order o where o.user.id = :u and oi.product.id = :p and o.status = :s order by o.id desc", Long.class)
                .setParameter("u", u.getId()).setParameter("p", p.getId()).setParameter("s", OrderStatus.COMPLETED).setMaxResults(1).getResultStream().findFirst().orElse(null);
        Review r = new Review();
        r.setProduct(p);
        r.setUser(u);
        r.setOrder(orderId != null ? em.find(Order.class, orderId) : null);
        r.setRating(Integer.parseInt(Texts.trim(f.get("rating"))));
        r.setComment(f.str("comment"));
        em.persist(r);
        Web.success(ra, "Cảm ơn bạn đã đánh giá sản phẩm!");
        return "redirect:/products/" + slug + "#reviews";
    }

    @GetMapping("/posts")
    @Transactional(readOnly = true)
    public String posts(@RequestParam(defaultValue = "1") int page, Model model) {
        long total = em.createQuery("select count(p) from Post p where p.published = true", Long.class).getSingleResult();
        int per = 9;
        int pg = Math.max(1, page);
        List<Post> items = em.createQuery("select p from Post p left join fetch p.author where p.published = true order by p.createdAt desc", Post.class)
                .setFirstResult((pg - 1) * per).setMaxResults(per).getResultList();
        model.addAttribute("title", "Góc sức khỏe");
        model.addAttribute("posts", new Page<>(items, total, per, pg));
        return "shop/posts";
    }

    @GetMapping("/posts/{slug}")
    @Transactional(readOnly = true)
    public String post(@PathVariable String slug, Model model) {
        Post post = em.createQuery("select p from Post p left join fetch p.author where p.slug = :s and p.published = true", Post.class)
                .setParameter("s", slug).getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Bài viết không tồn tại."));
        model.addAttribute("title", post.getTitle());
        model.addAttribute("metaDescription", post.getSummary());
        model.addAttribute("ogImage", post.imageUrl());
        model.addAttribute("post", post);
        model.addAttribute("others", em.createQuery("select p from Post p where p.published = true and p.id <> :id order by p.createdAt desc", Post.class)
                .setParameter("id", post.getId()).setMaxResults(4).getResultList());
        return "shop/post";
    }

    @GetMapping("/pages/{slug}")
    @Transactional(readOnly = true)
    public String page(@PathVariable String slug, Model model) {
        StaticPage page = em.createQuery("select p from StaticPage p where p.slug = :s and p.published = true", StaticPage.class)
                .setParameter("s", slug).getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Trang không tồn tại."));
        model.addAttribute("title", page.getTitle());
        model.addAttribute("metaDescription", page.getMetaDescription());
        model.addAttribute("page", page);
        return "shop/page";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("title", "Giới thiệu & chính sách");
        model.addAttribute("paymentMethods", settings.enabledPaymentMethods());
        return "shop/about";
    }

    @GetMapping("/faq")
    @Transactional(readOnly = true)
    public String faq(@RequestParam(required = false) String q, Model model) {
        String query = Texts.trim(q);
        List<Faq> faqs = em.createQuery("select f from Faq f where f.active = true order by f.sortOrder, f.id", Faq.class).getResultList();
        if (!query.isEmpty()) {
            String needle = Texts.vnAscii(query);
            faqs = faqs.stream().filter(x -> Texts.vnAscii(x.getQuestion() + " " + x.getAnswer()).contains(needle)).toList();
        }
        Map<String, List<Faq>> groups = new LinkedHashMap<>();
        for (Faq x : faqs) groups.computeIfAbsent(x.getGroup(), k -> new ArrayList<>()).add(x);
        model.addAttribute("title", "Câu hỏi thường gặp");
        model.addAttribute("metaDescription", "Giải đáp thắc mắc về đặt hàng, thanh toán, giao hàng, đổi trả tại " + settings.get("store_name") + ".");
        model.addAttribute("groups", groups);
        model.addAttribute("q", query);
        return "shop/faq";
    }
}
