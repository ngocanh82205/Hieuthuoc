package com.hieuthuoc.web;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Thông báo hiển thị một lần sau khi redirect. */
public final class Flash {
    private Flash() {
    }

    public static void success(RedirectAttributes ra, String msg) {
        put(ra, "success", msg);
    }

    public static void info(RedirectAttributes ra, String msg) {
        put(ra, "info", msg);
    }

    public static void warning(RedirectAttributes ra, String msg) {
        put(ra, "warning", msg);
    }

    public static void error(RedirectAttributes ra, String msg) {
        put(ra, "danger", msg);
    }

    private static void put(RedirectAttributes ra, String type, String msg) {
        ra.addFlashAttribute("flashType", type);
        ra.addFlashAttribute("flashMsg", msg);
    }
}
