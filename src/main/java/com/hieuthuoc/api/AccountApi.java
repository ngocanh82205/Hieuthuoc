package com.hieuthuoc.api;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Validator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

/** Tài khoản khách hàng: hồ sơ, hồ sơ sức khỏe, sổ địa chỉ, đơn thuốc, thông báo, sản phẩm yêu thích. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class AccountApi {
    private final CurrentUser currentUser;
    private final OrderService orders;
    private final CustomerCareService care;
    private final ProductService products;

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> profile() {
        User u = currentUser.get();
        CustomerCareService.TierInfo t = care.tierOf(u);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", Dto.UserDto.of(u));
        data.put("tier", Map.of("code", t.tier().name(), "label", t.tier().getLabel(), "spent", t.spent(), "toNext", t.toNext(), "progress", t.progress()));
        return Api.ok(data);
    }

    private boolean taken(String field, String value, Long exceptId) {
        return em.createQuery("select count(u) from User u where " + field + " = :v and u.id <> :id", Long.class)
                .setParameter("v", value).setParameter("id", exceptId).getSingleResult() > 0;
    }

    /** Body: {"fullName", "phone", "email", "gender": "Nam|Nữ|Khác", "birthday": "yyyy-MM-dd"} */
    @PutMapping
    @Transactional
    public Map<String, Object> updateProfile(@RequestBody Map<String, Object> body) {
        User u = currentUser.get();
        Form f = Api.form(body);
        Validator.of(f).label("fullName", "Họ tên").label("birthday", "Ngày sinh").label("gender", "Giới tính")
                .required("fullName").min("fullName", 2).max("fullName", 100)
                .required("phone").phone("phone", "Số điện thoại không hợp lệ.")
                .unique("phone", v -> taken("u.phone", v, u.getId()), "Số điện thoại đã được tài khoản khác sử dụng.")
                .email("email").max("email", 150).unique("email", v -> taken("lower(u.email)", v.toLowerCase(), u.getId()))
                .in("gender", List.of("Nam", "Nữ", "Khác")).date("birthday")
                .rule("birthday", f.str("birthday") == null || !f.str("birthday").matches("\\d{4}-\\d{2}-\\d{2}")
                        || LocalDate.parse(f.str("birthday")).isBefore(LocalDate.now()), "Ngày sinh phải trước hôm nay.")
                .check();
        u.setFullName(f.str("fullName"));
        u.setPhone(f.str("phone"));
        if (f.str("email") != null) u.setEmail(f.str("email").toLowerCase());
        u.setGender(f.str("gender"));
        u.setBirthday(f.str("birthday") != null ? LocalDate.parse(f.str("birthday")) : null);
        em.merge(u);
        return Api.ok(Dto.UserDto.of(u), "Đã cập nhật thông tin cá nhân.");
    }

    /** Body: {"allergies", "chronicConditions", "pregnancy": true|false} - dùng cho cảnh báo an toàn thuốc. */
    @PutMapping("/health")
    @Transactional
    public Map<String, Object> updateHealth(@RequestBody Map<String, Object> body) {
        User u = currentUser.get();
        Form f = Api.form(body);
        u.setAllergies(f.str("allergies") != null ? Texts.limit(f.str("allergies"), 500, "") : null);
        u.setChronicConditions(f.str("chronicConditions") != null ? Texts.limit(f.str("chronicConditions"), 500, "") : null);
        u.setPregnancy(f.bool("pregnancy"));
        em.merge(u);
        return Api.ok(Dto.UserDto.of(u), "Đã lưu hồ sơ sức khỏe.");
    }

    /* ---------------- Sổ địa chỉ ---------------- */

    private List<Address> addresses(User u) {
        return em.createQuery("select a from Address a where a.user.id = :u order by a.isDefault desc, a.id", Address.class).setParameter("u", u.getId()).getResultList();
    }

    private Address myAddress(Long id) {
        Address a = em.find(Address.class, id);
        if (a == null || !a.getUser().getId().equals(currentUser.get().getId())) throw BusinessException.notFound("Không tìm thấy địa chỉ.");
        return a;
    }

    @GetMapping("/addresses")
    @Transactional(readOnly = true)
    public Map<String, Object> listAddresses() {
        return Api.ok(addresses(currentUser.get()).stream().map(Dto.AddressDto::of).toList());
    }

    private void fill(Address a, Form f) {
        Validator.of(f).label("addressLine", "Địa chỉ").label("recipient", "Người nhận")
                .required("recipient").min("recipient", 2).max("recipient", 100)
                .required("phone").phone("phone", "Số điện thoại không hợp lệ.")
                .required("addressLine").max("addressLine", 300)
                .rule("addressLine", f.str("addressLine") == null || Texts.mbLen(f.str("addressLine")) >= 10, "Vui lòng nhập địa chỉ chi tiết (tối thiểu 10 ký tự).")
                .max("province", 60).integer("ghnProvinceId").integer("ghnDistrictId").max("ghnWardCode", 20)
                .check();
        a.setRecipient(f.str("recipient"));
        a.setPhone(f.str("phone"));
        a.setAddressLine(f.str("addressLine"));
        a.setProvince(f.str("province"));
        a.setGhnProvinceId(f.longVal("ghnProvinceId") != null ? f.longVal("ghnProvinceId").intValue() : null);
        a.setGhnDistrictId(f.longVal("ghnDistrictId") != null ? f.longVal("ghnDistrictId").intValue() : null);
        a.setGhnWardCode(f.str("ghnWardCode"));
    }

    private void makeDefault(Address a) {
        em.createQuery("update Address x set x.isDefault = false where x.user.id = :u").setParameter("u", a.getUser().getId()).executeUpdate();
        a.setDefault(true);
    }

    /** Body: {"recipient", "phone", "addressLine", "province", "ghnProvinceId", "ghnDistrictId", "ghnWardCode", "isDefault"} */
    @PostMapping("/addresses")
    @Transactional
    public ResponseEntity<Map<String, Object>> addAddress(@RequestBody Map<String, Object> body) {
        User u = currentUser.get();
        Form f = Api.form(body);
        Address a = new Address();
        fill(a, f);
        a.setUser(u);
        boolean first = addresses(u).isEmpty();
        em.persist(a);
        if (first || f.bool("isDefault")) makeDefault(a);
        return Api.created(Dto.AddressDto.of(a), "Đã thêm địa chỉ.");
    }

    @PutMapping("/addresses/{id}")
    @Transactional
    public Map<String, Object> updateAddress(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Address a = myAddress(id);
        Form f = Api.form(body);
        fill(a, f);
        if (f.bool("isDefault")) makeDefault(a);
        return Api.ok(Dto.AddressDto.of(a), "Đã cập nhật địa chỉ.");
    }

    @DeleteMapping("/addresses/{id}")
    @Transactional
    public Map<String, Object> deleteAddress(@PathVariable Long id) {
        em.remove(myAddress(id));
        return Api.ok(null, "Đã xóa địa chỉ.");
    }

    /* ---------------- Đơn thuốc ---------------- */

    @GetMapping("/prescriptions")
    @Transactional(readOnly = true)
    public Map<String, Object> prescriptions() {
        return Api.ok(em.createQuery("select p from Prescription p left join fetch p.order where p.user.id = :u order by p.id desc", Prescription.class)
                .setParameter("u", currentUser.get().getId()).getResultList().stream().map(Dto.PrescriptionDto::of).toList());
    }

    /** multipart/form-data: "prescription" (ảnh đơn thuốc), "note" - gửi đơn thuốc nhờ dược sĩ lên đơn. */
    @PostMapping(value = "/prescriptions", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<Map<String, Object>> submitPrescription(@RequestParam(required = false) MultipartFile prescription,
                                                                  @RequestParam(required = false) String note) {
        Prescription p = orders.submitStandalonePrescription(currentUser.get(), prescription, note);
        return Api.created(Dto.PrescriptionDto.of(p), "Đã gửi đơn thuốc. Dược sĩ sẽ liên hệ báo giá sớm nhất.");
    }

    /* ---------------- Thông báo ---------------- */

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public Map<String, Object> notifications(@RequestParam(defaultValue = "1") int page) {
        Long uid = currentUser.get().getId();
        Page<UserNotification> p = new com.hieuthuoc.web.Jpql("UserNotification n", "n").where("n.userId = :u", "u", uid).page(em, UserNotification.class, "n.id desc", 20, page);
        Map<String, Object> out = Api.page(p, Dto.NotificationDto::of);
        out.put("unread", em.createQuery("select count(n) from UserNotification n where n.userId = :u and n.seen = false", Long.class).setParameter("u", uid).getSingleResult());
        return out;
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public Map<String, Object> readAll() {
        int n = em.createQuery("update UserNotification n set n.seen = true where n.userId = :u and n.seen = false").setParameter("u", currentUser.get().getId()).executeUpdate();
        return Api.ok(Map.of("updated", n), "Đã đánh dấu đã đọc.");
    }

    /* ---------------- Yêu thích ---------------- */

    @GetMapping("/wishlist")
    @Transactional(readOnly = true)
    public Map<String, Object> wishlist() {
        List<Product> list = new ArrayList<>(em.createQuery("select p from Product p where p.id in (select w.product.id from WishlistItem w where w.userId = :u)", Product.class)
                .setParameter("u", currentUser.get().getId()).getResultList());
        products.enrich(list);
        return Api.ok(list.stream().map(Dto.ProductDto::of).toList());
    }

    /** Thêm / bỏ sản phẩm khỏi danh sách yêu thích. */
    @PostMapping("/wishlist/{productId}")
    @Transactional
    public Map<String, Object> toggleWishlist(@PathVariable long productId) {
        boolean added = care.toggleWishlist(currentUser.get(), productId);
        return Api.ok(Map.of("productId", productId, "inWishlist", added), added ? "Đã thêm vào yêu thích." : "Đã bỏ khỏi yêu thích.");
    }
}
