package com.hieuthuoc.entity;

import java.util.List;

/** Quyền theo chức năng. Admin luôn có tất cả quyền; dược sĩ có bộ quyền cố định. */
public enum StaffPermission {
    RX_REVIEW("Duyệt đơn thuốc", "Duyệt đơn thuốc, sổ thuốc kê đơn", "bi-file-earmark-medical"),
    ORDER("Đơn hàng", "Xử lý đơn hàng online", "bi-receipt"),
    CONSULT("Tư vấn / CSKH", "Tư vấn / CSKH: chat với khách hàng", "bi-chat-dots"),
    INVENTORY("Kho", "Kho: tồn kho, lô, phiếu nhập, kiểm kê", "bi-box-seam"),
    APPROVE_STOCK("Duyệt phiếu kho", "Duyệt phiếu kho: nhập, hủy, điều chỉnh kiểm kê", "bi-check2-square"),
    POS("Bán tại quầy", "Bán hàng tại quầy (POS)", "bi-shop-window"),
    REFUND("Hoàn tiền", "Hủy đơn đã thanh toán, đổi trả & hoàn tiền", "bi-cash-coin"),
    CONTENT("Nội dung", "Nội dung: bài viết, thông tin sản phẩm, đánh giá, FAQ", "bi-newspaper");

    /** Quyền cố định của dược sĩ; duyệt phiếu kho và hoàn tiền chỉ dành cho quản trị viên. */
    public static final List<StaffPermission> FOR_PHARMACIST = List.of(RX_REVIEW, ORDER, CONSULT, INVENTORY, POS, CONTENT);

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
