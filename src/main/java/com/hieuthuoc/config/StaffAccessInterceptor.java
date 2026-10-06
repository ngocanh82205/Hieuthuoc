package com.hieuthuoc.config;

import com.hieuthuoc.entity.StaffPermission;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.service.BusinessException;
import com.hieuthuoc.service.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Phân quyền theo chức năng (RBAC) cho khu vực nhân viên, tương ứng middleware perm:... của các nhóm route /staff/... */
@Component
@RequiredArgsConstructor
public class StaffAccessInterceptor implements HandlerInterceptor {
    private record Rule(String method, Pattern path, StaffPermission permission) {
    }

    private static final List<Rule> RULES = new ArrayList<>();

    private static void rule(String method, String regex, StaffPermission p) {
        RULES.add(new Rule(method, Pattern.compile(regex), p));
    }

    static {
        // Duyệt phiếu kho (chỉ admin) phải đứng trước quyền Kho
        rule("POST", "/staff/receipts/\\d+/decide", StaffPermission.APPROVE_STOCK);
        rule("POST", "/staff/adjustments/\\d+/decide", StaffPermission.APPROVE_STOCK);
        rule("POST", "/staff/orders/\\d+/(refund|return)", StaffPermission.REFUND);
        rule(null, "/staff/prescriptions(/.*)?", StaffPermission.RX_REVIEW);
        rule(null, "/staff/orders(/.*)?", StaffPermission.ORDER);
        rule(null, "/staff/pos(/.*)?", StaffPermission.POS);
        rule(null, "/staff/consultations(/.*)?", StaffPermission.CONSULT);
        rule(null, "/staff/(reviews|products|posts|faqs)(/.*)?", StaffPermission.CONTENT);
        rule(null, "/staff/(inventory|batches|stocktake|adjustments|receipts)(/.*)?", StaffPermission.INVENTORY);
    }

    private final CurrentUser currentUser;

    public static StaffPermission required(String method, String path) {
        for (Rule r : RULES) {
            if ((r.method() == null || r.method().equalsIgnoreCase(method)) && r.path().matcher(path).matches()) return r.permission();
        }
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        StaffPermission need = required(req.getMethod(), path);
        if (need == null) return true;
        User u = currentUser.getOrNull();
        if (u == null || !u.hasPermission(need)) {
            throw new BusinessException("Vai trò của bạn chưa được cấp quyền \"" + need.getShortLabel() + "\". Liên hệ quản trị viên nếu cần.", 403);
        }
        return true;
    }
}
