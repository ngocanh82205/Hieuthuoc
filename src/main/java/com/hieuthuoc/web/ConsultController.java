package com.hieuthuoc.web;

import com.hieuthuoc.entity.Conversation;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.service.ChatService;
import com.hieuthuoc.service.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Khách hàng chat tư vấn với dược sĩ (cập nhật tin nhắn bằng polling). */
@Controller
@RequestMapping("/consult")
@RequiredArgsConstructor
public class ConsultController {
    private final ChatService chatService;
    private final CurrentUser currentUser;
    private final com.hieuthuoc.service.Cart cart;
    private final com.hieuthuoc.service.CartService cartService;
    private final com.hieuthuoc.service.AiAssistantService aiAssistant;

    @GetMapping
    public String chat(Model model) {
        Conversation c = chatService.latestFor(currentUser.get());
        model.addAttribute("conv", c);
        model.addAttribute("messages", chatService.messages(c, 0));
        model.addAttribute("aiEnabled", chatService.aiEnabled());
        model.addAttribute("aiName", chatService.aiName());
        model.addAttribute("title", "Tư vấn với dược sĩ");
        return "consult/chat";
    }

    @GetMapping("/messages")
    @ResponseBody
    public Map<String, Object> messages(@RequestParam(defaultValue = "0") long after) {
        Conversation c = chatService.latestFor(currentUser.get());
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("messages", chatService.messages(c, after));
        out.put("mode", c != null && c.isAiMode() ? "AI" : "HUMAN");
        out.put("pharmacist", c != null && c.getPharmacist() != null ? c.getPharmacist().getFullName() : null);
        return out;
    }

    /** Khách bấm "Gặp dược sĩ": trợ lý AI chuyển hội thoại kèm tóm tắt. */
    @PostMapping("/handoff")
    @ResponseBody
    public Map<String, Object> handoff() {
        aiAssistant.requestHandoff(currentUser.get());
        return Map.of("ok", true);
    }

    @PostMapping("/messages")
    @ResponseBody
    public Map<String, Object> send(@RequestParam(required = false) String body,
                                    @RequestParam(required = false) MultipartFile image) {
        User u = currentUser.get();
        Conversation c = chatService.customerSend(u, body, image);
        if (c.isAiMode()) aiAssistant.onCustomerMessage(c.getId());
        return Map.of("ok", true, "mode", c.isAiMode() ? "AI" : "HUMAN");
    }

    /** Khách thêm giỏ hàng tư vấn của dược sĩ vào giỏ hàng. */
    @PostMapping("/carts/{id}/add")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String addSuggested(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        List<String> skipped = chatService.addSuggestedToCart(id, currentUser.get(), cart, cartService);
        if (skipped.isEmpty()) Flash.success(ra, "Đã thêm giỏ hàng tư vấn vào giỏ hàng của bạn.");
        else Flash.warning(ra, "Đã thêm vào giỏ. Không thêm được: " + String.join("; ", skipped));
        return "redirect:/cart";
    }
}
