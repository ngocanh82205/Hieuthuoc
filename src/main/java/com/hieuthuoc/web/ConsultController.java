package com.hieuthuoc.web;

import com.hieuthuoc.entity.Conversation;
import com.hieuthuoc.entity.SuggestedCart;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Khách hàng chat tư vấn với trợ lý AI / dược sĩ (cập nhật tin nhắn bằng polling). */
@Controller
@RequestMapping("/consult")
@RequiredArgsConstructor
public class ConsultController {
    private final ChatService chat;
    private final AiAssistantService ai;
    private final CurrentUser currentUser;
    private final Cart cart;
    private final CartService carts;

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    @Transactional(readOnly = true)
    public String chat(Model model) {
        Conversation c = chat.latestFor(currentUser.get());
        List<Map<String, Object>> messages = chat.messages(c, 0);
        boolean aiEnabled = chat.aiEnabled();
        model.addAttribute("title", "Tư vấn với dược sĩ");
        model.addAttribute("conv", c);
        model.addAttribute("pharmacistName", c != null && c.getPharmacist() != null ? c.getPharmacist().getFullName() : null);
        model.addAttribute("messages", messages);
        model.addAttribute("lastId", messages.isEmpty() ? 0 : messages.get(messages.size() - 1).get("id"));
        model.addAttribute("aiEnabled", aiEnabled);
        model.addAttribute("aiName", chat.aiName());
        model.addAttribute("aiMode", c != null ? c.isAiMode() : aiEnabled);
        return "consult/chat";
    }

    @GetMapping("/messages")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> messages(@RequestParam(defaultValue = "0") long after) {
        Conversation c = chat.latestFor(currentUser.get());
        Map<String, Object> out = new HashMap<>();
        out.put("messages", chat.messages(c, after));
        out.put("mode", c != null && c.isAiMode() ? "AI" : "HUMAN");
        out.put("pharmacist", c != null && c.getPharmacist() != null ? c.getPharmacist().getFullName() : null);
        return out;
    }

    /** Khách bấm "Gặp dược sĩ": trợ lý AI chuyển hội thoại kèm tóm tắt. */
    @PostMapping("/handoff")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> handoff() {
        User u = currentUser.get();
        Conversation c = chat.latestFor(u);
        if (c != null && !c.getCustomer().getId().equals(u.getId())) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "message", "Bạn không có quyền thay đổi cuộc hội thoại này."));
        }
        ai.requestHandoff(u);
        return ResponseEntity.ok(Map.of("ok", true, "mode", "HUMAN"));
    }

    /** Khách bấm "Quay lại tư vấn AI": chuyển chế độ từ HUMAN sang AI. */
    @PostMapping("/resume-ai")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> resumeAi() {
        if (!chat.aiEnabled()) return ResponseEntity.status(422).body(Map.of("ok", false, "message", "Chức năng Trợ lý AI hiện đang tạm tắt."));
        if (!ai.usesGemini()) return ResponseEntity.status(422).body(Map.of("ok", false, "message", "Trợ lý AI chưa được cấu hình API key."));
        User u = currentUser.get();
        Conversation c = chat.latestFor(u);
        if (c == null) return ResponseEntity.status(404).body(Map.of("ok", false, "message", "Không tìm thấy cuộc hội thoại."));
        if (!c.getCustomer().getId().equals(u.getId())) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "message", "Bạn không có quyền thay đổi cuộc hội thoại này."));
        }
        if (!c.isAiMode()) chat.resumeAi(c);
        return ResponseEntity.ok(Map.of("ok", true, "mode", "AI", "aiName", chat.aiName()));
    }

    @PostMapping("/messages")
    @ResponseBody
    @Transactional
    public Map<String, Object> send(@RequestParam(required = false) String body, @RequestParam(required = false) MultipartFile image) {
        ChatService.Sent sent = chat.customerSend(currentUser.get(), body, image);
        Conversation c = sent.conversation();
        if (c.isAiMode()) {
            // Trợ lý trả lời sau khi giao dịch lưu tin nhắn đã commit (giao diện nhận qua polling)
            Long id = c.getId();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            ai.onCustomerMessage(id);
                        }
                    });
        }
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("mode", c.isAiMode() ? "AI" : "HUMAN");
        out.put("message", sent.message() != null ? chat.formatMessage(sent.message()) : null);
        return out;
    }

    /** Khách thêm giỏ hàng tư vấn của dược sĩ vào giỏ hàng. */
    @PostMapping("/carts/{id}/add")
    @Transactional(readOnly = true)
    public String addSuggested(@PathVariable Long id, RedirectAttributes ra) {
        SuggestedCart sc = em.find(SuggestedCart.class, id);
        if (sc == null) throw BusinessException.notFound();
        List<String> skipped = chat.addSuggestedToCart(sc, currentUser.get(), cart, carts);
        if (skipped.isEmpty()) Web.success(ra, "Đã thêm giỏ hàng tư vấn vào giỏ hàng của bạn.");
        else Web.warning(ra, "Đã thêm vào giỏ. Không thêm được: " + String.join("; ", skipped));
        return "redirect:/cart";
    }
}
