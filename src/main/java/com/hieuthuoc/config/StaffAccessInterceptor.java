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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phân quyền theo chức năng (RBAC) cho khu vực nhân viên: mỗi nhóm đường dẫn /staff/... yêu cầu một quyền.
 * Vai trò (Dược sĩ quản lý, Dược sĩ, Biên tập viên...) do admin cấu hình tập quyền.
 */
@Component
@RequiredArgsConstructor
public class StaffAccessInterceptor implements HandlerInterceptor {
    public static final Map<String, StaffPermission> RULES = new LinkedHashMap<>();

    static {
        RULES.put("/staff/prescriptions", StaffPermission.RX_REVIEW);
        RULES.put("/staff/rx-log", StaffPermission.RX_REVIEW);
        RULES.put("/staff/orders", StaffPermission.ORDER);
        RULES.put("/staff/consultations", StaffPermission.CONSULT);
        RULES.put("/staff/questions", StaffPermission.CONSULT);
        RULES.put("/staff/callbacks", StaffPermission.CONSULT);
        RULES.put("/staff/inventory", StaffPermission.INVENTORY);
        RULES.put("/staff/batches", StaffPermission.INVENTORY);
        RULES.put("/staff/receipts", StaffPermission.INVENTORY);
        RULES.put("/staff/stocktake", StaffPermission.INVENTORY);
        RULES.put("/staff/adjustments", StaffPermission.INVENTORY);
        RULES.put("/staff/transfers", StaffPermission.INVENTORY);
        RULES.put("/staff/pos", StaffPermission.POS);
        RULES.put("/staff/posts", StaffPermission.CONTENT);
        RULES.put("/staff/reviews", StaffPermission.CONTENT);
        RULES.put("/staff/products", StaffPermission.CONTENT);
        RULES.put("/staff/schedule", StaffPermission.SCHEDULE);
        RULES.put("/staff/attendance", StaffPermission.SCHEDULE);
        RULES.put("/staff/shifts", StaffPermission.SCHEDULE);
        RULES.put("/staff/payroll", StaffPermission.SCHEDULE);
    }

    private final CurrentUser currentUser;

    public static StaffPermission required(String path) {
        for (Map.Entry<String, StaffPermission> e : RULES.entrySet()) {
            if (path.equals(e.getKey()) || path.startsWith(e.getKey() + "/")) return e.getValue();
        }
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        StaffPermission need = required(path);
        if (need == null) return true;
        User u = currentUser.getOrNull();
        if (u != null && !u.hasPermission(need)) {
            throw new BusinessException("Vai trò của bạn chưa được cấp quyền \"" + need.getLabel() + "\". Liên hệ quản trị viên nếu cần.", 403);
        }
        return true;
    }
}
