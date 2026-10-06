package com.hieuthuoc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieuthuoc.entity.Order;
import com.hieuthuoc.entity.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tích hợp Giao Hàng Nhanh (GHN): địa giới hành chính, tính phí, tạo vận đơn, tra cứu trạng thái.
 * Môi trường test: https://5sao.ghn.dev - API: https://dev-online-gateway.ghn.vn/shiip/public-api
 */
@Slf4j
@Service
public class GhnService {
    /** Tên đơn vị vận chuyển ghi vào đơn khi tạo vận đơn GHN tự động. */
    public static final String CARRIER = "Giao Hàng Nhanh (GHN)";

    public static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();

    static {
        String[][] s = {{"ready_to_pick", "Chờ lấy hàng"}, {"picking", "Đang lấy hàng"}, {"cancel", "Đã hủy vận đơn"},
                {"money_collect_picking", "Đang thu tiền người gửi"}, {"picked", "Đã lấy hàng"}, {"storing", "Hàng đang nằm ở kho"},
                {"transporting", "Đang luân chuyển"}, {"sorting", "Đang phân loại"}, {"delivering", "Đang giao hàng"},
                {"money_collect_delivering", "Đang thu tiền người nhận"}, {"delivered", "Giao hàng thành công"}, {"delivery_fail", "Giao hàng thất bại"},
                {"waiting_to_return", "Chờ trả hàng"}, {"return", "Trả hàng"}, {"return_transporting", "Đang luân chuyển hàng trả"},
                {"return_sorting", "Đang phân loại hàng trả"}, {"returning", "Đang trả hàng"}, {"return_fail", "Trả hàng thất bại"},
                {"returned", "Đã trả hàng"}, {"exception", "Đơn ngoại lệ"}, {"damage", "Hàng bị hư hỏng"}, {"lost", "Hàng bị thất lạc"}};
        for (String[] x : s) STATUS_LABELS.put(x[0], x[1]);
    }

    private final TtlCache cache;
    private final SettingService settings;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.ghn.base-url:https://dev-online-gateway.ghn.vn/shiip/public-api}")
    private String baseUrl;
    @Value("${app.ghn.token:}")
    private String token;
    @Value("${app.ghn.shop-id:}")
    private String shopId;
    @Value("${app.ghn.from-district-id:}")
    private String fromDistrictId;
    @Value("${app.ghn.from-ward-code:}")
    private String fromWardCode;
    @Value("${app.ghn.webhook-secret:}")
    private String webhookSecret;

    public GhnService(TtlCache cache, SettingService settings) {
        this.cache = cache;
        this.settings = settings;
    }

    public boolean enabled() {
        return !token.isBlank() && !shopId.isBlank();
    }

    public String webhookSecret() {
        return webhookSecret;
    }

    public static String statusLabel(String s) {
        return s == null || s.isEmpty() ? null : STATUS_LABELS.getOrDefault(s, s);
    }

    private RestClient http(int timeoutSec) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutSec * 1000);
        f.setReadTimeout(timeoutSec * 1000);
        return RestClient.builder().requestFactory(f).build();
    }

    private JsonNode call(String path, Map<String, Object> body, boolean withShop) {
        if (!enabled()) return null;
        try {
            var req = http(15).post().uri(baseUrl.replaceAll("/+$", "") + path).contentType(MediaType.APPLICATION_JSON)
                    .header("Token", token);
            if (withShop) req = req.header("ShopId", shopId);
            String res = req.body(json.writeValueAsString(body)).exchange((rq, rs) -> new String(rs.getBody().readAllBytes(), StandardCharsets.UTF_8));
            JsonNode j = json.readTree(res);
            if (j != null && j.path("code").asInt() == 200) return j.has("data") ? j.get("data") : json.createObjectNode();
            log.warn("GHN {} lỗi: {}", path, res);
        } catch (Exception e) {
            log.warn("GHN {} không kết nối được: {}", path, e.getMessage());
        }
        return null;
    }

    public List<Map<String, Object>> provinces() {
        return master("ghn.provinces", "/master-data/province", Map.of(), p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.path("ProvinceID").asInt());
            m.put("name", p.path("ProvinceName").asText());
            return m;
        });
    }

    public List<Map<String, Object>> districts(int provinceId) {
        return master("ghn.districts." + provinceId, "/master-data/district", Map.of("province_id", provinceId), d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.path("DistrictID").asInt());
            m.put("name", d.path("DistrictName").asText());
            return m;
        });
    }

    public List<Map<String, Object>> wards(int districtId) {
        return master("ghn.wards." + districtId, "/master-data/ward", Map.of("district_id", districtId), w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", w.path("WardCode").asText());
            m.put("name", w.path("WardName").asText());
            return m;
        });
    }

    /** Danh mục địa giới GHN, lưu cache 1 ngày; lỗi / rỗng thì không lưu để lần sau gọi lại. */
    private List<Map<String, Object>> master(String key, String path, Map<String, Object> body, Function<JsonNode, Map<String, Object>> map) {
        List<Map<String, Object>> cached = cache.get(key);
        if (cached != null && !cached.isEmpty()) return cached;
        JsonNode data = call(path, body, false);
        List<Map<String, Object>> list = new ArrayList<>();
        if (data != null && data.isArray()) for (JsonNode n : data) list.add(map.apply(n));
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));
        if (!list.isEmpty()) cache.put(key, list, 86400);
        return list;
    }

    /** Phí giao hàng GHN (đ) hoặc null nếu không tính được. */
    public Long fee(int districtId, String wardCode, long weightGram, long insurance) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service_type_id", 2);
        body.put("to_district_id", districtId);
        body.put("to_ward_code", wardCode);
        body.put("weight", Math.max(100, Math.min(30000, weightGram)));
        body.put("length", 20);
        body.put("width", 15);
        body.put("height", 10);
        body.put("insurance_value", Math.min(5_000_000, Math.max(0, insurance)));
        if (!fromDistrictId.isBlank()) body.put("from_district_id", Integer.parseInt(fromDistrictId.trim()));
        if (!fromWardCode.isBlank()) body.put("from_ward_code", fromWardCode.trim());
        String key;
        try {
            key = "ghn.fee." + md5(json.writeValueAsString(body));
        } catch (Exception e) {
            key = "ghn.fee." + body.hashCode();
        }
        JsonNode data = cache.remember(key, 1800, () -> call("/v2/shipping-order/fee", body, true));
        return data != null && data.has("total") ? data.get("total").asLong() : null;
    }

    /** Tạo vận đơn GHN cho đơn hàng (COD thu tiền khi giao, trả trước thì cod_amount = 0). */
    public JsonNode createOrder(Order o) {
        if (!enabled()) throw new BusinessException("Chưa cấu hình GHN (GHN_TOKEN, GHN_SHOP_ID).");
        if (o.getGhnDistrictId() == null || o.getGhnWardCode() == null || o.getGhnWardCode().isEmpty()) {
            throw new BusinessException("Đơn hàng chưa có mã quận/huyện, phường/xã GHN - không tạo được vận đơn tự động. Hãy nhập mã vận đơn thủ công.");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        long weight = 0;
        for (OrderItem it : o.getItems()) {
            int w = it.getProduct() != null && it.getProduct().getWeightGram() > 0 ? it.getProduct().getWeightGram() : 200;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", it.getProductName().length() > 100 ? it.getProductName().substring(0, 100) : it.getProductName());
            m.put("quantity", it.getQuantity());
            m.put("price", it.getPrice());
            m.put("weight", w);
            items.add(m);
            weight += (long) w * it.getQuantity();
        }
        long cod = "COD".equals(o.getPaymentMethod().name()) && "UNPAID".equals(o.getPaymentStatus().name()) ? o.getTotal() : 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payment_type_id", 1); // người gửi (nhà thuốc) trả phí; phí đã thu của khách trong đơn
        body.put("required_note", "CHOXEMHANGKHONGTHU");
        body.put("client_order_code", o.getCode());
        body.put("to_name", o.getRecipient());
        body.put("to_phone", o.getPhone());
        body.put("to_address", o.getAddress() == null ? "" : o.getAddress());
        body.put("to_ward_code", o.getGhnWardCode());
        body.put("to_district_id", o.getGhnDistrictId());
        body.put("cod_amount", Math.min(cod, 50_000_000));
        body.put("content", "Đơn hàng " + o.getCode() + " - " + settings.get("store_name"));
        body.put("weight", Math.max(100, weight));
        body.put("length", 20);
        body.put("width", 15);
        body.put("height", 10);
        body.put("insurance_value", Math.min(5_000_000, o.getSubtotal()));
        body.put("service_type_id", 2);
        body.put("items", items);
        body.put("note", o.getNote());
        JsonNode data = call("/v2/shipping-order/create", body, true);
        if (data == null || data.path("order_code").asText("").isEmpty()) {
            throw new BusinessException("GHN chưa tạo được vận đơn. Kiểm tra lại địa chỉ, cấu hình GHN hoặc nhập mã vận đơn thủ công.");
        }
        return data;
    }

    /** Đơn có vận đơn GHN (tạo tự động) chưa bị hủy bên GHN. */
    public static boolean hasActiveShipment(Order o) {
        return CARRIER.equals(o.getCarrier()) && o.getTrackingCode() != null && !o.getTrackingCode().isEmpty() && !"cancel".equals(o.getShippingStatus());
    }

    /**
     * Hủy vận đơn trên GHN (chỉ được khi shipper chưa lấy hàng). Không hủy được thì báo lỗi để giữ nguyên đơn:
     * hủy đơn trong hệ thống mà vận đơn vẫn chạy thì shipper vẫn đến lấy / giao hàng đã nhập lại kho.
     */
    public void cancelOrder(String orderCode) {
        if (!enabled()) {
            throw new BusinessException("Đơn có vận đơn GHN " + orderCode + " nhưng hệ thống chưa kết nối GHN - không hủy được vận đơn. Kiểm tra cấu hình GHN.");
        }
        JsonNode data = call("/v2/switch-status/cancel", Map.of("order_codes", List.of(orderCode)), true);
        JsonNode row = null;
        if (data != null && data.isArray()) {
            for (JsonNode n : data) if (orderCode.equals(n.path("order_code").asText())) row = n;
        }
        if (row == null || !row.path("result").asBoolean(false)) {
            String msg = row != null ? row.path("message").asText("") : "";
            throw new BusinessException("GHN không hủy được vận đơn " + orderCode + (!msg.isEmpty() ? " (" + msg + ")" : "")
                    + ". Có thể shipper đã lấy hàng - liên hệ GHN trước khi hủy đơn.");
        }
    }

    /** Tra cứu trạng thái vận đơn. */
    public JsonNode detail(String orderCode) {
        return call("/v2/shipping-order/detail", Map.of("order_code", orderCode), false);
    }

    /* ======================= Giải quyết địa chỉ (bản đồ) ======================= */

    /** Tự động giải quyết địa chỉ thành bộ ba GHN (Tỉnh, Quận, Phường) và tọa độ bản đồ. */
    public Map<String, Object> resolveAddress(String query, Double lat, Double lon) {
        String cacheKey = "geo_resolve_" + md5(lat != null && lon != null ? lat + "," + lon : Texts.trim(query).toLowerCase());
        return cache.remember(cacheKey, 86400, () -> doResolve(query, lat, lon));
    }

    private Map<String, Object> doResolve(String query, Double lat, Double lon) {
        // Địa chỉ khách gõ thường theo địa giới cũ (Tỉnh, Quận, Phường) - trùng danh mục GHN.
        // Bản đồ OSM đã chuyển sang địa giới sau sáp nhập 2025 nên chỉ dùng khi không khớp trực tiếp.
        if (query != null && !query.isBlank() && (lat == null || lon == null)) {
            Map<String, Object> direct = matchFromText(query);
            if (direct != null) return direct;
        }
        List<JsonNode> results = new ArrayList<>();
        try {
            if (lat != null && lon != null) {
                String url = UriComponentsBuilder.fromUriString("https://nominatim.openstreetmap.org/reverse")
                        .queryParam("format", "json").queryParam("lat", lat).queryParam("lon", lon).queryParam("addressdetails", 1)
                        .build().toUriString();
                JsonNode item = getJson(url);
                if (item != null && !item.isNull() && !item.isEmpty()) results.add(item);
            } else if (query != null && !query.isBlank()) {
                String q = query.trim();
                String qNorm = removeTones(q);
                if (!qNorm.contains("viet nam") && !qNorm.contains("vn")) q += ", Việt Nam";
                String url = UriComponentsBuilder.fromUriString("https://nominatim.openstreetmap.org/search")
                        .queryParam("format", "json").queryParam("q", q).queryParam("countrycodes", "vn")
                        .queryParam("addressdetails", 1).queryParam("limit", 5).encode().build().toUriString();
                JsonNode arr = getJson(url);
                if (arr != null && arr.isArray()) for (JsonNode n : arr) results.add(n);
            }
        } catch (Exception e) {
            log.warn("Dịch vụ bản đồ Nominatim gặp sự cố hoặc timeout: {}", e.getMessage());
            return null;
        }
        if (results.isEmpty()) return null;

        List<String> provCands = new ArrayList<>(), distCands = new ArrayList<>(), wardCands = new ArrayList<>(), roadParts = new ArrayList<>();
        Double bestLat = lat, bestLon = lon;
        for (JsonNode item : results) {
            JsonNode addr = item.path("address");
            if (bestLat == null && !item.path("lat").asText("").isEmpty()) {
                bestLat = item.path("lat").asDouble();
                bestLon = item.path("lon").asDouble();
            }
            for (String k : List.of("city", "state", "province")) if (!addr.path(k).asText("").isEmpty()) provCands.add(addr.path(k).asText());
            for (String k : List.of("city_district", "district", "county", "suburb")) if (!addr.path(k).asText("").isEmpty()) distCands.add(addr.path(k).asText());
            for (String k : List.of("ward", "quarter", "suburb", "neighbourhood", "village")) if (!addr.path(k).asText("").isEmpty()) wardCands.add(addr.path(k).asText());
            if (roadParts.isEmpty()) {
                if (!addr.path("house_number").asText("").isEmpty()) roadParts.add(addr.path("house_number").asText());
                if (!addr.path("road").asText("").isEmpty()) roadParts.add(addr.path("road").asText());
            }
        }

        List<Map<String, Object>> provinces = provinces();
        Map<String, Object> matchedProv = null;
        outer:
        for (String c : provCands) {
            String cleanC = cleanAdminPrefix(c);
            for (Map<String, Object> p : provinces) {
                String pClean = cleanAdminPrefix(String.valueOf(p.get("name")));
                if (pClean.equals(cleanC) || pClean.contains(cleanC) || cleanC.contains(pClean)) {
                    matchedProv = p;
                    break outer;
                }
            }
        }
        // Nếu query có nhắc tới tỉnh/thành nào trong 63 tỉnh
        if (matchedProv == null && query != null && !query.isBlank()) {
            String qClean = cleanAdminPrefix(query);
            for (Map<String, Object> p : provinces) {
                String pClean = cleanAdminPrefix(String.valueOf(p.get("name")));
                if (pClean.length() >= 4 && qClean.contains(pClean)) {
                    matchedProv = p;
                    break;
                }
            }
        }
        if (matchedProv == null) return null;

        List<Map<String, Object>> districts = districts((Integer) matchedProv.get("id"));
        Map<String, Object> matchedDist = null, matchedWard = null;
        // Ưu tiên 1: Khớp district từ candidates của OSM
        outer2:
        for (String c : distCands) {
            String cleanC = cleanAdminPrefix(c);
            for (Map<String, Object> d : districts) {
                String dClean = cleanAdminPrefix(String.valueOf(d.get("name")));
                if (dClean.equals(cleanC) || (cleanC.length() >= 4 && (dClean.contains(cleanC) || cleanC.contains(dClean)))) {
                    matchedDist = d;
                    break outer2;
                }
            }
        }
        // Ưu tiên 2: Nếu district chưa khớp, dò tìm ward candidate trong các quận/huyện thuộc tỉnh
        if (matchedDist == null) {
            outer3:
            for (Map<String, Object> d : districts) {
                List<Map<String, Object>> ws = wards((Integer) d.get("id"));
                for (String wc : wardCands) {
                    String cleanW = cleanAdminPrefix(wc);
                    if (cleanW.length() < 3) continue;
                    for (Map<String, Object> w : ws) {
                        String wClean = cleanAdminPrefix(String.valueOf(w.get("name")));
                        if (wClean.equals(cleanW) || wClean.contains(cleanW)) {
                            matchedDist = d;
                            matchedWard = w;
                            break outer3;
                        }
                    }
                }
            }
        }
        if (matchedDist == null) return null;

        List<Map<String, Object>> wards = wards((Integer) matchedDist.get("id"));
        if (matchedWard == null) {
            outer4:
            for (String wc : wardCands) {
                String cleanW = cleanAdminPrefix(wc);
                if (cleanW.length() < 3) continue;
                for (Map<String, Object> w : wards) {
                    String wClean = cleanAdminPrefix(String.valueOf(w.get("name")));
                    if (wClean.equals(cleanW) || wClean.contains(cleanW) || cleanW.contains(wClean)) {
                        matchedWard = w;
                        break outer4;
                    }
                }
            }
        }
        if (matchedWard == null) return null;

        // Tinh chỉnh cho các tuyến đường lớn chia nhiều phường (VD: Đường Láng số > 600 là Láng Thượng)
        if (((Integer) matchedDist.get("id")) == 1486 && Pattern.compile("\\b(1\\d{3}|[6-9]\\d{2})\\b").matcher(query == null ? "" : query).find()) {
            for (Map<String, Object> w : wards) {
                if (String.valueOf(w.get("name")).toLowerCase().contains("láng thượng")) {
                    matchedWard = w;
                    break;
                }
            }
        }

        String road = String.join(" ", roadParts);
        if (query != null) {
            Matcher m = Pattern.compile("^\\s*(\\d+[\\w/-]*)\\s+(.+)$", Pattern.UNICODE_CHARACTER_CLASS).matcher(query);
            if (m.find()) {
                String num = m.group(1);
                if (!road.isEmpty() && !road.startsWith(num)) road = num + " " + road;
                else if (road.isEmpty()) road = query;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("province", matchedProv);
        out.put("district", matchedDist);
        out.put("ward", matchedWard);
        out.put("districts", districts);
        out.put("wards", wards);
        out.put("lat", bestLat);
        out.put("lon", bestLon);
        out.put("road", road);
        return out;
    }

    private JsonNode getJson(String url) throws Exception {
        String body = http(5).get().uri(java.net.URI.create(url)).header("User-Agent", "VinaPharma/1.0")
                .exchange((rq, rs) -> rs.getStatusCode().is2xxSuccessful() ? new String(rs.getBody().readAllBytes(), StandardCharsets.UTF_8) : null);
        return body == null ? null : json.readTree(body);
    }

    /**
     * Khớp trực tiếp "số nhà đường, Phường, Quận, Tỉnh" với danh mục GHN (so tên sau khi bỏ dấu và tiền tố).
     * Cần khớp đủ cả ba cấp, nếu không trả về null để thử qua bản đồ.
     */
    private Map<String, Object> matchFromText(String query) {
        List<String> segments = Arrays.stream(query.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
        List<String> clean = segments.stream().map(GhnService::cleanAdminPrefix).map(x -> x.replaceAll("\\s*viet nam$", "")).toList();
        Object[] p = find(clean, provinces(), Set.of());
        if (p == null) return null;
        @SuppressWarnings("unchecked") Map<String, Object> prov = (Map<String, Object>) p[0];
        List<Map<String, Object>> districts = districts((Integer) prov.get("id"));
        Object[] d = find(clean, districts, Set.of((Integer) p[1]));
        if (d == null) return null;
        @SuppressWarnings("unchecked") Map<String, Object> dist = (Map<String, Object>) d[0];
        List<Map<String, Object>> wards = wards((Integer) dist.get("id"));
        Object[] w = find(clean, wards, Set.of((Integer) p[1], (Integer) d[1]));
        if (w == null) return null;
        Set<Integer> used = Set.of((Integer) p[1], (Integer) d[1], (Integer) w[1]);
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) if (!used.contains(i)) rest.add(segments.get(i));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("province", prov);
        out.put("district", dist);
        out.put("ward", w[0]);
        out.put("districts", districts);
        out.put("wards", wards);
        out.put("lat", null);
        out.put("lon", null);
        out.put("road", String.join(", ", rest));
        return out;
    }

    private static Object[] find(List<String> clean, List<Map<String, Object>> items, Set<Integer> skip) {
        for (int i = clean.size() - 1; i >= 0; i--) {
            if (skip.contains(i) || clean.get(i).isEmpty()) continue;
            for (Map<String, Object> it : items) {
                if (cleanAdminPrefix(String.valueOf(it.get("name"))).equals(clean.get(i))) return new Object[]{it, i};
            }
        }
        return null;
    }

    public static String removeTones(String str) {
        String s = str.toLowerCase();
        String[][] chars = {{"a", "à|á|ạ|ả|ã|â|ầ|ấ|ậ|ẩ|ẫ|ă|ằ|ắ|ặ|ẳ|ẵ"}, {"e", "è|é|ẹ|ẻ|ẽ|ê|ề|ế|ệ|ể|ễ"}, {"i", "ì|í|ị|ỉ|ĩ"},
                {"o", "ò|ó|ọ|ỏ|õ|ô|ồ|ố|ộ|ổ|ỗ|ơ|ờ|ớ|ợ|ở|ỡ"}, {"u", "ù|ú|ụ|ủ|ũ|ư|ừ|ứ|ự|ử|ữ"}, {"y", "ỳ|ý|ỵ|ỷ|ỹ"}, {"d", "đ"}};
        for (String[] c : chars) s = s.replaceAll("(?iu)(" + c[1] + ")", c[0]);
        return s.trim();
    }

    public static String cleanAdminPrefix(String str) {
        String s = removeTones(str);
        return s.replaceFirst("(?iu)^(?:(?:thanh pho|tinh|quan|huyen|thi xa|phuong|xa|thi tran)\\s+|(?:tp|q|h|tx|p|x|tt)\\.\\s*)", "").trim();
    }

    private static String md5(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
