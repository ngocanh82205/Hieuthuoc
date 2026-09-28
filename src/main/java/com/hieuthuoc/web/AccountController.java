package com.hieuthuoc.web;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.AddressRepository;
import com.hieuthuoc.repository.OrderRepository;
import com.hieuthuoc.repository.UserRepository;
import com.hieuthuoc.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/** Khu vực tài khoản khách hàng: hồ sơ, sức khỏe, sổ địa chỉ, đơn hàng. */
@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {
    private final CurrentUser currentUser;
    private final UserRepository userRepo;
    private final AddressRepository addressRepo;
    private final OrderRepository orderRepo;
    private final OrderService orderService;
    private final AccountService accountService;
    private final Cart cart;
    private final CustomerCareService care;
    private final com.hieuthuoc.repository.PrescriptionRepository prescriptionRepo;
    private final com.hieuthuoc.repository.WishlistItemRepository wishlistRepo;
    private final com.hieuthuoc.repository.StockSubscriptionRepository subscriptionRepo;
    private final com.hieuthuoc.repository.ReminderRepository reminderRepo;
    private final com.hieuthuoc.repository.ProductRepository productRepo;
    private final StockService stockService;
    private final SettingService settings;

    @GetMapping
    public String profile(Model model) {
        User u = currentUser.get();
        model.addAttribute("profile", u);
        model.addAttribute("orderCount", orderRepo.countByUser(u));
        model.addAttribute("spent", orderRepo.totalSpent(u));
        model.addAttribute("tier", care.tierOf(u));
        model.addAttribute("tiers", com.hieuthuoc.entity.MemberTier.values());
        model.addAttribute("today", care.todaySchedule(u));
        model.addAttribute("pointValue", settings.getLong("point_value"));
        model.addAttribute("title", "Tài khoản của tôi");
        return "account/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String fullName, @RequestParam String phone,
                                @RequestParam(required = false) String gender,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthday,
                                RedirectAttributes ra) {
        User u = currentUser.get();
        if (Texts.trim(fullName).length() < 2) throw new BusinessException("Vui lòng nhập họ tên.");
        if (!Texts.isPhone(Texts.trim(phone))) throw new BusinessException("Số điện thoại không hợp lệ.");
        u.setFullName(Texts.trim(fullName, 100));
        u.setPhone(phone.trim());
        u.setGender(Texts.emptyToNull(gender));
        u.setBirthday(birthday);
        userRepo.save(u);
        Flash.success(ra, "Đã cập nhật thông tin cá nhân.");
        return "redirect:/account";
    }

    @PostMapping("/health")
    public String updateHealth(@RequestParam(required = false) String allergies,
                               @RequestParam(required = false) String chronicConditions,
                               @RequestParam(defaultValue = "false") boolean pregnancy, RedirectAttributes ra) {
        User u = currentUser.get();
        u.setAllergies(Texts.emptyToNull(Texts.trim(allergies, 500)));
        u.setChronicConditions(Texts.emptyToNull(Texts.trim(chronicConditions, 500)));
        u.setPregnancy(pregnancy);
        userRepo.save(u);
        Flash.success(ra, "Đã lưu hồ sơ sức khỏe. Dược sĩ sẽ dùng thông tin này để tư vấn an toàn hơn.");
        return "redirect:/account#health";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String current, @RequestParam String password,
                                 @RequestParam String passwordConfirm, RedirectAttributes ra) {
        accountService.changePassword(currentUser.get(), current, password, passwordConfirm);
        Flash.success(ra, "Đổi mật khẩu thành công.");
        return "redirect:/account";
    }

    /* ---------------- Sổ địa chỉ ---------------- */

    @GetMapping("/addresses")
    public String addresses(Model model) {
        model.addAttribute("addresses", addressRepo.findByUserOrderByDefaultAddressDescIdAsc(currentUser.get()));
        model.addAttribute("title", "Sổ địa chỉ");
        return "account/addresses";
    }

    @PostMapping("/addresses")
    @Transactional
    public String addAddress(@RequestParam String recipient, @RequestParam String phone, @RequestParam String addressLine,
                             @RequestParam(defaultValue = "false") boolean makeDefault, RedirectAttributes ra) {
        User u = currentUser.get();
        if (Texts.trim(recipient).length() < 2 || !Texts.isPhone(Texts.trim(phone)) || Texts.trim(addressLine).length() < 10) {
            throw new BusinessException("Vui lòng nhập đầy đủ tên, số điện thoại hợp lệ và địa chỉ chi tiết (tối thiểu 10 ký tự).");
        }
        List<Address> existing = addressRepo.findByUserOrderByDefaultAddressDescIdAsc(u);
        boolean isDefault = makeDefault || existing.isEmpty();
        if (isDefault) existing.forEach(a -> a.setDefaultAddress(false));
        Address a = new Address();
        a.setUser(u);
        a.setRecipient(Texts.trim(recipient, 100));
        a.setPhone(phone.trim());
        a.setAddressLine(Texts.trim(addressLine, 300));
        a.setDefaultAddress(isDefault);
        addressRepo.save(a);
        Flash.success(ra, "Đã thêm địa chỉ.");
        return "redirect:/account/addresses";
    }

    @PostMapping("/addresses/{id}/default")
    @Transactional
    public String setDefault(@PathVariable Long id) {
        User u = currentUser.get();
        addressRepo.findByIdAndUser(id, u).ifPresent(target -> {
            addressRepo.findByUserOrderByDefaultAddressDescIdAsc(u).forEach(a -> a.setDefaultAddress(false));
            target.setDefaultAddress(true);
        });
        return "redirect:/account/addresses";
    }

    @PostMapping("/addresses/{id}/delete")
    public String deleteAddress(@PathVariable Long id, RedirectAttributes ra) {
        addressRepo.findByIdAndUser(id, currentUser.get()).ifPresent(addressRepo::delete);
        Flash.info(ra, "Đã xóa địa chỉ.");
        return "redirect:/account/addresses";
    }

    /* ---------------- Đơn hàng ---------------- */

    private Order myOrder(String code) {
        return orderRepo.findByCodeAndUser(code, currentUser.get())
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
    }

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) OrderStatus status, Model model) {
        User u = currentUser.get();
        model.addAttribute("orders", status == null ? orderRepo.findByUserOrderByCreatedAtDescIdDesc(u)
                : orderRepo.findByUserAndStatusOrderByCreatedAtDescIdDesc(u, status));
        model.addAttribute("status", status);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("title", "Đơn hàng của tôi");
        return "account/orders";
    }

    @GetMapping("/orders/{code}")
    public String order(@PathVariable String code, Model model) {
        Order o = myOrder(code);
        model.addAttribute("order", o);
        model.addAttribute("canPay", orderService.canPay(o));
        model.addAttribute("canReturn", orderService.canRequestReturn(o));
        model.addAttribute("showBank", orderService.showBankTransfer(o));
        model.addAttribute("addresses", addressRepo.findByUserOrderByDefaultAddressDescIdAsc(o.getUser()));
        model.addAttribute("shippingMethods", ShippingMethod.values());
        model.addAttribute("paymentMethods", settings.enabledPaymentMethods());
        model.addAttribute("provinces", settings.deliverableProvinces());
        model.addAttribute("title", "Đơn hàng " + o.getCode());
        return "account/order";
    }

    @PostMapping("/orders/{code}/cancel")
    @Transactional
    public String cancel(@PathVariable String code, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        Order o = myOrder(code);
        orderService.cancelByCustomer(o, currentUser.get(), reason);
        Flash.info(ra, "Đã hủy đơn hàng " + code + ".");
        return "redirect:/account/orders/" + code;
    }

    @GetMapping("/orders/{code}/pay")
    public String payPage(@PathVariable String code, Model model, RedirectAttributes ra) {
        Order o = myOrder(code);
        if (o.getPaymentStatus() == PaymentStatus.PAID) return "redirect:/account/orders/" + code;
        if (o.getStatus() == OrderStatus.PENDING_RX || o.getStatus() == OrderStatus.RX_REJECTED) {
            Flash.warning(ra, "Đơn có thuốc kê đơn chỉ được thanh toán sau khi dược sĩ duyệt đơn thuốc.");
            return "redirect:/account/orders/" + code;
        }
        if (!orderService.canPay(o)) return "redirect:/account/orders/" + code;
        model.addAttribute("order", o);
        model.addAttribute("title", "Thanh toán online");
        return "shop/pay";
    }

    @PostMapping("/orders/{code}/pay")
    @Transactional
    public String pay(@PathVariable String code, @RequestParam String result, RedirectAttributes ra) {
        orderService.pay(myOrder(code), currentUser.get(), "success".equals(result));
        Flash.success(ra, "Thanh toán thành công!");
        return "redirect:/account/orders/" + code;
    }

    @PostMapping("/orders/{code}/prescription")
    @Transactional
    public String reupload(@PathVariable String code, @RequestParam("prescription") MultipartFile file,
                           @RequestParam(required = false) String rxNote, RedirectAttributes ra) {
        orderService.reuploadPrescription(myOrder(code), currentUser.get(), file, rxNote);
        Flash.success(ra, "Đã gửi lại đơn thuốc, vui lòng chờ dược sĩ duyệt.");
        return "redirect:/account/orders/" + code;
    }

    @PostMapping("/orders/{code}/return")
    @Transactional
    public String requestReturn(@PathVariable String code, @RequestParam String reason, RedirectAttributes ra) {
        orderService.requestReturn(myOrder(code), currentUser.get(), reason);
        Flash.success(ra, "Đã gửi yêu cầu đổi/trả. Nhà thuốc sẽ liên hệ với bạn.");
        return "redirect:/account/orders/" + code;
    }

    @PostMapping("/orders/{code}/confirm")
    @Transactional
    public String confirm(@PathVariable String code, @ModelAttribute OrderService.ConfirmForm form, RedirectAttributes ra) {
        Order o = myOrder(code);
        orderService.confirmByCustomer(o, currentUser.get(), form);
        Flash.success(ra, "Đã xác nhận đơn hàng. Nhà thuốc sẽ xử lý sớm nhất."
                + (o.getPaymentMethod() == PaymentMethod.BANK_TRANSFER ? " Vui lòng chuyển khoản theo hướng dẫn." : ""));
        return o.getPaymentMethod() == PaymentMethod.ONLINE ? "redirect:/account/orders/" + code + "/pay" : "redirect:/account/orders/" + code;
    }

    /** Tạo nhắc mua lại từ một sản phẩm trong đơn đã mua. */
    @PostMapping("/orders/{code}/remind")
    @Transactional
    public String remindFromOrder(@PathVariable String code, @RequestParam Long productId, @RequestParam(defaultValue = "30") int days,
                                  RedirectAttributes ra) {
        myOrder(code);
        CustomerCareService.ReminderForm f = new CustomerCareService.ReminderForm();
        f.setType(ReminderType.REPURCHASE);
        f.setProductId(productId);
        f.setRemindDate(LocalDate.now().plusDays(Math.max(1, Math.min(days, 365))));
        care.createReminder(currentUser.get(), f);
        Flash.success(ra, "Đã đặt lịch nhắc mua lại sau " + days + " ngày.");
        return "redirect:/account/orders/" + code;
    }

    @PostMapping("/orders/{code}/reorder")
    public String reorder(@PathVariable String code, RedirectAttributes ra) {
        List<String> skipped = orderService.reorder(myOrder(code), cart);
        if (skipped.isEmpty()) Flash.success(ra, "Đã thêm các sản phẩm vào giỏ hàng.");
        else Flash.warning(ra, "Đã thêm vào giỏ. Không thêm được: " + String.join(", ", skipped));
        return "redirect:/cart";
    }

    /* ---------------- Gửi đơn thuốc (không chọn sản phẩm) ---------------- */

    @GetMapping("/prescriptions")
    public String prescriptions(Model model) {
        model.addAttribute("list", prescriptionRepo.findByUserAndStandaloneTrueOrderByCreatedAtDesc(currentUser.get()));
        model.addAttribute("title", "Gửi đơn thuốc");
        return "account/prescriptions";
    }

    @PostMapping("/prescriptions")
    @Transactional
    public String submitPrescription(@RequestParam("prescription") MultipartFile file, @RequestParam(required = false) String note,
                                     RedirectAttributes ra) {
        orderService.submitStandalonePrescription(currentUser.get(), file, note);
        Flash.success(ra, "Đã gửi đơn thuốc. Dược sĩ sẽ đọc đơn, lên đơn hàng và báo giá cho bạn.");
        return "redirect:/account/prescriptions";
    }

    /* ---------------- Yêu thích & báo có hàng ---------------- */

    @GetMapping("/wishlist")
    public String wishlist(Model model) {
        User u = currentUser.get();
        List<com.hieuthuoc.entity.Product> products = new java.util.ArrayList<>(
                wishlistRepo.findByUserOrderByCreatedAtDesc(u).stream().map(com.hieuthuoc.entity.WishlistItem::getProduct).toList());
        stockService.fill(products);
        model.addAttribute("products", products);
        model.addAttribute("subscriptions", subscriptionRepo.findByUserAndNotifiedFalseOrderByCreatedAtDesc(u));
        model.addAttribute("title", "Sản phẩm yêu thích");
        return "account/wishlist";
    }

    /* ---------------- Kho voucher ---------------- */

    @GetMapping("/vouchers")
    public String vouchers(Model model) {
        User u = currentUser.get();
        model.addAttribute("saved", care.savedVouchers(u));
        model.addAttribute("available", care.walletCandidates(u));
        model.addAttribute("title", "Kho voucher");
        return "account/vouchers";
    }

    @PostMapping("/vouchers/{id}/save")
    @Transactional
    public String saveVoucher(@PathVariable Long id, RedirectAttributes ra) {
        care.saveVoucher(currentUser.get(), id);
        Flash.success(ra, "Đã lưu voucher vào kho.");
        return "redirect:/account/vouchers";
    }

    /** Dùng voucher trong kho: áp mã vào giỏ hàng. */
    @PostMapping("/vouchers/{code}/use")
    public String useVoucher(@PathVariable String code) {
        cart.setVoucherCode(code);
        return "redirect:/cart";
    }

    /* ---------------- Nhắc lịch ---------------- */

    @GetMapping("/reminders")
    public String reminders(Model model) {
        User u = currentUser.get();
        model.addAttribute("reminders", reminderRepo.findByUserOrderByActiveDescCreatedAtDesc(u));
        model.addAttribute("today", care.todaySchedule(u));
        model.addAttribute("products", productRepo.findSellable());
        model.addAttribute("title", "Nhắc lịch uống thuốc");
        return "account/reminders";
    }

    @PostMapping("/reminders")
    @Transactional
    public String createReminder(@ModelAttribute CustomerCareService.ReminderForm form, RedirectAttributes ra) {
        care.createReminder(currentUser.get(), form);
        Flash.success(ra, "Đã tạo lịch nhắc. Bạn sẽ nhận thông báo trên website đúng giờ.");
        return "redirect:/account/reminders";
    }

    @PostMapping("/reminders/{id}/toggle")
    @Transactional
    public String toggleReminder(@PathVariable Long id) {
        care.toggleReminder(currentUser.get(), id);
        return "redirect:/account/reminders";
    }

    @PostMapping("/reminders/{id}/delete")
    @Transactional
    public String deleteReminder(@PathVariable Long id, RedirectAttributes ra) {
        care.deleteReminder(currentUser.get(), id);
        Flash.info(ra, "Đã xóa lịch nhắc.");
        return "redirect:/account/reminders";
    }
}
