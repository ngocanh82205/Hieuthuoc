package com.hieuthuoc.entity;

/** Quyền nghiệp vụ admin cấp cho từng dược sĩ / nhân viên. Admin luôn có tất cả quyền. */
public enum StaffPermission {
    REFUND("Hủy đơn đã thanh toán, đổi trả & hoàn tiền"),
    APPROVE_RECEIPT("Duyệt phiếu nhập kho (quản lý)"),
    INVENTORY_ADJUST("Điều chỉnh tăng tồn kho / áp dụng kiểm kê"),
    POS("Bán hàng tại quầy (POS)"),
    CONTENT("Nội dung: bài viết, thông tin sản phẩm, kiểm duyệt đánh giá");

    private final String label;

    StaffPermission(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
