package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Jpql;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Admin: nhân viên, khách hàng, yêu cầu quên mật khẩu, nhật ký hoạt động. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class UserController {
    private final NotificationService notifications;
    private final AccountService accounts;
    private final CustomerService customers;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private static String clean(String v, int max) {
        return Texts.emptyToNull(Texts.trim(v, max));
    }

    private User user(Long id) {
        return Web.found(em.find(User.class, id));
    }

    private boolean exists(String jpql, Object... params) {
        var q = em.createQuery(jpql, Long.class);
        for (int i = 0; i + 1 < params.length; i += 2) q.setParameter((String) params[i], params[i + 1]);
        return q.getSingleResult() > 0;
    }

    /* ---------------- Nhân viên ---------------- */

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public String index(Model model) {
        List<User> staff = em.createQuery("select u from User u where u.role in :r order by u.role, u.fullName", User.class)
                .setParameter("r", List.of(Role.ADMIN, Role.PHARMACIST)).getResultList();
        Map<Long, Long> rxCount = new HashMap<>();
        for (Object[] r : em.createQuery("select p.pharmacist.id, count(p) from Prescription p where p.pharmacist is not null group by p.pharmacist.id", Object[].class).getResultList()) {
            rxCount.put((Long) r[0], (Long) r[1]);
        }
        model.addAttribute("title", "Nhân viên");
        model.addAttribute("staff", staff);
        model.addAttribute("rxCount", rxCount);
        return "admin/users";
    }

    private String form(User u, String title, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("user", u);
        model.addAttribute("pharmacistPerms", StaffPermission.FOR_PHARMACIST);
        return "admin/user-form";
    }

    @GetMapping("/users/create")
    public String create(Model model) {
        User u = new User();
        u.setRole(Role.PHARMACIST);
        return form(u, "Thêm nhân viên", model);
    }

    @GetMapping("/users/{id}/edit")
    @Transactional(readOnly = true)
    public String edit(@PathVariable Long id, Model model) {
        User u = user(id);
        if (!u.isStaff()) throw new BusinessException("Không tìm thấy nhân viên.", 404);
        return form(u, "Sửa nhân viên", model);
    }

    @PostMapping({"/users", "/users/{id}"})
    @Transactional
    public String save(@PathVariable(required = false) Long id, @RequestParam Map<String, String> in, RedirectAttributes ra) {
        User me = currentUser.get();
        User existing = id != null ? user(id) : null;
        if (existing != null && !existing.isStaff()) throw new BusinessException("Không tìm thấy nhân viên.", 404);
        Role role;
        try {
            role = Role.valueOf(Texts.trim(in.get("role")));
        } catch (IllegalArgumentException e) {
            role = null;
        }
        if (role == null || role == Role.CUSTOMER) throw new BusinessException("Vai trò không hợp lệ.");
        String name = Texts.trim(in.get("full_name"));
        String email = Texts.trim(in.get("email")).toLowerCase();
        String phone = clean(in.get("phone"), 20);
        String password = in.getOrDefault("password", "");
        Long selfId = existing != null ? existing.getId() : -1L;
        if (Texts.mbLen(name) < 2) throw new BusinessException("Vui lòng nhập họ tên.");
        if (!Texts.isEmail(email)) throw new BusinessException("Email không hợp lệ.");
        if (exists("select count(u) from User u where u.email = :e and u.id <> :id", "e", email, "id", selfId)) throw new BusinessException("Email đã được sử dụng.");
        if (phone != null && exists("select count(u) from User u where u.phone = :p and u.id <> :id", "p", phone, "id", selfId)) {
            throw new BusinessException("Số điện thoại đã được sử dụng.");
        }
        if (existing == null && Texts.mbLen(password) < 6) throw new BusinessException("Mật khẩu tối thiểu 6 ký tự.");
        if (existing != null && !password.isEmpty() && Texts.mbLen(password) < 6) throw new BusinessException("Mật khẩu mới tối thiểu 6 ký tự.");
        if (existing != null && existing.getId().equals(me.getId()) && role != Role.ADMIN) throw new BusinessException("Bạn không thể tự hạ quyền của chính mình.");
        User u = existing != null ? existing : new User();
        u.setFullName(Texts.trim(name, 100));
        u.setEmail(email);
        u.setPhone(phone);
        u.setRole(role);
        u.setLicenseNo(clean(in.get("license_no"), 50));
        u.setDegree(clean(in.get("degree"), 200));
        // Dược sĩ duyệt đơn thuốc kê đơn (nghiệp vụ chuyên môn): bắt buộc có chứng chỉ hành nghề dược
        if (role == Role.PHARMACIST && u.getLicenseNo() == null) throw new BusinessException("Dược sĩ phải có số chứng chỉ hành nghề dược (CCHN).");
        if (!password.isEmpty()) u.setPassword(passwordEncoder.encode(password));
        if (u.getId() == null) em.persist(u);
        notifications.log(me, existing != null ? "user.update" : "user.create", u.getEmail() + " (" + u.positionLabel() + ")");
        Web.success(ra, "Đã lưu thông tin nhân viên.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/lock")
    @Transactional
    public String toggleLock(@PathVariable Long id, RedirectAttributes ra) {
        User me = currentUser.get();
        User u = user(id);
        if (u.getId().equals(me.getId())) throw new BusinessException("Không thể khóa chính mình.");
        u.setLocked(!u.isLocked());
        notifications.log(me, u.isLocked() ? "user.lock" : "user.unlock", u.getEmail() != null ? u.getEmail() : u.getPhone());
        Web.success(ra, (u.isLocked() ? "Đã khóa" : "Đã mở khóa") + " tài khoản " + u.getFullName() + ".");
        return u.isCustomer() ? "redirect:/admin/customers/" + u.getId() : "redirect:/admin/users";
    }

    /**
     * Xóa nhân viên: chỉ khi tài khoản chưa có hoạt động nghiệp vụ (tạo nhầm). Nhân viên đã làm việc phải giữ lại để
     * truy vết ai duyệt đơn thuốc / nhập kho / xử lý đơn (GPP) - dùng "Khóa" thay vì xóa.
     */
    @PostMapping("/users/{id}/delete")
    @Transactional
    public String deleteStaff(@PathVariable Long id, RedirectAttributes ra) {
        User me = currentUser.get();
        User u = user(id);
        if (!u.isStaff()) throw new BusinessException("Không tìm thấy nhân viên.", 404);
        if (u.getId().equals(me.getId())) throw new BusinessException("Không thể xóa tài khoản của chính mình.");
        if (u.isAdmin() && !exists("select count(x) from User x where x.role = :r and x.locked = false and x.id <> :id", "r", Role.ADMIN, "id", u.getId())) {
            throw new BusinessException("Không thể xóa quản trị viên cuối cùng.");
        }
        Long i = u.getId();
        List<String> used = new ArrayList<>();
        if (exists("select count(p) from Prescription p where p.pharmacist.id = :i", "i", i)) used.add("duyệt đơn thuốc");
        if (exists("select count(o) from Order o where o.handler.id = :i or o.user.id = :i or o.refundedBy.id = :i", "i", i)) used.add("xử lý / bán đơn hàng");
        if (exists("select count(r) from Receipt r where r.creator.id = :i or r.approver.id = :i", "i", i)) used.add("phiếu nhập kho");
        if (exists("select count(a) from StockAdjustment a where a.user.id = :i or a.approver.id = :i", "i", i)
                || exists("select count(s) from Stocktake s where s.user.id = :i", "i", i)) used.add("phiếu điều chỉnh / kiểm kê");
        if (exists("select count(c) from Conversation c where c.pharmacist.id = :i", "i", i)
                || exists("select count(m) from Message m where m.sender.id = :i", "i", i)) used.add("tư vấn khách hàng");
        if (exists("select count(p) from Post p where p.author.id = :i", "i", i)) used.add("bài viết");
        if (exists("select count(l) from AuditLog l where l.user.id = :i and l.action not like 'auth.%'", "i", i)) used.add("thao tác trong nhật ký");
        if (!used.isEmpty()) {
            throw new BusinessException("Không thể xóa " + u.getFullName() + " vì đã có lịch sử: " + String.join(", ", used)
                    + ". Hãy dùng \"Khóa\" để chặn đăng nhập mà vẫn giữ lịch sử truy vết.");
        }
        em.createQuery("delete from AuditLog l where l.user.id = :i").setParameter("i", i).executeUpdate();
        em.createQuery("delete from UserNotification n where n.user.id = :i").setParameter("i", i).executeUpdate();
        em.remove(u);
        notifications.log(me, "user.delete", u.getFullName() + " (" + u.getEmail() + ")");
        Web.success(ra, "Đã xóa nhân viên " + u.getFullName() + ".");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reset-password")
    @Transactional
    public String resetPassword(@PathVariable Long id, @RequestParam(name = "request_id", required = false) Long requestId, RedirectAttributes ra) {
        User me = currentUser.get();
        User u = user(id);
        String temp = accounts.resetPassword(u, me);
        PasswordResetRequest r = requestId != null ? em.find(PasswordResetRequest.class, requestId) : null;
        if (r != null) {
            r.setHandled(true);
            r.setHandler(me);
            r.setHandledAt(LocalDateTime.now());
        }
        Web.success(ra, "Mật khẩu tạm của " + u.getFullName() + " là: " + temp + " - hãy gọi điện báo cho người dùng và nhắc đổi mật khẩu.");
        return requestId != null ? "redirect:/admin/password-resets" : u.isCustomer() ? "redirect:/admin/customers/" + u.getId() : "redirect:/admin/users";
    }

    /* ---------------- Khách hàng ---------------- */

    @GetMapping("/customers")
    @Transactional(readOnly = true)
    public String customers(@RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int page, Model model) {
        q = Texts.trim(q);
        Page<User> p = new Jpql("User u", "u").where("u.role = :r", "r", Role.CUSTOMER)
                .where("(u.email is null or u.email <> :walk)", "walk", PosService.WALK_IN_EMAIL)
                .search(q, "u.fullName", "coalesce(u.email, '')", "coalesce(u.phone, '')")
                .page(em, User.class, "u.id desc", 20, page);
        List<Long> ids = p.getItems().stream().map(User::getId).toList();
        Map<Long, Long> orders = new HashMap<>(), spent = new HashMap<>();
        Map<Long, MemberTier> tiers = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] r : em.createQuery("select o.user.id, count(o) from Order o where o.user.id in :ids group by o.user.id", Object[].class)
                    .setParameter("ids", ids).getResultList()) orders.put((Long) r[0], (Long) r[1]);
            for (Object[] r : em.createQuery("select o.user.id, sum(" + User.SPENT_SQL + ") from Order o where o.user.id in :ids and o.status = :s group by o.user.id", Object[].class)
                    .setParameter("ids", ids).setParameter("s", OrderStatus.COMPLETED).getResultList()) spent.put((Long) r[0], ((Number) r[1]).longValue());
        }
        for (Long i : ids) tiers.put(i, MemberTier.of(spent.getOrDefault(i, 0L)));
        model.addAttribute("title", "Khách hàng");
        model.addAttribute("page", p);
        model.addAttribute("q", q);
        model.addAttribute("orders", orders);
        model.addAttribute("spent", spent);
        model.addAttribute("tiers", tiers);
        return "admin/customers";
    }

    @GetMapping("/customers/{id}")
    @Transactional(readOnly = true)
    public String customer(@PathVariable Long id, Model model) {
        User u = user(id);
        if (!u.isCustomer()) throw new BusinessException("Không tìm thấy khách hàng.", 404);
        long spent = customers.totalSpent(u);
        model.addAttribute("title", u.getFullName());
        model.addAttribute("customer", u);
        model.addAttribute("spent", spent);
        model.addAttribute("tier", MemberTier.of(spent));
        model.addAttribute("orders", em.createQuery("select o from Order o where o.user.id = :u order by o.id desc", Order.class).setParameter("u", u.getId()).getResultList());
        model.addAttribute("addresses", em.createQuery("select a from Address a where a.user.id = :u order by a.isDefault desc", Address.class).setParameter("u", u.getId()).getResultList());
        return "admin/customer";
    }

    @PostMapping("/customers/{id}/points")
    @Transactional
    public String adjustPoints(@PathVariable Long id, @RequestParam(defaultValue = "0") String delta, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        User u = user(id);
        if (!u.isCustomer()) throw new BusinessException("Không tìm thấy khách hàng.", 404);
        int d = Texts.toInt(delta, 0);
        String why = Texts.trim(reason);
        if (d == 0 || why.isEmpty()) throw new BusinessException("Nhập số điểm (khác 0) và lý do.");
        u.setPoints(Math.max(0, u.getPoints() + d));
        String sign = d > 0 ? "+" : "";
        notifications.log(currentUser.get(), "customer.points", (u.getEmail() != null ? u.getEmail() : u.getPhone()) + ": " + sign + d + " (" + why + ")");
        notifications.notify(u, "Điểm tích lũy của bạn được điều chỉnh " + sign + d + " điểm: " + why, "/account");
        Web.success(ra, "Đã điều chỉnh điểm tích lũy.");
        return "redirect:/admin/customers/" + u.getId();
    }

    /* ---------------- Yêu cầu quên mật khẩu ---------------- */

    @GetMapping("/password-resets")
    @Transactional(readOnly = true)
    public String passwordResets(@RequestParam(required = false) String handled, Model model) {
        boolean h = "1".equals(handled) || "true".equals(handled);
        model.addAttribute("title", "Yêu cầu quên mật khẩu");
        model.addAttribute("handled", h);
        model.addAttribute("list", em.createQuery("select r from PasswordResetRequest r left join fetch r.user left join fetch r.handler where r.handled = :h order by r.createdAt desc",
                PasswordResetRequest.class).setParameter("h", h).setMaxResults(200).getResultList());
        return "admin/password-resets";
    }

    @PostMapping("/password-resets/{id}/close")
    @Transactional
    public String closeReset(@PathVariable Long id, RedirectAttributes ra) {
        PasswordResetRequest r = Web.found(em.find(PasswordResetRequest.class, id));
        r.setHandled(true);
        r.setHandler(currentUser.get());
        r.setHandledAt(LocalDateTime.now());
        Web.info(ra, "Đã đóng yêu cầu.");
        return "redirect:/admin/password-resets";
    }

    /* ---------------- Nhật ký hoạt động ---------------- */

    @GetMapping("/logs")
    @Transactional(readOnly = true)
    public String logs(@RequestParam Map<String, String> in, @RequestParam(defaultValue = "1") int page, Model model) {
        Jpql j = new Jpql("AuditLog l", "l");
        Long userId = Texts.toLong(in.get("user_id"));
        j.when(userId != null, "l.user.id = :u", "u", userId);
        List<String> prefixes = AuditLog.GROUPS.get(in.getOrDefault("action", ""));
        if (prefixes != null) {
            StringJoiner or = new StringJoiner(" or ", "(", ")");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < prefixes.size(); i++) {
                or.add("l.action like :p" + i);
                params.add("p" + i);
                params.add(prefixes.get(i) + ".%");
            }
            j.where(or.toString(), params.toArray());
        }
        String q = Texts.trim(in.get("q"));
        j.when(!q.isEmpty(), "lower(l.detail) like :q", "q", "%" + q.toLowerCase() + "%");
        LocalDate from = parseDate(in.get("from")), to = parseDate(in.get("to"));
        j.when(from != null, "l.createdAt >= :from", "from", from != null ? from.atStartOfDay() : null);
        j.when(to != null, "l.createdAt < :to", "to", to != null ? to.plusDays(1).atStartOfDay() : null);
        model.addAttribute("title", "Nhật ký hoạt động");
        model.addAttribute("page", j.page(em, AuditLog.class, "l.id desc", 50, page));
        model.addAttribute("actions", AuditLog.GROUPS.keySet());
        model.addAttribute("f", in);
        model.addAttribute("staff", em.createQuery("select u from User u where u.role in :r order by u.fullName", User.class)
                .setParameter("r", List.of(Role.ADMIN, Role.PHARMACIST)).getResultList());
        return "admin/logs";
    }

    private static LocalDate parseDate(String v) {
        try {
            return v == null || v.isBlank() ? null : LocalDate.parse(v);
        } catch (Exception e) {
            return null;
        }
    }
}
