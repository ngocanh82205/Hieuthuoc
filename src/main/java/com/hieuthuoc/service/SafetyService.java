package com.hieuthuoc.service;

import com.hieuthuoc.entity.DrugInteraction;
import com.hieuthuoc.entity.OrderStatus;
import com.hieuthuoc.entity.Product;
import com.hieuthuoc.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Cảnh báo an toàn: dị ứng (kể cả nhóm chéo), bệnh nền, thai kỳ, trùng hoạt chất, tương tác thuốc. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyService {
    private static final Map<String, List<String>> ALLERGY_GROUPS = new LinkedHashMap<>();
    private static final List<String> NSAIDS = List.of("ibuprofen", "diclofenac", "naproxen", "meloxicam", "aspirin");

    static {
        ALLERGY_GROUPS.put("penicillin", List.of("amoxicillin", "ampicillin", "penicillin", "cloxacillin"));
        ALLERGY_GROUPS.put("beta-lactam", List.of("amoxicillin", "ampicillin", "penicillin", "cefuroxim", "cefalexin", "ceftriaxon", "cefixim"));
        ALLERGY_GROUPS.put("cephalosporin", List.of("cefuroxim", "cefalexin", "ceftriaxon", "cefixim"));
        ALLERGY_GROUPS.put("aspirin", List.of("aspirin", "acid acetylsalicylic", "ibuprofen", "diclofenac", "naproxen", "meloxicam"));
        ALLERGY_GROUPS.put("nsaid", List.of("ibuprofen", "diclofenac", "naproxen", "meloxicam", "aspirin"));
        ALLERGY_GROUPS.put("sulfa", List.of("sulfamethoxazol", "sulfadiazin"));
        ALLERGY_GROUPS.put("paracetamol", List.of("paracetamol", "acetaminophen"));
    }

    /** Một cảnh báo: level danger / warning. */
    public record Warning(String level, String message, String icon) {
        public boolean isDanger() {
            return "danger".equals(level);
        }
    }

    @PersistenceContext
    private EntityManager em;

    /** Sản phẩm khách đã mua trong 90 ngày (không tính đơn hủy / trả). */
    public List<Product> recentProducts(User customer) {
        if (customer == null) return List.of();
        return em.createQuery("select distinct p from OrderItem oi join oi.product p join oi.order o where o.user.id = :u and o.createdAt >= :since"
                        + " and o.status not in :st", Product.class)
                .setParameter("u", customer.getId()).setParameter("since", LocalDateTime.now().minusDays(90))
                .setParameter("st", List.of(OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.RX_REJECTED)).getResultList();
    }

    private static String ing(Product p) {
        return Texts.lower(p.getActiveIngredient());
    }

    private static boolean hasAny(String hay, List<String> needles) {
        for (String n : needles) if (hay.contains(n)) return true;
        return false;
    }

    public List<Warning> check(User customer, Collection<Product> current, Collection<Product> history) {
        Map<String, Warning> out = new LinkedHashMap<>();
        java.util.function.BiConsumer<String, String> add = (level, msg) -> out.putIfAbsent(msg,
                new Warning(level, msg, "danger".equals(level) ? "bi-exclamation-octagon-fill" : "bi-exclamation-triangle-fill"));

        Map<Long, Product> curMap = new LinkedHashMap<>();
        for (Product p : current) if (p != null) curMap.putIfAbsent(p.getId(), p);
        List<Product> cur = new ArrayList<>(curMap.values());
        Set<Long> curIds = curMap.keySet();
        Map<Long, Product> histMap = new LinkedHashMap<>();
        if (history != null) for (Product p : history) if (p != null && !curIds.contains(p.getId())) histMap.putIfAbsent(p.getId(), p);
        List<Product> hist = new ArrayList<>(histMap.values());

        if (customer != null) {
            String allergies = Texts.lower(customer.getAllergies());
            String chronic = Texts.lower(customer.getChronicConditions());
            for (Product p : cur) {
                String i = ing(p);
                if (i.isEmpty()) continue;
                if (!allergies.trim().isEmpty()) {
                    for (String phrase : allergies.split("[,;/\\n]+")) {
                        String a = phrase.replace("dị ứng", "").trim();
                        if (Texts.mbLen(a) >= 4 && i.contains(a)) {
                            add.accept("danger", "DỊ ỨNG: khách khai báo \"" + phrase.trim() + "\" - " + p.getName() + " chứa " + p.getActiveIngredient() + ".");
                        }
                    }
                    for (Map.Entry<String, List<String>> g : ALLERGY_GROUPS.entrySet()) {
                        if (allergies.contains(g.getKey()) && hasAny(i, g.getValue())) {
                            add.accept("danger", "DỊ ỨNG CHÉO: khách dị ứng nhóm " + g.getKey() + " - " + p.getName() + " (" + p.getActiveIngredient() + ") cùng nhóm/liên quan.");
                        }
                    }
                }
                if (!chronic.trim().isEmpty()) {
                    String contra = Texts.lower(p.getContraindications());
                    for (String cond : chronic.split("[,;/\\n]+")) {
                        String c = cond.trim();
                        if (Texts.mbLen(c) >= 4 && contra.contains(c)) {
                            add.accept("danger", "BỆNH NỀN: \"" + c + "\" nằm trong chống chỉ định của " + p.getName() + ".");
                        }
                    }
                    if (chronic.contains("huyết áp") && hasAny(i, List.of("phenylephrin", "pseudoephedrin", "caffeine"))) {
                        add.accept("warning", "BỆNH NỀN: khách tăng huyết áp - " + p.getName() + " chứa chất có thể làm tăng huyết áp.");
                    }
                    if ((chronic.contains("dạ dày") || chronic.contains("loét")) && hasAny(i, NSAIDS)) {
                        add.accept("warning", "BỆNH NỀN: khách có bệnh dạ dày - " + p.getName() + " (NSAID) có thể gây loét, xuất huyết tiêu hóa.");
                    }
                    if (chronic.contains("hen") && hasAny(i, List.of("bisoprolol", "propranolol", "aspirin"))) {
                        add.accept("warning", "BỆNH NỀN: khách bị hen - thận trọng với " + p.getName() + ".");
                    }
                }
                if (customer.isPregnancy()) {
                    String contra = Texts.lower(p.getContraindications());
                    if (contra.contains("có thai") || contra.contains("mang thai")) {
                        add.accept("danger", "THAI KỲ: " + p.getName() + " chống chỉ định cho phụ nữ có thai.");
                    } else {
                        List<String> risky = new ArrayList<>(NSAIDS);
                        risky.add("losartan");
                        risky.add("diazepam");
                        if (hasAny(i, risky)) add.accept("warning", "THAI KỲ: khách đang mang thai/cho con bú - thận trọng khi dùng " + p.getName() + ".");
                    }
                }
            }
        }

        List<Product> all = new ArrayList<>(cur);
        all.addAll(hist);
        List<Product> para = all.stream().filter(p -> ing(p).contains("paracetamol")).toList();
        if (para.size() >= 2 && cur.stream().anyMatch(p -> ing(p).contains("paracetamol"))) {
            add.accept("warning", "TRÙNG HOẠT CHẤT: " + para.stream().map(Product::getName).collect(Collectors.joining(", "))
                    + " cùng chứa paracetamol - dễ quá liều (tối đa 4g/ngày), hại gan.");
        }

        for (DrugInteraction r : em.createQuery("select d from DrugInteraction d order by d.id", DrugInteraction.class).getResultList()) {
            String ra = Texts.lower(r.getIngredientA()).trim();
            String rb = Texts.lower(r.getIngredientB()).trim();
            if (ra.isEmpty() || rb.isEmpty()) continue;
            for (Product a : cur) {
                String ia = ing(a);
                if (!ia.contains(ra) && !ia.contains(rb)) continue;
                String other = ia.contains(ra) ? rb : ra;
                for (Product b : all) {
                    if (b.getId().equals(a.getId()) || !ing(b).contains(other)) continue;
                    boolean fromHistory = !curIds.contains(b.getId());
                    add.accept(r.getLevel(), "TƯƠNG TÁC: " + a.getName() + " + " + b.getName() + (fromHistory ? " (khách đã mua gần đây)" : "") + " - " + r.getMessage());
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    public List<Warning> check(User customer, Collection<Product> current) {
        return check(customer, current, List.of());
    }
}
