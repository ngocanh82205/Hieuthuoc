package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.PayrollLineRepository;
import com.hieuthuoc.repository.PayrollRepository;
import com.hieuthuoc.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Tính lương theo tháng từ dữ liệu chấm công.
 * <ul>
 *   <li>Lương tháng: lương cơ bản x ngày công / ngày công chuẩn.</li>
 *   <li>Lương giờ: số giờ làm thực tế x đơn giá giờ.</li>
 *   <li>Cộng: phụ cấp ca, phụ cấp cố định, thưởng. Trừ: phạt đi muộn, bảo hiểm (lương tháng), khấu trừ khác.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PayrollService {
    private final PayrollRepository payrollRepo;
    private final PayrollLineRepository lineRepo;
    private final ShiftAssignmentRepository assignmentRepo;
    private final WorkScheduleService scheduleService;
    private final SettingService settings;
    private final NotificationService notifications;

    public Payroll get(Long id) {
        return payrollRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy bảng lương."));
    }

    /** Tạo (hoặc tính lại) bảng lương nháp của tháng. Giữ nguyên thưởng / khấu trừ / ghi chú đã nhập. */
    public Payroll generate(YearMonth month, User admin) {
        if (month.isAfter(YearMonth.now())) throw new BusinessException("Không tính lương cho tháng chưa tới.");
        Payroll p = payrollRepo.findByMonth(month.toString()).orElse(null);
        if (p != null && !Payroll.DRAFT.equals(p.getStatus())) throw new BusinessException("Bảng lương tháng " + p.getMonthLabel() + " đã chốt, không tính lại được.");
        Map<Long, PayrollLine> old = new HashMap<>();
        if (p == null) {
            p = new Payroll();
            p.setMonth(month.toString());
        } else {
            for (PayrollLine l : p.getLines()) old.put(l.getUser().getId(), l);
            p.getLines().clear();
            payrollRepo.saveAndFlush(p);
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<Long, List<ShiftAssignment>> byUser = new LinkedHashMap<>();
        Map<Long, User> users = new LinkedHashMap<>();
        for (User u : scheduleService.staff()) {
            users.put(u.getId(), u);
            byUser.put(u.getId(), new ArrayList<>());
        }
        for (ShiftAssignment a : assignmentRepo.findBetween(from, to)) {
            users.putIfAbsent(a.getUser().getId(), a.getUser());
            byUser.computeIfAbsent(a.getUser().getId(), k -> new ArrayList<>()).add(a);
        }
        for (Map.Entry<Long, User> e : users.entrySet()) {
            PayrollLine l = calculate(e.getValue(), byUser.get(e.getKey()));
            PayrollLine prev = old.get(e.getKey());
            if (prev != null) {
                l.setBonus(prev.getBonus());
                l.setOtherDeduction(prev.getOtherDeduction());
                l.setNote(prev.getNote());
                l.recomputeTotal();
            }
            if (l.getScheduledShifts() == 0 && l.getBonus() == 0) continue; // không có ca nào trong tháng
            l.setPayroll(p);
            p.getLines().add(l);
        }
        p.setCalculatedAt(LocalDateTime.now());
        payrollRepo.save(p);
        notifications.log(admin, "hr.payroll_calc", "Bảng lương " + p.getMonthLabel() + ": " + p.getLines().size() + " nhân viên, " + p.getTotal() + " đ");
        return p;
    }

    /** Tính một dòng lương từ các ca trong tháng (chỉ tính ca đã diễn ra). */
    public PayrollLine calculate(User u, List<ShiftAssignment> shifts) {
        LocalDateTime now = LocalDateTime.now();
        long grace = settings.getLong("late_grace_minutes");
        PayrollLine l = new PayrollLine();
        l.setUser(u);
        l.setSalaryType(u.isHourly() ? "HOURLY" : "MONTHLY");
        l.setRate(u.isHourly() ? Objects.requireNonNullElse(u.getHourlyRate(), 0L) : Objects.requireNonNullElse(u.getBaseSalary(), 0L));
        Set<LocalDate> days = new HashSet<>();
        for (ShiftAssignment a : shifts) {
            if (a.getStartAt().isAfter(now)) continue;
            l.setScheduledShifts(l.getScheduledShifts() + 1);
            if (a.isCompleted()) {
                l.setShiftsWorked(l.getShiftsWorked() + 1);
                l.setWorkedMinutes(l.getWorkedMinutes() + a.getPaidMinutes());
                days.add(a.getWorkDate());
                l.setShiftAllowance(l.getShiftAllowance() + a.getShift().getAllowance());
                if (a.getLateMinutes() > grace) l.setLateCount(l.getLateCount() + 1);
            } else if (a.getCheckInAt() != null) {
                if (a.getEndAt().isBefore(now)) l.setMissingCheckout(l.getMissingCheckout() + 1);
            } else if (a.getEndAt().isBefore(now)) {
                l.setAbsentShifts(l.getAbsentShifts() + 1);
            }
        }
        l.setDaysWorked(days.size());
        long standardDays = Math.max(1, settings.getLong("payroll_standard_days"));
        if (u.isHourly()) {
            l.setBaseAmount(round(l.getRate() * l.getWorkedMinutes() / 60.0));
        } else {
            l.setBaseAmount(round(l.getRate() * (double) l.getDaysWorked() / standardDays));
            if (l.getDaysWorked() > 0) l.setInsurance(round(l.getRate() * settings.getLong("insurance_permille") / 1000.0));
        }
        l.setFixedAllowance(l.getDaysWorked() > 0 ? Objects.requireNonNullElse(u.getAllowance(), 0L) : 0);
        l.setLatePenalty(l.getLateCount() * settings.getLong("late_penalty"));
        l.recomputeTotal();
        return l;
    }

    private static long round(double v) {
        return Math.round(v / 100.0) * 100;
    }

    public PayrollLine updateLine(Long lineId, long bonus, long deduction, String note, User admin) {
        PayrollLine l = lineRepo.findById(lineId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy dòng lương."));
        if (!Payroll.DRAFT.equals(l.getPayroll().getStatus())) throw new BusinessException("Bảng lương đã chốt, không sửa được.");
        if (bonus < 0 || deduction < 0) throw new BusinessException("Thưởng / khấu trừ không được âm.");
        l.setBonus(bonus);
        l.setOtherDeduction(deduction);
        l.setNote(Texts.emptyToNull(Texts.trim(note, 300)));
        l.recomputeTotal();
        notifications.log(admin, "hr.payroll_edit", l.getUser().getFullName() + " " + l.getPayroll().getMonthLabel() + ": thưởng " + bonus + ", khấu trừ " + deduction);
        return l;
    }

    public Payroll approve(Long id, User admin) {
        Payroll p = get(id);
        if (!Payroll.DRAFT.equals(p.getStatus())) throw new BusinessException("Bảng lương không ở trạng thái nháp.");
        if (p.getLines().isEmpty()) throw new BusinessException("Bảng lương chưa có nhân viên nào.");
        p.setStatus(Payroll.APPROVED);
        p.setApprovedBy(admin);
        p.setApprovedAt(LocalDateTime.now());
        for (PayrollLine l : p.getLines()) {
            notifications.notify(l.getUser(), "Phiếu lương tháng " + p.getMonthLabel() + " đã có: thực lĩnh "
                    + String.format("%,d", l.getTotal()).replace(',', '.') + " đ", "/staff/my-payslips");
        }
        notifications.log(admin, "hr.payroll_approve", p.getMonthLabel() + ": " + p.getTotal() + " đ");
        return p;
    }

    public Payroll markPaid(Long id, User admin) {
        Payroll p = get(id);
        if (!Payroll.APPROVED.equals(p.getStatus())) throw new BusinessException("Chỉ đánh dấu đã trả với bảng lương đã chốt.");
        p.setStatus(Payroll.PAID);
        p.setPaidAt(LocalDateTime.now());
        notifications.log(admin, "hr.payroll_paid", p.getMonthLabel());
        return p;
    }

    public void deleteDraft(Long id, User admin) {
        Payroll p = get(id);
        if (!Payroll.DRAFT.equals(p.getStatus())) throw new BusinessException("Chỉ xóa được bảng lương nháp.");
        payrollRepo.delete(p);
        notifications.log(admin, "hr.payroll_delete", p.getMonthLabel());
    }

    @Transactional(readOnly = true)
    public List<PayrollLine> myPayslips(User u) {
        return lineRepo.findPublishedForUser(u);
    }

    @Transactional(readOnly = true)
    public void export(Long id, OutputStream out) throws IOException {
        Payroll p = get(id);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Luong " + p.getMonth());
            CellStyle head = CatalogService.headerStyle(wb);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            String[] cols = {"Nhân viên", "Hình thức", "Lương CB / đơn giá", "Ca xếp", "Ca làm", "Ngày công", "Giờ công", "Vắng", "Đi muộn",
                    "Lương theo công", "Phụ cấp ca", "Phụ cấp cố định", "Thưởng", "Phạt muộn", "Bảo hiểm", "Khấu trừ khác", "Thực lĩnh", "Ghi chú"};
            Row h = sh.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(head);
            }
            int r = 1;
            for (PayrollLine l : p.getLines()) {
                Row row = sh.createRow(r++);
                Object[] v = {l.getUser().getFullName(), "HOURLY".equals(l.getSalaryType()) ? "Theo giờ" : "Theo tháng", l.getRate(), l.getScheduledShifts(),
                        l.getShiftsWorked(), l.getDaysWorked(), l.getWorkedHours(), l.getAbsentShifts(), l.getLateCount(), l.getBaseAmount(),
                        l.getShiftAllowance(), l.getFixedAllowance(), l.getBonus(), l.getLatePenalty(), l.getInsurance(), l.getOtherDeduction(),
                        l.getTotal(), l.getNote()};
                for (int i = 0; i < v.length; i++) {
                    Cell c = row.createCell(i);
                    CatalogService.setCell(c, v[i]);
                    if (v[i] instanceof Long) c.setCellStyle(money);
                }
            }
            Row t = sh.createRow(r);
            t.createCell(0).setCellValue("TỔNG CỘNG");
            Cell tc = t.createCell(16);
            tc.setCellValue(p.getTotal());
            tc.setCellStyle(money);
            for (int i = 0; i < cols.length; i++) sh.autoSizeColumn(i);
            wb.write(out);
        }
    }
}
