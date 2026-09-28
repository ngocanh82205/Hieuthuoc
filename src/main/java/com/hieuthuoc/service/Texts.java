package com.hieuthuoc.service;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class Texts {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private Texts() {
    }

    public static String slugify(String input) {
        if (input == null) return "";
        String s = Normalizer.normalize(input.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    /** Sinh mã chứng từ: tiền tố + yyMMdd + 4 ký tự ngẫu nhiên. VD: DH250928K7QF */
    public static String code(String prefix) {
        StringBuilder sb = new StringBuilder(prefix).append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")));
        for (int i = 0; i < 4; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    public static String trim(String s, int max) {
        String t = trim(s);
        return t.length() > max ? t.substring(0, max) : t;
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static boolean isPhone(String s) {
        return s != null && s.matches("0\\d{9,10}");
    }

    public static boolean isEmail(String s) {
        return s != null && s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }

    public static String emptyToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
