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
    private final SettingService settings;

    /** Dữ liệu tin nhắn trả về cho giao diện (JSON). */
    public record MessageDto(Long id, Long senderId, String senderName, boolean fromStaff, String body, String imageUrl, String time,
                             Long cartId, List<String> cartLines, long cartTotal, String kind) {
        static MessageDto of(Message m, String aiName) {
            SuggestedCart sc = m.getSuggestedCart();
            List<String> lines = sc == null ? List.of() : sc.getItems().stream()
                    .map(i -> i.getProduct().getName() + " x" + i.getQuantity() + " " + i.getUnitName()).toList();
            User s = m.getSender();
            String name = s != null ? s.getFullName() : m.isAi() ? aiName : "Hệ thống";
            return new MessageDto(m.getId(), s == null ? null : s.getId(), name, s != null && s.isStaff(),
                    m.getBody(), m.getImage() == null ? null : "/files/chat/" + m.getImage(), m.getCreatedAt().format(TIME),
                    sc == null ? null : sc.getId(), lines, sc == null ? 0 : sc.getTotal(), m.getKind());
        }
    }

    public String aiName() {
        return settings.get("ai_name");
    }

    public boolean aiEnabled() {
        return settings.getLong("ai_enabled") == 1;
    }

    @Transactional(readOnly = true)
    public Conversation latestFor(User customer) {
        return conversationRepo.findFirstByCustomerOrderByIdDesc(customer).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<MessageDto> messages(Conversation c, long afterId) {
        if (c == null) return List.of();
        String ai = aiName();
        return messageRepo.findByConversationAndIdGreaterThanOrderByIdAsc(c, afterId).stream().map(m -> MessageDto.of(m, ai)).toList();
    }

    public Conversation get(Long id) {
        return conversationRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy hội thoại."));
    }

    private Message post(Conversation c, User sender, String body, MultipartFile image) {
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
        return m;
    }

    /** Tin nhắn của trợ lý AI (kind = AI) hoặc thông báo hệ thống (kind = SYSTEM). */
    public Message postBot(Conversation c, String kind, String body) {
        Message m = new Message();
        m.setConversation(c);
        m.setKind(kind);
        m.setBody(Texts.trim(body, 2000));
        messageRepo.save(m);
        c.setUpdatedAt(LocalDateTime.now());
        return m;
    }

    /**
     * Chuyển hội thoại từ trợ lý AI sang dược sĩ: ưu tiên dược sĩ có chứng chỉ (quyền duyệt đơn thuốc) đang online.
     * Tóm tắt của trợ lý được lưu lại để dược sĩ không phải hỏi lại khách.
     */
    public void handoff(Conversation c, String reason, String summary) {
        if (!c.isAiMode()) return;
        c.setMode("HUMAN");
        c.setHandoffReason(Texts.trim(reason, 200));
        c.setAiSummary(Texts.emptyToNull(Texts.trim(summary, 1500)));
        c.setHandedOffAt(LocalDateTime.now());
        User assignee = pickHandoffPharmacist();
        c.setPharmacist(assignee);
        postBot(c, "SYSTEM", assignee != null
                ? "Đã chuyển cuộc trò chuyện cho " + assignee.getFullName() + ". Dược sĩ sẽ trả lời bạn ngay tại đây."
                : "Đã chuyển cuộc trò chuyện cho dược sĩ. Hiện chưa có dược sĩ online, dược sĩ sẽ trả lời sớm nhất (hoặc bạn có thể để lại số để được gọi lại).");
        String link = "/staff/consultations/" + c.getId();
        String msg = "Trợ lý AI chuyển tư vấn: " + c.getCustomer().getFullName() + " - " + reason;
        if (assignee != null) notifications.notify(assignee, msg, link);
        else notifications.notifyStaff(msg + " (chưa có dược sĩ online)", link);
    }

    /** Dược sĩ có CCHN (quyền duyệt đơn thuốc) + quyền tư vấn, đang online, ít việc nhất; không có thì người tư vấn bất kỳ. */
    public User pickHandoffPharmacist() {
        User best = null;
        long bestLoad = Long.MAX_VALUE;
        for (User u : userRepo.findByRoleAndLockedFalse(Role.PHARMACIST)) {
            if (!u.isOnline() || !u.hasPermission(StaffPermission.CONSULT) || !u.hasPermission(StaffPermission.RX_REVIEW)) continue;
            long load = conversationRepo.countByPharmacistAndClosedFalse(u);
            if (load < bestLoad) {
                best = u;
                bestLoad = load;
            }
        }
        return best != null ? best : pickOnlinePharmacist();
    }

    /** Khách gửi tin: tạo hội thoại mới nếu chưa có; báo cho dược sĩ phụ trách hoặc toàn bộ dược sĩ. */
    public Conversation customerSend(User customer, String body, MultipartFile image) {
        Conversation c = latestFor(customer);
        boolean fresh = c == null || c.isClosed();
        boolean needsAttention = fresh || c.getPharmacist() == null;
        if (c == null) {
            c = new Conversation();
            c.setCustomer(customer);
            conversationRepo.save(c);
        }
        // Phiên mới: trợ lý AI tiếp nhận trước (nếu admin bật)
        if (fresh && aiEnabled()) {
            c.setMode("AI");
            c.setPharmacist(null);
            c.setTriageAsked(null);
            c.setAiMisses(null);
            c.setAiSummary(null);
            c.setHandoffReason(null);
        }
        post(c, customer, body, image);
        if (c.isAiMode()) return c;
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
        return c;
    }

    /** Dược sĩ đang online có ít hội thoại mở nhất. */
    public User pickOnlinePharmacist() {
        User best = null;
        long bestLoad = Long.MAX_VALUE;
        for (User u : userRepo.findByRoleAndLockedFalse(Role.PHARMACIST)) {
            if (!u.isOnline() || !u.hasPermission(StaffPermission.CONSULT)) continue;
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
        for (User u : userRepo.findByRoleAndLockedFalse(Role.PHARMACIST)) if (u.isOnline() && u.hasPermission(StaffPermission.CONSULT)) list.add(u);
        return list;
    }

    public void claim(Long conversationId, User staff) {
        Conversation c = get(conversationId);
        if (c.isAiMode()) {
            c.setMode("HUMAN");
            c.setHandoffReason("Dược sĩ chủ động tiếp nhận");
            c.setHandedOffAt(LocalDateTime.now());
            postBot(c, "SYSTEM", staff.getFullName() + " đã tham gia cuộc trò chuyện.");
        }
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
        if (c.isAiMode()) claim(conversationId, staff);
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
        if (c.isAiMode()) claim(conversationId, staff);
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
