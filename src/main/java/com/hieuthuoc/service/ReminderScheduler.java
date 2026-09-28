package com.hieuthuoc.service;

import com.hieuthuoc.entity.Reminder;
import com.hieuthuoc.entity.ReminderType;
import com.hieuthuoc.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Chạy mỗi phút: gửi thông báo trong web khi đến giờ uống thuốc hoặc đến ngày nhắc mua lại. */
@Component
@RequiredArgsConstructor
public class ReminderScheduler {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final ReminderRepository reminderRepo;
    private final NotificationService notifications;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDate today = now.toLocalDate();
        String hm = now.format(HM);
        for (Reminder r : reminderRepo.findAllActive()) {
            if (r.getLastFiredAt() != null && !r.getLastFiredAt().isBefore(now)) continue;
            if (r.getType() == ReminderType.MEDICATION) {
                if (r.getStartDate() != null && r.getStartDate().isAfter(today)) continue;
                if (r.getEndDate() != null && r.getEndDate().isBefore(today)) {
                    r.setActive(false);
                    continue;
                }
                if (!r.getTimeList().contains(hm)) continue;
                notifications.notify(r.getUser(), "⏰ Đến giờ uống thuốc (" + hm + "): " + r.getTitle()
                        + (r.getNote() != null ? " - " + r.getNote() : ""), "/account/reminders");
                r.setLastFiredAt(now);
            } else if (r.getRemindDate() != null && !r.getRemindDate().isAfter(today) && now.getHour() >= 8) {
                String link = r.getProduct() != null ? "/products/" + r.getProduct().getSlug() : "/account/reminders";
                notifications.notify(r.getUser(), "🛒 Sắp hết thuốc? Đã đến lúc mua lại: " + r.getTitle(), link);
                r.setLastFiredAt(now);
                r.setActive(false);
            }
        }
    }
}
