package com.hieuthuoc.web;

import com.hieuthuoc.service.Form;
import com.hieuthuoc.service.Texts;
import com.hieuthuoc.service.ValidationException;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

/**
 * Kiểm tra dữ liệu form với thông báo tiếng Việt giống lang/vi/validation.php của bản Laravel.
 * VD: Validator.of(f).label("full_name", "Họ tên").required("full_name").max("full_name", 100).check();
 * Mỗi trường chỉ báo lỗi đầu tiên.
 */
public class Validator {
    private static final Map<String, String> ATTRIBUTES = Map.ofEntries(
            Map.entry("full_name", "Họ tên"), Map.entry("name", "Tên"), Map.entry("email", "Email"), Map.entry("phone", "Số điện thoại"),
            Map.entry("password", "Mật khẩu"), Map.entry("address", "Địa chỉ"), Map.entry("address_line", "Địa chỉ"), Map.entry("recipient", "Người nhận"),
            Map.entry("province", "Tỉnh/thành"), Map.entry("quantity", "Số lượng"), Map.entry("price", "Giá"), Map.entry("image", "Ảnh"),
            Map.entry("title", "Tiêu đề"), Map.entry("content", "Nội dung"), Map.entry("code", "Mã"), Map.entry("rating", "Số sao"),
            Map.entry("comment", "Nhận xét"), Map.entry("subject", "Tiêu đề"), Map.entry("body", "Nội dung"), Map.entry("question", "Câu hỏi"),
            Map.entry("answer", "Câu trả lời"), Map.entry("slug", "Đường dẫn"), Map.entry("reason", "Lý do"), Map.entry("note", "Ghi chú"));

    private final Form f;
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();

    private Validator(Form f) {
        this.f = f;
    }

    public static Validator of(Form f) {
        return new Validator(f);
    }

    public Validator label(String field, String label) {
        labels.put(field, label);
        return this;
    }

    private String attr(String field) {
        return labels.getOrDefault(field, ATTRIBUTES.getOrDefault(field, field.replace('_', ' ')));
    }

    private String val(String field) {
        return Texts.trim(f.get(field));
    }

    private boolean present(String field) {
        return !val(field).isEmpty();
    }

    /** Thêm lỗi tùy biến (giữ lỗi đầu tiên của trường). */
    public Validator fail(String field, String message) {
        errors.putIfAbsent(field, message);
        return this;
    }

    public Validator rule(String field, boolean ok, String message) {
        if (!ok) fail(field, message);
        return this;
    }

    public Validator required(String... fields) {
        for (String field : fields) if (!present(field)) fail(field, "Vui lòng nhập " + attr(field) + ".");
        return this;
    }

    public Validator required(String field, String message) {
        if (!present(field)) fail(field, message);
        return this;
    }

    public Validator min(String field, int min) {
        if (present(field) && Texts.mbLen(val(field)) < min) fail(field, attr(field) + " phải có ít nhất " + min + " ký tự.");
        return this;
    }

    public Validator max(String field, int max) {
        if (present(field) && Texts.mbLen(val(field)) > max) fail(field, attr(field) + " không được dài quá " + max + " ký tự.");
        return this;
    }

    public Validator email(String field) {
        if (present(field) && !Texts.isEmail(val(field))) fail(field, attr(field) + " không hợp lệ.");
        return this;
    }

    public Validator phone(String field, String message) {
        if (present(field) && !Texts.isPhone(val(field))) fail(field, message);
        return this;
    }

    public Validator integer(String field) {
        if (present(field) && !val(field).matches("-?\\d+")) fail(field, attr(field) + " phải là số nguyên.");
        return this;
    }

    public Validator numeric(String field) {
        if (present(field) && !val(field).matches("-?\\d+(\\.\\d+)?")) fail(field, attr(field) + " phải là số.");
        return this;
    }

    public Validator minValue(String field, long min) {
        if (present(field) && val(field).matches("-?\\d+(\\.\\d+)?") && Double.parseDouble(val(field)) < min)
            fail(field, attr(field) + " phải tối thiểu " + min + ".");
        return this;
    }

    public Validator maxValue(String field, long max) {
        if (present(field) && val(field).matches("-?\\d+(\\.\\d+)?") && Double.parseDouble(val(field)) > max)
            fail(field, attr(field) + " không được lớn hơn " + max + ".");
        return this;
    }

    public Validator between(String field, long min, long max) {
        integer(field);
        if (present(field) && val(field).matches("-?\\d+")) {
            long v = Long.parseLong(val(field));
            if (v < min || v > max) fail(field, attr(field) + " phải trong khoảng " + min + " - " + max + ".");
        }
        return this;
    }

    public Validator in(String field, Collection<String> allowed) {
        if (present(field) && !allowed.contains(val(field))) fail(field, attr(field) + " không hợp lệ.");
        return this;
    }

    public Validator date(String field) {
        if (present(field)) {
            try {
                LocalDate.parse(val(field));
            } catch (RuntimeException e) {
                fail(field, attr(field) + " không phải ngày hợp lệ.");
            }
        }
        return this;
    }

    public Validator confirmed(String field) {
        if (present(field) && !Objects.equals(f.get(field), f.get(field + "_confirmation"))) fail(field, attr(field) + " nhập lại không khớp.");
        return this;
    }

    public Validator unique(String field, Predicate<String> exists) {
        if (present(field) && exists.test(val(field))) fail(field, attr(field) + " đã được sử dụng.");
        return this;
    }

    public Validator unique(String field, Predicate<String> exists, String message) {
        if (present(field) && exists.test(val(field))) fail(field, message);
        return this;
    }

    public boolean passes() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return new ArrayList<>(errors.values());
    }

    /** Ném ValidationException (quay lại form kèm lỗi + dữ liệu đã nhập) nếu có lỗi. */
    public void check() {
        if (!errors.isEmpty()) throw new ValidationException(errors());
    }
}
