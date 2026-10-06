package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/** Dược sĩ tư vấn khách hàng: danh sách hội thoại, trả lời, tiếp nhận từ AI, chuyển, gửi giỏ hàng tư vấn. */
@Controller
@RequestMapping("/staff/consultations")
@RequiredArgsConstructor
public class ConsultationController {
    private static final Set<String> VIEWS = Set.of("mine", "unassigned", "ai", "all");
    private final ChatService chat;
    private final AiAssistantService ai;
    private final SafetyService safety;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private Conversation conv(Long id) {
        return Web.found(em.find(Conversation.class, id));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String index(@RequestParam(required = false) String view, @RequestParam(required = false) String closed, Model model) {
        User me = currentUser.get();
        String v = view != null && VIEWS.contains(view) ? view : "mine";
        boolean isClosed = "1".equals(closed) || "true".equals(closed);
        String jpql = "select c from Conversation c join fetch c.customer left join fetch c.pharmacist where c.closed = :closed"
                + switch (v) {
                    case "mine" -> " and c.pharmacist.id = :me";
                    case "unassigned" -> " and c.pharmacist is null and c.mode = 'HUMAN'";
                    case "ai" -> " and c.mode = 'AI'";
                    default -> "";
                } + " order by c.updatedAt desc";
        var q = em.createQuery(jpql, Conversation.class).setParameter("closed", isClosed).setMaxResults(200);
        if (v.equals("mine")) q.setParameter("me", me.getId());
        List<Conversation> list = q.getResultList();
        Map<Long, Message> last = new HashMap<>();
        if (!list.isEmpty()) {
            List<Long> ids = list.stream().map(Conversation::getId).toList();
            em.createQuery("select m from Message m left join fetch m.sender where m.id in (select max(x.id) from Message x where x.conversation.id in :ids group by x.conversation.id)",
                    Message.class).setParameter("ids", ids).getResultList().forEach(m -> last.put(m.getConversation().getId(), m));
        }
        Map<String, String> views = new LinkedHashMap<>();
        views.put("mine", "Của tôi");
        views.put("unassigned", "Chưa ai nhận");
        views.put("ai", "AI đang hỗ trợ");
        views.put("all", "Tất cả");
        model.addAttribute("title", "Tư vấn khách hàng");
        model.addAttribute("view", v);
        model.addAttribute("views", views);
        model.addAttribute("closed", isClosed);
        model.addAttribute("list", list);
        model.addAttribute("last", last);
        model.addAttribute("online", chat.onlineStaff());
        model.addAttribute("aiEngine", ai.engineLabel());
        return "staff/consultations";
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String show(@PathVariable Long id, Model model) {
        Conversation c = conv(id);
        User me = currentUser.get();
        User customer = c.getCustomer();
        List<Product> recent = safety.recentProducts(customer);
        List<Product> products = em.createQuery("select p from Product p where p.active = true order by p.name", Product.class).getResultList()
                .stream().filter(p -> p.getDrugType().isSellableOnline()).toList();
        List<Map<String, Object>> messages = chat.messages(c, 0);
        model.addAttribute("title", "Tư vấn: " + customer.getFullName());
        model.addAttribute("conv", c);
        model.addAttribute("customer", customer);
        model.addAttribute("messages", messages);
        model.addAttribute("lastId", messages.isEmpty() ? 0 : messages.get(messages.size() - 1).get("id"));
        model.addAttribute("products", products);
        model.addAttribute("recent", recent);
        model.addAttribute("recentNames", recent.stream().map(Product::getName).collect(Collectors.joining(", ")));
        model.addAttribute("busyOwner", chat.activeOwnerOtherThan(c, me));
        model.addAttribute("warnings", safety.check(customer, recent));
        model.addAttribute("orders", em.createQuery("select o from Order o where o.user.id = :u order by o.id desc", Order.class)
                .setParameter("u", customer.getId()).setMaxResults(5).getResultList());
        model.addAttribute("staffList", em.createQuery("select u from User u where u.role in :r and u.locked = false and u.id <> :me order by u.fullName", User.class)
                .setParameter("r", List.of(Role.PHARMACIST, Role.ADMIN)).setParameter("me", me.getId()).getResultList()
                .stream().filter(u -> u.can("CONSULT")).toList());
        return "staff/consultation";
    }

    @GetMapping("/{id}/messages")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> messages(@PathVariable Long id, @RequestParam(defaultValue = "0") long after) {
        Conversation c = conv(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("messages", chat.messages(c, after));
        out.put("mode", c.getMode());
        return out;
    }

    @PostMapping("/{id}/messages")
    @Transactional
    public Object send(@PathVariable Long id, @RequestParam(required = false) String body, @RequestParam(required = false) MultipartFile image,
                       HttpServletRequest req) {
        Conversation c = conv(id);
        if (image != null && !image.isEmpty()) {
            com.hieuthuoc.web.Validator.of(Form.of(Map.of())).rule("image", image.getContentType() != null && image.getContentType().startsWith("image/"), "Chỉ nhận file ảnh.")
                    .rule("image", image.getSize() <= 5L * 1024 * 1024, "Ảnh tối đa 5MB.").check();
        }
        chat.staffSend(c, currentUser.get(), body, image);
        if (Web.wantsJson(req)) return ResponseEntity.ok(Map.of("ok", true));
        return "redirect:/staff/consultations/" + c.getId();
    }

    @PostMapping("/{id}/claim")
    @Transactional
    public String claim(@PathVariable Long id, RedirectAttributes ra) {
        Conversation c = conv(id);
        chat.claim(c, currentUser.get());
        Web.success(ra, "Bạn đã nhận cuộc tư vấn này.");
        return "redirect:/staff/consultations/" + c.getId();
    }

    @PostMapping("/{id}/transfer")
    @Transactional
    public String transfer(@PathVariable Long id, @RequestParam(name = "to_user_id", required = false) Long to, RedirectAttributes ra) {
        chat.transfer(conv(id), currentUser.get(), to);
        Web.success(ra, "Đã chuyển cuộc tư vấn.");
        return "redirect:/staff/consultations";
    }

    @PostMapping("/{id}/suggest")
    @Transactional
    public String suggest(@PathVariable Long id, @RequestParam(name = "items[]", required = false) List<String> items,
                          @RequestParam(name = "qtys[]", required = false) List<String> qtys, @RequestParam(required = false) String note, RedirectAttributes ra) {
        Conversation c = conv(id);
        chat.sendSuggestedCart(c, currentUser.get(), items == null ? List.of() : items, qtys == null ? List.of() : qtys, note);
        Web.success(ra, "Đã gửi giỏ hàng tư vấn cho khách.");
        return "redirect:/staff/consultations/" + c.getId();
    }

    @PostMapping("/{id}/close")
    @Transactional
    public String close(@PathVariable Long id, RedirectAttributes ra) {
        Conversation c = conv(id);
        chat.toggleClosed(c, currentUser.get());
        Web.info(ra, c.isClosed() ? "Đã đóng cuộc tư vấn." : "Đã mở lại cuộc tư vấn.");
        return "redirect:/staff/consultations";
    }
}
