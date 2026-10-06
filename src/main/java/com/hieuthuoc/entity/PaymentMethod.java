package com.hieuthuoc.entity;

import java.util.List;

public enum PaymentMethod {
    COD("Thanh toán khi nhận hàng (COD)", "COD", "bi-cash-stack"),
    BANK_TRANSFER("Chuyển khoản ngân hàng (VietQR)", "Chuyển khoản", "bi-qr-code"),
    PAYOS("Cổng thanh toán PayOS (VietQR ngân hàng)", "PayOS", "bi-qr-code-scan"),
    VNPAY("VNPay (thẻ ATM / Visa / Master / QR)", "VNPay", "bi-credit-card"),
    CASH("Tiền mặt tại quầy", "Tiền mặt", "bi-cash"),
    MOMO("Ví MoMo (Lịch sử)", "MoMo", "bi-wallet2"); // Tương thích dữ liệu lịch sử

    /** Phương thức khách chọn được khi đặt online. */
    public static final List<PaymentMethod> ONLINE_METHODS = List.of(COD, BANK_TRANSFER, PAYOS, VNPAY);

    private final String label;
    private final String shortLabel;
    private final String icon;

    PaymentMethod(String label, String shortLabel, String icon) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public String getIcon() {
        return icon;
    }

    /** Phải nhận được tiền trước khi nhà thuốc xác nhận đơn. */
    public boolean isPrepaid() {
        return this != COD;
    }

    public boolean isGateway() {
        return this == PAYOS || this == VNPAY;
    }

    public static PaymentMethod tryFrom(String s) {
        if (s == null) return null;
        for (PaymentMethod m : values()) if (m.name().equals(s)) return m;
        return null;
    }
}
