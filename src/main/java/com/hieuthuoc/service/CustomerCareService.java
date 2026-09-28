package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Tiện ích chăm sóc khách hàng: yêu thích, báo có hàng, hỏi đáp, gọi lại, kho voucher, hạng thành viên, nhắc lịch. */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerCareService {
    private final WishlistItemRepository wishlistRepo;
    private final StockSubscriptionRepository subscriptionRepo;
    private final ProductQuestionRepository questionRepo;
    private final CallbackRequestRepository callbackRepo;
    private final UserVoucherRepository userVoucherRepo;
    private final VoucherRepository voucherRepo;
    private final ReminderRepository reminderRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final StockService stockService;
    private final NotificationService notifications;

    private Product product(Long id) {
        return productRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Sản phẩm không tồn tại."));
    }

    /* ---------------- Yêu thích ---------------- */

    /** Bật/tắt yêu thích; trả về true nếu sau thao tác sản phẩm nằm trong danh sách. */
    public boolean toggleWishlist(User user, Long productId) {
        Product p = product(productId);
        Optional<WishlistItem> existing = wishlistRepo.findByUserAndProduct(user, p);
        if (existing.isPresent()) {
            wishlistRepo.delete(existing.get());
            return false;
        }
        WishlistItem w = new WishlistItem();
        w.setUser(user);
        w.setProduct(p);
        wishlistRepo.save(w);
        return true;
    }

    @Transactional(readOnly = true)
    public Set<Long> wishlistIds(User user) {
        return user == null ? Set.of() : new HashSet<>(wishlistRepo.productIdsOf(user));
    }

    /* ---------------- Báo khi có hàng ---------------- */

    public void subscribeStock(User user, Long productId) {
        Product p = product(productId);
        stockService.fill(p);
        if (p.getAvailable() > 0) throw new BusinessException("Sản phẩm đang còn hàng, bạn có thể đặt mua ngay.");
        if (subscriptionRepo.existsByUserAndProductAndNotifiedFalse(user, p)) throw new BusinessException("Bạn đã đăng ký nhận thông báo cho sản phẩm này.");
        StockSubscription s = new StockSubscription();
        s.setUser(user);
        s.setProduct(p);
        subscriptionRepo.save(s);
    }

    public void unsubscribeStock(User user, Long productId) {
        subscriptionRepo.findFirstByUserAndProductAndNotifiedFalse(user, product(productId)).ifPresent(subscriptionRepo::delete);
    }

    /** Gọi sau khi nhập kho / mở khóa lô: báo cho khách đã đăng ký nếu sản phẩm có hàng trở lại. */
    public int notifyBackInStock(Product p) {
        stockService.fill(p);
        if (p.getAvailable() <= 0 || !p.isActive()) return 0;
        List<StockSubscription> subs = subscriptionRepo.findByProductAndNotifiedFalse(p);
        for (StockSubscription s : subs) {
            notifications.notify(s.getUser(), "🎉 " + p.getName() + " đã có hàng trở lại. Đặt mua ngay!", "/products/" + p.getSlug());
            s.setNotified(true);
            s.setNotifiedAt(LocalDateTime.now());
        }
        return subs.size();
    }

    /* ---------------- Hỏi đáp sản phẩm ---------------- */

    public void ask(User user, Long productId, String question) {
        question = Texts.trim(question, 1000);
        if (question.length() < 10) throw new BusinessException("Câu hỏi cần tối thiểu 10 ký tự.");
        Product p = product(productId);
        ProductQuestion q = new ProductQuestion();
        q.setProduct(p);
        q.setUser(user);
        q.setQuestion(question);
        questionRepo.save(q);
        notifications.notifyStaff("Câu hỏi mới về " + p.getName(), "/staff/questions");
    }

    public void answer(Long questionId, User staff, String answer) {
        answer = Texts.trim(answer, 2000);
        if (answer.length() < 5) throw new BusinessException("Vui lòng nhập câu trả lời.");
        ProductQuestion q = questionRepo.findById(questionId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy câu hỏi."));
        q.setAnswer(answer);
        q.setAnsweredBy(staff);
        q.setAnsweredAt(LocalDateTime.now());
        notifications.notify(q.getUser(), "Dược sĩ đã trả lời câu hỏi của bạn về " + q.getProduct().getName(),
                "/products/" + q.getProduct().getSlug() + "#qa");
        notifications.log(staff, "question.answer", "#" + q.getId());
    }

    public void toggleQuestionHidden(Long questionId, User staff) {
        ProductQuestion q = questionRepo.findById(questionId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy câu hỏi."));
        q.setHidden(!q.isHidden());
        notifications.log(staff, q.isHidden() ? "question.hide" : "question.show", "#" + q.getId());
    }

    /* ---------------- Yêu cầu gọi lại ---------------- */

    public void requestCallback(User user, String name, String phone, String preferredTime, String note, Long productId) {
        name = Texts.trim(name, 100);
        phone = Texts.trim(phone);
        if (name.length() < 2) throw new BusinessException("Vui lòng nhập họ tên.");
        if (!Texts.isPhone(phone)) throw new BusinessException("Số điện thoại không hợp lệ.");
        CallbackRequest c = new CallbackRequest();
        c.setUser(user);
        c.setName(name);
        c.setPhone(phone);
        c.setPreferredTime(Texts.emptyToNull(Texts.trim(preferredTime, 100)));
        c.setNote(Texts.emptyToNull(Texts.trim(note, 500)));
        if (productId != null) c.setProduct(productRepo.findById(productId).orElse(null));
        callbackRepo.save(c);
        notifications.notifyStaff("Yêu cầu gọi lại từ " + name + " - " + phone, "/staff/callbacks");
    }

    public void completeCallback(Long id, User staff, String result) {
        CallbackRequest c = callbackRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy yêu cầu."));
        c.setDone(true);
        c.setHandledBy(staff);
        c.setHandledAt(LocalDateTime.now());
        c.setResult(Texts.emptyToNull(Texts.trim(result, 500)));
        notifications.log(staff, "callback.done", c.getName() + " - " + c.getPhone());
    }

    /* ---------------- Kho voucher ---------------- */

    public void saveVoucher(User user, Long voucherId) {
        Voucher v = voucherRepo.findById(voucherId).filter(Voucher::isInWallet).filter(Voucher::isActive)
                .orElseThrow(() -> BusinessException.notFound("Voucher không tồn tại."));
        if (userVoucherRepo.existsByUserAndVoucher(user, v)) return;
        UserVoucher uv = new UserVoucher();
        uv.setUser(user);
        uv.setVoucher(v);
        userVoucherRepo.save(uv);
    }

    /** Voucher còn dùng được (đang bật, trong thời hạn, còn lượt). */
    public static boolean usable(Voucher v) {
        LocalDate today = LocalDate.now();
        return v.isActive() && (v.getStartDate() == null || !v.getStartDate().isAfter(today))
                && (v.getEndDate() == null || !v.getEndDate().isBefore(today))
                && (v.getUsageLimit() == null || v.getUsedCount() < v.getUsageLimit());
    }

    @Transactional(readOnly = true)
    public List<Voucher> savedVouchers(User user) {
        return userVoucherRepo.findByUserOrderBySavedAtDesc(user).stream().map(UserVoucher::getVoucher).filter(CustomerCareService::usable).toList();
    }

    @Transactional(readOnly = true)
    public List<Voucher> walletCandidates(User user) {
        Set<Long> saved = new HashSet<>();
        for (UserVoucher uv : userVoucherRepo.findByUserOrderBySavedAtDesc(user)) saved.add(uv.getVoucher().getId());
        return voucherRepo.findAllByOrderByIdDesc().stream().filter(Voucher::isInWallet).filter(CustomerCareService::usable)
                .filter(v -> !saved.contains(v.getId())).toList();
    }

    /* ---------------- Hạng thành viên ---------------- */

    @Getter
    public static class TierInfo {
        private final long spent;
        private final MemberTier tier;
        private final MemberTier next;
        private final long toNext;
        private final int progress;

        TierInfo(long spent) {
            this.spent = spent;
            this.tier = MemberTier.of(spent);
            this.next = tier.next();
            this.toNext = next == null ? 0 : next.getMinSpent() - spent;
            this.progress = next == null ? 100
                    : (int) Math.round(100.0 * (spent - tier.getMinSpent()) / (next.getMinSpent() - tier.getMinSpent()));
        }
    }

    @Transactional(readOnly = true)
    public TierInfo tierOf(User user) {
        return new TierInfo(orderRepo.totalSpent(user));
    }

    /* ---------------- Nhắc lịch ---------------- */

    @Getter
    @Setter
    public static class ReminderForm {
        private ReminderType type = ReminderType.MEDICATION;
        private String title;
        private Long productId;
        private String times;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate remindDate;
        private String note;
    }

    public Reminder createReminder(User user, ReminderForm f) {
        Reminder r = new Reminder();
        r.setUser(user);
        r.setType(f.getType() == null ? ReminderType.MEDICATION : f.getType());
        if (f.getProductId() != null) r.setProduct(productRepo.findById(f.getProductId()).orElse(null));
        String title = Texts.trim(f.getTitle(), 200);
        if (title.isEmpty() && r.getProduct() != null) title = r.getProduct().getName();
        if (title.length() < 2) throw new BusinessException("Vui lòng nhập tên thuốc / nội dung nhắc.");
        r.setTitle(title);
        r.setNote(Texts.emptyToNull(Texts.trim(f.getNote(), 300)));
        if (r.getType() == ReminderType.MEDICATION) {
            List<String> times = new ArrayList<>();
            for (String t : Texts.trim(f.getTimes()).split("[,;\\s]+")) {
                if (t.isEmpty()) continue;
                if (!t.matches("([01]\\d|2[0-3]):[0-5]\\d")) throw new BusinessException("Giờ uống không hợp lệ: \"" + t + "\" (định dạng HH:mm).");
                if (!times.contains(t)) times.add(t);
            }
            if (times.isEmpty()) throw new BusinessException("Vui lòng nhập ít nhất một giờ uống (VD: 08:00, 20:00).");
            r.setTimes(String.join(",", times));
            r.setStartDate(f.getStartDate() == null ? LocalDate.now() : f.getStartDate());
            r.setEndDate(f.getEndDate());
            if (r.getEndDate() != null && r.getEndDate().isBefore(r.getStartDate())) throw new BusinessException("Ngày kết thúc phải sau ngày bắt đầu.");
        } else {
            if (f.getRemindDate() == null) throw new BusinessException("Vui lòng chọn ngày nhắc mua lại.");
            r.setRemindDate(f.getRemindDate());
        }
        return reminderRepo.save(r);
    }

    public void toggleReminder(User user, Long id) {
        Reminder r = reminderRepo.findByIdAndUser(id, user).orElseThrow(() -> BusinessException.notFound("Không tìm thấy lịch nhắc."));
        r.setActive(!r.isActive());
    }

    public void deleteReminder(User user, Long id) {
        reminderRepo.findByIdAndUser(id, user).ifPresent(reminderRepo::delete);
    }

    /** Lịch uống thuốc hôm nay của khách (giờ -> tên thuốc). */
    @Transactional(readOnly = true)
    public List<String[]> todaySchedule(User user) {
        LocalDate today = LocalDate.now();
        List<String[]> rows = new ArrayList<>();
        for (Reminder r : reminderRepo.findByUserOrderByActiveDescCreatedAtDesc(user)) {
            if (!r.isActive() || r.getType() != ReminderType.MEDICATION) continue;
            if (r.getStartDate() != null && r.getStartDate().isAfter(today)) continue;
            if (r.getEndDate() != null && r.getEndDate().isBefore(today)) continue;
            for (String t : r.getTimeList()) rows.add(new String[]{t, r.getTitle()});
        }
        rows.sort(Comparator.comparing(a -> a[0]));
        return rows;
    }
}
