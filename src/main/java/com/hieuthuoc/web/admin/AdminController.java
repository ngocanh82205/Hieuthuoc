package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Admin: dashboard, nhân viên, khách hàng, báo cáo, cấu hình, nhật ký. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserRepository userRepo;
    private final OrderRepository orderRepo;
    private final AddressRepository addressRepo;
    private final ReceiptRepository receiptRepo;
    private final PrescriptionRepository prescriptionRepo;
    private final AuditLogRepository auditRepo;
    private final ProductRepository productRepo;
    private final ReportService reportService;
    private final StockService stockService;
    private final SettingService settings;
    private final AccountService accountService;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    @GetMapping
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        List<ReportService.DayRow> days = reportService.lastDays(14);
        long revenueMonth = reportService.completedBetween(today.withDayOfMonth(1), today).stream().mapToLong(Order::getTotal).sum();
        model.addAttribute("revenueToday", days.get(days.size() - 1).revenue());
        model.addAttribute("revenueMonth", revenueMonth);
        model.addAttribute("ordersToday", orderRepo.countByCreatedAtBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        model.addAttribute("processing", orderRepo.countByStatusIn(EnumSet.of(OrderStatus.PENDING_RX, OrderStatus.PENDING,
                OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.SHIPPING)));
        model.addAttribute("customers", userRepo.countByRole(Role.CUSTOMER));
        model.addAttribute("newCustomers", userRepo.countByRoleAndCreatedAtAfter(Role.CUSTOMER, today.minusDays(30).atStartOfDay()));
        model.addAttribute("inventoryValue", reportService.inventoryValue(settings.getLong("near_expiry_days"))[0]);
        model.addAttribute("pendingReceipts", receiptRepo.countByStatus(ApprovalStatus.PENDING));
        model.addAttribute("pendingRx", prescriptionRepo.countByStatus(ApprovalStatus.PENDING));
        model.addAttribute("chartLabels", days.stream().map(d -> d.day().format(DateTimeFormatter.ofPattern("dd/MM"))).toList());
        model.addAttribute("chartRevenue", days.stream().map(ReportService.DayRow::revenue).toList());
        model.addAttribute("chartOrders", days.stream().map(ReportService.DayRow::orders).toList());
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (Object[] r : orderRepo.countGroupByStatus()) counts.put((OrderStatus) r[0], (Long) r[1]);
        model.addAttribute("statusCounts", counts);
        model.addAttribute("top", reportService.build(today.minusDays(29), today).getTopProducts().stream().limit(5).toList());
        model.addAttribute("lowStock", stockService.fill(new ArrayList<>(productRepo.findByActiveTrueOrderByNameAsc()))
                .stream().filter(Product::isLowStock).limit(6).toList());
        model.addAttribute("title", "Bảng điều khiển");
        return "admin/dashboard";
    }

    /* ---------------- Nhân viên ---------------- */

    @GetMapping("/users")
    public String users(Model model) {
        List<User> staff = userRepo.findByRoleInOrderByRoleAscFullNameAsc(List.of(Role.ADMIN, Role.PHARMACIST));
        Map<Long, Long> rxCount = new HashMap<>();
        for (User u : staff) rxCount.put(u.getId(), prescriptionRepo.countByPharmacist(u));
        model.addAttribute("staff", staff);
        model.addAttribute("rxCount", rxCount);
        model.addAttribute("title", "Nhân viên & phân quyền");
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUser(Model model) {
        User u = new User();
        u.setRole(Role.PHARMACIST);
        model.addAttribute("user", u);
        model.addAttribute("title", "Thêm nhân viên");
        return "admin/user-form";
    }

    @GetMapping("/users/{id}/edit")
    public String editUser(@PathVariable Long id, Model model) {
        User u = userRepo.findById(id).filter(User::isStaff).orElseThrow(() -> BusinessException.notFound("Không tìm thấy nhân viên."));
        model.addAttribute("user", u);
        model.addAttribute("title", "Sửa nhân viên");
        return "admin/user-form";
    }

    @PostMapping({"/users", "/users/{id}"})
    @Transactional
    public String saveUser(@PathVariable(required = false) Long id, @RequestParam String fullName, @RequestParam String email,
                           @RequestParam(required = false) String phone, @RequestParam Role role,
                           @RequestParam(required = false) String licenseNo, @RequestParam(required = false) String password,
                           RedirectAttributes ra) {
        User me = currentUser.get();
        if (role == Role.CUSTOMER) throw new BusinessException("Vai trò không hợp lệ.");
        if (Texts.trim(fullName).length() < 2) throw new BusinessException("Vui lòng nhập họ tên.");
        if (!Texts.isEmail(Texts.trim(email))) throw new BusinessException("Email không hợp lệ.");
        Optional<User> dup = userRepo.findByEmailIgnoreCase(email.trim());
        if (dup.isPresent() && !dup.get().getId().equals(id)) throw new BusinessException("Email đã được sử dụng.");
        if (id == null && (password == null || password.length() < 6)) throw new BusinessException("Mật khẩu tối thiểu 6 ký tự.");
        if (id != null && !Texts.isBlank(password) && password.length() < 6) throw new BusinessException("Mật khẩu mới tối thiểu 6 ký tự.");
        if (me.getId().equals(id) && role != Role.ADMIN) throw new BusinessException("Bạn không thể tự hạ quyền của chính mình.");

        User u = id == null ? new User() : userRepo.findById(id).filter(User::isStaff)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy nhân viên."));
        u.setFullName(Texts.trim(fullName, 100));
        u.setEmail(email.trim().toLowerCase());
        u.setPhone(Texts.emptyToNull(phone));
        u.setRole(role);
        u.setLicenseNo(Texts.emptyToNull(licenseNo));
        if (!Texts.isBlank(password)) u.setPasswordHash(accountService.encode(password));
        userRepo.save(u);
        notifications.log(me, id == null ? "user.create" : "user.update", u.getEmail() + " (" + role + ")");
        Flash.success(ra, "Đã lưu thông tin nhân viên.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/lock")
    @Transactional
    public String toggleLock(@PathVariable Long id, RedirectAttributes ra) {
        User me = currentUser.get();
        User u = userRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy tài khoản."));
        if (u.getId().equals(me.getId())) throw new BusinessException("Không thể khóa chính mình.");
        u.setLocked(!u.isLocked());
        notifications.log(me, u.isLocked() ? "user.lock" : "user.unlock", u.getEmail());
        Flash.success(ra, (u.isLocked() ? "Đã khóa" : "Đã mở khóa") + " tài khoản " + u.getEmail() + ".");
        return u.getRole() == Role.CUSTOMER ? "redirect:/admin/customers/" + u.getId() : "redirect:/admin/users";
    }

    /* ---------------- Khách hàng ---------------- */

    @GetMapping("/customers")
    public String customers(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "1") int page, Model model) {
        var p = userRepo.searchCustomers(q.trim(), PageRequest.of(Math.max(page, 1) - 1, 20));
        Map<Long, Long> orders = new HashMap<>();
        Map<Long, Long> spent = new HashMap<>();
        for (User u : p) {
            orders.put(u.getId(), orderRepo.countByUser(u));
            spent.put(u.getId(), orderRepo.totalSpent(u));
        }
        model.addAttribute("page", p);
        model.addAttribute("orders", orders);
        model.addAttribute("spent", spent);
        model.addAttribute("q", q);
        model.addAttribute("title", "Khách hàng");
        return "admin/customers";
    }

    @GetMapping("/customers/{id}")
    public String customer(@PathVariable Long id, Model model) {
        User c = userRepo.findById(id).filter(u -> u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy khách hàng."));
        model.addAttribute("customer", c);
        model.addAttribute("orders", orderRepo.findByUserOrderByCreatedAtDescIdDesc(c));
        model.addAttribute("addresses", addressRepo.findByUserOrderByDefaultAddressDescIdAsc(c));
        model.addAttribute("spent", orderRepo.totalSpent(c));
        model.addAttribute("title", c.getFullName());
        return "admin/customer";
    }

    @PostMapping("/customers/{id}/points")
    @Transactional
    public String adjustPoints(@PathVariable Long id, @RequestParam int delta, @RequestParam String reason, RedirectAttributes ra) {
        if (delta == 0 || Texts.isBlank(reason)) throw new BusinessException("Nhập số điểm (khác 0) và lý do.");
        User c = userRepo.findById(id).filter(u -> u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy khách hàng."));
        c.setPoints(Math.max(0, c.getPoints() + delta));
        notifications.log(currentUser.get(), "customer.points", c.getEmail() + ": " + (delta > 0 ? "+" : "") + delta + " (" + reason + ")");
        Flash.success(ra, "Đã điều chỉnh điểm tích lũy.");
        return "redirect:/admin/customers/" + id;
    }

    /* ---------------- Báo cáo ---------------- */

    private static LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate t = to == null ? LocalDate.now() : to;
        LocalDate f = from == null ? t.minusDays(29) : from;
        if (f.isAfter(t)) f = t;
        return new LocalDate[]{f, t};
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          Model model) {
        LocalDate[] r = range(from, to);
        long nearDays = settings.getLong("near_expiry_days");
        ReportService.Report report = reportService.build(r[0], r[1]);
        model.addAttribute("report", report);
        model.addAttribute("dayLabels", report.getByDay().stream().map(d -> d.day().format(DateTimeFormatter.ofPattern("dd/MM"))).toList());
        model.addAttribute("dayRevenue", report.getByDay().stream().map(ReportService.DayRow::revenue).toList());
        model.addAttribute("catNames", report.getByCategory().stream().map(ReportService.NameValue::name).toList());
        model.addAttribute("catValues", report.getByCategory().stream().map(ReportService.NameValue::value).toList());
        model.addAttribute("inventory", reportService.inventoryValue(nearDays));
        model.addAttribute("nearDays", nearDays);
        model.addAttribute("title", "Báo cáo thống kê");
        return "admin/reports";
    }

    @GetMapping("/reports/export.csv")
    public void exportCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          HttpServletResponse res) throws IOException {
        LocalDate[] r = range(from, to);
        res.setContentType("text/csv; charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"doanh-thu_" + r[0] + "_" + r[1] + ".csv\"");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        PrintWriter w = res.getWriter();
        w.write('﻿');
        w.println(csv("Mã đơn", "Ngày hoàn thành", "Khách hàng", "Tạm tính", "Giảm giá", "Phí ship", "Tổng tiền", "Thanh toán"));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        for (Order o : reportService.completedBetween(r[0], r[1])) {
            w.println(csv(o.getCode(), o.getCompletedAt().format(f), o.getUser().getFullName(), o.getSubtotal(), o.getDiscount(),
                    o.getShippingFee(), o.getTotal(), o.getPaymentMethod().getLabel()));
        }
        w.flush();
    }

    private static String csv(Object... values) {
        StringJoiner j = new StringJoiner(",");
        for (Object v : values) j.add("\"" + String.valueOf(v).replace("\"", "\"\"") + "\"");
        return j.toString();
    }

    /* ---------------- Cấu hình & nhật ký ---------------- */

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        model.addAttribute("values", settings.all());
        model.addAttribute("labels", SettingService.LABELS);
        model.addAttribute("numeric", SettingService.NUMERIC);
        model.addAttribute("title", "Cấu hình hệ thống");
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam Map<String, String> values, RedirectAttributes ra) {
        settings.save(values);
        notifications.log(currentUser.get(), "settings.update", null);
        Flash.success(ra, "Đã lưu cấu hình.");
        return "redirect:/admin/settings";
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("page", auditRepo.findAllByOrderByIdDesc(PageRequest.of(Math.max(page, 1) - 1, 50)));
        model.addAttribute("title", "Nhật ký hệ thống");
        return "admin/logs";
    }
}
