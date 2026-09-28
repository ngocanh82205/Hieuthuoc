package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
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
    private final com.hieuthuoc.repository.UserRepository userRepo;
    private final com.hieuthuoc.repository.ProductRepository productRepo;
    private final com.hieuthuoc.repository.SuggestedCartRepository suggestedCartRepo;

    /** Dữ liệu tin nhắn trả về cho giao diện (JSON). */
    public record MessageDto(Long id, Long senderId, String senderName, boolean fromStaff, String body, String imageUrl, String time,
                             Long cartId, List<String> cartLines, long cartTotal) {
        static MessageDto of(Message m) {
            SuggestedCart sc = m.getSuggestedCart();
            List<String> lines = sc == null ? List.of() : sc.getItems().stream()
                    .map(i -> i.getProduct().getName() + " x" + i.getQuantity() + " " + i.getUnitName()).toList();
            return new MessageDto(m.getId(), m.getSender().getId(), m.getSender().getFullName(), m.getSender().isStaff(),
                    m.getBody(), m.getImage() == null ? null : "/files/chat/" + m.getImage(), m.getCreatedAt().format(TIME),
                    sc == null ? null : sc.getId(), lines, sc == null ? 0 : sc.getTotal());
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
        // Phân phối: hội thoại mới / chưa ai nhận / dược sĩ phụ trách đã offline -> giao cho dược sĩ online ít việc nhất
        if (needsAttention || !c.getPharmacist().isOnline()) {
            User assignee = pickOnlinePharmacist();
            if (assignee != null) c.setPharmacist(assignee);
        }
        if (c.getPharmacist() != null && c.getPharmacist().isOnline()) {
            notifications.notify(c.getPharmacist(), "Tin nhắn mới từ " + customer.getFullName(), link);
        } else {
            notifications.notifyStaff("Yêu cầu tư vấn từ " + customer.getFullName() + " (chưa có dược sĩ online nhận)", link);
        }
    }

    /** Dược sĩ đang online có ít hội thoại mở nhất. */
    public User pickOnlinePharmacist() {
        User best = null;
        long bestLoad = Long.MAX_VALUE;
        for (User u : userRepo.findByRoleAndLockedFalse(Role.PHARMACIST)) {
            if (!u.isOnline()) continue;
            long load = conversationRepo.countByPharmacistAndClosedFalse(u);
            if (load < bestLoad) {
                best = u;
                bestLoad = load;
            }
        }
        return best;
    }

    public List<User> onlineStaff() {
        List<User> list = new java.util.ArrayList<>();
        for (User u : userRepo.findByRoleAndLockedFalse(Role.PHARMACIST)) if (u.isOnline()) list.add(u);
        return list;
    }

    public void claim(Long conversationId, User staff) {
        Conversation c = get(conversationId);
        c.setPharmacist(staff);
        notifications.log(staff, "chat.claim", "Hội thoại #" + c.getId());
    }

    public void transfer(Long conversationId, User staff, Long toUserId) {
        Conversation c = get(conversationId);
        User to = userRepo.findById(toUserId).filter(User::isStaff).orElseThrow(() -> new BusinessException("Dược sĩ không hợp lệ."));
        c.setPharmacist(to);
        notifications.notify(to, staff.getFullName() + " chuyển cho bạn cuộc tư vấn với " + c.getCustomer().getFullName(), "/staff/consultations/" + c.getId());
        notifications.log(staff, "chat.transfer", "Hội thoại #" + c.getId() + " → " + to.getFullName());
    }

    /** Dược sĩ tạo giỏ hàng tư vấn và gửi vào hội thoại. items: "productId:unitId" -> số lượng. */
    public SuggestedCart sendSuggestedCart(Long conversationId, User staff, List<String> keys, List<Integer> qtys, String note) {
        Conversation c = get(conversationId);
        SuggestedCart sc = new SuggestedCart();
        sc.setConversation(c);
        sc.setPharmacist(staff);
        sc.setNote(Texts.emptyToNull(Texts.trim(note, 500)));
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            Integer q = i < qtys.size() ? qtys.get(i) : null;
            if (Texts.isBlank(k) || q == null || q <= 0) continue;
            Product p = productRepo.findById(Cart.productIdOf(k)).orElseThrow(() -> new BusinessException("Sản phẩm không tồn tại."));
            UnitOption u = p.findUnit(Cart.unitIdOf(k));
            if (u == null || !p.getDrugType().isSellableOnline()) throw new BusinessException("Sản phẩm " + p.getName() + " không thể gợi ý mua online.");
            SuggestedCartItem it = new SuggestedCartItem();
            it.setCart(sc);
            it.setProduct(p);
            it.setUnitId(u.id());
            it.setUnitName(u.name());
            it.setPrice(u.price());
            it.setQuantity(q);
            sc.getItems().add(it);
        }
        if (sc.getItems().isEmpty()) throw new BusinessException("Chọn ít nhất 1 sản phẩm cho giỏ hàng tư vấn.");
        suggestedCartRepo.save(sc);
        if (c.getPharmacist() == null) c.setPharmacist(staff);
        Message m = new Message();
        m.setConversation(c);
        m.setSender(staff);
        m.setBody("🛒 Dược sĩ gợi ý giỏ hàng cho bạn" + (sc.getNote() != null ? ": " + sc.getNote() : ""));
        m.setSuggestedCart(sc);
        messageRepo.save(m);
        c.setUpdatedAt(LocalDateTime.now());
        c.setClosed(false);
        notifications.notify(c.getCustomer(), "Dược sĩ " + staff.getFullName() + " đã gửi giỏ hàng tư vấn cho bạn", "/consult");
        return sc;
    }

    /** Khách thêm toàn bộ giỏ hàng tư vấn vào giỏ; trả về các sản phẩm không thêm được. */
    public List<String> addSuggestedToCart(Long cartId, User customer, Cart cart, CartService cartService) {
        SuggestedCart sc = suggestedCartRepo.findById(cartId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy giỏ hàng tư vấn."));
        if (!sc.getConversation().getCustomer().getId().equals(customer.getId())) throw BusinessException.notFound("Không tìm thấy giỏ hàng tư vấn.");
        List<String> skipped = new java.util.ArrayList<>();
        for (SuggestedCartItem it : sc.getItems()) {
            Product p = it.getProduct();
            UnitOption u = p.findUnit(it.getUnitId());
            String err = cartService.checkAdd(cart, p, u, it.getQuantity());
            if (err != null) {
                skipped.add(p.getName() + " (" + err + ")");
                continue;
            }
            cart.add(p.getId(), u.id(), it.getQuantity());
        }
        return skipped;
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
