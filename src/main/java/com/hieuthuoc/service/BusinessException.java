package com.hieuthuoc.service;

/** Lỗi nghiệp vụ - thông điệp được hiển thị trực tiếp cho người dùng. */
public class BusinessException extends RuntimeException {
    private final int status;

    public BusinessException(String message) {
        this(message, 400);
    }

    public BusinessException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(message, 404);
    }
}
