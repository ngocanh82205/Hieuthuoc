package com.hieuthuoc.service;

import java.util.List;

/** Nhiều lỗi kiểm tra form cùng lúc (tương ứng ValidationException của Laravel): hiện danh sách lỗi và giữ dữ liệu đã nhập. */
public class ValidationException extends BusinessException {
    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super(String.join(" ", errors));
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
