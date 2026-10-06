package com.hieuthuoc.config;

import com.hieuthuoc.service.SettingService;
import com.hieuthuoc.service.Texts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Hàm dùng trong template Thymeleaf, tương ứng helper của bản Laravel:
 * ${@h.money(x)}, ${@h.date(d)}, ${@h.dt(d)}, ${@h.is('/account/orders/**')}, ${@h.query('page', 2)}...
 */
@Component("h")
@RequiredArgsConstructor
public class ViewHelper {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final SettingService settings;

    private static NumberFormat nf() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
    }

    /** 125000 -> "125.000 ₫" */
    public String money(Object n) {
        return num(n) + " ₫";
    }

    public String num(Object n) {
        if (n == null) return "0";
        if (n instanceof Number x) return nf().format(Math.round(x.doubleValue()));
        try {
            return nf().format(Math.round(Double.parseDouble(n.toString().trim())));
        } catch (NumberFormatException e) {
            return n.toString();
        }
    }

    /** Số thập phân (tỷ lệ %, điểm đánh giá): 12,5 */
    public String dec(Object n) {
        if (n == null) return "0";
        double d = n instanceof Number x ? x.doubleValue() : Double.parseDouble(n.toString());
        NumberFormat f = nf();
        f.setMaximumFractionDigits(1);
        return f.format(d);
    }

    public String date(Object d) {
        if (d instanceof LocalDate x) return x.format(DATE);
        if (d instanceof LocalDateTime x) return x.format(DATE);
        return d == null ? "" : d.toString();
    }

    public String dt(Object d) {
        if (d instanceof LocalDateTime x) return x.format(DATE_TIME);
        if (d instanceof LocalDate x) return x.format(DATE);
        return d == null ? "" : d.toString();
    }

    public String fmt(Object d, String pattern) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern(pattern);
        if (d instanceof LocalDateTime x) return x.format(f);
        if (d instanceof LocalDate x) return x.format(f);
        return d == null ? "" : d.toString();
    }

    /** Giá trị cho input type=date. */
    public String iso(Object d) {
        if (d instanceof LocalDate x) return x.toString();
        if (d instanceof LocalDateTime x) return x.toLocalDate().toString();
        return d == null ? "" : d.toString();
    }

    /** Giá trị cho input type=datetime-local. */
    public String isoTime(LocalDateTime d) {
        return d == null ? "" : d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    public long daysUntil(LocalDate d) {
        return d == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), d);
    }

    /** Thời gian tương đối kiểu "5 phút trước". */
    public String ago(LocalDateTime d) {
        if (d == null) return "";
        long s = ChronoUnit.SECONDS.between(d, LocalDateTime.now());
        if (s < 60) return "vừa xong";
        if (s < 3600) return (s / 60) + " phút trước";
        if (s < 86400) return (s / 3600) + " giờ trước";
        if (s < 86400 * 30) return (s / 86400) + " ngày trước";
        return d.format(DATE);
    }

    public String stars(Object rating) {
        int r = rating == null ? 0 : (int) Math.round(rating instanceof Number x ? x.doubleValue() : Double.parseDouble(rating.toString()));
        r = Math.max(0, Math.min(5, r));
        return "★".repeat(r) + "☆".repeat(5 - r);
    }

    public String setting(String key) {
        return settings.get(key);
    }

    public String setting(String key, String def) {
        return settings.get(key, def);
    }

    /** Nội dung dạng markdown đơn giản (## tiêu đề, - danh sách, đoạn văn), luôn escape - dùng với th:utext. */
    public String md(String text) {
        return Texts.simpleMd(text);
    }

    /** Escape rồi đổi xuống dòng thành &lt;br&gt; - dùng với th:utext. */
    public String nl2br(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text).replace("\r\n", "\n").replace("\n", "<br>");
    }

    public String limit(String s, int max) {
        return Texts.limit(s, max);
    }

    public String limit(String s, int max, String end) {
        return Texts.limit(s, max, end);
    }

    public String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    public String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    /* ======================= Request hiện tại ======================= */

    private static HttpServletRequest request() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest() : null;
    }

    public String path() {
        HttpServletRequest r = request();
        return r == null ? "/" : r.getRequestURI().substring(r.getContextPath().length());
    }

    public String uri() {
        HttpServletRequest r = request();
        if (r == null) return "/";
        return r.getRequestURI() + (r.getQueryString() != null ? "?" + r.getQueryString() : "");
    }

    /** URL tuyệt đối (chia sẻ mạng xã hội, canonical): abs('/products/abc'). */
    public String abs(String path) {
        HttpServletRequest r = request();
        if (r == null) return path;
        String base = r.getScheme() + "://" + r.getServerName()
                + ((r.getServerPort() == 80 || r.getServerPort() == 443) ? "" : ":" + r.getServerPort()) + r.getContextPath();
        return base + path;
    }

    /** Đường dẫn hiện tại khớp một trong các mẫu (Ant: /staff/orders/**) - thay request()->routeIs(). */
    public boolean is(String... patterns) {
        String p = path();
        for (String pattern : patterns) {
            if (PATHS.match(pattern, p)) return true;
        }
        return false;
    }

    /** 'active' nếu đường dẫn hiện tại khớp. */
    public String active(String... patterns) {
        return is(patterns) ? "active" : "";
    }

    public String param(String name) {
        HttpServletRequest r = request();
        return r == null ? null : r.getParameter(name);
    }

    public String param(String name, String def) {
        String v = param(name);
        return v == null ? def : v;
    }

    /** Tham số query hiện tại trừ một số khóa (request()->except([...])) - dùng để giữ bộ lọc trong form ẩn. */
    public Map<String, String> paramsExcept(String... keys) {
        HttpServletRequest r = request();
        Map<String, String> out = new LinkedHashMap<>();
        if (r == null) return out;
        Set<String> skip = new HashSet<>(Arrays.asList(keys));
        skip.add("_csrf");
        skip.add("_token");
        r.getParameterMap().forEach((k, v) -> {
            if (!skip.contains(k) && v.length > 0) out.put(k, v[0]);
        });
        return out;
    }

    public boolean paramBool(String name) {
        String v = param(name);
        return v != null && (v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on") || v.equalsIgnoreCase("yes"));
    }

    /** Query string hiện tại, thay / bỏ một số tham số. VD: query('page', 2) -> ?q=abc&page=2; giá trị null = bỏ. */
    public String query(Object... kv) {
        return pathQuery(path(), kv);
    }

    /** Như query() nhưng cho đường dẫn khác: giữ tham số lọc hiện tại khi chuyển trang. */
    public String pathQuery(String path, Object... kv) {
        HttpServletRequest r = request();
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        Map<String, Object> override = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) override.put(String.valueOf(kv[i]), kv[i + 1]);
        if (r != null) {
            r.getParameterMap().forEach((k, v) -> {
                if (!override.containsKey(k) && !k.equals("_csrf")) b.queryParam(k, (Object[]) v);
            });
        }
        override.forEach((k, v) -> {
            if (v != null && !v.toString().isEmpty()) b.queryParam(k, v);
        });
        return b.build().encode().toUriString();
    }

    /** Đường dẫn kèm tham số: url('/products', 'category', slug, 'type', t) - tham số rỗng bị bỏ. */
    public String url(String path, Object... kv) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null && !kv[i + 1].toString().isEmpty()) b.queryParam(String.valueOf(kv[i]), kv[i + 1]);
        }
        return b.build().encode().toUriString();
    }

    /** Nhãn trạng thái vận đơn GHN (ready_to_pick, delivering...). */
    public String ghnStatus(String s) {
        return com.hieuthuoc.service.GhnService.statusLabel(s);
    }

    public int year() {
        return LocalDate.now().getYear();
    }

    public LocalDate today() {
        return LocalDate.now();
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    public List<Integer> range(int from, int to) {
        List<Integer> out = new ArrayList<>();
        for (int i = from; i <= to; i++) out.add(i);
        return out;
    }

    /** Dùng cho data-* / script: chuỗi JSON an toàn. */
    public String json(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(o);
        } catch (Exception e) {
            return "null";
        }
    }
}
