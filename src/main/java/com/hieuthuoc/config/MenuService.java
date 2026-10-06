package com.hieuthuoc.config;

import com.hieuthuoc.entity.StaffPermission;
import com.hieuthuoc.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Các nhóm chức năng trên menu bên trái + thanh tab đầu trang (menu_groups() của bản Laravel). Chỉ trả về mục người dùng được phép.
 * Mỗi mục có danh sách mẫu đường dẫn (Ant) để biết mục nào đang mở.
 */
@Component("menu")
@RequiredArgsConstructor
public class MenuService {
    private final ViewHelper h;

    public record Item(String url, List<String> active, String icon, String label) {
        public String getUrl() {
            return url;
        }

        public String getIcon() {
            return icon;
        }

        public String getLabel() {
            return label;
        }
    }

    public record Group(String label, String icon, List<Item> items) {
        public String getLabel() {
            return label;
        }

        public String getIcon() {
            return icon;
        }

        public List<Item> getItems() {
            return items;
        }
    }

    private static Item item(String url, String icon, String label, String... active) {
        return new Item(url, List.of(active.length == 0 ? new String[]{url, url + "/**"} : active), icon, label);
    }

    public Map<String, Group> groups(User u) {
        Map<String, Group> groups = new LinkedHashMap<>();
        if (u == null) return groups;
        if (u.hasPermission(StaffPermission.CONSULT)) {
            groups.put("care", new Group("Chăm sóc KH", "bi-headset", List.of(item("/staff/consultations", "bi-chat-dots", "Tư vấn khách hàng"))));
        }
        List<Item> stock = new ArrayList<>();
        if (u.hasPermission(StaffPermission.INVENTORY)) {
            stock.add(item("/staff/inventory", "bi-box-seam", "Tồn kho theo lô", "/staff/inventory", "/staff/inventory/{id:\\d+}", "/staff/batches/**"));
            stock.add(item("/staff/inventory/alerts", "bi-exclamation-triangle", "Cảnh báo kho", "/staff/inventory/alerts"));
            stock.add(item("/staff/receipts", "bi-truck", "Phiếu nhập kho"));
            stock.add(item("/staff/adjustments", "bi-trash3", "Phiếu hủy / điều chỉnh"));
            stock.add(item("/staff/stocktake", "bi-clipboard-check", "Kiểm kê"));
        }
        if (u.isAdmin()) {
            stock.add(item("/admin/stock-approvals", "bi-check2-square", "Duyệt phiếu kho"));
            stock.add(item("/admin/suppliers", "bi-building", "Nhà cung cấp", "/admin/suppliers", "/admin/suppliers-save/**"));
        }
        if (!stock.isEmpty()) groups.put("stock", new Group("Quản lý kho", "bi-boxes", stock));
        if (u.hasPermission(StaffPermission.CONTENT)) {
            groups.put("content", new Group("Nội dung", "bi-journal-richtext", List.of(
                    item("/staff/reviews", "bi-star", "Đánh giá"),
                    item("/staff/products", "bi-capsule", "Thông tin sản phẩm"),
                    item("/staff/posts", "bi-newspaper", "Bài viết sức khỏe"),
                    item("/staff/faqs", "bi-patch-question", "Câu hỏi thường gặp"))));
        }
        if (u.isAdmin()) {
            groups.put("reports", new Group("Báo cáo & tài chính", "bi-bar-chart", List.of(
                    item("/admin/reports", "bi-bar-chart-line", "Báo cáo thống kê"),
                    item("/admin/finance", "bi-bank", "Tài chính & giao dịch"),
                    item("/admin/refunds", "bi-cash-coin", "Duyệt hoàn tiền"))));
            groups.put("catalog", new Group("Sản phẩm", "bi-capsule", List.of(
                    item("/admin/products", "bi-capsule", "Sản phẩm"),
                    item("/admin/categories", "bi-diagram-3", "Danh mục"),
                    item("/admin/interactions", "bi-shield-exclamation", "Tương tác thuốc"))));
            groups.put("users", new Group("Người dùng", "bi-person-gear", List.of(
                    item("/admin/users", "bi-person-badge", "Nhân viên"),
                    item("/admin/customers", "bi-people", "Khách hàng"),
                    item("/admin/password-resets", "bi-key", "Quên mật khẩu"),
                    item("/admin/logs", "bi-clock-history", "Nhật ký hoạt động"))));
            groups.put("marketing", new Group("Marketing", "bi-megaphone", List.of(
                    item("/admin/vouchers", "bi-ticket-perforated", "Mã giảm giá"),
                    item("/admin/promotions", "bi-lightning-charge", "Flash sale / Combo / Quà"),
                    item("/admin/loyalty", "bi-gem", "Tích điểm & hạng"))));
            groups.put("system", new Group("Hệ thống", "bi-gear", List.of(item("/admin/settings", "bi-gear", "Cấu hình"))));
        }
        return groups;
    }

    public boolean isActive(Item i) {
        return h.is(i.active().toArray(String[]::new));
    }

    /** Nhóm đang mở (có mục khớp đường dẫn hiện tại). */
    public boolean isOpen(Group g) {
        return g.items().stream().anyMatch(this::isActive);
    }

    /** Thanh tab đầu trang: nhóm chứa trang hiện tại (chỉ khi nhóm có nhiều hơn 1 mục). */
    public Group currentGroup(User u) {
        for (Group g : groups(u).values()) {
            if (g.items().size() > 1 && isOpen(g)) return g;
        }
        return null;
    }

    public Item currentItem(Group g) {
        return g == null ? null : g.items().stream().filter(this::isActive).findFirst().orElse(null);
    }
}
