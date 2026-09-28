package com.hieuthuoc.entity;

public enum PaymentMethod {
    COD("Thanh toán khi nhận hàng (COD)"),
    ONLINE("Thanh toán online (VNPay / MoMo - demo)");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
