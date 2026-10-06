package com.hieuthuoc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Một trang dữ liệu (tương ứng LengthAwarePaginator của Laravel). */
public class Page<T> {
    private final List<T> items;
    private final long total;
    private final int perPage;
    private final int page;

    public Page(List<T> items, long total, int perPage, int page) {
        this.items = items;
        this.total = total;
        this.perPage = perPage;
        this.page = page;
    }

    /** Cắt danh sách đầy đủ thành trang (trang vượt quá thì về trang cuối). */
    public static <T> Page<T> of(List<T> all, int perPage, int page) {
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        int p = Math.min(Math.max(1, page), pages);
        int from = (p - 1) * perPage;
        return new Page<>(new ArrayList<>(all.subList(Math.min(from, all.size()), Math.min(from + perPage, all.size()))), all.size(), perPage, p);
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getPerPage() {
        return perPage;
    }

    public int getPage() {
        return page;
    }

    public int getLastPage() {
        return Math.max(1, (int) Math.ceil(total / (double) perPage));
    }

    public boolean hasPages() {
        return getLastPage() > 1;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int getFirstItem() {
        return total == 0 ? 0 : (page - 1) * perPage + 1;
    }

    public int getLastItem() {
        return (int) Math.min(total, (long) page * perPage);
    }

    /** Các số trang hiển thị (rút gọn quanh trang hiện tại, -1 = dấu "..."). */
    public List<Integer> getPageNumbers() {
        List<Integer> out = new ArrayList<>();
        int last = getLastPage();
        for (int i = 1; i <= last; i++) {
            if (i == 1 || i == last || Math.abs(i - page) <= 2) out.add(i);
            else if (out.isEmpty() || out.get(out.size() - 1) != -1) out.add(-1);
        }
        return out;
    }

    /** URL trang n giữ nguyên các tham số lọc khác. */
    public static String url(String path, Map<String, String[]> params, int n) {
        StringBuilder sb = new StringBuilder(path).append('?');
        params.forEach((k, vs) -> {
            if (k.equals("page")) return;
            for (String v : vs) {
                if (v == null || v.isEmpty()) continue;
                sb.append(java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8)).append('=')
                        .append(java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8)).append('&');
            }
        });
        return sb.append("page=").append(n).toString();
    }
}
