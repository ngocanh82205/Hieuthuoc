package com.hieuthuoc.api;

import com.hieuthuoc.entity.Conversation;
import com.hieuthuoc.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Tư vấn trực tuyến: trợ lý AI trả lời trước, chuyển dược sĩ khi cần.
 * Client gửi tin nhắn rồi lấy tin mới bằng polling GET /consult/messages?after={id tin cuối}.
 */
@RestController
@RequestMapping("/api/v1/consult")
@RequiredArgsConstructor
public class ConsultApi {
    private final ChatService chat;
    private final AiAssistantService ai;
    private final CurrentUser currentUser;

    private Map<String, Object> state(Conversation c, long after) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("conversationId", c != null ? c.getId() : null);
        m.put("mode", c == null || c.isAiMode() ? "AI" : "HUMAN");
        m.put("pharmacist", c != null && c.getPharmacist() != null ? c.getPharmacist().getFullName() : null);
        m.put("messages", c != null ? chat.messages(c, after) : List.of());
        return m;
    }

    @GetMapping("/messages")
    @Transactional(readOnly = true)
    public Map<String, Object> messages(@RequestParam(defaultValue = "0") long after) {
        return Api.ok(state(chat.latestFor(currentUser.get()), after));
    }

    /** multipart/form-data hoặc form: "body" (nội dung), "image" (ảnh, không bắt buộc). */
    @PostMapping("/messages")
    @Transactional
    public Map<String, Object> send(@RequestParam(required = false) String body, @RequestParam(required = false) MultipartFile image) {
        ChatService.Sent sent = chat.customerSend(currentUser.get(), body, image);
        Conversation c = sent.conversation();
        if (c.isAiMode()) {
            Long id = c.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ai.onCustomerMessage(id);
                }
            });
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", c.isAiMode() ? "AI" : "HUMAN");
        data.put("message", sent.message() != null ? chat.formatMessage(sent.message()) : null);
        return Api.ok(data, c.isAiMode() ? "Trợ lý đang trả lời..." : "Đã gửi tới dược sĩ.");
    }

    /** Yêu cầu chuyển sang dược sĩ tư vấn trực tiếp. */
    @PostMapping("/handoff")
    @Transactional
    public Map<String, Object> handoff() {
        Conversation c = chat.latestFor(currentUser.get());
        if (c == null) throw new BusinessException("Bạn chưa có cuộc tư vấn nào.", 409);
        if (c.isAiMode()) chat.handoff(c, "Khách yêu cầu gặp dược sĩ", null);
        return Api.ok(state(c, Long.MAX_VALUE), "Đã chuyển cho dược sĩ.");
    }
}
