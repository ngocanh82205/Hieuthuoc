package com.hieuthuoc.service;

import org.springframework.web.util.HtmlUtils;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Hàm tiện ích chuỗi tương ứng helpers.php / Str của Laravel. */
public final class Texts {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private Texts() {
    }

    /** vn_slug(): bỏ dấu, chữ thường, nối bằng "-". */
    public static String slugify(String input) {
        if (input == null) return "";
        return ascii(input).toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    /** Bỏ dấu tiếng Việt (giữ hoa / thường). */
    public static String ascii(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** vn_ascii(): bỏ dấu tiếng Việt, chữ thường (tìm kiếm / hiểu tiếng Việt không dấu). */
    public static String vnAscii(String s) {
        return ascii(s).toLowerCase();
    }

    /** doc_code(): tiền tố + yyMMdd + 4 ký tự ngẫu nhiên. VD: DH260928K7QF */
    public static String code(String prefix) {
        StringBuilder sb = new StringBuilder(prefix).append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")));
        for (int i = 0; i < 4; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** Str::limit(trim($s), $max, ''): cắt đúng $max ký tự. */
    public static String trim(String s, int max) {
        return limit(trim(s), max, "");
    }

    /** Str::limit($s, $max): quá dài thì cắt và thêm "...". */
    public static String limit(String s, int max) {
        return limit(s, max, "...");
    }

    public static String limit(String s, int max, String end) {
        if (s == null) return null;
        if (s.codePointCount(0, s.length()) <= max) return s;
        int endIdx = s.offsetByCodePoints(0, max);
        return s.substring(0, endIdx).stripTrailing() + end;
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** is_phone(): 0 + 9-10 chữ số. */
    public static boolean isPhone(String s) {
        return s != null && s.trim().matches("0\\d{9,10}");
    }

    public static boolean isEmail(String s) {
        return s != null && s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }

    public static String emptyToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    public static int mbLen(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    /** split_ingredients(): tách hoạt chất theo , ; + */
    public static List<String> splitIngredients(String s) {
        if (isBlank(s)) return List.of();
        return Arrays.stream(s.split("[,;+]")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    public static List<String> lines(String s) {
        if (s == null) return List.of();
        return Arrays.stream(s.split("\\r?\\n")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    public static String e(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }

    /** simple_md(): hiển thị nội dung dạng markdown đơn giản (## tiêu đề, - danh sách, đoạn văn). Luôn escape (chống XSS). */
    public static String simpleMd(String text) {
        StringBuilder html = new StringBuilder();
        List<String> list = new ArrayList<>();
        List<String> para = new ArrayList<>();
        for (String line : (text == null ? "" : text).split("\\r?\\n", -1)) {
            String t = line.trim();
            if (t.isEmpty()) {
                flushList(list, html);
                flushPara(para, html);
            } else if (t.startsWith("## ") || t.startsWith("### ")) {
                flushList(list, html);
                flushPara(para, html);
                html.append("<h2>").append(e(t.replaceAll("^[#\\s]+", ""))).append("</h2>");
            } else if (t.startsWith("- ") || t.startsWith("* ")) {
                flushPara(para, html);
                list.add(t.substring(2));
            } else {
                flushList(list, html);
                para.add(t);
            }
        }
        flushList(list, html);
        flushPara(para, html);
        return html.toString();
    }

    private static void flushList(List<String> list, StringBuilder html) {
        if (list.isEmpty()) return;
        html.append("<ul>");
        for (String l : list) html.append("<li>").append(e(l)).append("</li>");
        html.append("</ul>");
        list.clear();
    }

    private static void flushPara(List<String> para, StringBuilder html) {
        if (para.isEmpty()) return;
        html.append("<p>").append(String.join("<br>", para.stream().map(Texts::e).toList())).append("</p>");
        para.clear();
    }

    public static Long toLong(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int toInt(String s, int def) {
        try {
            return s == null || s.isBlank() ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static String ucfirst(String s) {
        return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
