package com.hieuthuoc.api;

import com.hieuthuoc.entity.Product;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Jpql;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/** Quản trị: quản lý sản phẩm (CRUD) và báo cáo kinh doanh theo khoảng ngày. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminApi {
    private final ReportService reports;
    private final SettingService settings;
    private final ProductAdminService productAdmin;
    private final StockService stock;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    /* ---------------- Sản phẩm ---------------- */

    /** Tất cả sản phẩm (kể cả ngừng bán). Tham số: q, active (true|false), page. */
    @GetMapping("/products")
    @Transactional(readOnly = true)
    public Map<String, Object> products(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean active,
                                        @RequestParam(defaultValue = "1") int page) {
        Page<Product> p = new Jpql("Product p", "p").search(Texts.trim(q), "p.name", "coalesce(p.activeIngredient, '')", "coalesce(p.registrationNo, '')")
                .when(active != null, "p.active = :a", "a", active).page(em, Product.class, "p.id desc", 20, page);
        stock.fill(p.getItems(), false);
        return Api.page(p, Dto.ProductDto::of);
    }

    private Product product(Long id) {
        Product p = em.find(Product.class, id);
        if (p == null) throw BusinessException.notFound("Không tìm thấy sản phẩm.");
        return p;
    }

    private Map<String, Object> detail(Product p) {
        stock.fill(p, false);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("product", Dto.ProductDto.of(p));
        d.put("active", p.isActive());
        d.put("units", p.getUnits().stream().map(u -> Map.of("name", u.getName(), "factor", u.getFactor(), "price", u.getPrice())).toList());
        d.put("categoryId", p.getCategoryId());
        d.put("equivalentIds", p.getEquivalents().stream().map(Product::getId).toList());
        d.put("minStock", p.getMinStock());
        d.put("maxPerOrder", p.getMaxPerOrder());
        d.put("weightGram", p.getWeightGram());
        d.put("registrationNo", p.getRegistrationNo());
        d.put("manufacturer", p.getManufacturer());
        d.put("description", p.getDescription());
        d.put("usageInstruction", p.getUsageInstruction());
        return d;
    }

    @GetMapping("/products/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> productDetail(@PathVariable Long id) {
        return Api.ok(detail(product(id)));
    }

    /** Body JSON (camelCase) -> tham số form của ProductAdminService. */
    private static Form productForm(Map<String, Object> body) {
        Map<String, String> rename = Map.ofEntries(Map.entry("drugType", "drug_type"), Map.entry("oldPrice", "old_price"), Map.entry("categoryId", "category_id"),
                Map.entry("activeIngredient", "active_ingredient"), Map.entry("dosageForm", "dosage_form"), Map.entry("registrationNo", "registration_no"),
                Map.entry("maxPerOrder", "max_per_order"), Map.entry("minStock", "min_stock"), Map.entry("weightGram", "weight_gram"),
                Map.entry("usageInstruction", "usage_instruction"), Map.entry("sideEffects", "side_effects"), Map.entry("metaTitle", "meta_title"),
                Map.entry("metaDescription", "meta_description"), Map.entry("equivalentIds", "equivalent_ids"));
        Map<String, Object> b = new LinkedHashMap<>();
        body.forEach((k, v) -> {
            if (!"units".equals(k)) b.put(rename.getOrDefault(k, k), v);
        });
        if (body.get("units") instanceof List<?> units) {
            List<Object> names = new ArrayList<>(), factors = new ArrayList<>(), prices = new ArrayList<>();
            for (Object o : units) {
                if (o instanceof Map<?, ?> u) {
                    names.add(u.get("name"));
                    factors.add(u.get("factor"));
                    prices.add(u.get("price"));
                }
            }
            b.put("unit_names", names);
            b.put("unit_factors", factors);
            b.put("unit_prices", prices);
        }
        return Api.form(b);
    }

    /**
     * Thêm sản phẩm. Body: {"name", "drugType", "price", "oldPrice", "unit", "units": [{"name","factor","price"}], "categoryId",
     * "activeIngredient", "strength", "dosageForm", "packaging", "registrationNo", "manufacturer", "country", "maxPerOrder", "minStock",
     * "weightGram", "description", "usageInstruction", "contraindications", "sideEffects", "active", "slug", "equivalentIds"}
     */
    @PostMapping("/products")
    @Transactional
    public ResponseEntity<Map<String, Object>> createProduct(@RequestBody Map<String, Object> body) {
        Product p = productAdmin.save(null, productForm(body), null, currentUser.get());
        return Api.created(detail(p), "Đã thêm sản phẩm.");
    }

    /** Cập nhật toàn bộ thông tin sản phẩm (cùng cấu trúc body như khi thêm). */
    @PutMapping("/products/{id}")
    @Transactional
    public Map<String, Object> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Product p = productAdmin.save(product(id), productForm(body), null, currentUser.get());
        return Api.ok(detail(p), "Đã cập nhật sản phẩm.");
    }

    /** Xóa sản phẩm chưa phát sinh giao dịch; đã có lô kho / phiếu nhập / đơn hàng thì trả 409 (dùng ngừng bán). */
    @DeleteMapping("/products/{id}")
    @Transactional
    public Map<String, Object> deleteProduct(@PathVariable Long id) {
        Product p = product(id);
        productAdmin.delete(p, currentUser.get());
        return Api.ok(null, "Đã xóa sản phẩm " + p.getName() + ".");
    }

    /* ---------------- Báo cáo ---------------- */

    /** Tham số: from, to (yyyy-MM-dd; mặc định 30 ngày gần nhất). */
    @GetMapping("/reports/summary")
    @Transactional(readOnly = true)
    public Map<String, Object> summary(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        LocalDate[] range = ReportService.range(from, to);
        int near = settings.getInt("near_expiry_days");
        ReportService.Report r = reports.build(range[0], range[1], near);
        long[] inv = reports.inventoryValue(near);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("from", range[0]);
        d.put("to", range[1]);
        d.put("completedOrders", r.getOrders());
        d.put("netRevenue", r.getRevenue());
        d.put("grossProfit", r.getGrossProfit());
        d.put("marginRate", r.getMarginRate());
        d.put("averageOrderValue", r.getAov());
        d.put("cancelRate", r.getCancelOnlyRate());
        d.put("returnRate", r.getReturnRate());
        d.put("newCustomers", r.getNewCustomers());
        d.put("returningCustomers", r.getReturningCustomers());
        d.put("inventoryValue", Map.of("valid", inv[0], "nearExpiry", inv[1], "expired", inv[2]));
        List<Map<String, Object>> byDay = new ArrayList<>();
        r.getByDay().forEach((day, v) -> byDay.add(Map.of("date", day, "orders", v[0], "revenue", v[1])));
        d.put("byDay", byDay);
        d.put("byCategory", r.getByCategory());
        List<Map<String, Object>> byPayment = new ArrayList<>();
        r.getByPayment().forEach((m, v) -> byPayment.add(Map.of("method", m, "orders", v[0], "revenue", v[1])));
        d.put("byPayment", byPayment);
        d.put("topProducts", r.getTopProducts().stream().map(t -> Map.of("name", t.name(), "unit", t.unit(), "quantity", t.quantity(), "revenue", t.revenue())).toList());
        return Api.ok(d);
    }
}
