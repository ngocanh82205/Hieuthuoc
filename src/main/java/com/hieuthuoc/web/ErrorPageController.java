package com.hieuthuoc.web;

import com.hieuthuoc.config.GlobalControllerAdvice;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/** Trang lỗi chung (errors/page) cho 403/404/419/500 - thay trang lỗi mặc định của Spring Boot. */
@Controller
public class ErrorPageController implements ErrorController {

    @RequestMapping({"/error", "/error-page"})
    public Object error(HttpServletRequest req) {
        Object st = req.getAttribute("errorStatus");
        if (st == null) st = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = st instanceof Integer i ? i : 500;
        String message = (String) req.getAttribute("errorMessage");
        Object uri = req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (uri != null && uri.toString().startsWith(req.getContextPath() + "/api/")) {
            // REST API: cùng định dạng lỗi với ApiExceptionHandler (lỗi xảy ra trước khi vào controller: sai đường dẫn / phương thức)
            String m = message != null ? message : status == 404 ? "Không tìm thấy endpoint." : status == 405 ? "Phương thức HTTP không được hỗ trợ."
                    : status == 403 ? "Không có quyền truy cập." : "Đã có lỗi xảy ra.";
            return ResponseEntity.status(status).body(com.hieuthuoc.api.Api.error(m, null));
        }
        if (GlobalControllerAdvice.wantsJson(req)) {
            return ResponseEntity.status(status).body(Map.of("ok", false, "message", message != null ? message
                    : status == 404 ? "Không tìm thấy." : status == 403 ? "Không có quyền truy cập." : "Đã có lỗi xảy ra."));
        }
        return GlobalControllerAdvice.errorPage(status, message);
    }
}
