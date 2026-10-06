package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.PayrollRepository;
import com.hieuthuoc.repository.ShiftAssignmentRepository;
import com.hieuthuoc.repository.WorkShiftRepository;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Nhân sự (quyền SCHEDULE - dược sĩ quản lý; admin cũng dùng được): ca làm, xếp lịch làm việc, chấm công,
 * lập bảng lương và trình admin duyệt. Duyệt / trả lại / đánh dấu đã trả nằm ở {@link com.hieuthuoc.web.admin.AdminPayrollController}.
 */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffScheduleController {
    public static final List<String> DAY_NAMES = List.of("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "CN");

    private final WorkScheduleService scheduleService;
    private final PayrollService payrollService;
    private final WorkShiftRepository shiftRepo;
    private final ShiftAssignmentRepository assignmentRepo;
    private final PayrollRepository payrollRepo;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    /* ---------------- Lịch làm việc (theo tuần) ---------------- */

    @GetMapping("/schedule")
    public String schedule(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week, Model model) {
        LocalDate monday = WorkScheduleService.monday(week == null ? LocalDate.now() : week);
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(monday.plusDays(i));
        Map<String, List<ShiftAssignment>> cells = scheduleService.week(monday);
        Map<Long, Double> hours = new HashMap<>();
        for (List<ShiftAssignment> list : cells.values()) {
            for (ShiftAssignment a : list) hours.merge(a.getUser().getId(), a.getShift().getPaidMinutes() / 60.0, Double::sum);
        }
        model.addAttribute("monday", monday);
        model.addAttribute("days", days);
        model.addAttribute("dayNames", DAY_NAMES);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("staff", scheduleService.staff());
        model.addAttribute("shifts", shiftRepo.findByActiveTrueOrderByStartTimeAsc());
        model.addAttribute("cells", cells);
        model.addAttribute("hours", hours);
        model.addAttribute("title", "Lịch làm việc");
        return "staff/schedule";
    }

    @PostMapping("/schedule")
    @Transactional
    public String assign(@RequestParam Long userId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam Long shiftId, @RequestParam(required = false) String note, RedirectAttributes ra) {
        User me = currentUser.get();
        ShiftAssignment a = scheduleService.assign(userId, date, shiftId, note, me);
        notifications.log(me, "hr.assign", a.getUser().getFullName() + " - " + a.getShift().getName() + " " + date);
        notifications.notify(a.getUser(), "Bạn được xếp " + a.getShift().getName() + " (" + a.getShift().getTimeRange() + ") ngày "
                + date.getDayOfMonth() + "/" + date.getMonthValue(), "/staff/my-schedule?week=" + date);
        Flash.success(ra, "Đã xếp " + a.getShift().getName() + " cho " + a.getUser().getFullName() + ".");
        return "redirect:/staff/schedule?week=" + WorkScheduleService.monday(date);
    }

    @PostMapping("/schedule/{id}/delete")
    @Transactional
    public String unassign(@PathVariable Long id, @RequestParam(required = false) String week, RedirectAttributes ra) {
        scheduleService.remove(id, currentUser.get());
        Flash.info(ra, "Đã bỏ ca.");
        return "redirect:/staff/schedule" + (week == null ? "" : "?week=" + week);
    }

    @PostMapping("/schedule/copy")
    @Transactional
    public String copyWeek(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, RedirectAttributes ra) {
        LocalDate src = WorkScheduleService.monday(from);
        LocalDate dst = src.plusWeeks(1);
        int n = scheduleService.copyWeek(src, dst, currentUser.get());
        Flash.success(ra, "Đã sao chép " + n + " ca sang tuần " + dst.getDayOfMonth() + "/" + dst.getMonthValue() + ".");
        return "redirect:/staff/schedule?week=" + dst;
    }

    /* ---------------- Chấm công ---------------- */

    @GetMapping("/attendance")
    public String attendance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        LocalDate d = date == null ? LocalDate.now() : date;
        List<ShiftAssignment> recent = assignmentRepo.findBetween(LocalDate.now().minusDays(14), LocalDate.now());
        model.addAttribute("date", d);
        model.addAttribute("list", assignmentRepo.findBetween(d, d));
        model.addAttribute("issues", recent.stream().filter(a -> {
            String s = a.getAttendance()[0];
            return s.equals("Vắng") || s.equals("Quên kết ca");
        }).toList());
        model.addAttribute("title", "Chấm công");
        return "staff/attendance";
    }

    @PostMapping("/attendance/{id}")
    @Transactional
    public String adjust(@PathVariable Long id,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkIn,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkOut,
                         @RequestParam String note, @RequestParam(required = false) String back, RedirectAttributes ra) {
        ShiftAssignment a = scheduleService.adjust(id, checkIn, checkOut, note, currentUser.get());
        Flash.success(ra, "Đã sửa công " + a.getUser().getFullName() + " ngày " + a.getWorkDate().getDayOfMonth() + "/" + a.getWorkDate().getMonthValue() + ".");
        return "redirect:" + (back != null && back.startsWith("/staff/") ? back : "/staff/attendance?date=" + a.getWorkDate());
    }

    /* ---------------- Ca làm ---------------- */

    @GetMapping("/shifts")
    public String shifts(@RequestParam(required = false) Long edit, Model model) {
        List<WorkShift> list = shiftRepo.findAllByOrderByStartTimeAsc();
        Map<Long, Long> used = new HashMap<>();
        for (WorkShift s : list) used.put(s.getId(), assignmentRepo.countByShift(s));
        model.addAttribute("list", list);
        model.addAttribute("used", used);
        model.addAttribute("edit", edit == null ? new WorkShift() : shiftRepo.findById(edit).orElse(new WorkShift()));
        model.addAttribute("colors", Map.of("primary", "Xanh dương", "info", "Xanh ngọc", "success", "Xanh lá", "warning", "Vàng", "danger", "Đỏ", "secondary", "Xám"));
        model.addAttribute("title", "Ca làm việc");
        return "staff/shifts";
    }

    @PostMapping({"/shifts", "/shifts/{id}"})
    @Transactional
    public String saveShift(@PathVariable(required = false) Long id, @RequestParam String name,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
                            @RequestParam(defaultValue = "0") int breakMinutes, @RequestParam(defaultValue = "0") long allowance,
                            @RequestParam(defaultValue = "primary") String color, @RequestParam(defaultValue = "false") boolean active,
                            RedirectAttributes ra) {
        if (Texts.trim(name).length() < 2) throw new BusinessException("Vui lòng nhập tên ca.");
        if (startTime.equals(endTime)) throw new BusinessException("Giờ bắt đầu và kết thúc phải khác nhau.");
        WorkShift s = id == null ? new WorkShift() : shiftRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy ca."));
        s.setName(Texts.trim(name, 60));
        s.setStartTime(startTime);
        s.setEndTime(endTime);
        if (breakMinutes < 0 || breakMinutes >= s.getLengthMinutes()) throw new BusinessException("Thời gian nghỉ không hợp lệ.");
        if (allowance < 0) throw new BusinessException("Phụ cấp không được âm.");
        s.setBreakMinutes(breakMinutes);
        s.setAllowance(allowance);
        s.setColor(List.of("primary", "info", "success", "warning", "danger", "secondary").contains(color) ? color : "primary");
        s.setActive(id == null || active);
        shiftRepo.save(s);
        notifications.log(currentUser.get(), "hr.shift", s.getName() + " " + s.getTimeRange());
        Flash.success(ra, "Đã lưu " + s.getName() + ".");
        return "redirect:/staff/shifts";
    }

    /* ---------------- Bảng lương ---------------- */

    @GetMapping("/payroll")
    public String payrolls(Model model) {
        model.addAttribute("list", payrollRepo.findAllByOrderByMonthDesc());
        model.addAttribute("thisMonth", YearMonth.now().toString());
        model.addAttribute("lastMonth", YearMonth.now().minusMonths(1).toString());
        model.addAttribute("title", "Bảng lương");
        return "staff/payrolls";
    }

    @PostMapping("/payroll/generate")
    @Transactional
    public String generate(@RequestParam String month, RedirectAttributes ra) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (Exception e) {
            throw new BusinessException("Tháng không hợp lệ.");
        }
        Payroll p = payrollService.generate(ym, currentUser.get());
        Flash.success(ra, "Đã tính lương tháng " + p.getMonthLabel() + " cho " + p.getLines().size() + " nhân viên.");
        return "redirect:/staff/payroll/" + p.getId();
    }

    @GetMapping("/payroll/{id}")
    public String payroll(@PathVariable Long id, Model model) {
        Payroll p = payrollService.get(id);
        model.addAttribute("payroll", p);
        model.addAttribute("title", "Bảng lương tháng " + p.getMonthLabel());
        return "staff/payroll";
    }

    @PostMapping("/payroll/lines/{id}")
    @Transactional
    public String updateLine(@PathVariable Long id, @RequestParam(defaultValue = "0") long bonus, @RequestParam(defaultValue = "0") long deduction,
                             @RequestParam(required = false) String note, RedirectAttributes ra) {
        PayrollLine l = payrollService.updateLine(id, bonus, deduction, note, currentUser.get());
        Flash.success(ra, "Đã cập nhật lương " + l.getUser().getFullName() + ".");
        return "redirect:/staff/payroll/" + l.getPayroll().getId();
    }

    @PostMapping("/payroll/{id}/{action}")
    @Transactional
    public String payrollAction(@PathVariable Long id, @PathVariable String action, RedirectAttributes ra) {
        User me = currentUser.get();
        switch (action) {
            case "submit" -> {
                payrollService.submit(id, me);
                Flash.success(ra, "Đã trình bảng lương, chờ admin duyệt.");
            }
            case "recalc" -> {
                Payroll p = payrollService.get(id);
                payrollService.generate(YearMonth.parse(p.getMonth()), me);
                Flash.success(ra, "Đã tính lại theo dữ liệu chấm công mới nhất.");
            }
            case "delete" -> {
                payrollService.deleteDraft(id, me);
                Flash.info(ra, "Đã xóa bảng lương nháp.");
                return "redirect:/staff/payroll";
            }
            default -> throw BusinessException.notFound("Thao tác không hợp lệ.");
        }
        return "redirect:/staff/payroll/" + id;
    }

    @GetMapping("/payroll/{id}/export.xlsx")
    public void export(@PathVariable Long id, HttpServletResponse res) throws IOException {
        Payroll p = payrollService.get(id);
        res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        res.setHeader("Content-Disposition", "attachment; filename=\"bang-luong_" + p.getMonth() + ".xlsx\"");
        payrollService.export(id, res.getOutputStream());
    }
}
