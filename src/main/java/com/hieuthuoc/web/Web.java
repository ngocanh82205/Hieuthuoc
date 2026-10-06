package com.hieuthuoc.web;

import com.hieuthuoc.config.GlobalControllerAdvice;
import com.hieuthuoc.service.BusinessException;
import com.hieuthuoc.service.Form;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Tiện ích chung cho controller (tương đương back(), redirect()->with(), response()->json() của Laravel). */
public final class Web {
    private Web() {
    }

    /** redirect về trang trước (cùng host), không có thì về fallback. */
    public static String back(HttpServletRequest req, String fallback) {
        String ref = GlobalControllerAdvice.sameHostReferer(req);
        return "redirect:" + (ref != null ? ref : fallback);
    }

    public static String back(HttpServletRequest req) {
        return back(req, "/");
    }

    public static void success(RedirectAttributes ra, String msg) {
        ra.addFlashAttribute("success", msg);
    }

    public static void error(RedirectAttributes ra, String msg) {
        ra.addFlashAttribute("error", msg);
    }

    public static void warning(RedirectAttributes ra, String msg) {
        ra.addFlashAttribute("warning", msg);
    }

    public static void info(RedirectAttributes ra, String msg) {
        ra.addFlashAttribute("info", msg);
    }

    public static Form form(MultiValueMap<String, String> params) {
        return new Form(params);
    }

    public static boolean wantsJson(HttpServletRequest req) {
        return GlobalControllerAdvice.wantsJson(req);
    }

    public static <T> T found(Optional<T> o) {
        return o.orElseThrow(BusinessException::notFound);
    }

    public static <T> T found(T o) {
        if (o == null) throw BusinessException.notFound();
        return o;
    }

    public static <T> T found(T o, String message) {
        if (o == null) throw BusinessException.notFound(message);
        return o;
    }

    /** File tải về (Excel...). */
    public static ResponseEntity<byte[]> download(ByteArrayOutputStream out, String filename, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"))
                .contentType(MediaType.parseMediaType(contentType))
                .body(out.toByteArray());
    }

    public static ResponseEntity<byte[]> xlsx(ByteArrayOutputStream out, String filename) {
        return download(out, filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }
}
