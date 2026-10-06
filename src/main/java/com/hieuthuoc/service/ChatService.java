package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Chat tư vấn giữa khách và dược sĩ (giao diện cập nhật bằng polling). */
@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {
    private final FileStorageService files;
    private final NotificationService notifications;
    private final SettingService settings;

    @PersistenceContext
    private EntityManager em;

    /** Kết quả khách gửi tin: hội thoại và tin vừa lưu. */
    public record Sent(Conversation conversation, Message message) {
    }

    public String aiName() {
        return Objects.requireNonNullElse(settings.get("ai_name"), "");
    }

    public boolean aiEnabled() {
        return settings.bool("ai_enabled");
    }

    @Transactional(readOnly = true)
    public Conversation latestFor(User customer) {
        List<Conversation> l = em.createQuery("select c from Conversation c where c.customer.id = :u order by c.id desc", Conversation.class)
                .setParameter("u", customer.getId()).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    /** Chuẩn hóa dữ liệu tin nhắn trả về cho giao diện (JSON). */
    public Map<String, Object> formatMessage(Message m) {
        SuggestedCart sc = m.getSuggestedCart();
        User s = m.getSender();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId());
        out.put("senderId", s != null ? s.getId() : null);
        out.put("senderName", s != null ? s.getFullName() : (m.isAi() ? aiName() : "Hệ thống"));
        out.put("fromStaff", s != null && s.isStaff());
        out.put("body", m.getBody());
        out.put("imageUrl", FileStorageService.url("chat", m.getImage()));
        out.put("time", m.getCreatedAt() != null ? m.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM")) : "");
        out.put("kind", m.getKind() != null ? m.getKind() : (s != null && !s.isStaff() ? "USER" : null));
        out.put("cartId", sc != null ? sc.getId() : null);
        out.put("cartLines", sc != null ? sc.getItems().stream().map(i -> i.getProduct().getName() + " x" + i.getQuantity() + " " + i.getUnitName()).toList() : List.of());
        out.put("cartTotal", sc != null ? sc.total() : 0);
        return out;
    }

    /** Dữ liệu tin nhắn trả về cho giao diện (JSON). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> messages(Conversation c, long afterId) {
        if (c == null) return List.of();
        return em.createQuery("select m from Message m where m.conversation.id = :c and m.id > :a order by m.id", Message.class)
                .setParameter("c", c.getId()).setParameter("a", afterId).getResultList().stream().map(this::formatMessage).toList();
    }

    private Message post(Conversation c, User sender, String body0, MultipartFile image) {
        String body = Texts.trim(body0, 2000);
        boolean hasImage = files.isPresent(image);
        if (body.isEmpty() && !hasImage) throw new BusinessException("Tin nhắn trống.");
        Message m = new Message();
        m.setConversation(c);
        m.setSender(sender);
        m.setBody(body.isEmpty() ? null : body);
        m.setImage(hasImage ? files.store("chat", image) : null);
        em.persist(m);
        c.setClosed(false);
        c.touch();
        return m;
    }

    /** Tin nhắn của trợ lý AI (kind = AI) hoặc thông báo hệ thống (kind = SYSTEM). */
    public Message postBot(Conversation c, String kind, String body) {
        Message m = new Message();
        m.setConversation(c);
        m.setKind(kind);
        m.setBody(Texts.trim(body, 2000));
        em.persist(m);
        c.touch();
        return m;
    }

    /** Chuyển hội thoại từ trợ lý AI sang dược sĩ (ưu tiên dược sĩ có CCHN đang online); lưu tóm tắt cho dược sĩ. */
    public void handoff(Conversation c, String reason, String summary) {
        if (!c.isAiMode()) return;
        User assignee = pickHandoffPharmacist();
        c.setMode("HUMAN");
        c.setHandoffReason(Texts.limit(reason, 200, ""));
        c.setAiSummary(summary != null && !summary.isEmpty() ? Texts.limit(summary, 1500, "") : null);
        c.setHandedOffAt(LocalDateTime.now().withNano(0));
        c.setPharmacist(assignee);
        postBot(c, "SYSTEM", assignee != null
                ? "Đã chuyển cuộc trò chuyện cho " + assignee.getFullName() + ". Dược sĩ sẽ trả lời bạn ngay tại đây."
                : "Đã chuyển cuộc trò chuyện cho dược sĩ. Hiện chưa có dược sĩ online, dược sĩ sẽ trả lời sớm nhất.");
        String link = "/staff/consultations/" + c.getId();
        String msg = "Trợ lý AI chuyển tư vấn: " + c.getCustomer().getFullName() + " - " + reason;
        if (assignee != null) notifications.notify(assignee, msg, link);
        else notifications.notifyPermission("CONSULT", msg + " (chưa có dược sĩ online)", link);
    }

    /** Khách hàng chuyển cuộc trò chuyện từ dược sĩ quay lại trợ lý AI. */
    public void resumeAi(Conversation c) {
        if (c.isAiMode()) return;
        c.setMode("AI");
        c.setPharmacist(null);
        c.setHandoffReason(null);
        c.setAiSummary(null);
        c.setHandedOffAt(null);
        String name = aiName().isEmpty() ? "Trợ lý AI" : aiName();
        postBot(c, "SYSTEM", "Cuộc trò chuyện đã được chuyển lại cho " + name + ". Bạn có thể tiếp tục đặt câu hỏi.");
    }

    private long load(User u) {
        return em.createQuery("select count(c) from Conversation c where c.pharmacist.id = :u and c.closed = false", Long.class)
                .setParameter("u", u.getId()).getSingleResult();
    }

    /** Dược sĩ online (có quyền tư vấn) ít việc nhất; needRx: phải có quyền duyệt đơn thuốc. */
    private User pick(boolean needRx) {
        return onlineStaff().stream().filter(u -> !needRx || u.hasPermission(StaffPermission.RX_REVIEW))
                .min(Comparator.comparingLong(this::load)).orElse(null);
    }

    public User pickHandoffPharmacist() {
        User u = pick(true);
        return u != null ? u : pick(false);
    }

    public User pickOnlinePharmacist() {
        return pick(false);
    }

    @Transactional(readOnly = true)
    public List<User> onlineStaff() {
        return em.createQuery("select u from User u where u.role = :r and u.locked = false order by u.id", User.class).setParameter("r", Role.PHARMACIST)
                .getResultList().stream().filter(u -> u.isOnline() && u.hasPermission(StaffPermission.CONSULT)).toList();
    }

    /** Khách gửi tin: tạo hội thoại nếu chưa có; phiên mới do trợ lý AI tiếp nhận trước (nếu bật). */
    public Sent customerSend(User customer, String body, MultipartFile image) {
        Conversation c = latestFor(customer);
        boolean fresh = c == null || c.isClosed();
        boolean needsAttention = fresh || c.getPharmacist() == null;
        if (c == null) {
            c = new Conversation();
            c.setCustomer(customer);
            c.setMode(aiEnabled() ? "AI" : "HUMAN");
            em.persist(c);
        }
        if (fresh && aiEnabled()) {
            c.setMode("AI");
            c.setPharmacist(null);
            c.setTriageAsked(false);
            c.setAiMisses(0);
            c.setAiSummary(null);
            c.setHandoffReason(null);
        } else if (fresh) {
            c.setMode("HUMAN");
        }
        Message m = post(c, customer, body, image);
        if (c.isAiMode()) return new Sent(c, m);
        String link = "/staff/consultations/" + c.getId();
        // Hội thoại mới / chưa ai nhận / dược sĩ phụ trách đã offline -> giao cho dược sĩ online ít việc nhất
        if (needsAttention || c.getPharmacist() == null || !c.getPharmacist().isOnline()) {
            User assignee = pickOnlinePharmacist();
            if (assignee != null) c.setPharmacist(assignee);
        }
        if (c.getPharmacist() != null && c.getPharmacist().isOnline()) {
            notifications.notify(c.getPharmacist(), "Tin nhắn mới từ " + customer.getFullName(), link);
        } else {
            notifications.notifyPermission("CONSULT", "Yêu cầu tư vấn từ " + customer.getFullName() + " (chưa có dược sĩ online nhận)", link);
        }
        return new Sent(c, m);
    }

    /** Dược sĩ khác đang phụ trách và đang trực tuyến (chưa bị khóa) - người khác không được xen vào / giành cuộc tư vấn. */
    public User activeOwnerOtherThan(Conversation c, User staff) {
        User owner = c.getPharmacist();
        if (owner == null || owner.getId().equals(staff.getId())) return null;
        return !owner.isLocked() && owner.isOnline() ? owner : null;
    }

    /**
     * Chỉ người phụ trách mới trả lời / gửi giỏ / chuyển / đóng cuộc tư vấn. Người phụ trách đang trực tuyến thì người khác bị chặn
     * (quản trị viên vẫn được xử lý); người phụ trách đã offline / bị khóa thì cho tiếp quản để khách không phải chờ.
     */
    private void assertCanHandle(Conversation c, User staff) {
        User owner = activeOwnerOtherThan(c, staff);
        if (owner != null && !staff.isAdmin()) {
            throw BusinessException.forbidden("Cuộc tư vấn đang do dược sĩ " + owner.getFullName() + " phụ trách (đang trực tuyến). "
                    + "Nhờ " + owner.getFullName() + " chuyển cho bạn nếu cần.");
        }
    }

    /** Ghi nhận người tiếp quản thay người phụ trách cũ: thông báo cho người cũ và ghi vào hội thoại. */
    private void takeOver(Conversation c, User staff) {
        User prev = c.getPharmacist();
        if (prev == null || prev.getId().equals(staff.getId())) return;
        notifications.notify(prev, staff.getFullName() + " đã tiếp nhận cuộc tư vấn với " + c.getCustomer().getFullName() + " thay bạn", "/staff/consultations/" + c.getId());
        postBot(c, "SYSTEM", staff.getFullName() + " tiếp nhận cuộc trò chuyện thay " + prev.getFullName() + ".");
        c.setPharmacist(staff);
    }

    public void claim(Conversation c, User staff) {
        assertCanHandle(c, staff);
        takeOver(c, staff);
        if (c.isAiMode()) {
            c.setMode("HUMAN");
            c.setHandoffReason("Dược sĩ chủ động tiếp nhận");
            c.setHandedOffAt(LocalDateTime.now().withNano(0));
            postBot(c, "SYSTEM", staff.getFullName() + " đã tham gia cuộc trò chuyện.");
        }
        c.setPharmacist(staff);
        notifications.log(staff, "chat.claim", "Hội thoại #" + c.getId());
    }

    public void transfer(Conversation c, User staff, Long toUserId) {
        assertCanHandle(c, staff);
        User to = toUserId == null ? null : em.find(User.class, toUserId);
        if (to == null || !to.isStaff() || to.isLocked()) throw new BusinessException("Dược sĩ không hợp lệ.");
        c.setPharmacist(to);
        notifications.notify(to, staff.getFullName() + " chuyển cho bạn cuộc tư vấn với " + c.getCustomer().getFullName(), "/staff/consultations/" + c.getId());
        notifications.log(staff, "chat.transfer", "Hội thoại #" + c.getId() + " → " + to.getFullName());
    }

    /** Dược sĩ tạo giỏ hàng tư vấn và gửi vào hội thoại. keys: "productId:unitId", qtys: số lượng tương ứng. */
    public SuggestedCart sendSuggestedCart(Conversation c, User staff, List<String> keys, List<String> qtys, String note0) {
        assertCanHandle(c, staff);
        List<SuggestedCartItem> items = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            int q = i < qtys.size() ? Texts.toInt(qtys.get(i), 0) : 0;
            if (k == null || k.isEmpty() || q <= 0) continue;
            Product p = em.find(Product.class, Cart.productIdOf(k));
            if (p == null) throw new BusinessException("Sản phẩm không tồn tại.");
            if (!p.isActive() || !p.getDrugType().isSellableOnline()) throw new BusinessException("Sản phẩm " + p.getName() + " không thể gợi ý mua online.");
            UnitOption u = p.findUnit(Cart.unitIdOf(k));
            SuggestedCartItem it = new SuggestedCartItem();
            it.setProduct(p);
            it.setUnitId(u.id() == 0 ? null : u.id());
            it.setUnitName(u.name());
            it.setPrice(u.price());
            it.setQuantity(q);
            items.add(it);
        }
        if (items.isEmpty()) throw new BusinessException("Chọn ít nhất 1 sản phẩm cho giỏ hàng tư vấn.");
        String note = note0 != null && !note0.trim().isEmpty() ? Texts.trim(note0, 500) : null;
        SuggestedCart sc = new SuggestedCart();
        sc.setConversation(c);
        sc.setPharmacist(staff);
        sc.setNote(note);
        for (SuggestedCartItem it : items) {
            it.setSuggestedCart(sc);
            sc.getItems().add(it);
        }
        em.persist(sc);
        if (c.isAiMode()) claim(c, staff);
        if (c.getPharmacist() == null) c.setPharmacist(staff);
        else if (activeOwnerOtherThan(c, staff) == null) takeOver(c, staff); // người phụ trách cũ đã offline: chuyển hẳn cho người đang trả lời
        Message m = new Message();
        m.setConversation(c);
        m.setSender(staff);
        m.setSuggestedCart(sc);
        m.setBody("🛒 Dược sĩ gợi ý giỏ hàng cho bạn" + (note != null ? ": " + note : ""));
        em.persist(m);
        c.setClosed(false);
        c.touch();
        notifications.notify(c.getCustomer(), "Dược sĩ " + staff.getFullName() + " đã gửi giỏ hàng tư vấn cho bạn", "/consult");
        return sc;
    }

    /** Khách thêm giỏ hàng tư vấn vào giỏ; trả về các sản phẩm không thêm được. */
    public List<String> addSuggestedToCart(SuggestedCart sc, User customer, Cart cart, CartService carts) {
        if (!sc.getConversation().getCustomer().getId().equals(customer.getId())) throw BusinessException.notFound("Không tìm thấy giỏ hàng tư vấn.");
        List<String> skipped = new ArrayList<>();
        for (SuggestedCartItem it : sc.getItems()) {
            Product p = it.getProduct();
            UnitOption u = p.findUnit(it.getUnitId());
            String err = carts.checkAdd(cart, p, u, it.getQuantity());
            if (err != null) {
                skipped.add(p.getName() + " (" + err + ")");
                continue;
            }
            cart.add(p.getId(), u.id(), it.getQuantity());
        }
        return skipped;
    }

    public void staffSend(Conversation c, User staff, String body, MultipartFile image) {
        assertCanHandle(c, staff);
        if (c.isAiMode()) claim(c, staff);
        if (c.getPharmacist() == null) c.setPharmacist(staff);
        else if (activeOwnerOtherThan(c, staff) == null) takeOver(c, staff); // người phụ trách cũ đã offline: chuyển hẳn cho người đang trả lời
        post(c, staff, body, image);
        notifications.notify(c.getCustomer(), "Dược sĩ " + staff.getFullName() + " đã trả lời tư vấn của bạn", "/consult");
    }

    public void toggleClosed(Conversation c, User staff) {
        assertCanHandle(c, staff);
        if (c.getPharmacist() == null) c.setPharmacist(staff);
        c.setClosed(!c.isClosed());
    }
}
