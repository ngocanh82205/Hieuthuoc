package com.hieuthuoc.web;

import com.hieuthuoc.entity.User;
import com.hieuthuoc.entity.UserNotification;
import com.hieuthuoc.service.BusinessException;
import com.hieuthuoc.service.CurrentUser;
import com.hieuthuoc.service.Page;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class NotificationController {
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/notifications")
    @Transactional
    public String index(@RequestParam(defaultValue = "1") int page, Model model) {
        User u = currentUser.get();
        int per = 30;
        int pg = Math.max(1, page);
        long total = em.createQuery("select count(n) from UserNotification n where n.userId = :u", Long.class).setParameter("u", u.getId()).getSingleResult();
        List<UserNotification> items = em.createQuery("select n from UserNotification n where n.userId = :u order by n.id desc", UserNotification.class)
                .setParameter("u", u.getId()).setFirstResult((pg - 1) * per).setMaxResults(per).getResultList();
        // Hiển thị trạng thái trước khi đánh dấu đã xem
        Page<Map<String, Object>> view = new Page<>(items.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("message", n.getMessage());
            m.put("link", n.getLink());
            m.put("seen", n.isSeen());
            m.put("createdAt", n.getCreatedAt());
            return m;
        }).toList(), total, per, pg);
        model.addAttribute("title", "Thông báo");
        model.addAttribute("notifications", view);
        List<Long> ids = items.stream().filter(n -> !n.isSeen()).map(UserNotification::getId).toList();
        if (!ids.isEmpty()) {
            em.createQuery("update UserNotification n set n.seen = true where n.userId = :u and n.id in :ids")
                    .setParameter("u", u.getId()).setParameter("ids", ids).executeUpdate();
        }
        return u.isStaff() ? "staff/notifications" : "account/notifications";
    }

    /** Số thông báo chưa đọc + thông báo mới nhất (JS gọi định kỳ để hiện toast / cửa sổ nhắc thuốc). */
    @GetMapping("/notifications/unread")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> unread() {
        User u = currentUser.get();
        List<Map<String, Object>> latest = em.createQuery("select n from UserNotification n where n.userId = :u and n.seen = false order by n.id desc", UserNotification.class)
                .setParameter("u", u.getId()).setMaxResults(5).getResultList().stream()
                .map(n -> Map.<String, Object>of("id", n.getId(), "message", n.getMessage(), "link", n.getLink() == null ? "" : n.getLink(), "popup", n.isPopup()))
                .toList();
        long count = em.createQuery("select count(n) from UserNotification n where n.userId = :u and n.seen = false", Long.class)
                .setParameter("u", u.getId()).getSingleResult();
        return Map.of("count", count, "latest", latest);
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public String readAll(HttpServletRequest req, RedirectAttributes ra) {
        User u = currentUser.get();
        em.createQuery("update UserNotification n set n.seen = true where n.userId = :u and n.seen = false").setParameter("u", u.getId()).executeUpdate();
        Web.info(ra, "Đã đánh dấu tất cả là đã đọc.");
        return Web.back(req, "/notifications");
    }

    @GetMapping("/notifications/{id}/go")
    @Transactional
    public String go(@PathVariable Long id) {
        UserNotification n = em.find(UserNotification.class, id);
        if (n == null || !n.getUserId().equals(currentUser.get().getId())) throw BusinessException.notFound();
        n.setSeen(true);
        String link = n.getLink() != null && !n.getLink().isBlank() ? n.getLink() : "/notifications";
        return "redirect:" + link;
    }
}
