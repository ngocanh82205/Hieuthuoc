package com.hieuthuoc.entity;

public enum PaymentMethod {
    COD("Thanh toán khi nhận hàng (COD)"),
    BANK_TRANSFER("Chuyển khoản ngân hàng (VietQR)"),
    ONLINE("Thanh toán online (VNPay / MoMo - demo)"),
    CASH("Tiền mặt tại quầy");

    /** Phương thức khách được chọn khi đặt online. */
    public static final java.util.List<PaymentMethod> ONLINE_METHODS = java.util.List.of(COD, BANK_TRANSFER, ONLINE);

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    /** Phải nhận được tiền trước khi nhà thuốc xác nhận đơn. */
    public boolean isPrepaid() {
        return this != COD;
    }

    public String getLabel() {
        return label;
    }
}
