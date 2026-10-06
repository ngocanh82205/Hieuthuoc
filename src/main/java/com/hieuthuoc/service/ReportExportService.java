package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Xuất báo cáo Excel nhiều sheet, file dữ liệu liên thông Dược Quốc gia và biên bản kiểm kê. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportExportService {
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReportService reports;
    private final SettingService settings;

    @PersistenceContext
    private EntityManager em;

    /** Bảng tính: sheet có dòng tiêu đề tô màu, các ô số định dạng #,##0. */
    private static final class Book {
        final Workbook wb = new XSSFWorkbook();
        final CellStyle head;
        final CellStyle number;

        Book() {
            head = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            f.setColor(IndexedColors.WHITE.getIndex());
            head.setFont(f);
            head.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            number = wb.createCellStyle();
            number.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        }

        Sheet sheet(String title, List<String> headers, List<List<Object>> rows) {
            Sheet sh = wb.createSheet(title.length() > 31 ? title.substring(0, 31) : title);
            Row h = sh.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(head);
            }
            sh.createFreezePane(0, 1);
            int r = 1;
            for (List<Object> row : rows) {
                Row x = sh.createRow(r++);
                for (int i = 0; i < row.size(); i++) set(x.createCell(i), row.get(i));
            }
            for (int i = 0; i < headers.size(); i++) sh.autoSizeColumn(i);
            return sh;
        }

        void set(Cell c, Object v) {
            if (v == null) return;
            if (v instanceof Number n) {
                c.setCellValue(n.doubleValue());
                c.setCellStyle(number);
            } else {
                c.setCellValue(v.toString());
            }
        }

        void write(OutputStream out) throws IOException {
            wb.write(out);
            wb.close();
        }
    }

    private static List<Object> row(Object... v) {
        return new ArrayList<>(Arrays.asList(v));
    }

    /** Một sheet dạng bảng đơn giản (danh sách giao dịch, danh mục sản phẩm...). */
    public void table(String title, List<String> headers, List<List<Object>> rows, OutputStream out) throws IOException {
        Book b = new Book();
        b.sheet(title, headers, rows);
        b.write(out);
    }

    public void report(LocalDate from, LocalDate to, OutputStream out) throws IOException {
        int near = settings.getInt("near_expiry_days");
        ReportService.Report r = reports.build(from, to, near);
        long[] inv = reports.inventoryValue(near);
        Book b = new Book();
        b.sheet("Tong quan", List.of("Chỉ tiêu", "Giá trị"), List.of(
                row("Kỳ báo cáo", from.format(D) + " - " + to.format(D)), row("Doanh số tiền hàng (đơn hoàn thành)", r.getGrossSales()),
                row("Hàng bán bị trả / hủy hóa đơn trong kỳ", r.getReturnsValue()), row("Doanh thu thuần", r.getRevenue()),
                row("Phí giao hàng thu của khách (không tính doanh thu)", r.getShipping()), row("Số đơn hoàn thành", r.getOrders()),
                row("Giá vốn (theo lô xuất)", r.getCost()), row("Lợi nhuận gộp", r.getGrossProfit()), row("Tỷ suất lợi nhuận gộp (%)", r.getMarginRate()),
                row("Giá trị đơn trung bình", r.getAov()), row("Tổng giảm giá", r.getDiscount()), row("Đơn tạo mới trong kỳ", r.getCreated()),
                row("Tỷ lệ hủy (%)", r.getCancelOnlyRate()), row("Tỷ lệ trả hàng (%)", r.getReturnRate()), row("Khách mua trong kỳ", r.getCustomersOrdered()),
                row("Khách mới", r.getNewCustomers()), row("Khách quay lại", r.getReturningCustomers()), row("Tỷ lệ quay lại (%)", r.getReturningRate()),
                row("Tài khoản đăng ký mới", r.getRegistrations()), row("Giá trị tồn kho còn hạn", inv[0]),
                row("Trong đó cận hạn (" + near + " ngày)", inv[1]), row("Hàng hết hạn chờ hủy", inv[2])));
        b.sheet("Theo ngay", List.of("Ngày", "Số đơn", "Doanh thu"), r.getByDay().entrySet().stream()
                .map(e -> row(LocalDate.parse(e.getKey()).format(D), e.getValue()[0], e.getValue()[1])).toList());
        b.sheet("Theo thang", List.of("Tháng", "Doanh thu"), r.getByMonth().entrySet().stream().map(e -> row(e.getKey(), e.getValue())).toList());
        b.sheet("Theo nam", List.of("Năm", "Doanh thu"), r.getByYear().entrySet().stream().map(e -> row(e.getKey(), e.getValue())).toList());
        b.sheet("Theo kenh", List.of("Kênh bán", "Số đơn", "Doanh thu"), pairs(r.getByChannel()));
        b.sheet("Theo thanh toan", List.of("Phương thức", "Số đơn", "Doanh thu"), pairs(r.getByPayment()));
        b.sheet("Theo danh muc", List.of("Danh mục", "Doanh thu"), r.getByCategory().entrySet().stream().map(e -> row(e.getKey(), e.getValue())).toList());
        b.sheet("Theo nhan vien", List.of("Nhân viên", "Số đơn", "Doanh thu"), pairs(r.getByStaff()));
        b.sheet("Ban chay", List.of("Sản phẩm", "ĐVT", "Số lượng", "Doanh thu"), r.getTopProducts().stream()
                .map(p -> row(p.name(), p.unit(), p.quantity(), p.revenue())).toList());
        b.sheet("Ton cham", List.of("Sản phẩm", "ĐVT", "Tồn"), r.getSlowProducts().stream().map(p -> row(p.getName(), p.getUnit(), p.getOnHand())).toList());
        List<List<Object>> exp = new ArrayList<>();
        for (ReportService.ExpiryRow e : r.getNearExpiry()) {
            exp.add(row("Cận hạn", e.batch().getProduct().getName(), e.batch().getBatchNo(), e.batch().getExpDate().format(D), "còn " + e.days(),
                    e.batch().getQuantity(), e.value()));
        }
        for (ReportService.ExpiryRow e : r.getExpired()) {
            exp.add(row("Hết hạn", e.batch().getProduct().getName(), e.batch().getBatchNo(), e.batch().getExpDate().format(D), "quá " + e.days(),
                    e.batch().getQuantity(), e.value()));
        }
        b.sheet("Can han - het han", List.of("Tình trạng", "Sản phẩm", "Số lô", "HSD", "Số ngày", "Tồn", "Giá trị (giá nhập)"), exp);
        b.sheet("Hieu suat duoc si", List.of("Nhân viên", "CCHN", "Duyệt đơn thuốc", "Từ chối", "Tỷ lệ từ chối (%)", "TG duyệt TB (phút)", "Cuộc tư vấn",
                "Tin nhắn", "Đơn xử lý", "Doanh thu"), r.getPharmacists().stream().map(p -> row(p.name(), p.license(), p.approved(), p.rejected(),
                p.rejectRate(), p.avgMinutes(), p.consultations(), p.messages(), p.orders(), p.revenue())).toList());
        List<Order> sales = new ArrayList<>(reports.salesBetween(from, to));
        sales.sort(Comparator.comparing(Order::getCompletedAt));
        b.sheet("Don hang", List.of("Mã đơn", "Hoàn thành", "Kênh", "Khách hàng", "Tạm tính", "Khuyến mãi", "Giảm giá", "Dùng điểm", "Phí ship", "Tổng",
                "Thanh toán", "Giá vốn", "Trạng thái hiện tại"), sales.stream().map(o -> row(o.getCode(), o.getCompletedAt().format(DT), o.isPos() ? "Quầy" : "Online",
                o.getRecipient(), o.getSubtotal(), o.getPromoDiscount(), o.getDiscount(), o.getPointsDiscount(), o.getShippingFee(), o.getTotal(),
                o.getPaymentMethod().getShortLabel(), o.getCostAmount(),
                o.getStatus().getLabel() + (o.getReturnedAt() != null ? " (" + o.getReturnedAt().format(D) + ")" : ""))).toList());
        List<Order> returns = new ArrayList<>(reports.returnsBetween(from, to));
        returns.sort(Comparator.comparing(Order::getReturnedAt));
        b.sheet("Tra hang", List.of("Mã đơn", "Ngày trả / hủy", "Hoàn thành", "Kênh", "Tiền hàng ghi giảm", "Giá vốn nhập lại kho"),
                returns.stream().map(o -> row(o.getCode(), o.getReturnedAt().format(DT), o.getCompletedAt() != null ? o.getCompletedAt().format(D) : null,
                        o.isPos() ? "Quầy" : "Online", ReportService.goodsValue(o), o.getReturnCost())).toList());
        b.wb.setActiveSheet(0);
        b.write(out);
    }

    private static List<List<Object>> pairs(Map<String, long[]> m) {
        return m.entrySet().stream().map(e -> row(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
    }

    /** File dữ liệu liên thông Dược Quốc gia: bán ra theo lô (kèm thông tin đơn thuốc) và nhập vào. */
    public void national(LocalDate from, LocalDate to, OutputStream out) throws IOException {
        Book b = new Book();
        b.sheet("Co so", List.of("Thông tin", "Giá trị"), List.of(
                row("Tên cơ sở", settings.get("store_name")), row("Địa chỉ", settings.get("store_address")),
                row("Giấy chứng nhận ĐĐKKD dược", settings.get("business_license")), row("GPP", settings.get("gpp_cert")),
                row("Dược sĩ phụ trách chuyên môn", settings.get("pharmacist_in_charge")), row("Kỳ dữ liệu", from.format(D) + " - " + to.format(D))));
        List<List<Object>> rows = new ArrayList<>();
        List<Order> orders = new ArrayList<>(reports.completedBetween(from, to));
        orders.sort(Comparator.comparing(Order::getCompletedAt));
        for (Order o : orders) {
            Prescription rx = o.getPrescriptions().stream().filter(p -> p.getStatus() == ApprovalStatus.APPROVED).findFirst().orElse(null);
            for (OrderItem it : o.getItems()) {
                Product p = it.getProduct();
                boolean isRx = it.getDrugType() != null && it.getDrugType().isPrescription();
                List<Object> base = row(o.getCompletedAt().format(D), o.getCode(), p.getRegistrationNo(), it.getProductName(), p.getActiveIngredient(),
                        p.getStrength(), it.getDrugType() != null ? it.getDrugType().getShortLabel() : null);
                List<Object> tail = row(isRx ? "Có" : "Không", isRx && rx != null ? rx.getPatientName() : null, isRx && rx != null ? rx.getDoctorName() : null,
                        isRx && rx != null ? rx.getClinic() : null, isRx && rx != null && rx.getRxDate() != null ? rx.getRxDate().format(D) : null,
                        isRx && rx != null && rx.getPharmacist() != null ? rx.getPharmacist().getFullName() : null);
                if (it.getAllocations().isEmpty()) {
                    List<Object> line = new ArrayList<>(base);
                    line.addAll(row("", "", p.getUnit(), it.baseQuantity(), it.getPrice(), it.lineTotal()));
                    line.addAll(tail);
                    rows.add(line);
                }
                for (OrderItemBatch a : it.getAllocations()) {
                    long unitPrice = Math.round((double) it.getPrice() / it.factor());
                    List<Object> line = new ArrayList<>(base);
                    line.addAll(row(a.getBatch().getBatchNo(), a.getBatch().getExpDate().format(D), p.getUnit(), a.getQuantity(), unitPrice, unitPrice * a.getQuantity()));
                    line.addAll(tail);
                    rows.add(line);
                }
            }
        }
        b.sheet("Ban ra", List.of("Ngày bán", "Số hóa đơn", "Mã thuốc (SĐK)", "Tên thuốc", "Hoạt chất", "Hàm lượng", "Loại", "Số lô", "Hạn dùng",
                "Đơn vị", "Số lượng", "Đơn giá", "Thành tiền", "Kê đơn", "Bệnh nhân", "Bác sĩ kê đơn", "Cơ sở khám", "Ngày kê", "Dược sĩ duyệt"), rows);
        List<List<Object>> in = new ArrayList<>();
        for (Receipt r : em.createQuery("select r from Receipt r where r.status = :s and r.approvedAt between :a and :b order by r.approvedAt", Receipt.class)
                .setParameter("s", ApprovalStatus.APPROVED).setParameter("a", from.atStartOfDay()).setParameter("b", to.atTime(23, 59, 59)).getResultList()) {
            for (ReceiptItem it : r.getItems()) {
                in.add(row(r.getApprovedAt().format(D), r.getCode(), r.getSupplier() != null ? r.getSupplier().getName() : null,
                        it.getProduct().getRegistrationNo(), it.getProduct().getName(), it.getBatchNo(), it.getMfgDate() != null ? it.getMfgDate().format(D) : null,
                        it.getExpDate().format(D), it.getQuantity(), it.getImportPrice()));
            }
        }
        b.sheet("Nhap vao", List.of("Ngày duyệt", "Số phiếu", "Nhà cung cấp", "Mã thuốc (SĐK)", "Tên thuốc", "Số lô", "NSX", "Hạn dùng", "Số lượng", "Giá nhập"), in);
        b.wb.setActiveSheet(0);
        b.write(out);
    }

    /** Biên bản kiểm kê (1 sheet): thông tin đợt kiểm, bảng chi tiết từng lô, dòng tổng, chỗ ký. */
    public void stocktake(Stocktake st, OutputStream out) throws IOException {
        Book b = new Book();
        Sheet sh = b.wb.createSheet("Kiem ke " + st.getCode());
        Map<String, String> s = settings.all();
        CellStyle bold = b.wb.createCellStyle();
        Font bf = b.wb.createFont();
        bf.setBold(true);
        bold.setFont(bf);
        CellStyle title = b.wb.createCellStyle();
        Font tf = b.wb.createFont();
        tf.setBold(true);
        tf.setFontHeightInPoints((short) 14);
        title.setFont(tf);
        title.setAlignment(HorizontalAlignment.CENTER);
        CellStyle diffRow = b.wb.createCellStyle();
        diffRow.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        diffRow.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        put(sh, 0, 0, s.get("store_name"), bold);
        put(sh, 1, 0, s.get("store_address"), null);
        put(sh, 0, 9, "Số: " + st.getCode(), null);
        sh.addMergedRegion(new CellRangeAddress(3, 3, 0, 9));
        put(sh, 3, 0, "BIÊN BẢN KIỂM KÊ THUỐC", title);
        List<String[]> info = new ArrayList<>();
        info.add(new String[]{"Thời điểm kiểm kê:", st.getCreatedAt() != null ? st.getCreatedAt().format(DT) : ""});
        info.add(new String[]{"Người kiểm kê:", st.getUser() != null ? st.getUser().getFullName() : "-"});
        info.add(new String[]{"Kết quả:", st.getTotalLines() + " lô đã kiểm, " + (st.getDiffLines() > 0 ? st.getDiffLines() + " lô chênh lệch" : "tất cả khớp sổ sách")});
        if (st.getNote() != null) info.add(new String[]{"Ghi chú:", st.getNote()});
        int r = 5;
        for (String[] kv : info) {
            put(sh, r, 0, kv[0], bold);
            put(sh, r, 2, kv[1], null);
            r++;
        }
        int head = ++r;
        String[] cols = {"STT", "Tên thuốc", "ĐVT", "Số lô", "HSD", "Sổ sách", "Chờ giao", "Thực tế", "Chênh lệch", "Xử lý"};
        for (int i = 0; i < cols.length; i++) put(sh, head, i, cols[i], b.head);
        int n = 0;
        long book = 0, pending = 0, counted = 0, diff = 0;
        for (StocktakeItem it : st.getItems()) {
            r++;
            int d = it.diff();
            StockAdjustment a = it.getAdjustment();
            String handled = a != null ? a.getStatus().getLabel() + (a.getRejectReason() != null ? ": " + a.getRejectReason() : "") : (d == 0 ? "Khớp" : "");
            Object[] v = {++n, it.getProductName(), it.getUnit(), it.getBatchNo(), it.getExpDate() != null ? it.getExpDate().format(D) : null,
                    it.getBookQty(), it.getPendingOutQty(), it.getCountedQty(), d, handled};
            Row x = sh.createRow(r);
            for (int i = 0; i < v.length; i++) {
                Cell c = x.createCell(i);
                b.set(c, v[i]);
                if (d != 0) c.setCellStyle(diffRow);
            }
            book += it.getBookQty();
            pending += it.getPendingOutQty();
            counted += it.getCountedQty();
            diff += d;
        }
        r++;
        sh.addMergedRegion(new CellRangeAddress(r, r, 0, 4));
        put(sh, r, 0, "Tổng", bold);
        Row total = sh.getRow(r);
        long[] sums = {book, pending, counted, diff};
        for (int i = 0; i < 4; i++) b.set(total.createCell(5 + i), sums[i]);
        r += 2;
        put(sh, r, 0, "Lô chênh lệch được xử lý bằng phiếu điều chỉnh kiểm kê; tồn kho chỉ thay đổi khi phiếu được duyệt.", null);
        r += 2;
        put(sh, r, 1, "Người kiểm kê", bold);
        put(sh, r, 6, "Dược sĩ phụ trách chuyên môn", bold);
        put(sh, r + 1, 1, "(Ký, ghi rõ họ tên)", null);
        put(sh, r + 1, 6, "(Ký, ghi rõ họ tên)", null);
        put(sh, r + 5, 1, st.getUser() != null ? st.getUser().getFullName() : "", null);
        put(sh, r + 5, 6, s.getOrDefault("pharmacist_in_charge", ""), null);
        int[] widths = {6, 34, 8, 14, 12, 10, 10, 10, 11, 22};
        for (int i = 0; i < widths.length; i++) sh.setColumnWidth(i, widths[i] * 256);
        b.write(out);
    }

    private static void put(Sheet sh, int r, int c, String v, CellStyle style) {
        Row row = sh.getRow(r) != null ? sh.getRow(r) : sh.createRow(r);
        Cell cell = row.createCell(c);
        cell.setCellValue(v == null ? "" : v);
        if (style != null) cell.setCellStyle(style);
    }
}
