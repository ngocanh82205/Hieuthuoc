package com.hieuthuoc.config;

import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Hàm định dạng dùng trong template: ${@fmt.money(x)} */
@Component("fmt")
public class Fmt {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    public String money(Number n) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(n == null ? 0 : n.longValue()) + " ₫";
    }

    public String money(String s) {
        try {
            return money(Long.parseLong(s.trim()));
        } catch (RuntimeException e) {
            return s;
        }
    }

    public String number(Number n) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(n == null ? 0 : n);
    }

    public String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    public String date(LocalDateTime d) {
        return d == null ? "" : d.format(DATE);
    }

    public String dateTime(LocalDateTime d) {
        return d == null ? "" : d.format(DATE_TIME);
    }

    public String iso(LocalDate d) {
        return d == null ? "" : d.format(ISO);
    }

    /** Giá trị cho input datetime-local. */
    public String isoTime(LocalDateTime d) {
        return d == null ? "" : d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    public long daysUntil(LocalDate d) {
        return d == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), d);
    }

    public String stars(Number rating) {
        int r = rating == null ? 0 : (int) Math.round(rating.doubleValue());
        return "★".repeat(Math.max(0, Math.min(5, r))) + "☆".repeat(5 - Math.max(0, Math.min(5, r)));
    }
}
