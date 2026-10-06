package com.hieuthuoc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieuthuoc.service.Form;
import com.hieuthuoc.service.Page;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Định dạng phản hồi thống nhất của REST API:
 * thành công {"success": true, "data": ..., "message"?: ..., "meta"?: {...}};
 * lỗi {"success": false, "message": ..., "errors"?: [...]}.
 */
public final class Api {
    private static final ObjectMapper JSON = new ObjectMapper();

    private Api() {
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("data", data);
        return m;
    }

    public static Map<String, Object> ok(Object data, String message) {
        Map<String, Object> m = ok(data);
        m.put("message", message);
        return m;
    }

    public static ResponseEntity<Map<String, Object>> created(Object data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(data, message));
    }

    /** Danh sách có phân trang: data = các phần tử, meta = thông tin trang. */
    public static <T> Map<String, Object> page(Page<T> p, Function<T, ?> mapper) {
        Map<String, Object> m = ok(p.getItems().stream().map(mapper).toList());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", p.getPage());
        meta.put("perPage", p.getPerPage());
        meta.put("total", p.getTotal());
        meta.put("lastPage", p.getLastPage());
        m.put("meta", meta);
        return m;
    }

    public static Map<String, Object> error(String message, List<String> errors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        if (errors != null && !errors.isEmpty()) m.put("errors", errors);
        return m;
    }

    static void writeError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(JSON.writeValueAsString(error(message, null)));
    }

    /**
     * Chuyển body JSON thành Form (dạng tham số form của tầng service): giá trị đơn giữ nguyên,
     * mảng đối tượng thành "name[i][field]", mảng giá trị thành "name[]".
     */
    public static Form form(Map<String, Object> body) {
        MultiValueMap<String, String> m = new LinkedMultiValueMap<>();
        if (body != null) body.forEach((k, v) -> flatten(m, k, v));
        return new Form(m);
    }

    private static void flatten(MultiValueMap<String, String> m, String key, Object v) {
        if (v == null) return;
        if (v instanceof Map<?, ?> map) {
            map.forEach((k, x) -> flatten(m, key + "[" + k + "]", x));
        } else if (v instanceof Collection<?> list) {
            int i = 0;
            for (Object x : list) {
                if (x instanceof Map<?, ?>) flatten(m, key + "[" + i + "]", x);
                else if (x != null) m.add(key + "[]", String.valueOf(x));
                i++;
            }
        } else if (v instanceof Boolean b) {
            if (b) m.add(key, "1");
        } else {
            m.add(key, String.valueOf(v));
        }
    }
}
