package com.hieuthuoc.service;

/** Đơn thuốc (cùng bệnh nhân, bác sĩ, ngày kê) đã được bán trước đó: form hiện ô để dược sĩ xác nhận còn được bán tiếp. */
public class RxReuseException extends BusinessException {
    public RxReuseException(String message) {
        super(message);
    }
}
