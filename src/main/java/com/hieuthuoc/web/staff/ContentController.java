package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Jpql;
import com.hieuthuoc.web.Validator;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/** Kiểm duyệt đánh giá, thông tin chuyên môn sản phẩm, bài viết sức khỏe (CMS), FAQ. */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class ContentController {
    private final NotificationService notifications;
    private final FileStorageService files;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private static String clean(String v, int max) {
        return Texts.emptyToNull(Texts.trim(v, max));
    }

    /* ---------------- Đánh giá ---------------- */

    @GetMapping("/reviews")
    @Transactional(readOnly = true)
    public String reviews(@RequestParam(defaultValue = "0") int rating, @RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("title", "Kiểm duyệt đánh giá");
        model.addAttribute("rating", rating);
        model.addAttribute("reviews", new Jpql("Review r", "r").when(rating > 0, "r.rating = :r", "r", rating)
                .page(em, Review.class, "r.createdAt desc, r.id desc", 30, page));
        return "staff/reviews";
    }

    @PostMapping("/reviews/{id}/toggle")
    @Transactional
    public String toggleReview(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
        Review r = Web.found(em.find(Review.class, id));
        r.setHidden(!r.isHidden());
        notifications.log(currentUser.get(), r.isHidden() ? "review.hide" : "review.show", "Đánh giá #" + r.getId());
        Web.success(ra, r.isHidden() ? "Đã ẩn đánh giá." : "Đã hiện lại đánh giá.");
        return Web.back(req, "/staff/reviews");
    }

    /* ---------------- Thông tin chuyên môn sản phẩm ---------------- */

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public String products(@RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int page, Model model) {
        q = Texts.trim(q);
        model.addAttribute("title", "Thông tin sản phẩm");
        model.addAttribute("q", q);
        model.addAttribute("products", new Jpql("Product p", "p").search(q, "p.name", "coalesce(p.activeIngredient, '')")
                .page(em, Product.class, "p.name", 30, page));
        return "staff/products";
    }

    @GetMapping("/products/{id}/info")
    @Transactional(readOnly = true)
    public String productInfo(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Cập nhật thông tin chuyên môn");
        model.addAttribute("product", Web.found(em.find(Product.class, id)));
        return "staff/product-info";
    }

    @PostMapping("/products/{id}/info")
    @Transactional
    public String saveProductInfo(@PathVariable Long id, @RequestParam Map<String, String> in, RedirectAttributes ra) {
        Product p = Web.found(em.find(Product.class, id));
        p.setDescription(clean(in.get("description"), 2000));
        p.setUsageInstruction(clean(in.get("usage_instruction"), 2000));
        p.setContraindications(clean(in.get("contraindications"), 1000));
        p.setSideEffects(clean(in.get("side_effects"), 1000));
        p.setActiveIngredient(clean(in.get("active_ingredient"), 200));
        p.setStrength(clean(in.get("strength"), 100));
        notifications.log(currentUser.get(), "product.info", p.getName());
        Web.success(ra, "Đã cập nhật thông tin chuyên môn của " + p.getName() + ".");
        return "redirect:/staff/products";
    }

    /* ---------------- Bài viết sức khỏe ---------------- */

    @GetMapping("/posts")
    @Transactional(readOnly = true)
    public String posts(@RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("title", "Bài viết sức khỏe");
        model.addAttribute("posts", new Jpql("Post p", "p").page(em, Post.class, "p.createdAt desc, p.id desc", 30, page));
        return "staff/posts";
    }

    @GetMapping("/posts/new")
    public String newPost(Model model) {
        model.addAttribute("title", "Viết bài mới");
        model.addAttribute("post", new Post());
        return "staff/post-form";
    }

    @GetMapping("/posts/{id}/edit")
    @Transactional(readOnly = true)
    public String editPost(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Sửa bài viết");
        model.addAttribute("post", Web.found(em.find(Post.class, id)));
        return "staff/post-form";
    }

    @PostMapping({"/posts", "/posts/{id}"})
    @Transactional
    public String savePost(@PathVariable(required = false) Long id, @RequestParam Map<String, String> in,
                           @RequestParam(required = false) MultipartFile image, RedirectAttributes ra) {
        if (files.isPresent(image)) {
            Validator.of(Form.of(Map.of())).rule("image", image.getContentType() != null && image.getContentType().startsWith("image/"), "Chỉ nhận file ảnh.")
                    .rule("image", image.getSize() <= 5L * 1024 * 1024, "Ảnh tối đa 5MB.").check();
        }
        String title = Texts.trim(in.get("title"));
        String content = Texts.trim(in.get("content"));
        if (Texts.mbLen(title) < 5 || Texts.mbLen(content) < 20) {
            throw new BusinessException("Tiêu đề tối thiểu 5 ký tự, nội dung tối thiểu 20 ký tự.");
        }
        User me = currentUser.get();
        Post p = id != null ? Web.found(em.find(Post.class, id)) : new Post();
        if (p.getId() == null) {
            p.setAuthor(me);
            String slug = Texts.slugify(title);
            if (slug.isEmpty()) slug = "bai-viet";
            boolean exists = em.createQuery("select count(p) from Post p where p.slug = :s", Long.class).setParameter("s", slug).getSingleResult() > 0;
            p.setSlug(exists ? slug + "-" + Long.toString(System.currentTimeMillis(), 36) : slug);
        }
        p.setTitle(Texts.trim(title, 200));
        p.setSummary(clean(in.get("summary"), 500));
        p.setContent(content);
        p.setPublished(in.containsKey("published"));
        if (files.isPresent(image)) {
            p.setImage(files.store("posts", image));
        } else if (in.containsKey("remove_image")) {
            p.setImage(null);
        }
        if (p.getId() == null) em.persist(p);
        notifications.log(me, "post.save", p.getTitle());
        Web.success(ra, "Đã lưu bài viết.");
        return "redirect:/staff/posts";
    }

    @PostMapping("/posts/{id}/delete")
    @Transactional
    public String deletePost(@PathVariable Long id, RedirectAttributes ra) {
        Post p = Web.found(em.find(Post.class, id));
        em.remove(p);
        notifications.log(currentUser.get(), "post.delete", p.getTitle());
        Web.info(ra, "Đã xóa bài viết.");
        return "redirect:/staff/posts";
    }

    /* ---------------- Câu hỏi thường gặp (FAQ) ---------------- */

    @GetMapping("/faqs")
    @Transactional(readOnly = true)
    public String faqs(@RequestParam(required = false) Long edit, Model model) {
        List<Faq> all = em.createQuery("select f from Faq f order by f.sortOrder, f.id", Faq.class).getResultList();
        Map<String, List<Faq>> grouped = all.stream().sorted(Comparator.comparing(Faq::getGroup))
                .collect(Collectors.groupingBy(Faq::getGroup, TreeMap::new, Collectors.toList()));
        model.addAttribute("title", "Câu hỏi thường gặp");
        model.addAttribute("grouped", grouped);
        model.addAttribute("edit", edit != null ? em.find(Faq.class, edit) : null);
        model.addAttribute("groups", grouped.keySet());
        return "staff/faqs";
    }

    @PostMapping({"/faqs", "/faqs/{id}"})
    @Transactional
    public String saveFaq(@PathVariable(required = false) Long id, @RequestParam Map<String, String> in, RedirectAttributes ra) {
        String q = Texts.trim(in.get("question"));
        String a = Texts.trim(in.get("answer"));
        if (Texts.mbLen(q) < 5 || Texts.mbLen(a) < 5) throw new BusinessException("Câu hỏi và câu trả lời tối thiểu 5 ký tự.");
        Faq f = id != null ? Web.found(em.find(Faq.class, id)) : new Faq();
        String group = clean(in.get("group"), 100);
        f.setGroup(group != null ? group : "Chung");
        f.setQuestion(Texts.trim(q, 300));
        f.setAnswer(a);
        f.setSortOrder(Texts.toInt(in.get("sort_order"), 0));
        f.setActive(f.getId() == null || in.containsKey("active"));
        if (f.getId() == null) em.persist(f);
        notifications.log(currentUser.get(), "faq.save", f.getQuestion());
        Web.success(ra, "Đã lưu câu hỏi thường gặp.");
        return "redirect:/staff/faqs";
    }

    @PostMapping("/faqs/{id}/delete")
    @Transactional
    public String deleteFaq(@PathVariable Long id, RedirectAttributes ra) {
        Faq f = Web.found(em.find(Faq.class, id));
        em.remove(f);
        notifications.log(currentUser.get(), "faq.delete", f.getQuestion());
        Web.info(ra, "Đã xóa câu hỏi.");
        return "redirect:/staff/faqs";
    }
}
