package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * Danh mục dữ liệu sản phẩm: hoạt chất, thương hiệu/NSX, thuốc tương đương, import/export Excel.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogService {
    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;
    private final IngredientRepository ingredientRepo;
    private final ManufacturerRepository manufacturerRepo;
    private final StockService stockService;

    /* ======================= Hoạt chất, thương hiệu ======================= */

    /** Tách thành phần "Paracetamol, Caffeine" / "A + B" thành từng hoạt chất. */
    public static List<String> splitIngredients(String s) {
        if (Texts.isBlank(s)) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : s.split("\\s*[,;+]\\s*")) if (!part.isBlank()) out.add(part.trim());
        return out;
    }

    /** Bổ sung vào danh mục các hoạt chất / thương hiệu đang dùng trong sản phẩm mà chưa có. Trả về số bản ghi thêm mới. */
    public int syncMasters() {
        int added = 0;
        Set<String> ing = new HashSet<>();
        ingredientRepo.findAll().forEach(i -> ing.add(i.getName().toLowerCase()));
        Set<String> man = new HashSet<>();
        manufacturerRepo.findAll().forEach(m -> man.add(m.getName().toLowerCase()));
        for (Product p : productRepo.findAll()) {
            for (String i : splitIngredients(p.getActiveIngredient())) {
                if (ing.add(i.toLowerCase())) {
                    ingredientRepo.save(new Ingredient(Texts.trim(i, 150)));
                    added++;
                }
            }
            if (!Texts.isBlank(p.getManufacturer()) && man.add(p.getManufacturer().toLowerCase())) {
                manufacturerRepo.save(new Manufacturer(Texts.trim(p.getManufacturer(), 150), p.getCountry()));
                added++;
            }
        }
        return added;
    }

    public long countProductsWithIngredient(String name) {
        String n = name.toLowerCase();
        return productRepo.findAll().stream()
                .filter(p -> splitIngredients(p.getActiveIngredient()).stream().anyMatch(i -> i.equalsIgnoreCase(n))).count();
    }

    /** Đổi tên hoạt chất: cập nhật luôn thành phần của các sản phẩm đang dùng tên cũ. Trả về số sản phẩm được cập nhật. */
    public int renameIngredient(String oldName, String newName) {
        if (oldName.equals(newName)) return 0;
        int n = 0;
        for (Product p : productRepo.findAll()) {
            List<String> parts = splitIngredients(p.getActiveIngredient());
            boolean hit = false;
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).equalsIgnoreCase(oldName)) {
                    parts.set(i, newName);
                    hit = true;
                }
            }
            if (hit) {
                p.setActiveIngredient(String.join(", ", parts));
                n++;
            }
        }
        return n;
    }

    public int renameManufacturer(String oldName, String newName) {
        if (oldName.equals(newName)) return 0;
        int n = 0;
        for (Product p : productRepo.findAll()) {
            if (oldName.equalsIgnoreCase(p.getManufacturer())) {
                p.setManufacturer(newName);
                n++;
            }
        }
        return n;
    }

    /* ======================= Thuốc tương đương ======================= */

    /**
     * Thuốc có thể thay thế: cùng hoạt chất (và cùng hàm lượng nếu sameStrength) hoặc được admin cấu hình tương đương.
     */
    @Transactional(readOnly = true)
    public List<Product> equivalents(Product p, boolean sameStrength) {
        LinkedHashMap<Long, Product> out = new LinkedHashMap<>();
        for (Product e : p.getEquivalents()) if (e.isActive()) out.put(e.getId(), e);
        if (p.getActiveIngredient() != null) {
            for (Product e : productRepo.findByActiveTrueAndActiveIngredientIgnoreCaseAndIdNot(p.getActiveIngredient(), p.getId())) {
                if (sameStrength && p.getStrength() != null && e.getStrength() != null && !p.getStrength().equalsIgnoreCase(e.getStrength())) continue;
                out.putIfAbsent(e.getId(), e);
            }
        }
        // Quan hệ tương đương cấu hình theo chiều ngược lại
        for (Product e : productRepo.findEquivalentOf(p)) if (e.isActive()) out.putIfAbsent(e.getId(), e);
        out.remove(p.getId());
        return new ArrayList<>(out.values());
    }

    /** Thuốc B có được phép thay thuốc A khi duyệt đơn: cùng hoạt chất + hàm lượng, hoặc admin cấu hình tương đương. */
    @Transactional(readOnly = true)
    public boolean isSubstitutable(Product a, Product b) {
        if (a.getEquivalents().stream().anyMatch(x -> x.getId().equals(b.getId()))) return true;
        if (b.getEquivalents().stream().anyMatch(x -> x.getId().equals(a.getId()))) return true;
        if (a.getActiveIngredient() == null || !a.getActiveIngredient().equalsIgnoreCase(b.getActiveIngredient())) return false;
        return a.getStrength() == null || b.getStrength() == null || a.getStrength().equalsIgnoreCase(b.getStrength());
    }

    /* ======================= Import / export Excel ======================= */

    public static final String[] COLUMNS = {"ID", "Tên sản phẩm", "Danh mục", "Hoạt chất", "Hàm lượng", "Dạng bào chế", "Quy cách",
            "Số đăng ký", "Thương hiệu / NSX", "Nước SX", "Loại (OTC/ETC/SPECIAL/SUPPLEMENT/DEVICE/COSMETIC)", "ĐVT gốc", "Giá bán",
            "Giá gốc (trước KM)", "Tối đa / đơn", "Tồn tối thiểu", "Đơn vị quy đổi (Hộp=10:120000; ...)", "Đang bán (1/0)",
            "Mô tả", "Cách dùng", "Chống chỉ định", "Tác dụng phụ", "SEO title", "SEO description", "Tồn kho hiện tại"};

    @Transactional(readOnly = true)
    public void exportProducts(OutputStream out) throws IOException {
        List<Product> products = stockService.fill(new ArrayList<>(productRepo.findAll()));
        products.sort(Comparator.comparing(Product::getId));
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

    public record ImportResult(int created, int updated, List<String> errors) {
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

    /**
     * Nhập sản phẩm từ file Excel theo đúng mẫu export. Có ID (hoặc trùng số đăng ký / tên) thì cập nhật, không thì tạo mới.
     * Toàn bộ file được kiểm tra trước; có lỗi thì không ghi gì.
     */
    public ImportResult importProducts(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("Vui lòng chọn file Excel (.xlsx).");
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase();
        if (!name.endsWith(".xlsx")) throw new BusinessException("Chỉ hỗ trợ file .xlsx (tải file mẫu bằng nút Xuất Excel).");
        List<String> errors = new ArrayList<>();
        List<Product> toSave = new ArrayList<>();
        int created = 0, updated = 0;
        Map<String, Category> cats = new HashMap<>();
        categoryRepo.findAll().forEach(c -> cats.put(c.getName().toLowerCase(), c));
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sh = wb.getSheetAt(0);
            Set<String> slugs = new HashSet<>();
            for (int r = 1; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                int line = r + 1;
                if (row == null || str(row, 1) == null) continue;
                try {
                    Long id = num(row, 0, "ID", line);
                    String regNo = str(row, 7);
                    String pname = Texts.trim(str(row, 1), 200);
                    Product p = id != null ? productRepo.findById(id).orElse(null) : null;
                    if (id != null && p == null) throw new BusinessException("Dòng " + line + ": không tìm thấy sản phẩm ID " + id + ".");
                    if (p == null && regNo != null) p = productRepo.findFirstByRegistrationNoIgnoreCase(regNo).orElse(null);
                    if (p == null) p = productRepo.findFirstByNameIgnoreCase(pname).orElse(null);
                    boolean isNew = p == null;
                    if (isNew) {
                        p = new Product();
                        String slug = Texts.slugify(pname);
                        if (productRepo.existsBySlug(slug) || !slugs.add(slug)) slug += "-" + Long.toString(System.nanoTime(), 36);
                        p.setSlug(slug);
                    }
                    String catName = str(row, 2);
                    Category cat = catName == null ? null : cats.get(catName.toLowerCase());
                    if (catName != null && cat == null) throw new BusinessException("Dòng " + line + ": danh mục \"" + catName + "\" không tồn tại.");
                    DrugType type;
                    try {
                        type = DrugType.valueOf(Objects.requireNonNullElse(str(row, 10), "OTC").toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new BusinessException("Dòng " + line + ": loại sản phẩm không hợp lệ.");
                    }
                    Long price = num(row, 12, "Giá bán", line);
                    Long oldPrice = num(row, 13, "Giá gốc", line);
                    if (price == null || price <= 0) throw new BusinessException("Dòng " + line + ": giá bán phải lớn hơn 0.");
                    if (type == DrugType.ETC && oldPrice != null && oldPrice > price) {
                        throw new BusinessException("Dòng " + line + ": không áp dụng khuyến mại (giá gốc) cho thuốc kê đơn.");
                    }
                    String unit = str(row, 11);
                    if (unit == null) throw new BusinessException("Dòng " + line + ": thiếu đơn vị tính gốc.");
                    p.setName(pname);
                    p.setCategory(cat);
                    p.setActiveIngredient(str(row, 3));
                    p.setStrength(str(row, 4));
                    p.setDosageForm(str(row, 5));
                    p.setPackaging(str(row, 6));
                    p.setRegistrationNo(regNo);
                    p.setManufacturer(str(row, 8));
                    p.setCountry(str(row, 9));
                    p.setDrugType(type);
                    p.setUnit(Texts.trim(unit, 30));
                    p.setPrice(price);
                    p.setOldPrice(oldPrice == null || oldPrice <= price ? null : oldPrice);
                    Long max = num(row, 14, "Tối đa / đơn", line);
                    p.setMaxPerOrder(max == null || max <= 0 ? null : max.intValue());
                    Long min = num(row, 15, "Tồn tối thiểu", line);
                    p.setMinStock(min == null ? 10 : (int) Math.max(0, min));
                    parseUnits(p, str(row, 16), line);
                    String act = str(row, 17);
                    p.setActive(act == null || !act.equals("0"));
                    p.setDescription(Texts.emptyToNull(Texts.trim(str(row, 18), 2000)));
                    p.setUsageInstruction(Texts.emptyToNull(Texts.trim(str(row, 19), 2000)));
                    p.setContraindications(Texts.emptyToNull(Texts.trim(str(row, 20), 1000)));
                    p.setSideEffects(Texts.emptyToNull(Texts.trim(str(row, 21), 1000)));
                    p.setMetaTitle(Texts.emptyToNull(Texts.trim(str(row, 22), 150)));
                    p.setMetaDescription(Texts.emptyToNull(Texts.trim(str(row, 23), 300)));
                    toSave.add(p);
                    if (isNew) created++;
                    else updated++;
                } catch (BusinessException e) {
                    errors.add(e.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException be) throw be;
            throw new BusinessException("Không đọc được file Excel: " + e.getMessage());
        }
        if (!errors.isEmpty()) {
            // Không lưu gì nếu có lỗi (tránh dữ liệu nửa vời); hủy thay đổi trên entity đã nạp
            throw new ImportException(errors);
        }
        productRepo.saveAll(toSave);
        syncMasters();
        return new ImportResult(created, updated, errors);
    }

    /** "Hộp=10:120000; Thùng=100:1100000" */
    private static void parseUnits(Product p, String spec, int line) {
        p.getUnits().clear();
        if (spec == null) return;
        Set<String> names = new HashSet<>();
        names.add(p.getUnit().toLowerCase());
        for (String part : spec.split(";")) {
            if (part.isBlank()) continue;
            String[] a = part.split("[=:]");
            try {
                String n = a[0].trim();
                int factor = Integer.parseInt(a[1].trim());
                long price = Long.parseLong(a[2].trim().replace(".", ""));
                if (factor < 2 || price <= 0 || !names.add(n.toLowerCase())) throw new IllegalArgumentException();
                p.getUnits().add(new ProductUnit(p, n, factor, price));
            } catch (RuntimeException e) {
                throw new BusinessException("Dòng " + line + ": đơn vị quy đổi \"" + part.trim() + "\" không hợp lệ (đúng dạng Hộp=10:120000).");
            }
        }
    }

    /** Lỗi import: danh sách lỗi theo từng dòng, rollback toàn bộ. */
    public static class ImportException extends BusinessException {
        private final List<String> errors;

        public ImportException(List<String> errors) {
            super("File có " + errors.size() + " dòng lỗi, chưa nhập dữ liệu nào: " + String.join(" | ", errors.stream().limit(5).toList())
                    + (errors.size() > 5 ? " ..." : ""));
            this.errors = errors;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
