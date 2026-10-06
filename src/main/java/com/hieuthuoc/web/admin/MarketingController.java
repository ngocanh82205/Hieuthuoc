package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Admin - Marketing: mã giảm giá (+ gửi email), flash sale / combo / quà tặng, tích điểm & hạng. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class MarketingController {
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final NotificationService notifications;
    private final SettingService settings;
    private final CategoryService categories;
    private final AudienceService audience;
    private final MailService mail;
    private final CurrentUser currentUser;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    @PersistenceContext
    private EntityManager em;

    private static String clean(String v, int max) {
        return Texts.emptyToNull(Texts.trim(v, max));
    }

    private static LocalDate date(String v) {
        if (Texts.isBlank(v)) return null;
        try {
            return LocalDate.parse(v.trim().substring(0, 10));
        } catch (Exception e) {
            throw new BusinessException("Ngày không hợp lệ: " + v);
        }
    }

    private static LocalDateTime dateTime(String v) {
        if (Texts.isBlank(v)) return null;
        try {
            return LocalDateTime.parse(v.trim().length() == 16 ? v.trim() + ":00" : v.trim());
        } catch (Exception e) {
            throw new BusinessException("Thời gian không hợp lệ: " + v);
        }
    }

    private static Integer positive(String v) {
        int x = Texts.toInt(v, 0);
        return x > 0 ? x : null;
    }

    /* ======================= Mã giảm giá ======================= */

    @GetMapping("/vouchers")
    @Transactional(readOnly = true)
    public String vouchers(@RequestParam(required = false) Long edit, Model model) {
        Voucher e = edit != null ? em.find(Voucher.class, edit) : null;
        if (e == null) {
            e = new Voucher();
            e.setStartDate(LocalDate.now());
            e.setEndDate(LocalDate.now().plusDays(30));
        }
        model.addAttribute("title", "Mã giảm giá");
        model.addAttribute("vouchers", em.createQuery("select v from Voucher v left join fetch v.category order by v.id desc", Voucher.class).getResultList());
        model.addAttribute("edit", e);
        model.addAttribute("categories", categories.tree());
        model.addAttribute("audiences", AudienceService.AUDIENCES);
        model.addAttribute("tiers", MemberTier.values());
        return "admin/vouchers";
    }

    @PostMapping({"/vouchers", "/vouchers/{id}"})
    @Transactional
    public String saveVoucher(@PathVariable(required = false) Long id, @RequestParam Map<String, String> in, RedirectAttributes ra) {
        String c = Texts.trim(in.get("code")).toUpperCase();
        if (!c.matches("^[A-Z0-9_-]{3,30}$")) throw new BusinessException("Mã chỉ gồm chữ in hoa, số, \"-\" hoặc \"_\" (3-30 ký tự).");
        String type = "FIXED".equals(in.get("type")) ? "FIXED" : "PERCENT";
        int value = Texts.toInt(in.get("value"), 0);
        if (value <= 0 || ("PERCENT".equals(type) && value > 100)) throw new BusinessException("Giá trị giảm không hợp lệ.");
        LocalDate start = date(in.get("start_date")), end = date(in.get("end_date"));
        if (start != null && end != null && start.isAfter(end)) throw new BusinessException("Ngày bắt đầu phải trước ngày kết thúc.");
        Voucher v = id != null ? Web.found(em.find(Voucher.class, id)) : new Voucher();
        List<Voucher> dup = em.createQuery("select v from Voucher v where upper(v.code) = :c", Voucher.class).setParameter("c", c).getResultList();
        if (!dup.isEmpty() && !dup.get(0).getId().equals(v.getId())) throw new BusinessException("Mã giảm giá đã tồn tại.");
        Integer catId = positive(in.get("category_id"));
        Integer maxDiscount = positive(in.get("max_discount"));
        v.setCode(c);
        v.setDescription(clean(in.get("description"), 300));
        v.setType(type);
        v.setDiscountValue(value);
        v.setMinOrder(Math.max(0, Texts.toInt(in.get("min_order"), 0)));
        v.setMaxDiscount(maxDiscount != null ? maxDiscount.longValue() : null);
        v.setUsageLimit(positive(in.get("usage_limit")));
        v.setStartDate(start);
        v.setEndDate(end);
        v.setActive(in.containsKey("active"));
        v.setShowInWallet(in.containsKey("show_in_wallet"));
        v.setMinTier(MemberTier.tryFrom(in.get("min_tier")));
        v.setCategory(catId != null ? em.find(Category.class, catId.longValue()) : null);
        v.setPerUserLimit(positive(in.get("per_user_limit")));
        v.setNewCustomerOnly(in.containsKey("new_customer_only"));
        if (v.getId() == null) em.persist(v);
        notifications.log(currentUser.get(), "voucher.save", c);
        Web.success(ra, "Đã lưu mã " + c + ".");
        return "redirect:/admin/vouchers";
    }

    /** Tặng voucher vào kho của nhóm khách + gửi thông báo và email marketing. */
    @PostMapping("/vouchers/{id}/email")
    @Transactional
    public String emailVoucher(@PathVariable Long id, @RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        Voucher v = Web.found(em.find(Voucher.class, id));
        if (!v.isRunning()) throw new BusinessException("Mã " + v.getCode() + " đang tắt hoặc đã hết hạn.");
        Form f = new Form(params);
        List<User> targets = audience.select(Texts.trim(f.get("audience")), f);
        if (targets.isEmpty()) throw new BusinessException("Không có khách hàng nào thuộc nhóm đã chọn.");
        String note = clean(f.get("message"), 500);
        int sent = 0;
        for (User u : targets) {
            notifications.notify(u, "Bạn nhận được mã giảm giá " + v.getCode() + ": " + v.valueLabel() + (v.getDescription() != null ? " - " + v.getDescription() : ""), "/cart", true);
            List<String> lines = new ArrayList<>();
            if (note != null) lines.add(note);
            lines.add("Mã giảm giá dành riêng cho bạn: " + v.getCode() + " - " + v.valueLabel() + (v.getMinOrder() > 0 ? " cho đơn từ " + OrderService.money(v.getMinOrder()) : "") + ".");
            if (v.getDescription() != null) lines.add(v.getDescription());
            if (v.getEndDate() != null) lines.add("Hạn sử dụng đến " + v.getEndDate().format(D) + ".");
            if (mail.send(u, "🎁 Quà tặng từ " + settings.get("store_name") + ": mã " + v.getCode(), lines, "Mua sắm ngay", appUrl.replaceAll("/$", "") + "/products")) sent++;
        }
        notifications.log(currentUser.get(), "voucher.email", v.getCode() + " → " + targets.size() + " khách (" + sent + " email)");
        Web.success(ra, "Đã tặng mã " + v.getCode() + " cho " + targets.size() + " khách"
                + (mail.enabled() ? ", gửi " + sent + " email." : " (chưa bật gửi email trong Cấu hình, chỉ gửi thông báo trên web)."));
        return "redirect:/admin/vouchers";
    }

    /* ======================= Khuyến mãi ======================= */

    @GetMapping("/promotions")
    @Transactional(readOnly = true)
    public String promotions(@RequestParam(required = false) Long edit, @RequestParam(required = false) String type, Model model) {
        Promotion e = edit != null ? em.find(Promotion.class, edit) : null;
        if (e == null) {
            e = new Promotion();
            e.setType(type != null && List.of(Promotion.FLASH_SALE, Promotion.COMBO, Promotion.GIFT).contains(type) ? type : Promotion.FLASH_SALE);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            e.setStartAt(now);
            e.setEndAt(now.plusDays(3));
        }
        List<PromotionItem> items = new ArrayList<>(e.getItems());
        while (items.size() < 4) items.add(null);
        model.addAttribute("title", "Khuyến mãi: flash sale, combo, quà tặng");
        model.addAttribute("edit", e);
        model.addAttribute("comboRows", items.subList(0, 4));
        model.addAttribute("list", em.createQuery("select p from Promotion p order by p.id desc", Promotion.class).getResultList());
        model.addAttribute("products", em.createQuery("select p from Product p where p.active = true order by p.name", Product.class).getResultList()
                .stream().filter(PromotionService::eligible).toList());
        return "admin/promotions";
    }

    private Product eligibleProduct(Long id, String label) {
        Product p = id != null ? em.find(Product.class, id) : null;
        if (p == null) throw new BusinessException("Vui lòng chọn " + label + ".");
        if (!PromotionService.eligible(p)) {
            throw new BusinessException("\"" + p.getName() + "\" là thuốc kê đơn / kiểm soát - theo quy định không được áp dụng khuyến mãi.");
        }
        return p;
    }

    @PostMapping({"/promotions", "/promotions/{id}"})
    @Transactional
    public String savePromotion(@PathVariable(required = false) Long id, @RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        Form in = new Form(params);
        String name = Texts.trim(in.get("name"));
        if (Texts.mbLen(name) < 3) throw new BusinessException("Vui lòng nhập tên chương trình.");
        LocalDateTime start = dateTime(in.get("start_at"));
        if (start == null) throw new BusinessException("Chọn thời gian bắt đầu.");
        LocalDateTime end = dateTime(in.get("end_at"));
        if (end == null) throw new BusinessException("Chọn thời gian kết thúc.");
        if (!end.isAfter(start)) throw new BusinessException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        Promotion p = id != null ? Web.found(em.find(Promotion.class, id)) : new Promotion();
        boolean isNew = p.getId() == null;
        if (isNew) p.setType(Texts.trim(in.get("type")));
        p.setName(Texts.trim(name, 150));
        p.setStartAt(start);
        p.setEndAt(end);
        p.setActive(isNew || in.get("active") != null);
        Map<Long, Integer> combo = new LinkedHashMap<>();
        switch (p.getType()) {
            case Promotion.FLASH_SALE -> {
                Product prod = eligibleProduct(Texts.toLong(in.get("product_id")), "sản phẩm flash sale");
                long price = Texts.toInt(in.get("sale_price"), 0);
                if (price <= 0 || price >= prod.getPrice()) {
                    throw new BusinessException("Giá flash sale phải lớn hơn 0 và nhỏ hơn giá bán hiện tại (" + OrderService.money(prod.getPrice()) + "/" + prod.getUnit() + ").");
                }
                p.setProduct(prod);
                p.setSalePrice(price);
                p.setQuantityLimit(positive(in.get("quantity_limit")));
            }
            case Promotion.GIFT -> {
                Product buy = eligibleProduct(Texts.toLong(in.get("product_id")), "sản phẩm mua (X)");
                Product gift = eligibleProduct(Texts.toLong(in.get("gift_product_id")), "sản phẩm tặng (Y)");
                int bq = Texts.toInt(in.get("buy_quantity"), 0), gq = Texts.toInt(in.get("gift_quantity"), 0);
                if (bq <= 0 || gq <= 0) throw new BusinessException("Nhập số lượng mua và số lượng tặng (lớn hơn 0).");
                p.setProduct(buy);
                p.setGiftProduct(gift);
                p.setBuyQuantity(bq);
                p.setGiftQuantity(gq);
            }
            case Promotion.COMBO -> {
                long discount = Texts.toInt(in.get("combo_discount"), 0);
                if (discount <= 0) throw new BusinessException("Nhập số tiền giảm cho mỗi combo.");
                List<String> ids = params.getOrDefault("combo_product_ids[]", List.of());
                List<String> qtys = params.getOrDefault("combo_quantities[]", List.of());
                long listTotal = 0;
                for (int i = 0; i < ids.size(); i++) {
                    Long pid = Texts.toLong(ids.get(i));
                    int q = Math.max(1, Texts.toInt(i < qtys.size() ? qtys.get(i) : null, 1));
                    if (pid == null || combo.containsKey(pid)) continue;
                    Product prod = eligibleProduct(pid, "sản phẩm combo");
                    combo.put(prod.getId(), q);
                    listTotal += prod.getPrice() * q;
                }
                if (combo.size() < 2) throw new BusinessException("Combo cần ít nhất 2 sản phẩm khác nhau.");
                if (discount >= listTotal) throw new BusinessException("Tiền giảm phải nhỏ hơn tổng giá combo (" + OrderService.money(listTotal) + ").");
                p.setComboDiscount(discount);
            }
            default -> throw new BusinessException("Loại khuyến mãi không hợp lệ.");
        }
        if (isNew) em.persist(p);
        if (Promotion.COMBO.equals(p.getType())) {
            p.getItems().clear();
            em.flush();
            combo.forEach((pid, q) -> {
                PromotionItem it = new PromotionItem();
                it.setPromotion(p);
                it.setProduct(em.find(Product.class, pid));
                it.setQuantity(q);
                p.getItems().add(it);
            });
        }
        notifications.log(currentUser.get(), "promotion.save", p.typeLabel() + ": " + p.getName());
        Web.success(ra, "Đã lưu chương trình \"" + p.getName() + "\".");
        return "redirect:/admin/promotions";
    }

    @PostMapping("/promotions/{id}/toggle")
    @Transactional
    public String togglePromotion(@PathVariable Long id, RedirectAttributes ra) {
        Promotion p = Web.found(em.find(Promotion.class, id));
        p.setActive(!p.isActive());
        notifications.log(currentUser.get(), "promotion.toggle", p.getName() + (p.isActive() ? " - bật" : " - tắt"));
        Web.info(ra, (p.isActive() ? "Đã bật " : "Đã tạm dừng ") + "chương trình \"" + p.getName() + "\".");
        return "redirect:/admin/promotions";
    }

    /* ======================= Tích điểm & hạng thành viên ======================= */

    @GetMapping("/loyalty")
    @Transactional(readOnly = true)
    public String loyalty(Model model) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MemberTier t : MemberTier.values()) counts.put(t.name(), 0L);
        Map<Long, Long> spent = new HashMap<>();
        for (Object[] r : em.createQuery("select o.user.id, sum(" + User.SPENT_SQL + ") from Order o where o.status = :s group by o.user.id", Object[].class)
                .setParameter("s", OrderStatus.COMPLETED).getResultList()) {
            spent.put((Long) r[0], ((Number) r[1]).longValue());
        }
        for (Long uid : em.createQuery("select u.id from User u where u.role = :r and u.locked = false", Long.class).setParameter("r", Role.CUSTOMER).getResultList()) {
            counts.merge(MemberTier.of(spent.getOrDefault(uid, 0L)).name(), 1L, Long::sum);
        }
        Long points = em.createQuery("select coalesce(sum(u.points), 0) from User u where u.role = :r", Long.class).setParameter("r", Role.CUSTOMER).getSingleResult();
        model.addAttribute("title", "Tích điểm & hạng thành viên");
        model.addAttribute("tiers", MemberTier.values());
        model.addAttribute("memberCounts", counts);
        model.addAttribute("cfg", settings.all());
        model.addAttribute("pointsOutstanding", points);
        model.addAttribute("pointsValue", points * settings.getLong("point_value"));
        return "admin/loyalty";
    }

    @PostMapping("/loyalty")
    @Transactional
    public String saveLoyalty(@RequestParam Map<String, String> in, RedirectAttributes ra) {
        Map<String, String> v = new LinkedHashMap<>();
        long prev = -1;
        for (MemberTier t : MemberTier.values()) {
            String min = Texts.trim(in.getOrDefault("tier_" + t.getKey() + "_min", "0"));
            String rate = Texts.trim(in.getOrDefault("tier_" + t.getKey() + "_rate", "100"));
            if (!min.matches("\\d+") || !rate.matches("\\d+")) throw new BusinessException("Ngưỡng và hệ số phải là số nguyên không âm.");
            long m = t == MemberTier.DONG ? 0 : Long.parseLong(min);
            if (t != MemberTier.DONG && m <= prev) throw new BusinessException("Ngưỡng hạng " + t.getLabel() + " phải lớn hơn hạng trước.");
            int r = Integer.parseInt(rate);
            if (r < 100 || r > 1000) throw new BusinessException("Hệ số điểm từ 100% đến 1000%.");
            prev = m;
            v.put("tier_" + t.getKey() + "_min", String.valueOf(m));
            v.put("tier_" + t.getKey() + "_rate", rate);
        }
        for (String k : List.of("points_per_amount", "point_value")) {
            String x = Texts.trim(in.get(k));
            if (!x.matches("\\d+") || Long.parseLong(x) <= 0) throw new BusinessException("Số tiền cho 1 điểm và giá trị 1 điểm phải lớn hơn 0.");
            v.put(k, x);
        }
        settings.save(v);
        notifications.log(currentUser.get(), "settings.loyalty", v.toString());
        Web.success(ra, "Đã lưu cấu hình tích điểm & hạng thành viên.");
        return "redirect:/admin/loyalty";
    }
}
