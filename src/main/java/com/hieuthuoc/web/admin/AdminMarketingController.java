package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Admin - Marketing: flash sale / combo / quà tặng, tích điểm & hạng, banner, trang tĩnh, gửi thông báo hàng loạt. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMarketingController {
    private final PromotionRepository promotionRepo;
    private final ProductRepository productRepo;
    private final BannerRepository bannerRepo;
    private final StaticPageRepository pageRepo;
    private final UserRepository userRepo;
    private final OrderRepository orderRepo;
    private final SettingService settings;
    private final FileStorageService files;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    /* ======================= Khuyến mãi ======================= */

    @GetMapping("/promotions")
    public String promotions(@RequestParam(required = false) Long edit, @RequestParam(defaultValue = "FLASH_SALE") String type, Model model) {
        Promotion p = edit == null ? null : promotionRepo.findById(edit).orElse(null);
        if (p == null) {
            p = new Promotion();
            p.setType(List.of(Promotion.FLASH_SALE, Promotion.COMBO, Promotion.GIFT).contains(type) ? type : Promotion.FLASH_SALE);
            p.setStartAt(LocalDateTime.now().withSecond(0).withNano(0));
            p.setEndAt(p.getStartAt().plusDays(3));
        }
        model.addAttribute("list", promotionRepo.findAllByOrderByIdDesc());
        model.addAttribute("edit", p);
        model.addAttribute("products", productRepo.findAllByOrderByNameAsc().stream().filter(PromotionService::eligible).toList());
        model.addAttribute("title", "Khuyến mãi: flash sale, combo, quà tặng");
        return "admin/promotions";
    }

    private Product eligibleProduct(Long id, String label) {
        Product p = id == null ? null : productRepo.findById(id).orElse(null);
        if (p == null) throw new BusinessException("Vui lòng chọn " + label + ".");
        if (!PromotionService.eligible(p)) {
            throw new BusinessException("\"" + p.getName() + "\" là thuốc kê đơn / kiểm soát - theo quy định không được áp dụng khuyến mãi.");
        }
        return p;
    }

    @PostMapping({"/promotions", "/promotions/{id}"})
    @Transactional
    public String savePromotion(@PathVariable(required = false) Long id, @RequestParam String name, @RequestParam String type,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                                @RequestParam(defaultValue = "false") boolean active,
                                @RequestParam(required = false) Long productId, @RequestParam(required = false) Long salePrice,
                                @RequestParam(required = false) Integer quantityLimit,
                                @RequestParam(required = false) Integer buyQuantity, @RequestParam(required = false) Long giftProductId,
                                @RequestParam(required = false) Integer giftQuantity,
                                @RequestParam(required = false) Long comboDiscount,
                                @RequestParam(value = "comboProductIds", required = false) List<Long> comboProductIds,
                                @RequestParam(value = "comboQuantities", required = false) List<Integer> comboQuantities,
                                RedirectAttributes ra) {
        String n = Texts.trim(name, 150);
        if (n.length() < 3) throw new BusinessException("Vui lòng nhập tên chương trình.");
        if (!endAt.isAfter(startAt)) throw new BusinessException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        Promotion p = id == null ? new Promotion() : promotionRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy chương trình."));
        if (id == null) p.setType(type);
        p.setName(n);
        p.setStartAt(startAt);
        p.setEndAt(endAt);
        p.setActive(id == null || active);
        switch (p.getType()) {
            case Promotion.FLASH_SALE -> {
                Product prod = eligibleProduct(productId, "sản phẩm flash sale");
                if (salePrice == null || salePrice <= 0 || salePrice >= prod.getPrice()) {
                    throw new BusinessException("Giá flash sale phải lớn hơn 0 và nhỏ hơn giá bán hiện tại (" + prod.getPrice() + " đ/" + prod.getUnit() + ").");
                }
                p.setProduct(prod);
                p.setSalePrice(salePrice);
                p.setQuantityLimit(quantityLimit == null || quantityLimit <= 0 ? null : quantityLimit);
            }
            case Promotion.GIFT -> {
                p.setProduct(eligibleProduct(productId, "sản phẩm mua (X)"));
                p.setGiftProduct(eligibleProduct(giftProductId, "sản phẩm tặng (Y)"));
                if (buyQuantity == null || buyQuantity <= 0 || giftQuantity == null || giftQuantity <= 0) {
                    throw new BusinessException("Nhập số lượng mua và số lượng tặng (lớn hơn 0).");
                }
                p.setBuyQuantity(buyQuantity);
                p.setGiftQuantity(giftQuantity);
            }
            case Promotion.COMBO -> {
                if (comboDiscount == null || comboDiscount <= 0) throw new BusinessException("Nhập số tiền giảm cho mỗi combo.");
                p.getItems().clear();
                Set<Long> seen = new HashSet<>();
                long listTotal = 0;
                for (int i = 0; comboProductIds != null && i < comboProductIds.size(); i++) {
                    Long pid = comboProductIds.get(i);
                    if (pid == null) continue;
                    int q = comboQuantities != null && i < comboQuantities.size() && comboQuantities.get(i) != null ? comboQuantities.get(i) : 1;
                    if (q <= 0 || !seen.add(pid)) continue;
                    Product prod = eligibleProduct(pid, "sản phẩm combo");
                    p.getItems().add(new PromotionItem(p, prod, q));
                    listTotal += prod.getPrice() * q;
                }
                if (p.getItems().size() < 2) throw new BusinessException("Combo cần ít nhất 2 sản phẩm khác nhau.");
                if (comboDiscount >= listTotal) throw new BusinessException("Tiền giảm phải nhỏ hơn tổng giá combo (" + listTotal + " đ).");
                p.setComboDiscount(comboDiscount);
            }
            default -> throw new BusinessException("Loại khuyến mãi không hợp lệ.");
        }
        promotionRepo.save(p);
        notifications.log(currentUser.get(), "promotion.save", p.getTypeLabel() + ": " + n);
        Flash.success(ra, "Đã lưu chương trình \"" + n + "\".");
        return "redirect:/admin/promotions";
    }

    @PostMapping("/promotions/{id}/toggle")
    @Transactional
    public String togglePromotion(@PathVariable Long id, RedirectAttributes ra) {
        Promotion p = promotionRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy chương trình."));
        p.setActive(!p.isActive());
        notifications.log(currentUser.get(), "promotion.toggle", p.getName() + (p.isActive() ? " - bật" : " - tắt"));
        Flash.info(ra, (p.isActive() ? "Đã bật " : "Đã tạm dừng ") + "chương trình \"" + p.getName() + "\".");
        return "redirect:/admin/promotions";
    }

    /* ======================= Tích điểm & hạng thành viên ======================= */

    @GetMapping("/loyalty")
    public String loyalty(Model model) {
        Map<MemberTier, Long> members = new EnumMap<>(MemberTier.class);
        for (MemberTier t : MemberTier.values()) members.put(t, 0L);
        for (User u : userRepo.findByRoleAndLockedFalse(Role.CUSTOMER)) members.merge(MemberTier.of(orderRepo.totalSpent(u)), 1L, Long::sum);
        Map<String, Long> memberCounts = new HashMap<>();
        members.forEach((k, v) -> memberCounts.put(k.name(), v));
        model.addAttribute("tiers", MemberTier.values());
        model.addAttribute("memberCounts", memberCounts);
        model.addAttribute("values", settings.all());
        model.addAttribute("title", "Tích điểm & hạng thành viên");
        return "admin/loyalty";
    }

    @PostMapping("/loyalty")
    public String saveLoyalty(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        Map<String, String> v = new HashMap<>();
        for (String k : SettingService.DEFAULTS.keySet()) {
            if ((k.startsWith("tier_") || k.equals("points_per_amount") || k.equals("point_value")) && params.containsKey(k)) v.put(k, params.get(k).trim());
        }
        long prev = -1;
        for (MemberTier t : MemberTier.values()) {
            String min = v.getOrDefault("tier_" + t.getKey() + "_min", "0");
            String rate = v.getOrDefault("tier_" + t.getKey() + "_rate", "100");
            if (!min.matches("\\d+") || !rate.matches("\\d+")) throw new BusinessException("Ngưỡng và hệ số phải là số nguyên không âm.");
            long m = t == MemberTier.DONG ? 0 : Long.parseLong(min);
            if (t != MemberTier.DONG && m <= prev) throw new BusinessException("Ngưỡng hạng " + t.getLabel() + " phải lớn hơn hạng trước.");
            if (Long.parseLong(rate) < 100 || Long.parseLong(rate) > 1000) throw new BusinessException("Hệ số điểm từ 100% đến 1000%.");
            prev = m;
            v.put("tier_" + t.getKey() + "_min", String.valueOf(m));
        }
        if ("0".equals(v.get("points_per_amount"))) throw new BusinessException("Số tiền cho 1 điểm phải lớn hơn 0.");
        settings.save(v);
        notifications.log(currentUser.get(), "settings.loyalty", v.toString());
        Flash.success(ra, "Đã lưu cấu hình tích điểm & hạng thành viên.");
        return "redirect:/admin/loyalty";
    }

    /* ======================= Banner ======================= */

    @GetMapping("/banners")
    public String banners(@RequestParam(required = false) Long edit, Model model) {
        model.addAttribute("list", bannerRepo.findAllByOrderBySortOrderAscIdAsc());
        model.addAttribute("edit", edit == null ? new Banner() : bannerRepo.findById(edit).orElse(new Banner()));
        model.addAttribute("themes", Map.of("ocean", "Xanh biển", "teal", "Xanh ngọc", "violet", "Tím", "sunset", "Cam hoàng hôn"));
        model.addAttribute("title", "Banner trang chủ");
        return "admin/banners";
    }

    @PostMapping({"/banners", "/banners/{id}"})
    @Transactional
    public String saveBanner(@PathVariable(required = false) Long id, @RequestParam String title, @RequestParam(required = false) String subtitle,
                             @RequestParam(required = false) String link, @RequestParam(required = false) String buttonText,
                             @RequestParam(defaultValue = "ocean") String theme, @RequestParam(defaultValue = "0") int sortOrder,
                             @RequestParam(defaultValue = "false") boolean active,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                             @RequestParam(value = "imageFile", required = false) MultipartFile image,
                             @RequestParam(defaultValue = "false") boolean removeImage, RedirectAttributes ra) {
        if (Texts.trim(title).length() < 3) throw new BusinessException("Vui lòng nhập tiêu đề banner.");
        String l = Texts.emptyToNull(Texts.trim(link, 300));
        if (l != null && !(l.startsWith("/") || l.startsWith("https://"))) throw new BusinessException("Liên kết phải là đường dẫn trong web (bắt đầu bằng /) hoặc https://");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) throw new BusinessException("Ngày bắt đầu phải trước ngày kết thúc.");
        Banner b = id == null ? new Banner() : bannerRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy banner."));
        b.setTitle(Texts.trim(title, 150));
        b.setSubtitle(Texts.emptyToNull(Texts.trim(subtitle, 300)));
        b.setLink(l);
        b.setButtonText(Texts.emptyToNull(Texts.trim(buttonText, 50)));
        b.setTheme(List.of("ocean", "teal", "violet", "sunset").contains(theme) ? theme : "ocean");
        b.setSortOrder(sortOrder);
        b.setActive(id == null || active);
        b.setStartDate(startDate);
        b.setEndDate(endDate);
        if (files.isPresent(image)) b.setImage("/media/banners/" + files.store(FileStorageService.Kind.BANNERS, image));
        else if (removeImage) b.setImage(null);
        bannerRepo.save(b);
        notifications.log(currentUser.get(), "banner.save", b.getTitle());
        Flash.success(ra, "Đã lưu banner.");
        return "redirect:/admin/banners";
    }

    @PostMapping("/banners/{id}/delete")
    @Transactional
    public String deleteBanner(@PathVariable Long id, RedirectAttributes ra) {
        bannerRepo.findById(id).ifPresent(bannerRepo::delete);
        Flash.info(ra, "Đã xóa banner.");
        return "redirect:/admin/banners";
    }

    /* ======================= Trang tĩnh ======================= */

    @GetMapping("/pages")
    public String pages(@RequestParam(required = false) Long edit, Model model) {
        model.addAttribute("list", pageRepo.findAllByOrderBySortOrderAscIdAsc());
        model.addAttribute("edit", edit == null ? new StaticPage() : pageRepo.findById(edit).orElse(new StaticPage()));
        model.addAttribute("title", "Trang tĩnh (chính sách, giới thiệu)");
        return "admin/pages";
    }

    @PostMapping({"/pages", "/pages/{id}"})
    @Transactional
    public String savePage(@PathVariable(required = false) Long id, @RequestParam String title, @RequestParam(required = false) String slug,
                           @RequestParam String content, @RequestParam(required = false) String metaDescription,
                           @RequestParam(defaultValue = "0") int sortOrder, @RequestParam(defaultValue = "false") boolean published,
                           @RequestParam(defaultValue = "false") boolean showInFooter, RedirectAttributes ra) {
        if (Texts.trim(title).length() < 3) throw new BusinessException("Vui lòng nhập tiêu đề trang.");
        if (Texts.trim(content).length() < 10) throw new BusinessException("Nội dung trang quá ngắn.");
        StaticPage p = id == null ? new StaticPage() : pageRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy trang."));
        String s = Texts.slugify(Texts.isBlank(slug) ? title : slug);
        if (!s.equals(p.getSlug()) && pageRepo.existsBySlug(s)) throw new BusinessException("Đường dẫn /pages/" + s + " đã tồn tại.");
        p.setSlug(s);
        p.setTitle(Texts.trim(title, 200));
        p.setContent(Texts.trim(content, 20000));
        p.setMetaDescription(Texts.emptyToNull(Texts.trim(metaDescription, 300)));
        p.setSortOrder(sortOrder);
        p.setPublished(published);
        p.setShowInFooter(showInFooter);
        pageRepo.save(p);
        notifications.log(currentUser.get(), "page.save", "/pages/" + s);
        Flash.success(ra, "Đã lưu trang /pages/" + s + ".");
        return "redirect:/admin/pages";
    }

    /* ======================= Gửi thông báo hàng loạt ======================= */

    @GetMapping("/broadcast")
    public String broadcast(Model model) {
        model.addAttribute("tiers", MemberTier.values());
        model.addAttribute("customerCount", userRepo.countByRole(Role.CUSTOMER));
        model.addAttribute("title", "Gửi thông báo hàng loạt");
        return "admin/broadcast";
    }

    @PostMapping("/broadcast")
    @Transactional
    public String sendBroadcast(@RequestParam String audience, @RequestParam(required = false) MemberTier tier,
                                @RequestParam(required = false) Integer inactiveDays, @RequestParam(required = false) String phones,
                                @RequestParam String message, @RequestParam(required = false) String link, RedirectAttributes ra) {
        String msg = Texts.trim(message, 480);
        if (msg.length() < 10) throw new BusinessException("Nội dung thông báo tối thiểu 10 ký tự.");
        String l = Texts.emptyToNull(Texts.trim(link, 300));
        if (l != null && !l.startsWith("/")) throw new BusinessException("Liên kết phải là đường dẫn trong web, VD: /products?type=SUPPLEMENT");
        List<User> customers = userRepo.findByRoleAndLockedFalse(Role.CUSTOMER);
        List<User> targets = switch (audience) {
            case "ALL" -> customers;
            case "TIER" -> {
                if (tier == null) throw new BusinessException("Chọn hạng thành viên.");
                yield customers.stream().filter(u -> MemberTier.of(orderRepo.totalSpent(u)).ordinal() >= tier.ordinal()).toList();
            }
            case "INACTIVE" -> {
                int days = inactiveDays == null || inactiveDays <= 0 ? 60 : inactiveDays;
                LocalDateTime since = LocalDateTime.now().minusDays(days);
                yield customers.stream().filter(u -> orderRepo.findByUserOrderByCreatedAtDescIdDesc(u).stream()
                        .noneMatch(o -> o.getCreatedAt().isAfter(since))).toList();
            }
            case "PHONES" -> {
                Set<String> set = new HashSet<>();
                for (String p : Objects.requireNonNullElse(phones, "").split("[,;\\s]+")) if (!p.isBlank()) set.add(p.trim());
                yield customers.stream().filter(u -> u.getPhone() != null && set.contains(u.getPhone())).toList();
            }
            default -> throw new BusinessException("Chọn nhóm khách nhận thông báo.");
        };
        if (targets.isEmpty()) throw new BusinessException("Không có khách hàng nào thuộc nhóm đã chọn.");
        for (User u : targets) notifications.notify(u, "[" + settings.get("store_name") + "] " + msg, l);
        notifications.log(currentUser.get(), "broadcast.send", audience + (tier != null ? " " + tier : "") + " - " + targets.size() + " khách: " + msg);
        Flash.success(ra, "Đã gửi thông báo tới " + targets.size() + " khách hàng (hiện trong chuông thông báo và popup trên web).");
        return "redirect:/admin/broadcast";
    }
}
