package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.OrderRepository;
import com.hieuthuoc.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Xuất báo cáo Excel (nhiều sheet) và file dữ liệu liên thông Dược Quốc gia. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportExportService {
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReportService reportService;
    private final OrderRepository orderRepo;
    private final ReceiptRepository receiptRepo;
    private final SettingService settings;

    private static class Sheets {
        final Workbook wb;
        final CellStyle head;
        final CellStyle money;

        Sheets(Workbook wb) {
            this.wb = wb;
            this.head = CatalogService.headerStyle(wb);
            this.money = wb.createCellStyle();
            this.money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        }

        Sheet sheet(String name, String... headers) {
            Sheet sh = wb.createSheet(name);
            Row h = sh.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(head);
            }
            sh.createFreezePane(0, 1);
            return sh;
        }

        void row(Sheet sh, Object... values) {
            Row r = sh.createRow(sh.getLastRowNum() + 1);
            for (int i = 0; i < values.length; i++) {
                Cell c = r.createCell(i);
                CatalogService.setCell(c, values[i]);
                if (values[i] instanceof Long || values[i] instanceof Integer) c.setCellStyle(money);
            }
        }

        void fit(Sheet sh, int cols) {
            for (int i = 0; i < cols; i++) sh.autoSizeColumn(i);
        }
    }

    public void exportReport(LocalDate from, LocalDate to, OutputStream out) throws IOException {
        long nearDays = settings.getLong("near_expiry_days");
        ReportService.Report r = reportService.build(from, to, nearDays);
        long[] inv = reportService.inventoryValue(nearDays);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheets x = new Sheets(wb);
            Sheet s = x.sheet("Tong quan", "Chỉ tiêu", "Giá trị");
            x.row(s, "Kỳ báo cáo", from.format(D) + " - " + to.format(D));
            x.row(s, "Doanh thu (đơn hoàn thành)", r.getRevenue());
            x.row(s, "Số đơn hoàn thành", r.getOrders());
            x.row(s, "Giá vốn (theo lô xuất)", r.getCost());
            x.row(s, "Lợi nhuận gộp", r.getGrossProfit());
            x.row(s, "Tỷ suất lợi nhuận gộp (%)", r.getMarginRate());
            x.row(s, "Giá trị đơn trung bình", r.getAov());
            x.row(s, "Tổng giảm giá", r.getDiscount());
            x.row(s, "Đơn tạo mới trong kỳ", r.getCreated());
            x.row(s, "Tỷ lệ hủy (%)", r.getCancelOnlyRate());
            x.row(s, "Tỷ lệ trả hàng (%)", r.getReturnRate());
            x.row(s, "Khách mua trong kỳ", r.getCustomersOrdered());
            x.row(s, "Khách mới", r.getNewCustomers());
            x.row(s, "Khách quay lại", r.getReturningCustomers());
            x.row(s, "Tỷ lệ quay lại (%)", r.getReturningRate());
            x.row(s, "Tài khoản đăng ký mới", r.getRegistrations());
            x.row(s, "Giá trị tồn kho còn hạn", inv[0]);
            x.row(s, "Trong đó cận hạn (" + nearDays + " ngày)", inv[1]);
            x.row(s, "Hàng hết hạn chờ hủy", inv[2]);
            x.fit(s, 2);

            s = x.sheet("Theo ngay", "Ngày", "Số đơn", "Doanh thu");
            for (ReportService.DayRow d : r.getByDay()) x.row(s, d.day().format(D), d.orders(), d.revenue());
            x.fit(s, 3);
            s = x.sheet("Theo thang", "Tháng", "Doanh thu");
            for (ReportService.NameValue v : r.getByMonth()) x.row(s, v.name(), v.value());
            x.fit(s, 2);
            s = x.sheet("Theo kenh", "Kênh bán", "Số đơn", "Doanh thu");
            for (ReportService.ChannelRow c : r.getByChannel()) x.row(s, c.name(), c.orders(), c.revenue());
            x.fit(s, 3);
            s = x.sheet("Theo danh muc", "Danh mục", "Doanh thu");
            for (ReportService.NameValue v : r.getByCategory()) x.row(s, v.name(), v.value());
            x.fit(s, 2);
            s = x.sheet("Theo nhan vien", "Nhân viên", "Số đơn", "Doanh thu");
            for (ReportService.ChannelRow c : r.getByStaff()) x.row(s, c.name(), c.orders(), c.revenue());
            x.fit(s, 3);
            s = x.sheet("Ban chay", "Sản phẩm", "ĐVT", "Số lượng", "Doanh thu");
            for (ReportService.ProductRow p : r.getTopProducts()) x.row(s, p.name(), p.unit(), p.quantity(), p.revenue());
            x.fit(s, 4);
            s = x.sheet("Ton cham", "Sản phẩm", "ĐVT", "Tồn");
            for (Product p : r.getSlowProducts()) x.row(s, p.getName(), p.getUnit(), p.getOnHand());
            x.fit(s, 3);
            s = x.sheet("Can han - het han", "Tình trạng", "Sản phẩm", "Số lô", "HSD", "Số ngày", "Tồn", "Giá trị (giá nhập)");
            for (ReportService.ExpiryRow e : r.getNearExpiry()) {
                x.row(s, "Cận hạn", e.batch().getProduct().getName(), e.batch().getBatchNo(), e.batch().getExpDate().format(D), "còn " + e.days(), e.batch().getQuantity(), e.value());
            }
            for (ReportService.ExpiryRow e : r.getExpired()) {
                x.row(s, "Hết hạn", e.batch().getProduct().getName(), e.batch().getBatchNo(), e.batch().getExpDate().format(D), "quá " + e.days(), e.batch().getQuantity(), e.value());
            }
            x.fit(s, 7);
            s = x.sheet("Hieu suat duoc si", "Nhân viên", "CCHN", "Duyệt đơn thuốc", "Từ chối", "Tỷ lệ từ chối (%)", "TG duyệt TB (phút)",
                    "Cuộc tư vấn", "Tin nhắn", "Đơn xử lý", "Doanh thu");
            for (ReportService.PharmacistRow p : r.getPharmacists()) {
                x.row(s, p.name(), p.licenseNo(), p.approved(), p.rejected(), p.getRejectRate(), p.avgMinutes(), p.consultations(), p.messages(), p.orders(), p.revenue());
            }
            x.fit(s, 10);
            s = x.sheet("Don hang", "Mã đơn", "Hoàn thành", "Kênh", "Khách hàng", "Tạm tính", "Khuyến mãi", "Giảm giá", "Dùng điểm", "Phí ship", "Tổng", "Thanh toán");
            for (Order o : reportService.completedBetween(from, to)) {
                x.row(s, o.getCode(), o.getCompletedAt().format(DT), o.isPos() ? "Quầy" : "Online", o.getRecipient(), o.getSubtotal(), o.getPromoDiscountValue(),
                        o.getDiscount(), o.getPointsDiscountValue(), o.getShippingFee(), o.getTotal(), o.getPaymentMethod().getLabel());
            }
            x.fit(s, 11);
            wb.write(out);
        }
    }

    /**
     * Dữ liệu liên thông Dược Quốc gia: chi tiết bán thuốc theo lô (kèm thông tin đơn thuốc với thuốc kê đơn) và nhập hàng trong kỳ.
     * File dùng để đối chiếu / tải lên thủ công; kết nối API liên thông cần tài khoản cơ sở do Sở Y tế cấp.
     */
    public void exportNational(LocalDate from, LocalDate to, OutputStream out) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheets x = new Sheets(wb);
            Sheet info = x.sheet("Co so", "Thông tin", "Giá trị");
            x.row(info, "Tên cơ sở", settings.get("store_name"));
            x.row(info, "Địa chỉ", settings.get("store_address"));
            x.row(info, "Giấy chứng nhận ĐĐKKD dược", settings.get("business_license"));
            x.row(info, "GPP", settings.get("gpp_cert"));
            x.row(info, "Dược sĩ phụ trách chuyên môn", settings.get("pharmacist_in_charge"));
            x.row(info, "Kỳ dữ liệu", from.format(D) + " - " + to.format(D));
            x.fit(info, 2);

            Sheet s = x.sheet("Ban ra", "Ngày bán", "Số hóa đơn", "Mã thuốc (SĐK)", "Tên thuốc", "Hoạt chất", "Hàm lượng", "Loại", "Số lô", "Hạn dùng",
                    "Đơn vị", "Số lượng", "Đơn giá", "Thành tiền", "Kê đơn", "Bệnh nhân", "Bác sĩ kê đơn", "Cơ sở khám", "Ngày kê", "Dược sĩ duyệt");
            List<Order> orders = orderRepo.findCompletedWithItems(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
            for (Order o : orders) {
                Prescription rx = o.getPrescriptions().stream().filter(p -> p.getStatus() == ApprovalStatus.APPROVED).findFirst().orElse(null);
                for (OrderItem it : o.getItems()) {
                    Product p = it.getProduct();
                    boolean isRx = it.getDrugType() != null && it.getDrugType().isPrescription();
                    if (it.getAllocations().isEmpty()) {
                        x.row(s, o.getCompletedAt().format(D), o.getCode(), p.getRegistrationNo(), it.getProductName(), p.getActiveIngredient(), p.getStrength(),
                                it.getDrugType() == null ? "" : it.getDrugType().getShortLabel(), "", "", p.getUnit(), it.getBaseQuantity(), it.getPrice(), it.getLineTotal(),
                                isRx ? "Có" : "Không", isRx && rx != null ? rx.getPatientName() : null, isRx && rx != null ? rx.getDoctorName() : null,
                                isRx && rx != null ? rx.getClinic() : null, isRx && rx != null && rx.getRxDate() != null ? rx.getRxDate().format(D) : null,
                                isRx && rx != null && rx.getPharmacist() != null ? rx.getPharmacist().getFullName() : null);
                        continue;
                    }
                    for (OrderItemBatch a : it.getAllocations()) {
                        x.row(s, o.getCompletedAt().format(D), o.getCode(), p.getRegistrationNo(), it.getProductName(), p.getActiveIngredient(), p.getStrength(),
                                it.getDrugType() == null ? "" : it.getDrugType().getShortLabel(), a.getBatch().getBatchNo(), a.getBatch().getExpDate().format(D),
                                p.getUnit(), a.getQuantity(), Math.round(it.getPrice() / (double) Math.max(1, it.getFactor())),
                                Math.round(it.getPrice() / (double) Math.max(1, it.getFactor())) * a.getQuantity(),
                                isRx ? "Có" : "Không", isRx && rx != null ? rx.getPatientName() : null, isRx && rx != null ? rx.getDoctorName() : null,
                                isRx && rx != null ? rx.getClinic() : null, isRx && rx != null && rx.getRxDate() != null ? rx.getRxDate().format(D) : null,
                                isRx && rx != null && rx.getPharmacist() != null ? rx.getPharmacist().getFullName() : null);
                    }
                }
            }
            x.fit(s, 19);

            Sheet in = x.sheet("Nhap vao", "Ngày duyệt", "Số phiếu", "Nhà cung cấp", "Mã thuốc (SĐK)", "Tên thuốc", "Số lô", "NSX", "Hạn dùng", "Số lượng", "Giá nhập");
            for (Receipt r : receiptRepo.findAllByOrderByCreatedAtDescIdDesc()) {
                if (r.getStatus() != ApprovalStatus.APPROVED || r.getApprovedAt() == null) continue;
                LocalDate d = r.getApprovedAt().toLocalDate();
                if (d.isBefore(from) || d.isAfter(to)) continue;
                for (ReceiptItem it : r.getItems()) {
                    x.row(in, d.format(D), r.getCode(), r.getSupplier() == null ? null : r.getSupplier().getName(), it.getProduct().getRegistrationNo(),
                            it.getProduct().getName(), it.getBatchNo(), it.getMfgDate() == null ? null : it.getMfgDate().format(D), it.getExpDate().format(D),
                            it.getQuantity(), it.getImportPrice());
                }
            }
            x.fit(in, 10);
            wb.write(out);
        }
    }
}
