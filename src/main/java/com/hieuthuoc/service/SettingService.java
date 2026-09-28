package com.hieuthuoc.service;

import com.hieuthuoc.entity.Setting;
import com.hieuthuoc.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingService {
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
        def("shipping_fee", "20000", "Phí giao hàng (đ)");
        def("free_ship_threshold", "300000", "Miễn phí giao hàng cho đơn từ (đ)");
        def("near_expiry_days", "90", "Cảnh báo cận hạn trước (ngày)");
        def("return_days", "7", "Thời hạn đổi/trả (ngày)");
        def("return_rx_allowed", "0", "Cho phép đổi/trả đơn có thuốc kê đơn (1 = có, 0 = không)");
        def("return_policy", "Đổi/trả trong thời hạn quy định khi sản phẩm lỗi, sai sản phẩm, hư hỏng do vận chuyển hoặc còn nguyên tem niêm phong. "
                + "Không đổi/trả thuốc kê đơn, thuốc đã mở niêm phong, sản phẩm bảo quản lạnh (trừ lỗi từ nhà thuốc). Tiền được hoàn trong 3-7 ngày làm việc.",
                "Chính sách đổi trả (hiển thị cho khách)");
        def("points_per_amount", "10000", "Số tiền cho 1 điểm tích lũy (đ)");
        def("rx_valid_days", "5", "Hiệu lực đơn thuốc (ngày kể từ ngày kê)");
        def("cod_verify_threshold", "1000000", "Đơn COD từ giá trị này phải gọi xác minh (đ)");
        def("point_value", "1000", "Giá trị quy đổi 1 điểm khi thanh toán (đ)");
        for (com.hieuthuoc.entity.MemberTier t : com.hieuthuoc.entity.MemberTier.values()) {
            def("tier_" + t.getKey() + "_min", String.valueOf(t.getMinSpent()), "Hạng " + t.getLabel() + ": tổng chi tiêu tối thiểu (đ)");
            def("tier_" + t.getKey() + "_rate", String.valueOf(Math.round(t.getPointMultiplier() * 100)), "Hạng " + t.getLabel() + ": hệ số điểm (%)");
        }
        def("carriers", "Giao Hàng Nhanh (GHN)\nGiao Hàng Tiết Kiệm (GHTK)\nViettel Post\nJ&T Express\nNhân viên nhà thuốc tự giao",
                "Đơn vị vận chuyển (mỗi dòng một đơn vị)");
        def("pay_cod", "1", "Bật thanh toán khi nhận hàng (COD)");
        def("pay_bank_transfer", "1", "Bật chuyển khoản ngân hàng (VietQR)");
        def("pay_online", "1", "Bật ví điện tử / cổng thanh toán online");
        def("vat_rate", "5", "Thuế suất VAT thuốc, TPCN (%) - giá bán đã gồm VAT");
        def("company_tax_code", "0109876543", "Mã số thuế nhà thuốc (in trên hóa đơn)");
        def("einvoice_provider", "Chưa kết nối (xuất thủ công trên phần mềm HĐĐT)", "Nhà cung cấp hóa đơn điện tử");
        def("tpl_order_status", "Đơn hàng {code}: {status}{note}", "Mẫu: đổi trạng thái đơn");
        def("tpl_order_shipping", "Đơn hàng {code} đang được giao bởi {carrier}{tracking}. Vui lòng để ý điện thoại nhé!", "Mẫu: bắt đầu giao hàng");
        def("tpl_order_completed", "Cảm ơn bạn đã mua hàng tại {store}! Đơn {code} đã hoàn thành, bạn được cộng {points} điểm.", "Mẫu: đơn hoàn thành");
        def("tpl_order_cancelled", "Đơn hàng {code} đã bị hủy: {note}. Liên hệ {phone} nếu cần hỗ trợ.", "Mẫu: hủy đơn");
        def("ai_enabled", "1", "Trợ lý AI tiếp nhận chat trước khi chuyển dược sĩ");
        def("ai_name", "Trợ lý AI VinaPharma", "Tên hiển thị của trợ lý AI");
        def("bank_code", "VCB", "Mã ngân hàng (VietQR, VD: VCB, TCB, MB)");
        def("bank_account", "0123456789", "Số tài khoản nhận chuyển khoản");
        def("bank_holder", "CONG TY CP VINAPHARMA", "Chủ tài khoản");
    }

    public static final java.util.Set<String> NUMERIC = java.util.Set.of(
            "shipping_fee", "free_ship_threshold", "near_expiry_days", "return_days", "points_per_amount", "point_value", "rx_valid_days", "cod_verify_threshold", "return_rx_allowed",
            "tier_dong_min", "tier_bac_min", "tier_vang_min", "tier_kim_cuong_min", "tier_dong_rate", "tier_bac_rate", "tier_vang_rate", "tier_kim_cuong_rate",
            "pay_cod", "pay_bank_transfer", "pay_online", "vat_rate", "ai_enabled");

    /** Nhóm cấu hình hiển thị trên trang Cấu hình hệ thống. */
    public static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    public static final java.util.Set<String> BOOLEAN = java.util.Set.of("pay_cod", "pay_bank_transfer", "pay_online", "return_rx_allowed", "ai_enabled");
    public static final java.util.Set<String> TEXTAREA = java.util.Set.of("carriers", "return_policy", "tpl_order_status", "tpl_order_shipping",
            "tpl_order_completed", "tpl_order_cancelled");

    static {
        GROUPS.put("Thông tin nhà thuốc (bắt buộc hiển thị trên web)", List.of("store_name", "store_address", "store_phone", "store_email",
                "business_license", "gpp_cert", "pharmacist_in_charge"));
        GROUPS.put("Phương thức thanh toán", List.of("pay_cod", "pay_bank_transfer", "pay_online", "bank_code", "bank_account", "bank_holder"));
        GROUPS.put("Vận chuyển (biểu phí theo khu vực cấu hình tại Khu vực & phí ship)", List.of("carriers", "shipping_fee", "free_ship_threshold"));
        GROUPS.put("Đơn hàng, đơn thuốc & đổi trả", List.of("rx_valid_days", "cod_verify_threshold", "return_days", "return_rx_allowed", "return_policy"));
        GROUPS.put("Tư vấn trực tuyến", List.of("ai_enabled", "ai_name"));
        GROUPS.put("Hóa đơn & thuế VAT", List.of("vat_rate", "company_tax_code", "einvoice_provider"));
        GROUPS.put("Mẫu thông báo gửi khách (biến: {code} {status} {note} {carrier} {tracking} {points} {store} {phone})",
                List.of("tpl_order_status", "tpl_order_shipping", "tpl_order_completed", "tpl_order_cancelled"));
    }

    /** Điền mẫu thông báo: thay {biến} bằng giá trị. */
    public String render(String key, Map<String, String> vars) {
        String t = get(key);
        if (t == null) return "";
        Map<String, String> all = new java.util.HashMap<>(vars);
        all.putIfAbsent("store", get("store_name"));
        all.putIfAbsent("phone", get("store_phone"));
        for (Map.Entry<String, String> e : all.entrySet()) t = t.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        return t.replaceAll("\\{[a-z]+}", "").trim();
    }

    /** Các nhóm key hiển thị ở trang riêng (không lặp lại ở trang Cấu hình chung). */
    public static boolean isDedicated(String key) {
        return key.startsWith("tier_") || key.equals("points_per_amount") || key.equals("point_value") || key.equals("near_expiry_days")
                || key.startsWith("tpl_") || key.startsWith("pay_") || key.equals("carriers");
    }

    private static void def(String key, String value, String label) {
        DEFAULTS.put(key, value);
        LABELS.put(key, label);
    }

    private final SettingRepository repo;
    private final com.hieuthuoc.repository.ShippingZoneRepository zoneRepo;

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>(DEFAULTS);
        for (Setting s : repo.findAll()) map.put(s.getKey(), s.getValue());
        return map;
    }

    /** Giá trị nhiều dòng (VD: danh sách đơn vị vận chuyển). */
    public java.util.List<String> lines(String key) {
        String v = get(key);
        if (v == null) return java.util.List.of();
        return java.util.Arrays.stream(v.split("\\r?\\n")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    public String get(String key) {
        return repo.findById(key).map(Setting::getValue).orElse(DEFAULTS.get(key));
    }

    public long getLong(String key) {
        try {
            return Long.parseLong(get(key));
        } catch (NumberFormatException e) {
            return Long.parseLong(DEFAULTS.get(key));
        }
    }

    /** Nạp ngưỡng / hệ số hạng thành viên từ cấu hình. */
    @jakarta.annotation.PostConstruct
    @Transactional(readOnly = true)
    public void applyTiers() {
        try {
            for (com.hieuthuoc.entity.MemberTier t : com.hieuthuoc.entity.MemberTier.values()) {
                com.hieuthuoc.entity.MemberTier.configure(t, getLong("tier_" + t.getKey() + "_min"), getLong("tier_" + t.getKey() + "_rate") / 100.0);
            }
        } catch (RuntimeException e) {
            // CSDL chưa sẵn sàng: dùng giá trị mặc định
        }
    }

    @Transactional
    public void save(Map<String, String> values) {
        for (String key : DEFAULTS.keySet()) {
            String v = values.get(key);
            if (v == null) continue;
            v = v.trim();
            if (NUMERIC.contains(key) && !v.matches("\\d+")) {
                throw new BusinessException("\"" + LABELS.get(key) + "\" phải là số nguyên không âm.");
            }
            repo.save(new Setting(key, v));
        }
        applyTiers();
    }

    /** Phí giao hàng: nhận tại nhà thuốc = 0; đủ ngưỡng thì miễn phí. */
    public long shippingFee(com.hieuthuoc.entity.ShippingMethod method, long amount) {
        return shippingFee(method, amount, null);
    }

    /** Phí giao hàng theo khu vực (tỉnh/thành) nếu admin đã cấu hình khu vực giao hàng. */
    public long shippingFee(com.hieuthuoc.entity.ShippingMethod method, long amount, String province) {
        if (method == com.hieuthuoc.entity.ShippingMethod.PICKUP) return 0;
        if (province != null) {
            for (com.hieuthuoc.entity.ShippingZone z : zoneRepo.findByActiveTrueOrderBySortOrderAscIdAsc()) {
                if (z.covers(province)) return z.feeFor(amount);
            }
        }
        return amount >= getLong("free_ship_threshold") ? 0 : getLong("shipping_fee");
    }

    /** Phương thức thanh toán online admin đang bật (luôn còn ít nhất COD nếu tắt hết). */
    public List<com.hieuthuoc.entity.PaymentMethod> enabledPaymentMethods() {
        List<com.hieuthuoc.entity.PaymentMethod> list = com.hieuthuoc.entity.PaymentMethod.ONLINE_METHODS.stream()
                .filter(m -> getLong("pay_" + m.name().toLowerCase()) == 1).toList();
        return list.isEmpty() ? List.of(com.hieuthuoc.entity.PaymentMethod.COD) : list;
    }

    /** Có giao hàng tới tỉnh/thành này không (không cấu hình khu vực = giao toàn quốc). */
    public boolean deliversTo(String province) {
        List<com.hieuthuoc.entity.ShippingZone> zones = zoneRepo.findByActiveTrueOrderBySortOrderAscIdAsc();
        return zones.isEmpty() || zones.stream().anyMatch(z -> z.covers(province));
    }

    /** Tỉnh -> [phí ship, ngưỡng miễn phí (-1 = không)] để trang thanh toán tính phí ngay khi chọn tỉnh. */
    public Map<String, long[]> provinceFees() {
        Map<String, long[]> out = new LinkedHashMap<>();
        List<com.hieuthuoc.entity.ShippingZone> zones = zoneRepo.findByActiveTrueOrderBySortOrderAscIdAsc();
        for (String p : deliverableProvinces()) {
            com.hieuthuoc.entity.ShippingZone z = zones.stream().filter(x -> x.covers(p)).findFirst().orElse(null);
            out.put(p, z == null ? new long[]{getLong("shipping_fee"), getLong("free_ship_threshold")}
                    : new long[]{z.getFee(), z.getFreeThreshold() == null ? -1 : z.getFreeThreshold()});
        }
        return out;
    }

    public List<String> deliverableProvinces() {
        List<com.hieuthuoc.entity.ShippingZone> zones = zoneRepo.findByActiveTrueOrderBySortOrderAscIdAsc();
        if (zones.isEmpty()) return com.hieuthuoc.entity.ShippingZone.PROVINCES;
        return com.hieuthuoc.entity.ShippingZone.PROVINCES.stream().filter(p -> zones.stream().anyMatch(z -> z.covers(p))).toList();
    }
}
