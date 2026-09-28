package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Cảnh báo an toàn khi tư vấn / duyệt đơn: dị ứng, bệnh nền, thai kỳ, trùng hoạt chất và tương tác thuốc.
 * Dữ liệu tương tác là bộ quy tắc mẫu phổ biến - chỉ hỗ trợ dược sĩ, không thay thế tra cứu chuyên môn.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyService {
    private final OrderRepository orderRepo;
    private final com.hieuthuoc.repository.DrugInteractionRepository interactionRepo;

    public record Warning(String level, String message) {
        public String getIcon() {
            return "danger".equals(level) ? "bi-exclamation-octagon-fill" : "bi-exclamation-triangle-fill";
        }
    }

    /** Nhóm dị ứng: từ khóa khách khai báo -> các hoạt chất liên quan. */
    private static final Map<String, List<String>> ALLERGY_GROUPS = Map.of(
            "penicillin", List.of("amoxicillin", "ampicillin", "penicillin", "cloxacillin"),
            "beta-lactam", List.of("amoxicillin", "ampicillin", "penicillin", "cefuroxim", "cefalexin", "ceftriaxon", "cefixim"),
            "cephalosporin", List.of("cefuroxim", "cefalexin", "ceftriaxon", "cefixim"),
            "aspirin", List.of("aspirin", "acid acetylsalicylic", "ibuprofen", "diclofenac", "naproxen", "meloxicam"),
            "nsaid", List.of("ibuprofen", "diclofenac", "naproxen", "meloxicam", "aspirin"),
            "sulfa", List.of("sulfamethoxazol", "sulfadiazin"),
            "paracetamol", List.of("paracetamol", "acetaminophen"));

    private static final List<String> NSAIDS = List.of("ibuprofen", "diclofenac", "naproxen", "meloxicam", "aspirin");

    /** Bộ quy tắc tương tác mẫu (nạp vào CSDL lần đầu; admin sửa/thêm tại Quản trị > Tương tác thuốc). */
    public static final String[][] DEFAULT_INTERACTIONS = {
            {"ibuprofen", "aspirin", "danger", "Phối hợp hai NSAID làm tăng nguy cơ loét, xuất huyết tiêu hóa."},
            {"ibuprofen", "losartan", "warning", "NSAID làm giảm tác dụng hạ áp của losartan, tăng nguy cơ suy thận."},
            {"ibuprofen", "amlodipin", "warning", "NSAID có thể làm giảm tác dụng hạ huyết áp."},
            {"ibuprofen", "bisoprolol", "warning", "NSAID có thể làm giảm tác dụng hạ huyết áp của bisoprolol."},
            {"phenylephrin", "bisoprolol", "warning", "Phenylephrin gây co mạch, tăng huyết áp - đối kháng thuốc hạ áp."},
            {"phenylephrin", "amlodipin", "warning", "Phenylephrin gây tăng huyết áp - đối kháng thuốc hạ áp."},
            {"phenylephrin", "losartan", "warning", "Phenylephrin gây tăng huyết áp - đối kháng thuốc hạ áp."},
            {"caffeine", "bisoprolol", "warning", "Caffeine có thể làm tăng nhịp tim, huyết áp."},
            {"omeprazol", "clopidogrel", "danger", "Omeprazol làm giảm tác dụng chống kết tập tiểu cầu của clopidogrel."},
            {"dầu cá", "aspirin", "warning", "Dầu cá liều cao phối hợp thuốc chống kết tập tiểu cầu làm tăng nguy cơ chảy máu."},
            {"bạch quả", "aspirin", "warning", "Cao bạch quả phối hợp aspirin/thuốc chống đông làm tăng nguy cơ chảy máu."},
            {"bạch quả", "ibuprofen", "warning", "Cao bạch quả phối hợp NSAID làm tăng nguy cơ chảy máu."},
            {"bacillus", "amoxicillin", "info", "Uống men vi sinh cách kháng sinh ít nhất 2 giờ."},
            {"bacillus", "cefuroxim", "info", "Uống men vi sinh cách kháng sinh ít nhất 2 giờ."},
            {"calci", "cefuroxim", "info", "Nên uống calci cách kháng sinh 2 giờ để tránh giảm hấp thu."},
            {"diazepam", "phenylephrin", "warning", "Cần thận trọng khi phối hợp thuốc an thần với thuốc cảm."},
    };

    /** Các sản phẩm khách đã mua trong 90 ngày (không tính đơn hủy / trả). */
    public List<Product> recentProducts(User customer) {
        if (customer == null) return List.of();
        return orderRepo.productsBoughtSince(customer, LocalDateTime.now().minusDays(90),
                EnumSet.of(OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.RX_REJECTED));
    }

    private static String ing(Product p) {
        return p.getActiveIngredient() == null ? "" : p.getActiveIngredient().toLowerCase();
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    /** Kiểm tra sản phẩm sắp bán (current) với hồ sơ khách và các thuốc khách đã mua gần đây (history). */
    public List<Warning> check(User customer, Collection<Product> current, Collection<Product> history) {
        LinkedHashMap<String, Warning> out = new LinkedHashMap<>();
        List<Product> cur = new ArrayList<>(new LinkedHashSet<>(current));
        Set<Long> curIds = new HashSet<>();
        cur.forEach(p -> curIds.add(p.getId()));
        List<Product> hist = history == null ? List.of() : history.stream().filter(p -> !curIds.contains(p.getId())).distinct().toList();

        if (customer != null) {
            String allergies = lower(customer.getAllergies());
            String chronic = lower(customer.getChronicConditions());
            for (Product p : cur) {
                String i = ing(p);
                if (i.isEmpty()) continue;
                // Dị ứng
                if (!allergies.isBlank()) {
                    for (String phrase : allergies.split("[,;/\\n]+")) {
                        String a = phrase.replace("dị ứng", "").trim();
                        if (a.length() >= 4 && i.contains(a)) {
                            add(out, "danger", "DỊ ỨNG: khách khai báo \"" + phrase.trim() + "\" - " + p.getName() + " chứa " + p.getActiveIngredient() + ".");
                        }
                    }
                    ALLERGY_GROUPS.forEach((key, group) -> {
                        if (allergies.contains(key) && group.stream().anyMatch(i::contains)) {
                            add(out, "danger", "DỊ ỨNG CHÉO: khách dị ứng nhóm " + key + " - " + p.getName() + " (" + p.getActiveIngredient() + ") cùng nhóm/liên quan.");
                        }
                    });
                }
                // Bệnh nền
                if (!chronic.isBlank()) {
                    String contra = lower(p.getContraindications());
                    for (String cond : chronic.split("[,;/\\n]+")) {
                        String c = cond.trim();
                        if (c.length() >= 4 && contra.contains(c)) {
                            add(out, "danger", "BỆNH NỀN: \"" + c + "\" nằm trong chống chỉ định của " + p.getName() + ".");
                        }
                    }
                    if (chronic.contains("huyết áp") && (i.contains("phenylephrin") || i.contains("pseudoephedrin") || i.contains("caffeine"))) {
                        add(out, "warning", "BỆNH NỀN: khách tăng huyết áp - " + p.getName() + " chứa chất có thể làm tăng huyết áp.");
                    }
                    if ((chronic.contains("dạ dày") || chronic.contains("loét")) && NSAIDS.stream().anyMatch(i::contains)) {
                        add(out, "warning", "BỆNH NỀN: khách có bệnh dạ dày - " + p.getName() + " (NSAID) có thể gây loét, xuất huyết tiêu hóa.");
                    }
                    if (chronic.contains("hen") && (i.contains("bisoprolol") || i.contains("propranolol") || i.contains("aspirin"))) {
                        add(out, "warning", "BỆNH NỀN: khách bị hen - thận trọng với " + p.getName() + ".");
                    }
                }
                // Thai kỳ
                if (customer.isPregnancy()) {
                    String contra = lower(p.getContraindications());
                    if (contra.contains("có thai") || contra.contains("mang thai")) {
                        add(out, "danger", "THAI KỲ: " + p.getName() + " chống chỉ định cho phụ nữ có thai.");
                    } else if (NSAIDS.stream().anyMatch(i::contains) || i.contains("losartan") || i.contains("diazepam")) {
                        add(out, "warning", "THAI KỲ: khách đang mang thai/cho con bú - thận trọng khi dùng " + p.getName() + ".");
                    }
                }
            }
        }

        // Trùng hoạt chất paracetamol (nguy cơ quá liều)
        List<Product> all = new ArrayList<>(cur);
        all.addAll(hist);
        List<Product> para = all.stream().filter(p -> ing(p).contains("paracetamol")).toList();
        if (para.size() >= 2 && cur.stream().anyMatch(p -> ing(p).contains("paracetamol"))) {
            add(out, "warning", "TRÙNG HOẠT CHẤT: " + String.join(", ", para.stream().map(Product::getName).toList())
                    + " cùng chứa paracetamol - dễ quá liều (tối đa 4g/ngày), hại gan.");
        }

        // Tương tác giữa thuốc sắp bán với nhau và với thuốc đã mua gần đây
        List<String[]> rules = interactionRepo.findAll().stream()
                .map(r -> new String[]{r.getIngredientA().toLowerCase().trim(), r.getIngredientB().toLowerCase().trim(), r.getLevel(), r.getMessage()})
                .filter(r -> !r[0].isEmpty() && !r[1].isEmpty()).toList();
        for (String[] rule : rules) {
            for (Product a : cur) {
                if (!ing(a).contains(rule[0]) && !ing(a).contains(rule[1])) continue;
                String other = ing(a).contains(rule[0]) ? rule[1] : rule[0];
                for (Product b : all) {
                    if (b.getId().equals(a.getId()) || !ing(b).contains(other)) continue;
                    boolean fromHistory = !curIds.contains(b.getId());
                    add(out, rule[2], "TƯƠNG TÁC: " + a.getName() + " + " + b.getName() + (fromHistory ? " (khách đã mua gần đây)" : "") + " - " + rule[3]);
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private static void add(Map<String, Warning> out, String level, String message) {
        out.putIfAbsent(message, new Warning(level, message));
    }
}
