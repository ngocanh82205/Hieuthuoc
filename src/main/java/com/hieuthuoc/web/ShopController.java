package com.hieuthuoc.web;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class ShopController {
    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;
    private final PostRepository postRepo;
    private final ReviewRepository reviewRepo;
    private final OrderItemRepository orderItemRepo;
    private final ProductService productService;
    private final CurrentUser currentUser;
    private final ProductQuestionRepository questionRepo;
    private final StockSubscriptionRepository subscriptionRepo;

    @GetMapping("/")
    public String home(Model model) {
        List<Product> all = productService.search(new ProductService.Filter(null, null, null, null, null, false, "bestseller", true));
        List<Product> sellable = all.stream().filter(p -> p.getDrugType().isSellableOnline()).toList();
        model.addAttribute("bestSellers", sellable.stream().limit(8).toList());
        model.addAttribute("onSale", sellable.stream().filter(p -> p.isOnSale() && !p.getDrugType().isPrescription())
                .sorted(Comparator.comparingInt(Product::getDiscountPercent).reversed()).limit(4).toList());
        model.addAttribute("newest", sellable.stream().sorted(Comparator.comparing(Product::getId).reversed()).limit(8).toList());
        Map<Long, Long> counts = new HashMap<>();
        for (Product p : all) if (p.getCategory() != null) counts.merge(p.getCategory().getId(), 1L, Long::sum);
        model.addAttribute("categoryCounts", counts);
        model.addAttribute("posts", postRepo.findTop3ByPublishedTrueOrderByCreatedAtDesc());
        model.addAttribute("title", "Trang chủ");
        return "shop/home";
    }

    @GetMapping("/products")
    public String products(@RequestParam(required = false) String q,
                           @RequestParam(required = false) String category,
                           @RequestParam(required = false) String type,
                           @RequestParam(required = false) Long min,
                           @RequestParam(required = false) Long max,
                           @RequestParam(required = false) String instock,
                           @RequestParam(defaultValue = "bestseller") String sort,
                           @RequestParam(required = false) String brand,
                           @RequestParam(required = false) String country,
                           @RequestParam(required = false) String form,
                           @RequestParam(defaultValue = "1") int page,
                           Model model) {
        Category cat = category == null ? null : categoryRepo.findBySlug(category).orElse(null);
        DrugType dt = null;
        try {
            if (type != null && !type.isBlank()) dt = DrugType.valueOf(type);
        } catch (IllegalArgumentException ignored) {
        }
        List<Product> list = productService.search(new ProductService.Filter(q, cat, dt, min, max, instock != null, sort, true, brand, country, form));
        model.addAttribute("brands", productRepo.distinctManufacturers());
        model.addAttribute("countries", productRepo.distinctCountries());
        model.addAttribute("forms", productRepo.distinctDosageForms());
        model.addAttribute("brand", brand);
        model.addAttribute("country", country);
        model.addAttribute("form", form);
        model.addAttribute("page", ProductService.page(list, page, 12));
        model.addAttribute("category", cat);
        model.addAttribute("q", q);
        model.addAttribute("type", dt);
        model.addAttribute("min", min);
        model.addAttribute("max", max);
        model.addAttribute("instock", instock != null);
        model.addAttribute("sort", sort);
        model.addAttribute("drugTypes", DrugType.values());
        model.addAttribute("title", cat != null ? cat.getName() : (q != null && !q.isBlank() ? "Tìm kiếm: " + q : "Tất cả sản phẩm"));
        return "shop/products";
    }

    @GetMapping("/products/{slug}")
    public String product(@PathVariable String slug, Model model) {
        Product p = productRepo.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> BusinessException.notFound("Sản phẩm không tồn tại hoặc đã ngừng kinh doanh."));
        productService.enrich(p);
        model.addAttribute("product", p);
        model.addAttribute("reviews", reviewRepo.findByProductAndHiddenFalseOrderByCreatedAtDesc(p));
        List<Product> equivalents = p.getActiveIngredient() == null ? List.of()
                : productService.enrich(new ArrayList<>(productRepo.findTop4ByActiveTrueAndActiveIngredientIgnoreCaseAndIdNot(p.getActiveIngredient(), p.getId())));
        model.addAttribute("equivalents", equivalents);
        model.addAttribute("related", p.getCategory() == null ? List.of()
                : productService.enrich(new ArrayList<>(productRepo.findTop4ByActiveTrueAndCategoryAndIdNotAndDrugTypeNot(p.getCategory(), p.getId(), DrugType.SPECIAL))));
        User u = currentUser.getOrNull();
        boolean canReview = u != null && u.getRole() == Role.CUSTOMER
                && orderItemRepo.hasPurchased(u, p) && !reviewRepo.existsByProductAndUser(p, u);
        model.addAttribute("canReview", canReview);
        model.addAttribute("questions", questionRepo.findByProductAndHiddenFalseAndAnswerIsNotNullOrderByCreatedAtDesc(p));
        model.addAttribute("myPendingQuestions", u != null && u.getRole() == Role.CUSTOMER
                ? questionRepo.findByProductAndUserAndAnswerIsNullOrderByCreatedAtDesc(p, u) : List.of());
        model.addAttribute("subscribed", u != null && u.getRole() == Role.CUSTOMER && subscriptionRepo.existsByUserAndProductAndNotifiedFalse(u, p));
        model.addAttribute("title", p.getName());
        return "shop/product";
    }

    @PostMapping("/products/{slug}/reviews")
    public String review(@PathVariable String slug, @RequestParam(defaultValue = "0") int rating,
                         @RequestParam(required = false) String comment, RedirectAttributes ra) {
        Product p = productRepo.findBySlugAndActiveTrue(slug).orElseThrow(() -> BusinessException.notFound("Sản phẩm không tồn tại."));
        User u = currentUser.get();
        if (!orderItemRepo.hasPurchased(u, p)) throw new BusinessException("Bạn chỉ có thể đánh giá sản phẩm đã mua thành công.");
        if (rating < 1 || rating > 5) throw new BusinessException("Vui lòng chọn số sao từ 1 đến 5.");
        if (reviewRepo.existsByProductAndUser(p, u)) throw new BusinessException("Bạn đã đánh giá sản phẩm này.");
        Review r = new Review();
        r.setProduct(p);
        r.setUser(u);
        r.setRating(rating);
        r.setComment(Texts.emptyToNull(Texts.trim(comment, 1000)));
        reviewRepo.save(r);
        Flash.success(ra, "Cảm ơn bạn đã đánh giá sản phẩm!");
        return "redirect:/products/" + slug + "#reviews";
    }

    @GetMapping("/posts")
    public String posts(Model model) {
        model.addAttribute("posts", postRepo.findByPublishedTrueOrderByCreatedAtDesc());
        model.addAttribute("title", "Góc sức khỏe");
        return "shop/posts";
    }

    @GetMapping("/posts/{slug}")
    public String post(@PathVariable String slug, Model model) {
        Post post = postRepo.findBySlugAndPublishedTrue(slug).orElseThrow(() -> BusinessException.notFound("Bài viết không tồn tại."));
        model.addAttribute("post", post);
        model.addAttribute("others", postRepo.findTop4ByPublishedTrueAndIdNotOrderByCreatedAtDesc(post.getId()));
        model.addAttribute("title", post.getTitle());
        return "shop/post";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("title", "Giới thiệu & chính sách");
        return "shop/about";
    }
}
