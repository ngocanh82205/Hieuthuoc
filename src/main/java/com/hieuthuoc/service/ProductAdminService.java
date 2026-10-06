package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.web.Validator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Quản lý sản phẩm (thêm / sửa / xóa) dùng chung cho trang quản trị và REST API.
 * Tham số theo tên trường form: name, drug_type, price, old_price, unit, unit_names[], unit_factors[], unit_prices[],
 * category_id, active_ingredient, strength, ..., equivalent_ids[], active, slug, meta_title, meta_description.
 */
@Service
@RequiredArgsConstructor
public class ProductAdminService {
    private final FileStorageService files;
    private final NotificationService notifications;

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

    /** @param p sản phẩm cần sửa, null = thêm mới */
    @Transactional
    public Product save(Product p, Form in, MultipartFile image, User by) {
        if (files.isPresent(image)) {
            Validator.of(in).rule("image", image.getContentType() != null && image.getContentType().startsWith("image/"), "Chỉ nhận file ảnh.")
                    .rule("image", image.getSize() <= 5L * 1024 * 1024, "Ảnh tối đa 5MB.").check();
        }
        String name = Texts.trim(in.get("name"));
        DrugType type = DrugType.tryFrom(Texts.trim(in.get("drug_type")));
        if (type == null) type = DrugType.OTC;
        long price = Texts.toInt(in.get("price"), 0);
        long old = Texts.toInt(in.get("old_price"), 0);
        String unit = Texts.trim(in.get("unit"));
        List<String> errors = new ArrayList<>();
        if (Texts.mbLen(name) < 2) errors.add("Tên sản phẩm tối thiểu 2 ký tự.");
        if (price <= 0) errors.add("Giá bán phải lớn hơn 0.");
        if (unit.isEmpty()) errors.add("Vui lòng nhập đơn vị tính.");
        if (type.isPrescription() && old > 0) errors.add("Không áp dụng giảm giá/khuyến mại cho thuốc kê đơn.");
        if (old > 0 && old <= price) errors.add("Giá gốc (trước giảm) phải lớn hơn giá bán.");
        if (!errors.isEmpty()) throw new ValidationException(errors);

        boolean isNew = p == null;
        if (isNew) p = new Product();
        String slugInput = Texts.trim(in.get("slug"));
        String wantSlug = Texts.slugify(slugInput.isEmpty() ? name : slugInput);
        if (wantSlug.isEmpty()) wantSlug = "san-pham";
        if (isNew || !wantSlug.equals(p.getSlug())) {
            String slug = wantSlug;
            if (exists("select count(x) from Product x where x.slug = :s and x.id <> :id", "s", slug, "id", isNew ? 0L : p.getId())) {
                if (!isNew && !slugInput.isEmpty()) throw new BusinessException("Đường dẫn \"" + slug + "\" đã được sản phẩm khác sử dụng.");
                slug += "-" + Long.toString(System.currentTimeMillis(), 36);
            }
            p.setSlug(slug);
        }
        // Đơn vị quy đổi (tối đa 3 dòng)
        List<String> unitNames = in.list("unit_names"), factors = in.list("unit_factors"), prices = in.list("unit_prices");
        Set<String> seen = new HashSet<>(List.of(unit.toLowerCase()));
        List<ProductUnit> units = new ArrayList<>();
        for (int i = 0; i < unitNames.size(); i++) {
            String un = Texts.trim(unitNames.get(i), 30);
            if (un.isEmpty()) continue;
            int f = Texts.toInt(i < factors.size() ? factors.get(i) : null, 0);
            long pr = Texts.toInt(i < prices.size() ? prices.get(i) : null, 0);
            if (f < 2) throw new BusinessException("Đơn vị \"" + un + "\": số " + unit + " quy đổi phải từ 2 trở lên.");
            if (pr <= 0) throw new BusinessException("Đơn vị \"" + un + "\": vui lòng nhập giá bán.");
            if (!seen.add(un.toLowerCase())) throw new BusinessException("Tên đơn vị \"" + un + "\" bị trùng.");
            ProductUnit pu = new ProductUnit();
            pu.setName(un);
            pu.setFactor(f);
            pu.setPrice(pr);
            units.add(pu);
        }
        Long catId = Texts.toLong(in.get("category_id"));
        int maxPer = Texts.toInt(in.get("max_per_order"), 0);
        p.setCategory(catId != null ? em.find(Category.class, catId) : null);
        p.setName(Texts.trim(name, 200));
        p.setActiveIngredient(clean(in.get("active_ingredient"), 200));
        p.setStrength(clean(in.get("strength"), 100));
        p.setDosageForm(clean(in.get("dosage_form"), 100));
        p.setPackaging(clean(in.get("packaging"), 150));
        p.setRegistrationNo(clean(in.get("registration_no"), 100));
        p.setManufacturer(clean(in.get("manufacturer"), 150));
        p.setCountry(clean(in.get("country"), 80));
        p.setDrugType(type);
        p.setUnit(Texts.trim(unit, 30));
        p.setPrice(price);
        p.setOldPrice(old > 0 ? old : null);
        p.setMaxPerOrder(maxPer > 0 ? maxPer : null);
        p.setMinStock(Math.max(0, Texts.toInt(in.get("min_stock"), 10)));
        p.setWeightGram(Math.max(1, Texts.toInt(in.get("weight_gram"), 200)));
        p.setDescription(clean(in.get("description"), 2000));
        p.setUsageInstruction(clean(in.get("usage_instruction"), 2000));
        p.setContraindications(clean(in.get("contraindications"), 1000));
        p.setSideEffects(clean(in.get("side_effects"), 1000));
        p.setActive(in.bool("active"));
        p.setMetaTitle(clean(in.get("meta_title"), 150));
        p.setMetaDescription(clean(in.get("meta_description"), 300));
        if (files.isPresent(image)) {
            p.setImage(files.store("products", image));
        } else if (in.bool("remove_image")) {
            p.setImage(null);
        }
        if (isNew) em.persist(p);
        p.getUnits().clear();
        em.flush();
        for (ProductUnit pu : units) {
            pu.setProduct(p);
            p.getUnits().add(pu);
        }
        Long selfId = p.getId();
        List<Long> eq = in.list("equivalent_ids").stream().map(Texts::toLong).filter(Objects::nonNull).filter(x -> !x.equals(selfId)).toList();
        p.getEquivalents().clear();
        if (!eq.isEmpty()) p.getEquivalents().addAll(em.createQuery("select x from Product x where x.id in :ids", Product.class).setParameter("ids", eq).getResultList());
        notifications.log(by, isNew ? "product.create" : "product.update", "#" + p.getId() + " " + p.getName() + " - giá " + p.getPrice());
        return p;
    }

    /**
     * Xóa sản phẩm: chỉ khi chưa phát sinh giao dịch (lô kho, phiếu nhập, đơn hàng) để không mất lịch sử
     * và truy xuất nguồn gốc theo GPP. Sản phẩm đã có giao dịch thì dùng "Ngừng bán".
     */
    @Transactional
    public void delete(Product p, User by) {
        List<String> used = new ArrayList<>();
        if (exists("select count(b) from Batch b where b.productId = :p", "p", p.getId())) used.add("lô hàng trong kho");
        if (exists("select count(r) from ReceiptItem r where r.product.id = :p", "p", p.getId())) used.add("phiếu nhập");
        if (exists("select count(o) from OrderItem o where o.productId = :p", "p", p.getId())) used.add("đơn hàng");
        if (!used.isEmpty()) {
            throw new BusinessException("Không thể xóa \"" + p.getName() + "\" vì đã có " + String.join(", ", used)
                    + ". Hãy dùng \"Ngừng bán\" để ẩn sản phẩm mà vẫn giữ lịch sử.", 409);
        }
        for (String sql : List.of("delete from product_equivalents where product_id = :p or equivalent_id = :p", "delete from wishlist_items where product_id = :p",
                "delete from product_views where product_id = :p", "delete from reviews where product_id = :p", "delete from promotion_items where product_id = :p",
                "delete from suggested_cart_items where product_id = :p")) {
            em.createNativeQuery(sql).setParameter("p", p.getId()).executeUpdate();
        }
        em.remove(p);
        notifications.log(by, "product.delete", p.getName());
    }

    @Transactional
    public Product toggle(Product p, User by) {
        p.setActive(!p.isActive());
        notifications.log(by, "product.toggle", p.getName() + (p.isActive() ? " - bật" : " - tắt"));
        return p;
    }
}
