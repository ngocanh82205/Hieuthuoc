package com.hieuthuoc.config;

import com.hieuthuoc.entity.Conversation;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.service.ChatService;
import com.hieuthuoc.service.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dữ liệu cho khung chat tư vấn nổi (View::composer('partials.consult-widget') của bản Laravel): ${@consultWidget.data()} */
@Component("consultWidget")
@RequiredArgsConstructor
public class ConsultWidget {
    private final ChatService chat;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public Map<String, Object> data() {
        User user = currentUser.getOrNull();
        boolean aiEnabled = chat.aiEnabled();
        Map<String, Object> d = new HashMap<>();
        Conversation conv = null;
        List<Map<String, Object>> messages = List.of();
        boolean aiMode = aiEnabled;
        if (user != null && user.isCustomer()) {
            conv = chat.latestFor(user);
            messages = chat.messages(conv, 0);
            aiMode = conv != null ? conv.isAiMode() : aiEnabled;
        }
        d.put("conv", conv);
        d.put("pharmacistName", conv != null && conv.getPharmacist() != null ? conv.getPharmacist().getFullName() : null);
        d.put("messages", messages);
        d.put("lastId", messages.isEmpty() ? 0 : messages.get(messages.size() - 1).get("id"));
        d.put("aiEnabled", aiEnabled);
        d.put("aiName", chat.aiName());
        d.put("aiMode", aiMode);
        return d;
    }
}
