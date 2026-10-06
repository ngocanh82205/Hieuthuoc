package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.CategoryRepository;
import com.hieuthuoc.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CatalogService {
    public static final String[] COLUMNS = {"ID", "Tên sản phẩm", "Danh mục", "Hoạt chất", "Hàm lượng", "Dạng bào chế", "Quy cách",
            "Số đăng ký", "Thương hiệu / NSX", "Nước SX", "Loại (OTC/ETC/SPECIAL/SUPPLEMENT/DEVICE/COSMETIC)", "ĐVT gốc", "Giá bán",
            "Giá gốc (trước KM)", "Tối đa / đơn", "Tồn tối thiểu", "Đơn vị quy đổi (Hộp=10:120000; ...)", "Đang bán (1/0)",
            "Mô tả", "Cách dùng", "Chống chỉ định", "Tác dụng phụ", "SEO title", "SEO description", "Tồn kho hiện tại"};

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StockService stock;
    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;
    private final Sql sql;

    @PersistenceContext
    private EntityManager em;

    /** Gợi ý khi nhập: các hoạt chất đã có trong sản phẩm (không trùng, không phân biệt hoa thường). */
    @Transactional(readOnly = true)
    public List<String> ingredientSuggestions() {
        Map<String, String> uniq = new LinkedHashMap<>();
        for (String s : em.createQuery("select p.activeIngredient from Product p where p.activeIngredient is not null", String.class).getResultList()) {
            for (String i : Texts.splitIngredients(s)) uniq.putIfAbsent(i.toLowerCase(), i);
        }
        List<String> out = new ArrayList<>(uniq.values());
        Collections.sort(out);
        return out;
    }

    /** Gợi ý khi nhập: các thương hiệu / nhà sản xuất đã có trong sản phẩm. */
    @Transactional(readOnly = true)
    public List<String> manufacturerSuggestions() {
        Map<String, String> uniq = new LinkedHashMap<>();
        for (String m : em.createQuery("select p.manufacturer from Product p where p.manufacturer is not null and p.manufacturer <> ''", String.class).getResultList()) {
            uniq.putIfAbsent(m.toLowerCase(), m);
        }
        List<String> out = new ArrayList<>(uniq.values());
        Collections.sort(out);
        return out;
    }

    /** Thuốc có thể thay thế: cùng hoạt chất (và hàm lượng nếu sameStrength) hoặc admin cấu hình tương đương. */
    @Transactional(readOnly = true)
    public List<Product> equivalents(Product p, boolean sameStrength) {
        Map<Long, Product> out = new LinkedHashMap<>();
        for (Product e : p.getEquivalents()) if (e.isActive()) out.put(e.getId(), e);
        if (p.getActiveIngredient() != null && !p.getActiveIngredient().isEmpty()) {
            List<Product> same = em.createQuery("select x from Product x where x.active = true and x.id <> :id and lower(x.activeIngredient) = :ai", Product.class)
                    .setParameter("id", p.getId()).setParameter("ai", p.getActiveIngredient().toLowerCase()).getResultList();
            for (Product e : same) {
                if (sameStrength && !sameStrength(p, e)) continue;
                out.putIfAbsent(e.getId(), e);
            }
        }
        List<Product> reverse = em.createQuery("select x from Product x join x.equivalents e where x.active = true and e.id = :id", Product.class)
                .setParameter("id", p.getId()).getResultList();
        for (Product e : reverse) out.putIfAbsent(e.getId(), e);
        out.remove(p.getId());
        return new ArrayList<>(out.values());
    }

    public List<Product> equivalents(Product p) {
        return equivalents(p, false);
    }

    /**
     * Cùng hàm lượng: cả hai thuốc phải ghi hàm lượng và trùng nhau (bỏ qua hoa thường, khoảng trắng: "500 mg" = "500mg").
     * Thiếu hàm lượng thì không suy ra được là tương đương.
     */
    public static boolean sameStrength(Product a, Product b) {
        String x = normStrength(a.getStrength());
        return !x.isEmpty() && x.equals(normStrength(b.getStrength()));
    }

    private static String normStrength(String s) {
        return Texts.trim(s).toLowerCase().replaceAll("\\s+", "");
    }

    /** B có được thay A khi duyệt đơn: cùng hoạt chất + hàm lượng, hoặc admin cấu hình tương đương. */
    @Transactional(readOnly = true)
    public boolean isSubstitutable(Product a, Product b) {
        long pair = sql.scalar("select count(*) from product_equivalents where (product_id = :a and equivalent_id = :b) or (product_id = :b and equivalent_id = :a)",
                Map.of("a", a.getId(), "b", b.getId()));
        if (pair > 0) return true;
        if (Texts.isBlank(a.getActiveIngredient()) || !a.getActiveIngredient().toLowerCase().equals(Texts.lower(b.getActiveIngredient()))) return false;
        return sameStrength(a, b);
    }

    /* ======================= Import / export Excel ======================= */

    @Transactional(readOnly = true)
    public void exportProducts(OutputStream out) throws IOException {
        List<Product> products = new ArrayList<>(em.createQuery("select p from Product p order by p.id", Product.class).getResultList());
        stock.fill(products, false);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("San pham");
            CellStyle head = headerStyle(wb);
            Row h = sh.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(COLUMNS[i]);
                c.setCellStyle(head);
            }
            int r = 1;
            for (Product p : products) {
                Row row = sh.createRow(r++);
                StringJoiner units = new StringJoiner("; ");
                for (ProductUnit u : p.getUnits()) units.add(u.getName() + "=" + u.getFactor() + ":" + u.getPrice());
                Object[] v = {p.getId(), p.getName(), p.getCategory() == null ? null : p.getCategory().getName(), p.getActiveIngredient(),
                        p.getStrength(), p.getDosageForm(), p.getPackaging(), p.getRegistrationNo(), p.getManufacturer(), p.getCountry(),
                        p.getDrugType().name(), p.getUnit(), p.getPrice(), p.getOldPrice(), p.getMaxPerOrder(), p.getMinStock(),
                        units.toString(), p.isActive() ? 1 : 0, p.getDescription(), p.getUsageInstruction(), p.getContraindications(),
                        p.getSideEffects(), p.getMetaTitle(), p.getMetaDescription(), p.getOnHand()};
                for (int i = 0; i < v.length; i++) setCell(row.createCell(i), v[i]);
            }
            for (int i = 0; i < 12; i++) sh.autoSizeColumn(i);
            sh.createFreezePane(2, 1);
            wb.write(out);
        }
    }

    public static CellStyle headerStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        st.setFont(f);
        st.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return st;
    }

    public static void setCell(Cell c, Object v) {
        if (v == null) return;
        if (v instanceof Number n) c.setCellValue(n.doubleValue());
        else c.setCellValue(String.valueOf(v));
    }

    public record ImportResult(int created, int updated) {
    }

    private static String str(Row row, int i) {
        Cell c = row.getCell(i);
        if (c == null) return null;
        String s = switch (c.getCellType()) {
            case NUMERIC -> {
                double d = c.getNumericCellValue();
                yield d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> c.getBooleanCellValue() ? "1" : "0";
            case FORMULA -> c.getCellFormula();
            default -> c.getStringCellValue();
        };
        s = s == null ? null : s.trim();
        return s == null || s.isEmpty() ? null : s;
    }

    private static Long num(Row row, int i, String label, int line) {
        String s = str(row, i);
        if (s == null) return null;
        try {
            return Math.round(Double.parseDouble(s.replace(".", "").replace(",", "")));
        } catch (NumberFormatException e) {
            throw new BusinessException("Dòng " + line + ": \"" + label + "\" phải là số.");
        }
    }

    private record Plan(Product product, boolean isNew, List<ProductUnit> units) {
    }

    /**
     * Nhập sản phẩm từ Excel theo mẫu export. Có ID (hoặc trùng SĐK / tên) thì cập nhật, không thì tạo mới.
     * Kiểm tra toàn bộ file trước; có lỗi thì không ghi gì.
     */
    public ImportResult importProducts(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("Vui lòng chọn file Excel (.xlsx).");
        String fname = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase();
        if (!fname.endsWith(".xlsx")) throw new BusinessException("Chỉ hỗ trợ file .xlsx (tải file mẫu bằng nút Xuất Excel).");
        Map<String, Category> cats = new HashMap<>();
        categoryRepo.findAll().forEach(c -> cats.put(c.getName().toLowerCase(), c));
        List<String> errors = new ArrayList<>();
        List<Plan> plans = new ArrayList<>();
        Set<String> slugs = new HashSet<>();
        Sheet sh;
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            sh = wb.getSheetAt(0);
            for (int r = 1; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                int line = r + 1;
                if (row == null || str(row, 1) == null) continue;
                try {
                    Long id = num(row, 0, "ID", line);
                    String name = Texts.trim(str(row, 1), 200);
                    String reg = str(row, 7);
                    Product p = id != null ? productRepo.findById(id).orElse(null) : null;
                    if (id != null && p == null) throw new BusinessException("Dòng " + line + ": không tìm thấy sản phẩm ID " + id + ".");
                    if (p == null && reg != null) p = first("select p from Product p where lower(p.registrationNo) = :v", reg.toLowerCase());
                    if (p == null) p = first("select p from Product p where lower(p.name) = :v", name.toLowerCase());
                    boolean isNew = p == null;
                    String catName = str(row, 2);
                    Category cat = catName != null ? cats.get(catName.toLowerCase()) : null;
                    if (catName != null && cat == null) throw new BusinessException("Dòng " + line + ": danh mục \"" + catName + "\" không tồn tại.");
                    DrugType type = DrugType.tryFrom(Objects.requireNonNullElse(str(row, 10), "OTC").toUpperCase());
                    if (type == null) throw new BusinessException("Dòng " + line + ": loại sản phẩm không hợp lệ.");
                    Long price = num(row, 12, "Giá bán", line);
                    Long old = num(row, 13, "Giá gốc", line);
                    if (price == null || price <= 0) throw new BusinessException("Dòng " + line + ": giá bán phải lớn hơn 0.");
                    if (!type.isPromotable() && old != null && old > price) {
                        throw new BusinessException("Dòng " + line + ": không áp dụng khuyến mại (giá gốc) cho thuốc kê đơn.");
                    }
                    String unit = str(row, 11);
                    if (unit == null) throw new BusinessException("Dòng " + line + ": thiếu đơn vị tính gốc.");
                    List<ProductUnit> units = parseUnits(str(row, 16), unit, String.valueOf(line));
                    if (isNew) {
                        p = new Product();
                        String slug = Texts.slugify(name);
                        if (productRepo.existsBySlug(slug) || slugs.contains(slug)) slug += "-" + randomLower(5);
                        slugs.add(slug);
                        p.setSlug(slug);
                    }
                    Long max = num(row, 14, "Tối đa / đơn", line);
                    Long min = num(row, 15, "Tồn tối thiểu", line);
                    Product target = isNew ? p : new Product();
                    target.setName(name);
                    target.setCategory(cat);
                    target.setActiveIngredient(str(row, 3));
                    target.setStrength(str(row, 4));
                    target.setDosageForm(str(row, 5));
                    target.setPackaging(str(row, 6));
                    target.setRegistrationNo(reg);
                    target.setManufacturer(str(row, 8));
                    target.setCountry(str(row, 9));
                    target.setDrugType(type);
                    target.setUnit(Texts.trim(unit, 30));
                    target.setPrice(price);
                    target.setOldPrice(old != null && old > price ? old : null);
                    target.setMaxPerOrder(max != null && max > 0 ? max.intValue() : null);
                    target.setMinStock(min == null ? 10 : (int) Math.max(0, min));
                    target.setActive(!"0".equals(str(row, 17)));
                    target.setDescription(str(row, 18));
                    target.setUsageInstruction(str(row, 19));
                    target.setContraindications(str(row, 20));
                    target.setSideEffects(str(row, 21));
                    target.setMetaTitle(str(row, 22) != null ? Texts.trim(str(row, 22), 150) : null);
                    target.setMetaDescription(str(row, 23) != null ? Texts.trim(str(row, 23), 300) : null);
                    if (!isNew) {
                        // Chưa ghi vào sản phẩm thật cho tới khi cả file hợp lệ
                        target.setId(p.getId());
                        plans.add(new Plan(target, false, units));
                    } else {
                        plans.add(new Plan(target, true, units));
                    }
                } catch (BusinessException e) {
                    errors.add(e.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException be) throw be;
            throw new BusinessException("Không đọc được file Excel: " + e.getMessage());
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("File có " + errors.size() + " dòng lỗi, chưa nhập dữ liệu nào: "
                    + String.join(" | ", errors.subList(0, Math.min(5, errors.size()))) + (errors.size() > 5 ? " ..." : ""));
        }
        int created = 0, updated = 0;
        for (Plan plan : plans) {
            Product p;
            Product d = plan.product();
            if (plan.isNew()) {
                p = d;
                em.persist(p);
                created++;
            } else {
                p = productRepo.findById(d.getId()).orElseThrow();
                p.setName(d.getName());
                p.setCategory(d.getCategory());
                p.setActiveIngredient(d.getActiveIngredient());
                p.setStrength(d.getStrength());
                p.setDosageForm(d.getDosageForm());
                p.setPackaging(d.getPackaging());
                p.setRegistrationNo(d.getRegistrationNo());
                p.setManufacturer(d.getManufacturer());
                p.setCountry(d.getCountry());
                p.setDrugType(d.getDrugType());
                p.setUnit(d.getUnit());
                p.setPrice(d.getPrice());
                p.setOldPrice(d.getOldPrice());
                p.setMaxPerOrder(d.getMaxPerOrder());
                p.setMinStock(d.getMinStock());
                p.setActive(d.isActive());
                p.setDescription(d.getDescription());
                p.setUsageInstruction(d.getUsageInstruction());
                p.setContraindications(d.getContraindications());
                p.setSideEffects(d.getSideEffects());
                p.setMetaTitle(d.getMetaTitle());
                p.setMetaDescription(d.getMetaDescription());
                updated++;
            }
            p.getUnits().clear();
            em.flush();
            for (ProductUnit u : plan.units()) {
                u.setProduct(p);
                p.getUnits().add(u);
            }
        }
        return new ImportResult(created, updated);
    }

    private Product first(String jpql, String v) {
        List<Product> l = em.createQuery(jpql, Product.class).setParameter("v", v).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    /** "Hộp=10:120000; Thùng=100:1100000" */
    public List<ProductUnit> parseUnits(String spec, String baseUnit, String line) {
        List<ProductUnit> out = new ArrayList<>();
        if (spec == null || spec.isBlank()) return out;
        Set<String> names = new HashSet<>();
        names.add(baseUnit.toLowerCase());
        for (String part : spec.split(";")) {
            if (part.trim().isEmpty()) continue;
            String[] a = part.split("[=:]");
            String name = a.length > 0 ? a[0].trim() : "";
            int factor = a.length > 1 ? Texts.toInt(a[1].trim(), 0) : 0;
            long price = a.length > 2 ? Objects.requireNonNullElse(Texts.toLong(a[2].trim().replace(".", "")), 0L) : 0;
            if (name.isEmpty() || factor < 2 || price <= 0 || names.contains(name.toLowerCase())) {
                throw new BusinessException((line != null && !line.isEmpty() ? "Dòng " + line + ": " : "")
                        + "đơn vị quy đổi \"" + part.trim() + "\" không hợp lệ (đúng dạng Hộp=10:120000).");
            }
            names.add(name.toLowerCase());
            ProductUnit u = new ProductUnit();
            u.setName(name);
            u.setFactor(factor);
            u.setPrice(price);
            out.add(u);
        }
        return out;
    }

    static String randomLower(int n) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }
}
