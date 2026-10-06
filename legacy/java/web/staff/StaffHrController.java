package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.ShiftAssignment;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.ShiftAssignmentRepository;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Dược sĩ / nhân viên: lịch làm của tôi, vào / kết thúc ca, phiếu lương. */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffHrController {
    private final WorkScheduleService scheduleService;
    private final PayrollService payrollService;
    private final ShiftAssignmentRepository assignmentRepo;
    private final CurrentUser currentUser;

    @GetMapping("/my-schedule")
    public String mySchedule(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week, Model model) {
        User me = currentUser.get();
        LocalDate monday = WorkScheduleService.monday(week == null ? LocalDate.now() : week);
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(monday.plusDays(i));
        List<ShiftAssignment> shifts = assignmentRepo.findForUser(me, monday, monday.plusDays(6));
        model.addAttribute("monday", monday);
        model.addAttribute("days", days);
        model.addAttribute("dayNames", StaffScheduleController.DAY_NAMES);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("shifts", shifts);
        model.addAttribute("todayShifts", scheduleService.todayShifts(me));
        model.addAttribute("weekMinutes", shifts.stream().mapToLong(a -> a.getShift().getPaidMinutes()).sum());
        model.addAttribute("title", "Lịch làm của tôi");
        return "staff/my-schedule";
    }

    @PostMapping("/check-in")
    @Transactional
    public String checkIn(@RequestParam(defaultValue = "/staff/my-schedule") String back, RedirectAttributes ra) {
        ShiftAssignment a = scheduleService.checkIn(currentUser.get());
        long late = a.getLateMinutes();
        Flash.success(ra, "Đã vào " + a.getShift().getName() + (late > 0 ? " (muộn " + late + " phút)" : "") + ". Chúc bạn một ca làm việc hiệu quả!");
        return "redirect:" + (back.startsWith("/staff") ? back : "/staff/my-schedule");
    }

    @PostMapping("/check-out")
    @Transactional
    public String checkOut(@RequestParam(defaultValue = "/staff/my-schedule") String back, RedirectAttributes ra) {
        ShiftAssignment a = scheduleService.checkOut(currentUser.get());
        Flash.success(ra, "Đã kết thúc " + a.getShift().getName() + ": tính " + Math.round(a.getPaidMinutes() / 6.0) / 10.0 + " giờ công.");
        return "redirect:" + (back.startsWith("/staff") ? back : "/staff/my-schedule");
    }

    @GetMapping("/my-payslips")
    public String myPayslips(Model model) {
        model.addAttribute("lines", payrollService.myPayslips(currentUser.get()));
        model.addAttribute("title", "Phiếu lương của tôi");
        return "staff/my-payslips";
    }
}
