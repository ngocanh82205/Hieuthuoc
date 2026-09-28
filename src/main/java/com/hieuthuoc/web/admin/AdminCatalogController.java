package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Admin: danh mục, sản phẩm, nhà cung cấp, mã giảm giá. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminCatalogController {
    private final CategoryRepository categoryRepo;
    private final ProductRepository productRepo;
    private final SupplierRepository supplierRepo;
    private final VoucherRepository voucherRepo;
    private final ReceiptRepository receiptRepo;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final IngredientRepository ingredientRepo;
    private final ManufacturerRepository manufacturerRepo;
    private final FileStorageService files;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    /* ---------------- Danh mục ---------------- */

    @GetMapping("/categories")
    public String categories(@RequestParam(required = false) Long edit, Model model) {
        List<Category> list = categoryService.tree();
        Map<Long, Long> counts = new HashMap<>();
        for (Category c : list) counts.put(c.getId(), productRepo.countByCategory(c));
        model.addAttribute("categories", list);
        model.addAttribute("counts", counts);
        model.addAttribute("edit", edit == null ? new Category() : categoryRepo.findById(edit).orElse(new Category()));
        model.addAttribute("title", "Danh mục sản phẩm");
        return "admin/categories";
    }

    @PostMapping({"/categories", "/categories/{id}"})
    @Transactional
    public String saveCategory(@PathVariable(required = false) Long id, @RequestParam String name,
                               @RequestParam(required = false) String icon, @RequestParam(defaultValue = "0") int sortOrder,
                               @RequestParam(required = false) Long parentId, @RequestParam(required = false) String metaDescription,
                               RedirectAttributes ra) {
        if (Texts.trim(name).length() < 2) throw new BusinessException("Tên danh mục tối thiểu 2 ký tự.");
        Category c = id == null ? new Category() : categoryRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy danh mục."));
        String slug = Texts.slugify(name);
        Optional<Category> dup = categoryRepo.findBySlug(slug);
        if (dup.isPresent() && !dup.get().getId().equals(id)) slug += "-" + Long.toString(System.currentTimeMillis(), 36);
        c.setName(Texts.trim(name, 100));
        c.setSlug(slug);
        c.setIcon(icon != null && icon.matches("bi-[a-z0-9-]+") ? icon : "bi-capsule");
        c.setSortOrder(sortOrder);
        Category parent = parentId == null ? null : categoryRepo.findById(parentId).orElseThrow(() -> new BusinessException("Danh mục cha không tồn tại."));
        if (parent != null && c.getId() != null && categoryService.descendantIds(c).contains(parent.getId())) {
            throw new BusinessException("Không thể chọn chính danh mục này hoặc danh mục con của nó làm danh mục cha.");
        }
        c.setParent(parent);
        c.setMetaDescription(Texts.emptyToNull(Texts.trim(metaDescription, 300)));
        categoryRepo.save(c);
        notifications.log(currentUser.get(), "category.save", c.getName());
        Flash.success(ra, "Đã lưu danh mục.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/delete")
    @Transactional
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        Category c = categoryRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy danh mục."));
        if (productRepo.countByCategory(c) > 0) throw new BusinessException("Danh mục đang có sản phẩm, không thể xóa.");
        if (categoryRepo.existsByParent(c)) throw new BusinessException("Danh mục đang có danh mục con, không thể xóa.");
        categoryRepo.delete(c);
        notifications.log(currentUser.get(), "category.delete", c.getName());
        Flash.info(ra, "Đã xóa danh mục.");
        return "redirect:/admin/categories";
    }

    /* ---------------- Sản phẩm ---------------- */

    @GetMapping("/products")
    public String products(@RequestParam(required = false) String q, @RequestParam(required = false) Long category,
                           @RequestParam(required = false) DrugType type, @RequestParam(defaultValue = "1") int page, Model model) {
        Category cat = category == null ? null : categoryRepo.findById(category).orElse(null);
        List<Product> list = productService.search(new ProductService.Filter(q, cat, type, null, null, false, "newest", false));
        model.addAttribute("page", ProductService.page(list, page, 20));
        model.addAttribute("categories", categoryService.tree());
        model.addAttribute("drugTypes", DrugType.values());
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("type", type);
        model.addAttribute("title", "Sản phẩm");
        return "admin/products";
    }

    private void productFormModel(Model model, Product p, String title) {
        model.addAttribute("product", p);
        model.addAttribute("categories", categoryService.tree());
        model.addAttribute("drugTypes", DrugType.values());
        model.addAttribute("allProducts", productRepo.findAllByOrderByNameAsc());
        Set<Long> eq = new HashSet<>();
        p.getEquivalents().forEach(e -> eq.add(e.getId()));
        model.addAttribute("equivalentIds", eq);
        model.addAttribute("ingredients", ingredientRepo.findAllByOrderByNameAsc());
        model.addAttribute("manufacturers", manufacturerRepo.findAllByOrderByNameAsc());
        model.addAttribute("countries", productRepo.distinctCountries());
        model.addAttribute("title", title);
    }

    @GetMapping("/products/new")
    public String newProduct(Model model) {
        productFormModel(model, new Product(), "Thêm sản phẩm");
        return "admin/product-form";
    }

    @GetMapping("/products/{id}/edit")
    public String editProduct(@PathVariable Long id, Model model) {
        productFormModel(model, productRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy sản phẩm.")), "Sửa sản phẩm");
        return "admin/product-form";
    }

    @GetMapping("/products/export.xlsx")
    public void exportProducts(jakarta.servlet.http.HttpServletResponse res) throws java.io.IOException {
        res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        res.setHeader("Content-Disposition", "attachment; filename=\"san-pham_" + LocalDate.now() + ".xlsx\"");
        catalogService.exportProducts(res.getOutputStream());
        notifications.log(currentUser.get(), "product.export", "Xuất Excel sản phẩm");
    }

    @PostMapping("/products/import")
    public String importProducts(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        try {
            CatalogService.ImportResult r = catalogService.importProducts(file);
            notifications.log(currentUser.get(), "product.import", "Thêm " + r.created() + ", cập nhật " + r.updated());
            Flash.success(ra, "Nhập Excel thành công: thêm mới " + r.created() + ", cập nhật " + r.updated() + " sản phẩm.");
        } catch (CatalogService.ImportException e) {
            ra.addFlashAttribute("importErrors", e.getErrors());
            Flash.error(ra, "File có " + e.getErrors().size() + " dòng lỗi - chưa nhập dữ liệu nào. Sửa file rồi nhập lại.");
        }
        return "redirect:/admin/products";
    }

    @Getter
    @Setter
    public static class ProductForm {
        private Long categoryId;
        private String name;
        private String activeIngredient;
        private String strength;
        private String dosageForm;
        private String packaging;
        private String registrationNo;
        private String manufacturer;
        private String country;
        private DrugType drugType = DrugType.OTC;
        private String unit;
        private Long price;
        private Long oldPrice;
        private Integer maxPerOrder;
        private Integer minStock;
        private String description;
        private String usageInstruction;
        private String contraindications;
        private String sideEffects;
        private boolean active;
        private boolean removeImage;
        private String slug;
        private String metaTitle;
        private String metaDescription;
        private List<Long> equivalentIds = new ArrayList<>();
        /** Đơn vị quy đổi (tối đa 3 dòng): tên, số đơn vị gốc, giá. */
        private List<String> unitNames = new ArrayList<>();
        private List<Integer> unitFactors = new ArrayList<>();
        private List<Long> unitPrices = new ArrayList<>();
    }

    @PostMapping({"/products", "/products/{id}"})
    @Transactional
    public String saveProduct(@PathVariable(required = false) Long id, @ModelAttribute ProductForm f,
                              @RequestParam(value = "imageFile", required = false) MultipartFile image, RedirectAttributes ra) {
        List<String> errors = new ArrayList<>();
        if (Texts.trim(f.getName()).length() < 2) errors.add("Tên sản phẩm tối thiểu 2 ký tự.");
        if (f.getPrice() == null || f.getPrice() <= 0) errors.add("Giá bán phải lớn hơn 0.");
        if (Texts.isBlank(f.getUnit())) errors.add("Vui lòng nhập đơn vị tính.");
        if (f.getDrugType() == DrugType.ETC && f.getOldPrice() != null && f.getOldPrice() > 0) {
            errors.add("Không áp dụng giảm giá/khuyến mại cho thuốc kê đơn.");
        }
        if (!errors.isEmpty()) throw new BusinessException(String.join(" ", errors));

        Product p = id == null ? new Product() : productRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy sản phẩm."));
        String wantSlug = Texts.slugify(Texts.isBlank(f.getSlug()) ? f.getName() : f.getSlug());
        if (id == null || !wantSlug.equals(p.getSlug())) {
            String slug = wantSlug;
            if (productRepo.existsBySlug(slug)) {
                if (!Texts.isBlank(f.getSlug()) && id != null) throw new BusinessException("Đường dẫn \"" + slug + "\" đã được sản phẩm khác sử dụng.");
                slug += "-" + Long.toString(System.currentTimeMillis(), 36);
            }
            p.setSlug(slug);
        }
        p.setMetaTitle(Texts.emptyToNull(Texts.trim(f.getMetaTitle(), 150)));
        p.setMetaDescription(Texts.emptyToNull(Texts.trim(f.getMetaDescription(), 300)));
        p.getEquivalents().clear();
        for (Long eid : f.getEquivalentIds()) {
            if (eid == null || eid.equals(p.getId())) continue;
            productRepo.findById(eid).ifPresent(p.getEquivalents()::add);
        }
        p.setCategory(f.getCategoryId() == null ? null : categoryRepo.findById(f.getCategoryId()).orElse(null));
        p.setName(Texts.trim(f.getName(), 200));
        p.setActiveIngredient(Texts.emptyToNull(f.getActiveIngredient()));
        p.setStrength(Texts.emptyToNull(f.getStrength()));
        p.setDosageForm(Texts.emptyToNull(f.getDosageForm()));
        p.setPackaging(Texts.emptyToNull(f.getPackaging()));
        p.setRegistrationNo(Texts.emptyToNull(f.getRegistrationNo()));
        p.setManufacturer(Texts.emptyToNull(f.getManufacturer()));
        p.setCountry(Texts.emptyToNull(f.getCountry()));
        p.setDrugType(f.getDrugType() == null ? DrugType.OTC : f.getDrugType());
        p.setUnit(Texts.trim(f.getUnit(), 30));
        p.setPrice(f.getPrice());
        p.setOldPrice(f.getOldPrice() == null || f.getOldPrice() <= 0 ? null : f.getOldPrice());
        p.setMaxPerOrder(f.getMaxPerOrder() == null || f.getMaxPerOrder() <= 0 ? null : f.getMaxPerOrder());
        p.setMinStock(f.getMinStock() == null ? 10 : Math.max(0, f.getMinStock()));
        p.setDescription(Texts.emptyToNull(Texts.trim(f.getDescription(), 2000)));
        p.setUsageInstruction(Texts.emptyToNull(Texts.trim(f.getUsageInstruction(), 2000)));
        p.setContraindications(Texts.emptyToNull(Texts.trim(f.getContraindications(), 1000)));
        p.setSideEffects(Texts.emptyToNull(Texts.trim(f.getSideEffects(), 1000)));
        p.setActive(f.isActive());
        // Đơn vị quy đổi
        p.getUnits().clear();
        Set<String> names = new HashSet<>();
        names.add(p.getUnit().toLowerCase());
        for (int i = 0; i < f.getUnitNames().size(); i++) {
            String n = Texts.trim(f.getUnitNames().get(i), 30);
            if (n.isEmpty()) continue;
            Integer factor = i < f.getUnitFactors().size() ? f.getUnitFactors().get(i) : null;
            Long price = i < f.getUnitPrices().size() ? f.getUnitPrices().get(i) : null;
            if (factor == null || factor < 2) throw new BusinessException("Đơn vị \"" + n + "\": số " + p.getUnit() + " quy đổi phải từ 2 trở lên.");
            if (price == null || price <= 0) throw new BusinessException("Đơn vị \"" + n + "\": vui lòng nhập giá bán.");
            if (!names.add(n.toLowerCase())) throw new BusinessException("Tên đơn vị \"" + n + "\" bị trùng.");
            p.getUnits().add(new ProductUnit(p, n, factor, price));
        }
        if (files.isPresent(image)) p.setImage("/media/products/" + files.store(FileStorageService.Kind.PRODUCTS, image));
        else if (f.isRemoveImage()) p.setImage(null);
        productRepo.save(p);
        catalogService.syncMasters();
        notifications.log(currentUser.get(), id == null ? "product.create" : "product.update", "#" + p.getId() + " " + p.getName() + " - giá " + p.getPrice());
        Flash.success(ra, "Đã lưu sản phẩm.");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/toggle")
    @Transactional
    public String toggleProduct(@PathVariable Long id) {
        Product p = productRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy sản phẩm."));
        p.setActive(!p.isActive());
        notifications.log(currentUser.get(), "product.toggle", p.getName() + (p.isActive() ? " - bật" : " - tắt"));
        return "redirect:/admin/products";
    }

    /* ---------------- Nhà cung cấp ---------------- */

    @GetMapping("/suppliers")
    public String suppliers(@RequestParam(required = false) Long edit, Model model) {
        List<Supplier> list = supplierRepo.findAllByOrderByNameAsc();
        Map<Long, Long> value = new HashMap<>();
        for (Supplier s : list) {
            value.put(s.getId(), receiptRepo.findBySupplierAndStatus(s, ApprovalStatus.APPROVED).stream().mapToLong(Receipt::getTotal).sum());
        }
        Map<Long, InventoryService.Debt> debts = new HashMap<>();
        for (Supplier s : list) debts.put(s.getId(), inventoryService.debt(s));
        model.addAttribute("suppliers", list);
        model.addAttribute("value", value);
        model.addAttribute("debts", debts);
        model.addAttribute("edit", edit == null ? new Supplier() : supplierRepo.findById(edit).orElse(new Supplier()));
        model.addAttribute("title", "Nhà cung cấp");
        return "admin/suppliers";
    }

    @PostMapping({"/suppliers", "/suppliers/{id}"})
    @Transactional
    public String saveSupplier(@PathVariable(required = false) Long id, @RequestParam String name,
                               @RequestParam(required = false) String phone, @RequestParam(required = false) String email,
                               @RequestParam(required = false) String address, @RequestParam(required = false) String taxCode,
                               @RequestParam(required = false) Integer paymentTermDays, RedirectAttributes ra) {
        if (Texts.trim(name).length() < 2) throw new BusinessException("Vui lòng nhập tên nhà cung cấp.");
        Supplier s = id == null ? new Supplier() : supplierRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy nhà cung cấp."));
        s.setName(Texts.trim(name, 200));
        s.setPhone(Texts.emptyToNull(phone));
        s.setEmail(Texts.emptyToNull(email));
        s.setAddress(Texts.emptyToNull(address));
        s.setTaxCode(Texts.emptyToNull(Texts.trim(taxCode, 20)));
        s.setPaymentTermDays(paymentTermDays == null ? null : Math.max(0, paymentTermDays));
        supplierRepo.save(s);
        notifications.log(currentUser.get(), "supplier.save", s.getName());
        Flash.success(ra, "Đã lưu nhà cung cấp.");
        return "redirect:/admin/suppliers";
    }

    /* ---------------- Mã giảm giá ---------------- */

    @GetMapping("/vouchers")
    public String vouchers(@RequestParam(required = false) Long edit, Model model) {
        Voucher v = edit == null ? null : voucherRepo.findById(edit).orElse(null);
        if (v == null) {
            v = new Voucher();
            v.setStartDate(LocalDate.now());
            v.setEndDate(LocalDate.now().plusDays(30));
        }
        model.addAttribute("vouchers", voucherRepo.findAllByOrderByIdDesc());
        model.addAttribute("edit", v);
        model.addAttribute("types", VoucherType.values());
        model.addAttribute("tiers", MemberTier.values());
        model.addAttribute("categories", categoryService.tree());
        model.addAttribute("title", "Mã giảm giá");
        return "admin/vouchers";
    }

    @PostMapping({"/vouchers", "/vouchers/{id}"})
    @Transactional
    public String saveVoucher(@PathVariable(required = false) Long id, @RequestParam String code,
                              @RequestParam(required = false) String description, @RequestParam VoucherType type,
                              @RequestParam long value, @RequestParam(defaultValue = "0") long minOrder,
                              @RequestParam(required = false) Long maxDiscount, @RequestParam(required = false) Integer usageLimit,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                              @RequestParam(defaultValue = "false") boolean active,
                              @RequestParam(defaultValue = "false") boolean showInWallet,
                              @RequestParam(required = false) MemberTier minTier, @RequestParam(required = false) Long categoryId,
                              @RequestParam(required = false) Integer perUserLimit, @RequestParam(defaultValue = "false") boolean newCustomerOnly,
                              RedirectAttributes ra) {
        String c = Texts.trim(code).toUpperCase();
        if (!c.matches("[A-Z0-9_-]{3,30}")) throw new BusinessException("Mã chỉ gồm chữ in hoa, số, \"-\" hoặc \"_\" (3-30 ký tự).");
        if (value <= 0 || (type == VoucherType.PERCENT && value > 100)) throw new BusinessException("Giá trị giảm không hợp lệ.");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) throw new BusinessException("Ngày bắt đầu phải trước ngày kết thúc.");
        Optional<Voucher> dup = voucherRepo.findByCodeIgnoreCase(c);
        if (dup.isPresent() && !dup.get().getId().equals(id)) throw new BusinessException("Mã giảm giá đã tồn tại.");
        Voucher v = id == null ? new Voucher() : voucherRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy mã."));
        v.setCode(c);
        v.setDescription(Texts.emptyToNull(Texts.trim(description, 300)));
        v.setType(type);
        v.setValue(value);
        v.setMinOrder(Math.max(0, minOrder));
        v.setMaxDiscount(maxDiscount == null || maxDiscount <= 0 ? null : maxDiscount);
        v.setUsageLimit(usageLimit == null || usageLimit <= 0 ? null : usageLimit);
        v.setStartDate(startDate);
        v.setEndDate(endDate);
        v.setActive(active);
        v.setShowInWallet(showInWallet);
        v.setMinTier(minTier);
        v.setCategory(categoryId == null ? null : categoryRepo.findById(categoryId).orElse(null));
        v.setPerUserLimit(perUserLimit == null || perUserLimit <= 0 ? null : perUserLimit);
        v.setNewCustomerOnly(newCustomerOnly ? Boolean.TRUE : null);
        voucherRepo.save(v);
        notifications.log(currentUser.get(), "voucher.save", c);
        Flash.success(ra, "Đã lưu mã " + c + ".");
        return "redirect:/admin/vouchers";
    }
}
