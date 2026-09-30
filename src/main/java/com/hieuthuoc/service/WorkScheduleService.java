package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.ShiftAssignmentRepository;
import com.hieuthuoc.repository.UserRepository;
import com.hieuthuoc.repository.WorkShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Lịch làm việc: xếp ca cho dược sĩ, chấm công vào / ra ca, sửa công. */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkScheduleService {
    /** Được vào ca sớm tối đa trước giờ bắt đầu (phút). */
    public static final int EARLY_CHECKIN_MINUTES = 60;

    private final ShiftAssignmentRepository assignmentRepo;
    private final WorkShiftRepository shiftRepo;
    private final UserRepository userRepo;
    private final NotificationService notifications;

    /** Nhân viên được xếp lịch: tài khoản dược sĩ / nhân viên đang hoạt động. */
    @Transactional(readOnly = true)
    public List<User> staff() {
        return userRepo.findByRoleAndLockedFalse(Role.PHARMACIST).stream().sorted(Comparator.comparing(User::getFullName)).toList();
    }

    public static LocalDate monday(LocalDate d) {
        return d.with(DayOfWeek.MONDAY);
    }

    /** Lịch tuần: khóa "userId_yyyy-MM-dd" -> các ca. */
    @Transactional(readOnly = true)
    public Map<String, List<ShiftAssignment>> week(LocalDate monday) {
        Map<String, List<ShiftAssignment>> map = new HashMap<>();
        for (ShiftAssignment a : assignmentRepo.findBetween(monday, monday.plusDays(6))) {
            map.computeIfAbsent(a.getUser().getId() + "_" + a.getWorkDate(), k -> new ArrayList<>()).add(a);
        }
        return map;
    }

    private static boolean overlaps(ShiftAssignment a, LocalDateTime start, LocalDateTime end) {
        return a.getStartAt().isBefore(end) && start.isBefore(a.getEndAt());
    }

    public ShiftAssignment assign(Long userId, LocalDate date, Long shiftId, String note, User admin) {
        User u = userRepo.findById(userId == null ? -1 : userId).filter(x -> x.getRole() == Role.PHARMACIST && !x.isLocked())
                .orElseThrow(() -> new BusinessException("Chọn nhân viên hợp lệ."));
        WorkShift shift = shiftRepo.findById(shiftId == null ? -1 : shiftId).filter(WorkShift::isActive)
                .orElseThrow(() -> new BusinessException("Chọn ca làm hợp lệ."));
        if (date == null) throw new BusinessException("Chọn ngày làm.");
        if (date.isBefore(LocalDate.now().minusDays(31))) throw new BusinessException("Không xếp ca cho ngày đã qua quá 31 ngày.");
        ShiftAssignment a = new ShiftAssignment(u, date, shift);
        for (ShiftAssignment other : assignmentRepo.findForUser(u, date.minusDays(1), date.plusDays(1))) {
            if (overlaps(other, a.getStartAt(), a.getEndAt())) {
                throw new BusinessException(u.getFullName() + " đã có " + other.getShift().getName() + " ngày " + other.getWorkDate().getDayOfMonth()
                        + "/" + other.getWorkDate().getMonthValue() + " trùng giờ với ca này.");
            }
        }
        a.setNote(Texts.emptyToNull(Texts.trim(note, 200)));
        a.setCreatedBy(admin);
        assignmentRepo.save(a);
        return a;
    }

    public void remove(Long assignmentId, User admin) {
        ShiftAssignment a = assignmentRepo.findById(assignmentId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy ca."));
        if (a.getCheckInAt() != null) throw new BusinessException("Ca đã chấm công, không thể xóa (hãy sửa công nếu cần).");
        assignmentRepo.delete(a);
        notifications.log(admin, "hr.unassign", a.getUser().getFullName() + " - " + a.getShift().getName() + " " + a.getWorkDate());
    }

    /** Sao chép lịch từ tuần nguồn sang tuần đích (bỏ qua ca đã có / trùng giờ). Trả về số ca được thêm. */
    public int copyWeek(LocalDate fromMonday, LocalDate toMonday, User admin) {
        if (fromMonday.equals(toMonday)) throw new BusinessException("Tuần nguồn và tuần đích phải khác nhau.");
        int n = 0;
        for (ShiftAssignment src : assignmentRepo.findBetween(fromMonday, fromMonday.plusDays(6))) {
            if (src.getUser().isLocked() || !src.getShift().isActive()) continue;
            LocalDate d = toMonday.plusDays(src.getWorkDate().toEpochDay() - fromMonday.toEpochDay());
            if (assignmentRepo.existsByUserAndWorkDateAndShift(src.getUser(), d, src.getShift())) continue;
            try {
                assign(src.getUser().getId(), d, src.getShift().getId(), src.getNote(), admin);
                n++;
            } catch (BusinessException ignored) {
                // trùng giờ với ca khác: bỏ qua
            }
        }
        notifications.log(admin, "hr.copy_week", fromMonday + " → " + toMonday + ": " + n + " ca");
        return n;
    }

    /* ======================= Chấm công ======================= */

    /** Ca của nhân viên trong hôm nay (kể cả ca qua đêm từ hôm qua). */
    @Transactional(readOnly = true)
    public List<ShiftAssignment> todayShifts(User u) {
        LocalDate today = LocalDate.now();
        return assignmentRepo.findForUser(u, today.minusDays(1), today).stream()
                .filter(a -> a.getWorkDate().equals(today) || (a.getShift().isOvernight() && a.getEndAt().isAfter(LocalDateTime.now())))
                .toList();
    }

    public ShiftAssignment checkIn(User u) {
        LocalDateTime now = LocalDateTime.now();
        ShiftAssignment a = todayShifts(u).stream()
                .filter(x -> x.getCheckInAt() == null && !now.isBefore(x.getStartAt().minusMinutes(EARLY_CHECKIN_MINUTES)) && now.isBefore(x.getEndAt()))
                .findFirst().orElseThrow(() -> new BusinessException("Không có ca nào để vào lúc này (chỉ vào ca trong khoảng "
                        + EARLY_CHECKIN_MINUTES + " phút trước giờ bắt đầu đến hết ca)."));
        a.setCheckInAt(now);
        notifications.log(u, "hr.check_in", a.getShift().getName() + " " + a.getWorkDate());
        return a;
    }

    public ShiftAssignment checkOut(User u) {
        ShiftAssignment a = assignmentRepo.findForUser(u, LocalDate.now().minusDays(1), LocalDate.now()).stream()
                .filter(x -> x.getCheckInAt() != null && x.getCheckOutAt() == null)
                .reduce((x, y) -> y).orElseThrow(() -> new BusinessException("Bạn chưa vào ca nào."));
        a.setCheckOutAt(LocalDateTime.now());
        notifications.log(u, "hr.check_out", a.getShift().getName() + " " + a.getWorkDate());
        return a;
    }

    /** Quản trị viên sửa công (quên chấm, chấm sai...). */
    public ShiftAssignment adjust(Long assignmentId, LocalDateTime in, LocalDateTime out, String note, User admin) {
        ShiftAssignment a = assignmentRepo.findById(assignmentId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy ca."));
        if (Texts.isBlank(note)) throw new BusinessException("Vui lòng ghi lý do sửa công.");
        if (in == null && out != null) throw new BusinessException("Phải có giờ vào ca trước khi có giờ ra ca.");
        if (in != null && out != null && !out.isAfter(in)) throw new BusinessException("Giờ ra ca phải sau giờ vào ca.");
        if (in != null && (in.isBefore(a.getStartAt().minusHours(3)) || in.isAfter(a.getEndAt()))) {
            throw new BusinessException("Giờ vào ca phải nằm trong khung ca.");
        }
        a.setCheckInAt(in);
        a.setCheckOutAt(out);
        a.setAdjustNote(Texts.trim(note, 200) + " (" + admin.getFullName() + ")");
        notifications.log(admin, "hr.adjust", a.getUser().getFullName() + " " + a.getWorkDate() + " " + a.getShift().getName() + ": " + note);
        return a;
    }
}
