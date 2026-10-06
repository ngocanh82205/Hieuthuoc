package com.hieuthuoc.service;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.*;

/**
 * Dữ liệu form gửi lên (tương ứng $request->all() của Laravel), hỗ trợ tên trường kiểu mảng:
 * "checks[]" -> list("checks"), "qty[12]" -> map("qty").get("12").
 */
public class Form {
    private final MultiValueMap<String, String> data;

    public Form(MultiValueMap<String, String> data) {
        this.data = data == null ? new LinkedMultiValueMap<>() : data;
    }

    public static Form of(Map<String, String> m) {
        LinkedMultiValueMap<String, String> d = new LinkedMultiValueMap<>();
        m.forEach(d::add);
        return new Form(d);
    }

    public MultiValueMap<String, String> raw() {
        return data;
    }

    /** Giá trị đầu tiên (null nếu không có). */
    public String get(String key) {
        String v = data.getFirst(key);
        return v;
    }

    public String get(String key, String def) {
        String v = get(key);
        return v == null ? def : v;
    }

    /** Giá trị đã trim, chuỗi rỗng -> null. */
    public String str(String key) {
        return Texts.emptyToNull(get(key));
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    /** Ô tích / giá trị khác rỗng và khác "0". */
    public boolean bool(String key) {
        String v = get(key);
        return v != null && !v.isEmpty() && !"0".equals(v) && !"false".equalsIgnoreCase(v);
    }

    public Long longVal(String key) {
        return Texts.toLong(get(key));
    }

    public int intVal(String key, int def) {
        return Texts.toInt(get(key), def);
    }

    /** Danh sách giá trị của "name[]" (hoặc "name" lặp lại). */
    public List<String> list(String name) {
        List<String> out = new ArrayList<>();
        List<String> a = data.get(name + "[]");
        if (a != null) out.addAll(a);
        List<String> b = data.get(name);
        if (b != null) out.addAll(b);
        // name[0], name[1]...
        SortedMap<Integer, String> idx = new TreeMap<>();
        for (Map.Entry<String, List<String>> e : data.entrySet()) {
            String k = e.getKey();
            if (k.startsWith(name + "[") && k.endsWith("]")) {
                String inner = k.substring(name.length() + 1, k.length() - 1);
                if (inner.matches("\\d+")) idx.put(Integer.parseInt(inner), e.getValue().isEmpty() ? null : e.getValue().get(0));
            }
        }
        out.addAll(idx.values());
        return out;
    }

    /** "name[key]" => value. */
    public Map<String, String> map(String name) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : data.entrySet()) {
            String k = e.getKey();
            if (k.startsWith(name + "[") && k.endsWith("]") && !k.equals(name + "[]")) {
                out.put(k.substring(name.length() + 1, k.length() - 1), e.getValue().isEmpty() ? null : e.getValue().get(0));
            }
        }
        return out;
    }

    /** Các dòng dạng "name[i][field]" => danh sách map field -> giá trị, theo thứ tự i. */
    public List<Map<String, String>> rows(String name) {
        TreeMap<String, Map<String, String>> rows = new TreeMap<>(Comparator.comparing((String s) -> s.matches("\\d+") ? Long.parseLong(s) : Long.MAX_VALUE)
                .thenComparing(Comparator.naturalOrder()));
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("^" + java.util.regex.Pattern.quote(name) + "\\[([^\\]]*)]\\[([^\\]]+)]$");
        for (Map.Entry<String, List<String>> e : data.entrySet()) {
            java.util.regex.Matcher m = p.matcher(e.getKey());
            if (m.matches()) {
                rows.computeIfAbsent(m.group(1), k -> new LinkedHashMap<>()).put(m.group(2), e.getValue().isEmpty() ? null : e.getValue().get(0));
            }
        }
        return new ArrayList<>(rows.values());
    }

    /** Toàn bộ giá trị đơn (key -> giá trị đầu tiên). */
    public Map<String, String> flat() {
        Map<String, String> out = new LinkedHashMap<>();
        data.forEach((k, v) -> out.put(k, v.isEmpty() ? null : v.get(0)));
        return out;
    }
}
