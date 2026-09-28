package com.hieuthuoc.service;

import com.hieuthuoc.entity.Setting;
import com.hieuthuoc.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
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
        def("points_per_amount", "10000", "Số tiền cho 1 điểm tích lũy (đ)");
        def("rx_valid_days", "5", "Hiệu lực đơn thuốc (ngày kể từ ngày kê)");
        def("cod_verify_threshold", "1000000", "Đơn COD từ giá trị này phải gọi xác minh (đ)");
        def("point_value", "1000", "Giá trị quy đổi 1 điểm khi thanh toán (đ)");
        def("bank_code", "VCB", "Mã ngân hàng (VietQR, VD: VCB, TCB, MB)");
        def("bank_account", "0123456789", "Số tài khoản nhận chuyển khoản");
        def("bank_holder", "CONG TY CP VINAPHARMA", "Chủ tài khoản");
    }

    public static final java.util.Set<String> NUMERIC = java.util.Set.of(
            "shipping_fee", "free_ship_threshold", "near_expiry_days", "return_days", "points_per_amount", "point_value", "rx_valid_days", "cod_verify_threshold");

    private static void def(String key, String value, String label) {
        DEFAULTS.put(key, value);
        LABELS.put(key, label);
    }

    private final SettingRepository repo;

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>(DEFAULTS);
        for (Setting s : repo.findAll()) map.put(s.getKey(), s.getValue());
        return map;
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
    }

    /** Phí giao hàng: nhận tại nhà thuốc = 0; đủ ngưỡng thì miễn phí. */
    public long shippingFee(com.hieuthuoc.entity.ShippingMethod method, long amount) {
        if (method == com.hieuthuoc.entity.ShippingMethod.PICKUP) return 0;
        return amount >= getLong("free_ship_threshold") ? 0 : getLong("shipping_fee");
    }
}
