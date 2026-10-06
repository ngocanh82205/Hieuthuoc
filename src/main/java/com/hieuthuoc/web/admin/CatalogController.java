package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Jpql;
import com.hieuthuoc.web.Validator;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/** Admin: danh mục (cây nhiều cấp), sản phẩm (ảnh, đơn vị quy đổi, SEO, thuốc tương đương), nhập / xuất Excel. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class CatalogController {
    private final CategoryService categories;
    private final CatalogService catalog;
    private final NotificationService notifications;
    private final FileStorageService files;
    private final StockService stock;
    private final ProductAdminService productAdmin;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private static String clean(String v, int max) {
        return Texts.emptyToNull(Texts.trim(v, max));
    }

    private boolean exists(String jpql, Object... params) {
        var q = em.createQuery(jpql, Long.class);
        for (int i = 0; i + 1 < params.length; i += 2) q.setParameter((String) params[i], params[i + 1]);
        return q.getSingleResult() > 0;
    }

    private static String suffix() {
        return "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    /* ---------------- Danh mục ---------------- */

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public String categories(@RequestParam(required = false) Long edit, Model model) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] r : em.createQuery("select p.categoryId, count(p) from Product p where p.categoryId is not null group by p.categoryId", Object[].class).getResultList()) {
            counts.put((Long) r[0], (Long) r[1]);
        }
        model.addAttribute("title", "Danh mục sản phẩm");
        model.addAttribute("categories", categories.tree());
        model.addAttribute("counts", counts);
        model.addAttribute("edit", edit != null ? em.find(Category.class, edit) : null);
        return "admin/categories";
    }

    @PostMapping({"/categories", "/categories/{id}"})
    @Transactional
    public String saveCategory(@PathVariable(required = false) Long id, @RequestParam Map<String, String> in, RedirectAttributes ra) {
        String name = Texts.trim(in.get("name"));
        if (Texts.mbLen(name) < 2) throw new BusinessException("Tên danh mục tối thiểu 2 ký tự.");
        Category c = id != null ? Web.found(em.find(Category.class, id)) : new Category();
        String slug = Texts.slugify(name);
        if (slug.isEmpty()) slug = "danh-muc";
        if (exists("select count(x) from Category x where x.slug = :s and x.id <> :id", "s", slug, "id", c.getId() != null ? c.getId() : -1L)) slug += suffix();
        Long parentId = Texts.toLong(in.get("parent_id"));
        Category parent = null;
        if (parentId != null) {
            parent = em.find(Category.class, parentId);
            if (parent == null) throw new BusinessException("Danh mục cha không tồn tại.");
            if (c.getId() != null && categories.descendantIds(c).contains(parent.getId())) {
                throw new BusinessException("Không thể chọn chính danh mục này hoặc danh mục con của nó làm danh mục cha.");
            }
        }
        String icon = Texts.trim(in.get("icon"));
        c.setName(Texts.trim(name, 100));
        c.setSlug(slug);
        c.setIcon(icon.matches("^bi-[a-z0-9-]+$") ? icon : "bi-capsule");
        c.setSortOrder(Texts.toInt(in.get("sort_order"), 0));
        c.setParent(parent);
        c.setMetaDescription(clean(in.get("meta_description"), 300));
        if (c.getId() == null) em.persist(c);
        notifications.log(currentUser.get(), "category.save", c.getName());
        Web.success(ra, "Đã lưu danh mục.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/delete")
    @Transactional
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        Category c = Web.found(em.find(Category.class, id));
        if (exists("select count(p) from Product p where p.categoryId = :c", "c", c.getId())) throw new BusinessException("Danh mục đang có sản phẩm, không thể xóa.");
        if (exists("select count(x) from Category x where x.parentId = :c", "c", c.getId())) throw new BusinessException("Danh mục đang có danh mục con, không thể xóa.");
        em.remove(c);
        notifications.log(currentUser.get(), "category.delete", c.getName());
        Web.info(ra, "Đã xóa danh mục.");
        return "redirect:/admin/categories";
    }

    /* ---------------- Sản phẩm ---------------- */

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public String products(@RequestParam Map<String, String> in, @RequestParam(defaultValue = "1") int page, Model model) {
        String q = Texts.trim(in.get("q"));
        Long catId = Texts.toLong(in.get("category"));
        Category cat = catId != null ? em.find(Category.class, catId) : null;
        DrugType type;
        try {
            type = DrugType.valueOf(Texts.trim(in.get("type")));
        } catch (IllegalArgumentException e) {
            type = null;
        }
        Page<Product> p = new Jpql("Product p", "p")
                .search(q, "p.name", "coalesce(p.activeIngredient, '')", "coalesce(p.registrationNo, '')")
                .when(cat != null, "p.categoryId in :cats", "cats", cat != null ? categories.descendantIds(cat) : null)
                .when(type != null, "p.drugType = :t", "t", type)
                .when("inactive".equals(in.get("status")), "p.active = false")
                .page(em, Product.class, "p.id desc", 20, page);
        stock.fill(p.getItems(), false);
        model.addAttribute("title", "Sản phẩm");
        model.addAttribute("page", p);
        model.addAttribute("categories", categories.tree());
        model.addAttribute("drugTypes", DrugType.values());
        model.addAttribute("q", q);
        model.addAttribute("category", cat != null ? cat.getId() : null);
        model.addAttribute("type", type);
        return "admin/products";
    }

    private String form(Product p, String title, Model model) {
        long selfId = p.getId() != null ? p.getId() : 0L;
        List<ProductUnit> units = new ArrayList<>(p.getUnits());
        while (units.size() < 3) units.add(null);
        model.addAttribute("title", title);
        model.addAttribute("product", p);
        model.addAttribute("unitRows", units.subList(0, 3));
        model.addAttribute("categories", categories.tree());
        model.addAttribute("drugTypes", DrugType.values());
        model.addAttribute("allProducts", em.createQuery("select p from Product p where p.id <> :id order by p.name", Product.class).setParameter("id", selfId).getResultList());
        model.addAttribute("equivalentIds", p.getEquivalents().stream().map(Product::getId).toList());
        model.addAttribute("ingredients", catalog.ingredientSuggestions());
        model.addAttribute("manufacturers", catalog.manufacturerSuggestions());
        model.addAttribute("countries", em.createQuery("select distinct p.country from Product p where p.country is not null order by p.country", String.class).getResultList());
        return "admin/product-form";
    }

    @GetMapping("/products/create")
    @Transactional(readOnly = true)
    public String createProduct(Model model) {
        return form(new Product(), "Thêm sản phẩm", model);
    }

    @GetMapping("/products/{id}/edit")
    @Transactional(readOnly = true)
    public String editProduct(@PathVariable Long id, Model model) {
        return form(Web.found(em.find(Product.class, id)), "Sửa sản phẩm", model);
    }

    @PostMapping({"/products", "/products/{id}"})
    @Transactional
    public String saveProduct(@PathVariable(required = false) Long id, @RequestParam MultiValueMap<String, String> params,
                              @RequestParam(required = false) MultipartFile image, RedirectAttributes ra) {
        Product p = id != null ? Web.found(em.find(Product.class, id)) : null;
        productAdmin.save(p, new Form(params), image, currentUser.get());
        Web.success(ra, "Đã lưu sản phẩm.");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/toggle")
    @Transactional
    public String toggleProduct(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
        Product p = productAdmin.toggle(Web.found(em.find(Product.class, id)), currentUser.get());
        Web.success(ra, (p.isActive() ? "Đã mở bán " : "Đã ngừng bán ") + p.getName() + ".");
        return Web.back(req, "/admin/products");
    }

    /** Xóa sản phẩm (chỉ khi chưa phát sinh giao dịch - xem ProductAdminService.delete). */
    @PostMapping("/products/{id}/delete")
    @Transactional
    public String deleteProduct(@PathVariable Long id, RedirectAttributes ra) {
        Product p = Web.found(em.find(Product.class, id));
        productAdmin.delete(p, currentUser.get());
        Web.success(ra, "Đã xóa sản phẩm " + p.getName() + ".");
        return "redirect:/admin/products";
    }

    @GetMapping("/products/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportProducts() throws IOException {
        notifications.log(currentUser.get(), "product.export", "Xuất Excel sản phẩm");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        catalog.exportProducts(out);
        return Web.xlsx(out, "san-pham_" + LocalDate.now() + ".xlsx");
    }

    @PostMapping("/products/import")
    @Transactional
    public String importProducts(@RequestParam(required = false) MultipartFile file, RedirectAttributes ra) {
        CatalogService.ImportResult r = catalog.importProducts(file);
        notifications.log(currentUser.get(), "product.import", "Thêm " + r.created() + ", cập nhật " + r.updated());
        Web.success(ra, "Nhập Excel thành công: thêm mới " + r.created() + ", cập nhật " + r.updated() + " sản phẩm.");
        return "redirect:/admin/products";
    }
}
