package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Admin: nhân viên, vai trò & phân quyền (RBAC), nhật ký hoạt động. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminUserController {
    public static final List<String> SHIFTS = List.of("Ca sáng 7h - 15h", "Ca chiều 14h - 22h", "Hành chính 8h - 17h", "Ca đêm 22h - 7h", "Bán thời gian");

    private final UserRepository userRepo;
    private final StaffRoleRepository roleRepo;
    private final PrescriptionRepository prescriptionRepo;
    private final AuditLogRepository auditRepo;
    private final AccountService accountService;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    /* ---------------- Nhân viên ---------------- */

    @GetMapping("/users")
    public String users(@RequestParam(required = false) Long role, Model model) {
        List<User> staff = new ArrayList<>(userRepo.findByRoleInOrderByRoleAscFullNameAsc(List.of(Role.ADMIN, Role.PHARMACIST)));
        if (role != null) staff.removeIf(u -> u.getStaffRole() == null || !u.getStaffRole().getId().equals(role));
        Map<Long, Long> rxCount = new HashMap<>();
        for (User u : staff) rxCount.put(u.getId(), prescriptionRepo.countByPharmacist(u));
        model.addAttribute("staff", staff);
        model.addAttribute("rxCount", rxCount);
        model.addAttribute("roles", roleRepo.findAllByOrderByNameAsc());
        model.addAttribute("roleFilter", role);
        model.addAttribute("title", "Nhân viên");
        return "admin/users";
    }

    private void formModel(Model model, User u, String title) {
        model.addAttribute("user", u);
        model.addAttribute("allPerms", StaffPermission.values());
        model.addAttribute("roles", roleRepo.findAllByOrderByNameAsc());
        model.addAttribute("shifts", SHIFTS);
        model.addAttribute("title", title);
    }

    @GetMapping("/users/new")
    public String newUser(Model model) {
        User u = new User();
        u.setRole(Role.PHARMACIST);
        formModel(model, u, "Thêm nhân viên");
        return "admin/user-form";
    }

    @GetMapping("/users/{id}/edit")
    public String editUser(@PathVariable Long id, Model model) {
        User u = userRepo.findById(id).filter(User::isStaff).orElseThrow(() -> BusinessException.notFound("Không tìm thấy nhân viên."));
        formModel(model, u, "Sửa nhân viên");
        return "admin/user-form";
    }

    @PostMapping({"/users", "/users/{id}"})
    @Transactional
    public String saveUser(@PathVariable(required = false) Long id, @RequestParam String fullName, @RequestParam String email,
                           @RequestParam(required = false) String phone, @RequestParam Role role,
                           @RequestParam(required = false) Long staffRoleId,
                           @RequestParam(required = false) String licenseNo, @RequestParam(required = false) String degree,
                           @RequestParam(required = false) String shift, @RequestParam(required = false) String password,
                           @RequestParam(value = "perms", required = false) List<StaffPermission> perms,
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
        StaffRole sr = staffRoleId == null ? null : roleRepo.findById(staffRoleId).orElseThrow(() -> new BusinessException("Vai trò không tồn tại."));
        u.setFullName(Texts.trim(fullName, 100));
        u.setEmail(email.trim().toLowerCase());
        u.setPhone(Texts.emptyToNull(phone));
        u.setRole(role);
        u.setStaffRole(role == Role.ADMIN ? null : sr);
        u.setLicenseNo(Texts.emptyToNull(Texts.trim(licenseNo, 50)));
        u.setDegree(Texts.emptyToNull(Texts.trim(degree, 200)));
        u.setShift(Texts.emptyToNull(Texts.trim(shift, 100)));
        u.setPermissions(role == Role.ADMIN || perms == null || perms.isEmpty() ? null
                : String.join(",", perms.stream().distinct().map(Enum::name).toList()));
        // Duyệt đơn thuốc kê đơn là nghiệp vụ chuyên môn: bắt buộc có chứng chỉ hành nghề dược
        if (role == Role.PHARMACIST && u.hasPermission(StaffPermission.RX_REVIEW) && u.getLicenseNo() == null) {
            throw new BusinessException("Nhân viên có quyền duyệt đơn thuốc phải có số chứng chỉ hành nghề dược.");
        }
        if (!Texts.isBlank(password)) u.setPasswordHash(accountService.encode(password));
        userRepo.save(u);
        notifications.log(me, id == null ? "user.create" : "user.update", u.getEmail() + " (" + u.getPositionLabel() + ")");
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

    /* ---------------- Vai trò & phân quyền ---------------- */

    @GetMapping("/roles")
    public String roles(Model model) {
        List<StaffRole> roles = roleRepo.findAllByOrderByNameAsc();
        Map<Long, Long> members = new HashMap<>();
        for (StaffRole r : roles) members.put(r.getId(), userRepo.countByStaffRole(r));
        model.addAttribute("roles", roles);
        model.addAttribute("members", members);
        model.addAttribute("allPerms", StaffPermission.values());
        model.addAttribute("title", "Vai trò & phân quyền");
        return "admin/roles";
    }

    @PostMapping({"/roles", "/roles/{id}"})
    @Transactional
    public String saveRole(@PathVariable(required = false) Long id, @RequestParam String name,
                           @RequestParam(required = false) String description,
                           @RequestParam(value = "perms", required = false) List<StaffPermission> perms, RedirectAttributes ra) {
        String n = Texts.trim(name, 80);
        if (n.length() < 2) throw new BusinessException("Vui lòng nhập tên vai trò.");
        Optional<StaffRole> dup = roleRepo.findByNameIgnoreCase(n);
        if (dup.isPresent() && !dup.get().getId().equals(id)) throw new BusinessException("Tên vai trò đã tồn tại.");
        StaffRole r = id == null ? new StaffRole() : roleRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy vai trò."));
        r.setName(n);
        r.setDescription(Texts.emptyToNull(Texts.trim(description, 300)));
        r.setPermissionSet(perms == null ? List.of() : perms);
        roleRepo.save(r);
        notifications.log(currentUser.get(), "role.save", n + ": " + (r.getPermissions() == null ? "(không quyền)" : r.getPermissions()));
        Flash.success(ra, "Đã lưu vai trò \"" + n + "\".");
        return "redirect:/admin/roles";
    }

    @PostMapping("/roles/{id}/delete")
    @Transactional
    public String deleteRole(@PathVariable Long id, RedirectAttributes ra) {
        StaffRole r = roleRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy vai trò."));
        if (userRepo.countByStaffRole(r) > 0) throw new BusinessException("Vai trò đang được gán cho nhân viên, không thể xóa.");
        roleRepo.delete(r);
        notifications.log(currentUser.get(), "role.delete", r.getName());
        Flash.info(ra, "Đã xóa vai trò.");
        return "redirect:/admin/roles";
    }

    /* ---------------- Nhật ký hoạt động ---------------- */

    @GetMapping("/logs")
    public String logs(@RequestParam(required = false) Long userId, @RequestParam(required = false) String action,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (userId != null) ps.add(cb.equal(root.get("user").get("id"), userId));
            if (!Texts.isBlank(action)) ps.add(cb.like(root.get("action"), action.trim() + "%"));
            if (!Texts.isBlank(q)) ps.add(cb.like(cb.lower(root.get("detail")), "%" + q.trim().toLowerCase() + "%"));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null) ps.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        model.addAttribute("page", auditRepo.findAll(spec, PageRequest.of(Math.max(page, 1) - 1, 50, Sort.by(Sort.Direction.DESC, "id"))));
        model.addAttribute("staff", userRepo.findByRoleInOrderByRoleAscFullNameAsc(List.of(Role.ADMIN, Role.PHARMACIST)));
        model.addAttribute("actions", List.of("order", "rx", "receipt", "batch", "inventory", "stock", "product", "user", "role", "customer",
                "voucher", "promotion", "settings", "refund", "broadcast", "backup"));
        model.addAttribute("userId", userId);
        model.addAttribute("action", action);
        model.addAttribute("q", q);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("title", "Nhật ký hoạt động");
        return "admin/logs";
    }
}
