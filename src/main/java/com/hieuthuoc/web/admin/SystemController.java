package com.hieuthuoc.web.admin;

import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Web;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/** Admin - Hệ thống: cấu hình nhà thuốc, thanh toán, giao hàng, thông báo; trạng thái các tích hợp. */
@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class SystemController {
    private final SettingService settings;
    private final NotificationService notifications;
    private final GhnService ghn;
    private final CurrentUser currentUser;

    @Value("${app.payos.client-id:}")
    private String payosClientId;
    @Value("${app.payos.api-key:}")
    private String payosApiKey;
    @Value("${app.vnpay.tmn-code:}")
    private String vnpayTmn;
    @Value("${app.ai.gemini-key:}")
    private String geminiKey;
    @Value("${app.ai.anthropic-key:}")
    private String anthropicKey;
    @Value("${app.mail.mailer:log}")
    private String mailer;

    @GetMapping
    public String settings(Model model) {
        Map<String, Boolean> integrations = new LinkedHashMap<>();
        integrations.put("PayOS", !payosClientId.isBlank() && !payosApiKey.isBlank());
        integrations.put("VNPay", !vnpayTmn.isBlank());
        integrations.put("GHN", ghn.enabled());
        integrations.put("Google đăng nhập", false);
        integrations.put("Google Gemini (trợ lý AI)", !geminiKey.isBlank());
        integrations.put("Claude (trợ lý AI - deprecated)", !anthropicKey.isBlank());
        integrations.put("SMTP email", !"log".equals(mailer));
        Map<String, String> labels = new HashMap<>();
        SettingService.DEFAULTS.keySet().forEach(k -> labels.put(k, settings.label(k)));
        model.addAttribute("title", "Cấu hình hệ thống");
        model.addAttribute("groups", SettingService.GROUPS);
        model.addAttribute("cfg", settings.all());
        model.addAttribute("labels", labels);
        model.addAttribute("booleans", SettingService.BOOLEAN);
        model.addAttribute("textareas", SettingService.TEXTAREA);
        model.addAttribute("numerics", SettingService.NUMERIC);
        model.addAttribute("integrations", integrations);
        return "admin/settings";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> in, RedirectAttributes ra) {
        Map<String, String> values = new LinkedHashMap<>(in);
        values.remove("_token");
        String email = values.get("store_email");
        if (email != null && !email.isEmpty() && !Texts.isEmail(email)) throw new BusinessException("Email nhà thuốc không hợp lệ.");
        boolean anyPay = List.of("pay_cod", "pay_bank_transfer", "pay_payos", "pay_vnpay").stream().anyMatch(k -> !Texts.isBlank(values.get(k)));
        if (values.containsKey("_bool_pay_cod") && !anyPay) throw new BusinessException("Cần bật ít nhất một phương thức thanh toán.");
        settings.save(values);
        notifications.log(currentUser.get(), "settings.save", values.keySet().stream().filter(k -> !k.startsWith("_")).collect(Collectors.joining(", ")));
        Web.success(ra, "Đã lưu cấu hình.");
        return "redirect:/admin/settings";
    }
}
