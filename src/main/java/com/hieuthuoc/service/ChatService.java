package com.hieuthuoc.service;

import com.hieuthuoc.entity.Conversation;
import com.hieuthuoc.entity.Message;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.ConversationRepository;
import com.hieuthuoc.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final FileStorageService files;
    private final NotificationService notifications;

    /** Dữ liệu tin nhắn trả về cho giao diện (JSON). */
    public record MessageDto(Long id, Long senderId, String senderName, boolean fromStaff, String body, String imageUrl, String time) {
        static MessageDto of(Message m) {
            return new MessageDto(m.getId(), m.getSender().getId(), m.getSender().getFullName(), m.getSender().isStaff(),
                    m.getBody(), m.getImage() == null ? null : "/files/chat/" + m.getImage(), m.getCreatedAt().format(TIME));
        }
    }

    @Transactional(readOnly = true)
    public Conversation latestFor(User customer) {
        return conversationRepo.findFirstByCustomerOrderByIdDesc(customer).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<MessageDto> messages(Conversation c, long afterId) {
        if (c == null) return List.of();
        return messageRepo.findByConversationAndIdGreaterThanOrderByIdAsc(c, afterId).stream().map(MessageDto::of).toList();
    }

    public Conversation get(Long id) {
        return conversationRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy hội thoại."));
    }

    private void post(Conversation c, User sender, String body, MultipartFile image) {
        body = Texts.trim(body, 2000);
        if (body.isEmpty() && !files.isPresent(image)) throw new BusinessException("Tin nhắn trống.");
        Message m = new Message();
        m.setConversation(c);
        m.setSender(sender);
        m.setBody(body.isEmpty() ? null : body);
        if (files.isPresent(image)) m.setImage(files.store(FileStorageService.Kind.CHAT, image));
        messageRepo.save(m);
        c.setUpdatedAt(LocalDateTime.now());
        c.setClosed(false);
    }

    /** Khách gửi tin: tạo hội thoại mới nếu chưa có; báo cho dược sĩ phụ trách hoặc toàn bộ dược sĩ. */
    public void customerSend(User customer, String body, MultipartFile image) {
        Conversation c = latestFor(customer);
        boolean needsAttention = c == null || c.isClosed() || c.getPharmacist() == null;
        if (c == null) {
            c = new Conversation();
            c.setCustomer(customer);
            conversationRepo.save(c);
        }
        post(c, customer, body, image);
        String link = "/staff/consultations/" + c.getId();
        if (c.getPharmacist() != null && !needsAttention) {
            notifications.notify(c.getPharmacist(), "Tin nhắn mới từ " + customer.getFullName(), link);
        } else if (needsAttention) {
            notifications.notifyStaff("Yêu cầu tư vấn từ " + customer.getFullName(), link);
        }
    }

    public void staffSend(Long conversationId, User staff, String body, MultipartFile image) {
        Conversation c = get(conversationId);
        if (c.getPharmacist() == null) c.setPharmacist(staff);
        post(c, staff, body, image);
        notifications.notify(c.getCustomer(), "Dược sĩ " + staff.getFullName() + " đã trả lời tư vấn của bạn", "/consult");
    }

    public void toggleClosed(Long conversationId, User staff) {
        Conversation c = get(conversationId);
        if (c.getPharmacist() == null) c.setPharmacist(staff);
        c.setClosed(!c.isClosed());
    }
}
