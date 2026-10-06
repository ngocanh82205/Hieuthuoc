package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {
    /** Nhóm thao tác để lọc: tên hiển thị => tiền tố mã thao tác. */
    public static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    /** Mã thao tác => mô tả cho người dùng (gồm cả mã của chức năng đã gỡ còn trong dữ liệu cũ). */
    public static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        GROUPS.put("Đăng nhập & tài khoản", List.of("auth"));
        GROUPS.put("Đơn hàng & bán hàng", List.of("order", "pos", "refund", "payment"));
        GROUPS.put("Đơn thuốc", List.of("rx"));
        GROUPS.put("Kho hàng", List.of("receipt", "batch", "inventory", "stock", "supplier"));
        GROUPS.put("Sản phẩm & danh mục", List.of("product", "category"));
        GROUPS.put("Nội dung", List.of("post", "faq", "page", "review"));
        GROUPS.put("Khuyến mãi & khách hàng", List.of("voucher", "promotion", "customer"));
        GROUPS.put("Tư vấn", List.of("chat"));
        GROUPS.put("Nhân viên", List.of("user", "staff"));
        GROUPS.put("Hệ thống", List.of("settings", "backup", "system"));

        String[][] labels = {
                {"auth.login", "Đăng nhập"}, {"auth.login_google", "Đăng nhập bằng Google"}, {"auth.register", "Đăng ký tài khoản"},
                {"auth.register_google", "Đăng ký bằng Google"}, {"auth.change_password", "Đổi mật khẩu"},
                {"order.create", "Tạo đơn hàng"}, {"order.status", "Đổi trạng thái đơn"}, {"order.cancel", "Hủy đơn hàng"},
                {"order.paid", "Xác nhận đã thanh toán"}, {"order.return", "Xử lý đổi / trả hàng"}, {"order.einvoice", "Ghi số hóa đơn điện tử"},
                {"order.assign", "Phân công xử lý đơn"}, {"order.verify_call", "Gọi xác minh đơn"},
                {"pos.sale", "Bán tại quầy"}, {"pos.cancel_request", "Yêu cầu hủy hóa đơn quầy"}, {"pos.cancel_approve", "Duyệt hủy hóa đơn quầy"},
                {"pos.cancel_reject", "Từ chối hủy hóa đơn quầy"}, {"refund.approve", "Duyệt hoàn tiền"},
                {"rx.submit", "Khách gửi đơn thuốc"}, {"rx.approve", "Duyệt đơn thuốc"}, {"rx.reject", "Từ chối đơn thuốc"}, {"rx.quote", "Báo giá theo đơn thuốc"},
                {"receipt.create", "Lập phiếu nhập"}, {"receipt.approve", "Duyệt phiếu nhập"}, {"receipt.reject", "Từ chối phiếu nhập"},
                {"batch.adjust", "Điều chỉnh / hủy hàng của lô"}, {"batch.lock", "Khóa lô"}, {"batch.unlock", "Mở khóa lô"},
                {"batch.recall_notify", "Thông báo thu hồi lô"}, {"inventory.stocktake", "Kiểm kê kho"},
                {"stock.approve", "Duyệt phiếu điều chỉnh kho"}, {"stock.reject", "Từ chối phiếu điều chỉnh kho"}, {"stock.min_stock", "Sửa định mức tồn"},
                {"supplier.save", "Lưu nhà cung cấp"},
                {"product.create", "Thêm sản phẩm"}, {"product.update", "Sửa sản phẩm"}, {"product.delete", "Xóa sản phẩm"},
                {"product.toggle", "Mở bán / ngừng bán sản phẩm"}, {"product.info", "Sửa thông tin chuyên môn sản phẩm"},
                {"product.import", "Nhập sản phẩm từ Excel"}, {"product.export", "Xuất danh sách sản phẩm"},
                {"product.ingredient", "Lưu hoạt chất"}, {"product.ingredient_delete", "Xóa hoạt chất"},
                {"product.manufacturer", "Lưu nhà sản xuất"}, {"product.manufacturer_delete", "Xóa nhà sản xuất"},
                {"product.interaction", "Lưu tương tác thuốc"}, {"product.interaction_delete", "Xóa tương tác thuốc"},
                {"category.save", "Lưu danh mục"}, {"category.delete", "Xóa danh mục"},
                {"post.save", "Lưu bài viết"}, {"post.delete", "Xóa bài viết"}, {"faq.save", "Lưu câu hỏi thường gặp"}, {"faq.delete", "Xóa câu hỏi thường gặp"},
                {"page.save", "Lưu trang tĩnh"}, {"review.hide", "Ẩn đánh giá"}, {"review.show", "Hiện lại đánh giá"},
                {"voucher.save", "Lưu mã giảm giá"}, {"voucher.email", "Gửi mã giảm giá qua email"}, {"promotion.save", "Lưu chương trình khuyến mãi"},
                {"promotion.toggle", "Bật / tắt khuyến mãi"}, {"customer.points", "Điều chỉnh điểm khách hàng"},
                {"chat.claim", "Nhận cuộc tư vấn"}, {"chat.transfer", "Chuyển cuộc tư vấn"},
                {"user.create", "Thêm nhân viên"}, {"user.update", "Sửa nhân viên"}, {"user.lock", "Khóa tài khoản"}, {"user.delete", "Xóa nhân viên"}, {"user.unlock", "Mở khóa tài khoản"},
                {"user.reset_password", "Cấp lại mật khẩu"}, {"staff.notifications", "Cài đặt thông báo nhân viên"},
                {"role.save", "Lưu vai trò"}, {"role.delete", "Xóa vai trò"},
                {"settings.save", "Sửa cấu hình"}, {"settings.loyalty", "Sửa cấu hình tích điểm"}, {"settings.zone", "Lưu khu vực giao hàng"},
                {"settings.zone_delete", "Xóa khu vực giao hàng"}, {"backup.create", "Tạo bản sao lưu"}, {"backup.download", "Tải bản sao lưu"},
                {"system.seed", "Khởi tạo dữ liệu"}, {"broadcast.send", "Gửi thông báo hàng loạt"},
                {"callback.done", "Xử lý yêu cầu gọi lại"}, {"hr.payroll_calc", "Tính lương"}, {"hr.payroll_submit", "Gửi duyệt bảng lương"},
                {"hr.payroll_approve", "Duyệt bảng lương"}, {"hr.payroll_paid", "Chi trả lương"},
        };
        for (String[] l : labels) LABELS.put(l[0], l[1]);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 1000)
    private String detail;

    @Column(length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now().withNano(0);
    }

    public String actionLabel() {
        String l = LABELS.get(action);
        if (l != null) return l;
        String s = action.replace('.', ' ').replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public String groupLabel() {
        String prefix = action.contains(".") ? action.substring(0, action.indexOf('.')) : action;
        for (Map.Entry<String, List<String>> e : GROUPS.entrySet()) if (e.getValue().contains(prefix)) return e.getKey();
        return null;
    }

    /** IP dễ đọc: truy cập ngay trên máy chủ (::1 / 127.0.0.1) ghi là "Máy chủ". */
    public String ipLabel() {
        return "::1".equals(ip) || "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) ? "Máy chủ" : (ip == null ? "" : ip);
    }
}
