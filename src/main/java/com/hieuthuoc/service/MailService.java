package com.hieuthuoc.service;

import com.hieuthuoc.entity.Order;
import com.hieuthuoc.entity.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

/**
 * Gửi email cho khách: xác nhận đơn, cập nhật trạng thái, khuyến mãi. Lỗi gửi mail không làm hỏng nghiệp vụ.
 * app.mail.mailer = log (mặc định, giống MAIL_MAILER=log của Laravel: chỉ ghi log) hoặc smtp (cấu hình spring.mail.*).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {
    private final SettingService settings;
    private final TemplateEngine templates;
    private final ObjectProvider<JavaMailSender> sender;

    @Value("${app.mail.mailer:log}")
    private String mailer;

    @Value("${app.mail.from:hotro@vinapharma.vn}")
    private String from;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    public boolean enabled() {
        return settings.bool("mail_enabled");
    }

    /** Đường dẫn tuyệt đối tới trang trong web (dùng cho nút trong email). */
    public String url(String path) {
        return appUrl.replaceAll("/+$", "") + path;
    }

    public boolean send(User user, String subject, List<String> lines, String actionText, String actionUrl) {
        return send(user, subject, lines, actionText, actionUrl, null, null);
    }

    public boolean send(User user, String subject, List<String> lines, String actionText, String actionUrl, Order order, String email) {
        String to = email != null ? email : (user != null ? user.getEmail() : null);
        if (!enabled() || to == null || to.isBlank()) return false;
        try {
            Context ctx = new Context();
            ctx.setVariable("subjectLine", subject);
            ctx.setVariable("greeting", "Xin chào " + (user != null ? user.getFullName() : "quý khách") + ",");
            ctx.setVariable("lines", lines);
            ctx.setVariable("actionText", actionText);
            ctx.setVariable("actionUrl", actionUrl);
            ctx.setVariable("order", order);
            ctx.setVariable("settings", settings.all());
            String html = templates.process("emails/notice", ctx);
            JavaMailSender s = sender.getIfAvailable();
            if (!"smtp".equalsIgnoreCase(mailer) || s == null) {
                log.info("[mail] To: {} | Subject: {}\n{}", to, subject, String.join("\n", lines));
                return true;
            }
            MimeMessage msg = s.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");
            h.setFrom(from, settings.get("store_name"));
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true);
            s.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("Gửi email thất bại: {}", e.getMessage());
            return false;
        }
    }

    public void orderPlaced(Order order) {
        String first = "Cảm ơn bạn đã đặt hàng tại " + settings.get("store_name") + ". Mã đơn hàng của bạn là " + order.getCode() + ".";
        String second = order.isNeedsPrescription()
                ? "Đơn có thuốc kê đơn - dược sĩ sẽ kiểm tra đơn thuốc và thông báo cho bạn sớm nhất."
                : "Nhà thuốc sẽ xác nhận và chuẩn bị hàng cho bạn trong thời gian sớm nhất.";
        send(order.getUser(), "Xác nhận đơn hàng " + order.getCode(), List.of(first, second), "Theo dõi đơn hàng",
                url("/account/orders/" + order.getCode()), order, null);
    }

    public void orderStatus(Order order, String message) {
        send(order.getUser(), "Cập nhật đơn hàng " + order.getCode() + ": " + order.getStatus().getLabel(), List.of(message),
                "Xem đơn hàng", url("/account/orders/" + order.getCode()));
    }
}
