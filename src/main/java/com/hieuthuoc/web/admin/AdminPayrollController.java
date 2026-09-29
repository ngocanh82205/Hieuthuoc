package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.User;
import com.hieuthuoc.service.BusinessException;
import com.hieuthuoc.service.CurrentUser;
import com.hieuthuoc.service.PayrollService;
import com.hieuthuoc.web.Flash;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin duyệt bảng lương do dược sĩ quản lý trình: duyệt (chốt), trả lại kèm lý do, đánh dấu đã trả. */
@Controller
@RequestMapping("/admin/payroll")
@RequiredArgsConstructor
public class AdminPayrollController {
    private final PayrollService payrollService;
    private final CurrentUser currentUser;

    @PostMapping("/{id}/{action}")
    @Transactional
    public String action(@PathVariable Long id, @PathVariable String action, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        User me = currentUser.get();
        switch (action) {
            case "approve" -> {
                payrollService.approve(id, me);
                Flash.success(ra, "Đã duyệt bảng lương, nhân viên xem được phiếu lương.");
            }
            case "reject" -> {
                payrollService.reject(id, reason, me);
                Flash.info(ra, "Đã trả lại bảng lương cho dược sĩ quản lý.");
            }
            case "paid" -> {
                payrollService.markPaid(id, me);
                Flash.success(ra, "Đã đánh dấu đã trả lương.");
            }
            default -> throw BusinessException.notFound("Thao tác không hợp lệ.");
        }
        return "redirect:/staff/payroll/" + id;
    }
}
