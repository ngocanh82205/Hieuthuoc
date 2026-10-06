package com.hieuthuoc.web;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
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

import java.time.LocalDate;
import java.util.*;

/** Tài khoản khách hàng: thông tin, hồ sơ sức khỏe, sổ địa chỉ, đơn hàng, gửi đơn thuốc, yêu thích. */
@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {
    private final OrderService orders;
    private final CustomerCareService care;
    private final SettingService settings;
    private final AccountService accounts;
    private final PaymentService payments;
    private final ProductService products;
    private final GhnService ghn;
    private final Cart cart;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    @Transactional(readOnly = true)
    public String profile(Model model) {
        User u = currentUser.get();
        model.addAttribute("title", "Tài khoản của tôi");
        model.addAttribute("profile", u);
        model.addAttribute("orderCount", em.createQuery("select count(o) from Order o where o.user.id = :u", Long.class).setParameter("u", u.getId()).getSingleResult());
        model.addAttribute("tier", care.tierOf(u));
        model.addAttribute("tiers", settings.tiers());
        model.addAttribute("pointValue", settings.getInt("point_value"));
        return "account/profile";
    }

    @PostMapping("/profile")
    @Transactional
    public String updateProfile(@RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        User u = currentUser.get();
        Form f = new Form(params);
        Validator.of(f)
                .required("full_name").min("full_name", 2).max("full_name", 100)
                .required("phone").phone("phone", "Số điện thoại không hợp lệ.")
                .unique("phone", v -> taken("u.phone", v, u.getId()), "Số điện thoại đã được tài khoản khác sử dụng.")
                .email("email").max("email", 150).unique("email", v -> taken("lower(u.email)", v.toLowerCase(), u.getId()))
                .in("gender", List.of("Nam", "Nữ", "Khác"))
                .date("birthday")
                .rule("birthday", f.str("birthday") == null || !f.str("birthday").matches("\\d{4}-\\d{2}-\\d{2}")
                        || LocalDate.parse(f.str("birthday")).isBefore(LocalDate.now()), "Ngày sinh phải trước hôm nay.")
                .check();
        u.setFullName(f.str("full_name"));
        u.setPhone(f.str("phone"));
        if (f.str("email") != null) u.setEmail(f.str("email").toLowerCase());
        u.setGender(f.str("gender"));
        u.setBirthday(f.str("birthday") != null ? LocalDate.parse(f.str("birthday")) : null);
        em.merge(u);
        Web.success(ra, "Đã cập nhật thông tin cá nhân.");
        return "redirect:/account";
    }

    private boolean taken(String field, String value, Long exceptId) {
        return em.createQuery("select count(u) from User u where " + field + " = :v and u.id <> :id", Long.class)
                .setParameter("v", value).setParameter("id", exceptId).getSingleResult() > 0;
    }

    @PostMapping("/health")
    @Transactional
    public String updateHealth(@RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        User u = currentUser.get();
        Form f = new Form(params);
        u.setAllergies(f.str("allergies") != null ? Texts.limit(f.str("allergies"), 500, "") : null);
        u.setChronicConditions(f.str("chronic_conditions") != null ? Texts.limit(f.str("chronic_conditions"), 500, "") : null);
        u.setPregnancy(f.bool("pregnancy"));
        em.merge(u);
        Web.success(ra, "Đã lưu hồ sơ sức khỏe. Dược sĩ sẽ dùng thông tin này để tư vấn an toàn hơn.");
        return "redirect:/account#health";
    }

    @PostMapping("/password")
    @Transactional
    public String changePassword(@RequestParam(required = false) String current, @RequestParam(required = false) String password,
                                 @RequestParam(name = "password_confirmation", required = false) String confirm, RedirectAttributes ra) {
        accounts.changePassword(currentUser.get(), current, password, confirm);
        Web.success(ra, "Đổi mật khẩu thành công.");
        return "redirect:/account";
    }

    /* ---------------- Sổ địa chỉ ---------------- */

    @GetMapping("/addresses")
    @Transactional(readOnly = true)
    public String addresses(Model model) {
        model.addAttribute("title", "Sổ địa chỉ");
        model.addAttribute("ghnEnabled", ghn.enabled());
        model.addAttribute("provinces", SettingService.PROVINCES);
        model.addAttribute("addresses", myAddresses(currentUser.get()));
        return "account/addresses";
    }

    private List<Address> myAddresses(User u) {
        return em.createQuery("select a from Address a where a.user.id = :u order by a.isDefault desc, a.id", Address.class)
                .setParameter("u", u.getId()).getResultList();
    }

    @PostMapping("/addresses")
    @Transactional
    public String addAddress(@RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        User u = currentUser.get();
        Form f = new Form(params);
        Validator.of(f)
                .required("recipient").min("recipient", 2).max("recipient", 100)
                .required("phone").phone("phone", "Số điện thoại không hợp lệ.")
                .required("address_line").max("address_line", 300)
                .rule("address_line", f.str("address_line") == null || Texts.mbLen(f.str("address_line")) >= 10, "Vui lòng nhập địa chỉ chi tiết (tối thiểu 10 ký tự).")
                .max("province", 60).integer("ghn_province_id").integer("ghn_district_id").max("ghn_ward_code", 20)
                .check();
        boolean isDefault = f.bool("make_default") || myAddresses(u).isEmpty();
        if (isDefault) {
            em.createQuery("update Address a set a.isDefault = false where a.user.id = :u").setParameter("u", u.getId()).executeUpdate();
        }
        Address a = new Address();
        a.setUser(u);
        a.setRecipient(f.str("recipient"));
        a.setPhone(f.str("phone"));
        a.setAddressLine(f.str("address_line"));
        a.setProvince(f.str("province"));
        a.setGhnProvinceId(f.longVal("ghn_province_id") != null ? f.longVal("ghn_province_id").intValue() : null);
        a.setGhnDistrictId(f.longVal("ghn_district_id") != null ? f.longVal("ghn_district_id").intValue() : null);
        a.setGhnWardCode(f.str("ghn_ward_code"));
        a.setDefault(isDefault);
        em.persist(a);
        Web.success(ra, "Đã thêm địa chỉ.");
        return "redirect:/account/addresses";
    }

    private Address myAddress(Long id) {
        Address a = em.find(Address.class, id);
        if (a == null || !a.getUser().getId().equals(currentUser.get().getId())) throw BusinessException.notFound();
        return a;
    }

    @PostMapping("/addresses/{id}/default")
    @Transactional
    public String setDefault(@PathVariable Long id) {
        Address a = myAddress(id);
        em.createQuery("update Address x set x.isDefault = false where x.user.id = :u").setParameter("u", a.getUser().getId()).executeUpdate();
        em.refresh(a);
        a.setDefault(true);
        return "redirect:/account/addresses";
    }

    @PostMapping("/addresses/{id}/delete")
    @Transactional
    public Object deleteAddress(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
        em.remove(myAddress(id));
        if (Web.wantsJson(req)) return ResponseEntity.ok(Map.of("ok", true, "message", "Đã xóa địa chỉ khỏi sổ địa chỉ thành công."));
        Web.info(ra, "Đã xóa địa chỉ.");
        return "redirect:/account/addresses";
    }

    /* ---------------- Đơn hàng ---------------- */

    private Order myOrder(String code) {
        return em.createQuery("select o from Order o where o.code = :c and o.user.id = :u", Order.class)
                .setParameter("c", code).setParameter("u", currentUser.get().getId())
                .getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
    }

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public String orders(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page, Model model) {
        OrderStatus st = null;
        try {
            st = status == null || status.isEmpty() ? null : OrderStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            // trạng thái không hợp lệ -> bỏ lọc
        }
        Long uid = currentUser.get().getId();
        String where = " from Order o where o.user.id = :u" + (st != null ? " and o.status = :s" : "");
        var count = em.createQuery("select count(o)" + where, Long.class).setParameter("u", uid);
        var list = em.createQuery("select o" + where + " order by o.createdAt desc, o.id desc", Order.class).setParameter("u", uid);
        if (st != null) {
            count.setParameter("s", st);
            list.setParameter("s", st);
        }
        int per = 10;
        int pg = Math.max(1, page);
        model.addAttribute("title", "Đơn hàng của tôi");
        model.addAttribute("orders", new Page<>(list.setFirstResult((pg - 1) * per).setMaxResults(per).getResultList(), count.getSingleResult(), per, pg));
        model.addAttribute("status", st);
        model.addAttribute("statuses", OrderStatus.values());
        return "account/orders";
    }

    @GetMapping("/orders/{code}")
    @Transactional(readOnly = true)
    public String order(@PathVariable String code, Model model) {
        Order o = myOrder(code);
        List<Long> productIds = o.getItems().stream().map(OrderItem::getProductId).toList();
        List<Long> reviewed = productIds.isEmpty() ? List.of() : em.createQuery("select r.product.id from Review r where r.user.id = :u and r.product.id in :p", Long.class)
                .setParameter("u", o.getUser().getId()).setParameter("p", productIds).getResultList();
        model.addAttribute("title", "Đơn hàng " + o.getCode());
        model.addAttribute("order", o);
        model.addAttribute("canPay", payments.canPay(o));
        model.addAttribute("canReturn", orders.canRequestReturn(o));
        model.addAttribute("returnBlockedByRx", orders.returnBlockedByRx(o));
        model.addAttribute("showBank", orders.showBankTransfer(o));
        model.addAttribute("vietQr", orders.showBankTransfer(o) ? payments.vietQrUrl(o) : null);
        model.addAttribute("addresses", myAddresses(o.getUser()));
        model.addAttribute("paymentMethods", settings.enabledPaymentMethods());
        model.addAttribute("provinces", SettingService.PROVINCES);
        model.addAttribute("reviewed", reviewed);
        model.addAttribute("shippingMethods", ShippingMethod.values());
        return "account/order";
    }

    @PostMapping("/orders/{code}/cancel")
    @Transactional
    public String cancel(@PathVariable String code, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        orders.cancelByCustomer(myOrder(code), currentUser.get(), reason);
        Web.info(ra, "Đã hủy đơn hàng " + code + ".");
        return "redirect:/account/orders/" + code;
    }

    @PostMapping("/orders/{code}/prescription")
    @Transactional
    public String reupload(@PathVariable String code, @RequestParam(required = false) MultipartFile prescription,
                           @RequestParam(name = "rx_note", required = false) String note, RedirectAttributes ra) {
        orders.reuploadPrescription(myOrder(code), currentUser.get(), prescription, note);
        Web.success(ra, "Đã gửi lại đơn thuốc, vui lòng chờ dược sĩ duyệt.");
        return "redirect:/account/orders/" + code;
    }

    @PostMapping("/orders/{code}/return")
    @Transactional
    public String requestReturn(@PathVariable String code, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        orders.requestReturn(myOrder(code), currentUser.get(), reason);
        Web.success(ra, "Đã gửi yêu cầu đổi/trả. Nhà thuốc sẽ liên hệ với bạn.");
        return "redirect:/account/orders/" + code;
    }

    @PostMapping("/orders/{code}/confirm")
    @Transactional
    public String confirm(@PathVariable String code, @RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        Order o = myOrder(code);
        orders.confirmByCustomer(o, currentUser.get(), new Form(params));
        em.refresh(o);
        if (o.getPaymentMethod().isGateway()) return "redirect:/account/orders/" + code + "/pay";
        Web.success(ra, "Đã xác nhận đơn hàng. Nhà thuốc sẽ xử lý sớm nhất."
                + (o.getPaymentMethod() == PaymentMethod.BANK_TRANSFER ? " Vui lòng chuyển khoản theo hướng dẫn." : ""));
        return "redirect:/account/orders/" + code;
    }

    @PostMapping("/orders/{code}/reorder")
    @Transactional(readOnly = true)
    public String reorder(@PathVariable String code, RedirectAttributes ra) {
        List<String> skipped = orders.reorder(myOrder(code), cart);
        if (skipped.isEmpty()) Web.success(ra, "Đã thêm các sản phẩm vào giỏ hàng.");
        else Web.warning(ra, "Đã thêm vào giỏ. Không thêm được: " + String.join(", ", skipped));
        return "redirect:/cart";
    }

    /* ---------------- Gửi đơn thuốc (không chọn sản phẩm) ---------------- */

    @GetMapping("/prescriptions")
    @Transactional(readOnly = true)
    public String prescriptions(Model model) {
        model.addAttribute("title", "Gửi đơn thuốc");
        model.addAttribute("list", em.createQuery("select p from Prescription p left join fetch p.order where p.user.id = :u and p.standalone = true order by p.createdAt desc",
                Prescription.class).setParameter("u", currentUser.get().getId()).getResultList());
        return "account/prescriptions";
    }

    @PostMapping("/prescriptions")
    @Transactional
    public String submitPrescription(@RequestParam(required = false) MultipartFile prescription, @RequestParam(required = false) String note, RedirectAttributes ra) {
        orders.submitStandalonePrescription(currentUser.get(), prescription, note);
        Web.success(ra, "Đã gửi đơn thuốc. Dược sĩ sẽ đọc đơn, lên đơn hàng và báo giá cho bạn.");
        return "redirect:/account/prescriptions";
    }

    /* ---------------- Yêu thích ---------------- */

    @GetMapping("/wishlist")
    @Transactional(readOnly = true)
    public String wishlist(Model model) {
        List<Product> list = new ArrayList<>(em.createQuery("select p from Product p where p.id in (select w.product.id from WishlistItem w where w.userId = :u)", Product.class)
                .setParameter("u", currentUser.get().getId()).getResultList());
        products.enrich(list);
        model.addAttribute("title", "Sản phẩm yêu thích");
        model.addAttribute("products", list);
        return "account/wishlist";
    }
}
