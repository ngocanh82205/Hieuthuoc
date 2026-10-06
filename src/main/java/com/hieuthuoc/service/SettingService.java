package com.hieuthuoc.service;

import com.hieuthuoc.entity.MemberTier;
import com.hieuthuoc.entity.PaymentMethod;
import com.hieuthuoc.entity.Setting;
import com.hieuthuoc.entity.ShippingMethod;
import com.hieuthuoc.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Cấu hình hệ thống lưu trong bảng settings, có giá trị mặc định (dùng chung với bản Laravel). */
@Service
@RequiredArgsConstructor
public class SettingService {
    /** Danh sách tỉnh/thành để chọn địa chỉ giao hàng khi chưa dùng GHN. */
    public static final List<String> PROVINCES = List.of(
            "Hà Nội", "TP. Hồ Chí Minh", "Hải Phòng", "Đà Nẵng", "Cần Thơ", "Huế",
            "Tuyên Quang", "Cao Bằng", "Lai Châu", "Lào Cai", "Thái Nguyên", "Điện Biên", "Lạng Sơn", "Sơn La", "Phú Thọ",
            "Bắc Ninh", "Quảng Ninh", "Hưng Yên", "Ninh Bình", "Thanh Hóa", "Nghệ An", "Hà Tĩnh", "Quảng Trị", "Quảng Ngãi",
            "Gia Lai", "Khánh Hòa", "Lâm Đồng", "Đắk Lắk", "Đồng Nai", "Tây Ninh", "Vĩnh Long", "Đồng Tháp", "Cà Mau", "An Giang");

    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    public static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        def("store_name", "VinaPharma", "Tên nhà thuốc");
        def("store_address", "123 Nguyễn Trãi, Thanh Xuân, Hà Nội", "Địa chỉ");
        def("store_phone", "1900 1234", "Hotline");
        def("store_email", "hotro@vinapharma.vn", "Email");
        def("business_license", "Giấy CN đủ ĐKKD dược số 01-1234/ĐKKDD-HNO", "Giấy chứng nhận đủ điều kiện kinh doanh dược");
        def("gpp_cert", "GPP số 1234/GPP", "Chứng nhận GPP");
        def("pharmacist_in_charge", "DS. Nguyễn Thị Lan - CCHN 012345/HNO-CCHND", "Dược sĩ phụ trách chuyên môn");
        def("shipping_fee", "20000", "Phí giao hàng mặc định (đ)");
        def("free_ship_threshold", "300000", "Miễn phí giao hàng cho đơn từ (đ)");
        def("near_expiry_days", "90", "Cảnh báo cận hạn trước (ngày)");
        def("online_min_shelf_days", "30", "Bán online chỉ xuất lô còn hạn dùng tối thiểu (ngày) - lô cận hạn hơn chỉ bán tại quầy");
        def("return_days", "7", "Thời hạn đổi/trả (ngày)");
        def("return_rx_allowed", "0", "Cho phép đổi/trả đơn có thuốc kê đơn");
        def("return_policy", "Đổi/trả trong thời hạn quy định khi sản phẩm lỗi, sai sản phẩm, hư hỏng do vận chuyển hoặc còn nguyên tem niêm phong. "
                + "Không đổi/trả thuốc kê đơn, thuốc đã mở niêm phong, sản phẩm bảo quản lạnh (trừ lỗi từ nhà thuốc). Tiền được hoàn trong 3-7 ngày làm việc.",
                "Chính sách đổi trả (hiển thị cho khách)");
        def("points_per_amount", "10000", "Số tiền cho 1 điểm tích lũy (đ)");
        def("point_value", "1000", "Giá trị quy đổi 1 điểm khi thanh toán (đ)");
        def("rx_valid_days", "5", "Hiệu lực đơn thuốc (ngày kể từ ngày kê)");
        def("rx_rejected_cancel_days", "3", "Tự hủy đơn bị từ chối đơn thuốc nếu khách không tải lại sau (ngày)");
        def("unpaid_cancel_hours", "24", "Tự hủy đơn PayOS / VNPay chưa thanh toán sau (giờ, 0 = không tự hủy)");
        def("quote_cancel_days", "3", "Tự hủy báo giá / đơn chờ khách xác nhận sau (ngày, 0 = không tự hủy)");
        def("pos_cancel_days", "7", "Thời hạn lập phiếu hủy hóa đơn bán tại quầy (ngày kể từ ngày bán, 0 = không giới hạn)");
        def("tier_dong_min", "0", "Hạng Đồng: tổng chi tiêu tối thiểu (đ)");
        def("tier_dong_rate", "100", "Hạng Đồng: hệ số điểm (%)");
        def("tier_bac_min", "2000000", "Hạng Bạc: tổng chi tiêu tối thiểu (đ)");
        def("tier_bac_rate", "120", "Hạng Bạc: hệ số điểm (%)");
        def("tier_vang_min", "5000000", "Hạng Vàng: tổng chi tiêu tối thiểu (đ)");
        def("tier_vang_rate", "150", "Hạng Vàng: hệ số điểm (%)");
        def("tier_kim_cuong_min", "10000000", "Hạng Kim cương: tổng chi tiêu tối thiểu (đ)");
        def("tier_kim_cuong_rate", "200", "Hạng Kim cương: hệ số điểm (%)");
        def("carriers", "Giao Hàng Nhanh (GHN)\nGiao Hàng Tiết Kiệm (GHTK)\nViettel Post\nJ&T Express\nNhân viên nhà thuốc tự giao",
                "Đơn vị vận chuyển (mỗi dòng một đơn vị)");
        def("pay_cod", "1", "Bật thanh toán khi nhận hàng (COD)");
        def("pay_bank_transfer", "1", "Bật chuyển khoản ngân hàng (VietQR)");
        def("pay_payos", "1", "Bật cổng thanh toán PayOS (VietQR ngân hàng)");
        def("pay_vnpay", "1", "Bật VNPay (thẻ ATM / Visa / QR)");
        def("bank_code", "VCB", "Mã ngân hàng (VietQR, VD: VCB, TCB, MB)");
        def("bank_account", "0123456789", "Số tài khoản nhận chuyển khoản");
        def("bank_holder", "CONG TY CP VINAPHARMA", "Chủ tài khoản");
        def("vat_rate", "5", "Thuế suất VAT thuốc, TPCN (%) - giá bán đã gồm VAT");
        def("company_tax_code", "0109876543", "Mã số thuế nhà thuốc (in trên hóa đơn)");
        def("einvoice_provider", "Chưa kết nối (xuất thủ công trên phần mềm HĐĐT)", "Nhà cung cấp hóa đơn điện tử");
        def("mail_enabled", "1", "Gửi email cho khách (xác nhận đơn, trạng thái, khuyến mãi)");
        def("ai_enabled", "1", "Trợ lý AI tiếp nhận chat trước khi chuyển dược sĩ");
        def("ai_name", "Trợ lý AI VinaPharma", "Tên hiển thị của trợ lý AI");
    }

    public static final Set<String> NUMERIC = Set.of("shipping_fee", "free_ship_threshold", "near_expiry_days", "online_min_shelf_days", "return_days",
            "points_per_amount", "point_value", "rx_valid_days", "rx_rejected_cancel_days", "unpaid_cancel_hours", "quote_cancel_days", "pos_cancel_days",
            "return_rx_allowed", "tier_dong_min", "tier_bac_min", "tier_vang_min", "tier_kim_cuong_min",
            "tier_dong_rate", "tier_bac_rate", "tier_vang_rate", "tier_kim_cuong_rate", "pay_cod", "pay_bank_transfer", "pay_payos", "pay_vnpay",
            "vat_rate", "ai_enabled", "mail_enabled");

    public static final Set<String> BOOLEAN = Set.of("pay_cod", "pay_bank_transfer", "pay_payos", "pay_vnpay", "return_rx_allowed", "ai_enabled", "mail_enabled");
    public static final Set<String> TEXTAREA = Set.of("carriers", "return_policy");

    /** Nhóm cấu hình hiển thị trên trang Cấu hình hệ thống. */
    public static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("Thông tin nhà thuốc (bắt buộc hiển thị trên web)", List.of("store_name", "store_address", "store_phone", "store_email",
                "business_license", "gpp_cert", "pharmacist_in_charge"));
        GROUPS.put("Phương thức thanh toán", List.of("pay_cod", "pay_bank_transfer", "pay_payos", "pay_vnpay", "bank_code", "bank_account", "bank_holder"));
        GROUPS.put("Vận chuyển (phí cố định khi chưa bật GHN)", List.of("carriers", "shipping_fee", "free_ship_threshold"));
        GROUPS.put("Đơn hàng, đơn thuốc & đổi trả", List.of("rx_valid_days", "rx_rejected_cancel_days", "unpaid_cancel_hours", "quote_cancel_days",
                "pos_cancel_days", "return_days", "return_rx_allowed", "return_policy"));
        GROUPS.put("Kho hàng (định mức tồn đặt riêng từng sản phẩm)", List.of("near_expiry_days", "online_min_shelf_days"));
        GROUPS.put("Email & thông báo", List.of("mail_enabled"));
        GROUPS.put("Tư vấn trực tuyến", List.of("ai_enabled", "ai_name"));
        GROUPS.put("Hóa đơn & thuế VAT", List.of("vat_rate", "company_tax_code", "einvoice_provider"));
    }

    private static void def(String key, String value, String label) {
        DEFAULTS.put(key, value);
        LABELS.put(key, label);
    }

    private final SettingRepository repo;

    /** Bộ nhớ đệm theo request (giống $cache của SettingService Laravel): xóa khi lưu. */
    private volatile Map<String, String> cache;

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> c = cache;
        if (c == null) {
            Map<String, String> map = new LinkedHashMap<>(DEFAULTS);
            try {
                for (Setting s : repo.findAll()) map.put(s.getSettingKey(), s.getSettingValue());
            } catch (RuntimeException e) {
                // CSDL chưa sẵn sàng: dùng mặc định
            }
            c = map;
            cache = c;
            applyTiers(c);
        }
        return c;
    }

    /** Bản Laravel cùng ghi bảng settings: đọc lại sau mỗi request để thấy thay đổi từ phía bên kia. */
    public void clearCache() {
        cache = null;
    }

    public String get(String key) {
        return all().get(key);
    }

    public String get(String key, String def) {
        String v = get(key);
        return v != null ? v : def;
    }

    public int getInt(String key) {
        String v = get(key);
        try {
            return Integer.parseInt(v.trim());
        } catch (RuntimeException e) {
            try {
                return Integer.parseInt(DEFAULTS.getOrDefault(key, "0"));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
    }

    public long getLong(String key) {
        String v = get(key);
        try {
            return Long.parseLong(v.trim());
        } catch (RuntimeException e) {
            try {
                return Long.parseLong(DEFAULTS.getOrDefault(key, "0"));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
    }

    public boolean bool(String key) {
        return getInt(key) == 1;
    }

    public String label(String key) {
        return LABELS.getOrDefault(key, key);
    }

    /** Giá trị nhiều dòng (VD: danh sách đơn vị vận chuyển). */
    public List<String> lines(String key) {
        return Texts.lines(get(key));
    }

    @Transactional
    public void save(Map<String, String> values) {
        Map<String, String> v0 = new HashMap<>(values);
        for (String key : DEFAULTS.keySet()) {
            if (BOOLEAN.contains(key) && v0.containsKey("_bool_" + key)) {
                String x = v0.get(key);
                v0.put(key, x != null && !x.isEmpty() && !"0".equals(x) ? "1" : "0");
            }
            if (!v0.containsKey(key)) continue;
            String v = Texts.trim(v0.get(key));
            if (NUMERIC.contains(key) && !v.matches("\\d+")) {
                throw new BusinessException("\"" + label(key) + "\" phải là số nguyên không âm.");
            }
            repo.save(new Setting(key, v));
        }
        cache = null;
    }

    @Transactional
    public void set(String key, String value) {
        repo.save(new Setting(key, value));
        cache = null;
    }

    /** Điền mẫu thông báo: thay {biến} bằng giá trị ({store}, {phone} lấy từ cấu hình nhà thuốc). */
    public String render(String t, Map<String, String> vars) {
        Map<String, String> all = new LinkedHashMap<>(vars);
        all.putIfAbsent("store", get("store_name"));
        all.putIfAbsent("phone", get("store_phone"));
        for (Map.Entry<String, String> e : all.entrySet()) t = t.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        return t.replaceAll("\\{[a-z]+}", "").trim();
    }

    /* ------------------ Hạng thành viên ------------------ */

    private static void applyTiers(Map<String, String> map) {
        for (MemberTier t : MemberTier.values()) {
            long min = parse(map.get("tier_" + t.getKey() + "_min"), t.getDefaultMin());
            int rate = (int) parse(map.get("tier_" + t.getKey() + "_rate"), t.getDefaultRate());
            MemberTier.configure(t, min, rate);
        }
    }

    private static long parse(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    public List<MemberTier> tiers() {
        all();
        return List.of(MemberTier.values());
    }

    /* ------------------ Vận chuyển ------------------ */

    /** Phí giao hàng cố định (khi chưa dùng GHN), nhận tại quầy = 0, đủ ngưỡng thì miễn phí. */
    public long shippingFee(ShippingMethod method, long amount) {
        if (method == ShippingMethod.PICKUP) return 0;
        return amount >= getLong("free_ship_threshold") ? 0 : getLong("shipping_fee");
    }

    public boolean isProvince(String province) {
        return province != null && PROVINCES.contains(province);
    }

    /** Tỉnh => [phí, ngưỡng miễn phí] để trang thanh toán tính phí ngay. */
    public Map<String, long[]> provinceFees() {
        long[] fee = {getLong("shipping_fee"), getLong("free_ship_threshold")};
        Map<String, long[]> out = new LinkedHashMap<>();
        for (String p : PROVINCES) out.put(p, fee);
        return out;
    }

    public List<PaymentMethod> enabledPaymentMethods() {
        List<PaymentMethod> list = PaymentMethod.ONLINE_METHODS.stream().filter(m -> bool("pay_" + m.name().toLowerCase())).toList();
        return list.isEmpty() ? List.of(PaymentMethod.COD) : list;
    }
}
