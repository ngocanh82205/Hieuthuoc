package com.hieuthuoc.entity;

/** Quyền theo chức năng. Vai trò nhân viên (StaffRole) là một tập quyền; admin luôn có tất cả quyền. */
public enum StaffPermission {
    RX_REVIEW("Duyệt đơn thuốc", "Duyệt đơn thuốc, sổ thuốc kê đơn", "bi-file-earmark-medical"),
    ORDER("Đơn hàng", "Xử lý đơn hàng online", "bi-receipt"),
    CONSULT("Tư vấn / CSKH", "Tư vấn / CSKH: chat, hỏi đáp, gọi lại", "bi-chat-dots"),
    INVENTORY("Kho", "Kho: tồn kho, lô, phiếu nhập, kiểm kê, chuyển kho", "bi-box-seam"),
    APPROVE_STOCK("Duyệt phiếu kho", "Duyệt phiếu kho: nhập, hủy, điều chỉnh kiểm kê", "bi-check2-square"),
    POS("Bán tại quầy", "Bán hàng tại quầy (POS)", "bi-shop-window"),
    REFUND("Hoàn tiền", "Hủy đơn đã thanh toán, đổi trả & hoàn tiền", "bi-cash-coin"),
    CONTENT("Nội dung", "Nội dung: bài viết, thông tin sản phẩm, đánh giá", "bi-newspaper");

    private final String shortLabel;
    private final String label;
    private final String icon;

    StaffPermission(String shortLabel, String label, String icon) {
        this.shortLabel = shortLabel;
        this.label = label;
        this.icon = icon;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }
}
