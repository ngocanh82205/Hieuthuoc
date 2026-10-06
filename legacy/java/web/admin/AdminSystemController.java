package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.ShippingZone;
import com.hieuthuoc.repository.ShippingZoneRepository;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.util.*;

/** Admin - hệ thống: khu vực giao hàng & biểu phí, sao lưu dữ liệu. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminSystemController {
    private final ShippingZoneRepository zoneRepo;
    private final BackupService backupService;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    /* ---------------- Khu vực giao hàng ---------------- */

    @GetMapping("/shipping-zones")
    public String zones(@RequestParam(required = false) Long edit, Model model) {
        List<ShippingZone> zones = zoneRepo.findAllByOrderBySortOrderAscIdAsc();
        Set<String> covered = new HashSet<>();
        zones.stream().filter(ShippingZone::isActive).forEach(z -> covered.addAll(z.getProvinceList()));
        model.addAttribute("zones", zones);
        model.addAttribute("uncovered", ShippingZone.PROVINCES.stream().filter(p -> !covered.contains(p)).toList());
        model.addAttribute("provinces", ShippingZone.PROVINCES);
        ShippingZone e = edit == null ? new ShippingZone() : zoneRepo.findById(edit).orElse(new ShippingZone());
        model.addAttribute("edit", e);
        model.addAttribute("editProvinces", e.getProvinces() == null ? List.of() : e.getProvinceList());
        model.addAttribute("title", "Khu vực giao hàng & biểu phí");
        return "admin/shipping-zones";
    }

    @PostMapping({"/shipping-zones", "/shipping-zones/{id}"})
    @Transactional
    public String saveZone(@PathVariable(required = false) Long id, @RequestParam String name,
                           @RequestParam(value = "provinces", required = false) List<String> provinces,
                           @RequestParam long fee, @RequestParam(required = false) Long freeThreshold, @RequestParam(required = false) String eta,
                           @RequestParam(defaultValue = "0") int sortOrder, @RequestParam(defaultValue = "false") boolean active, RedirectAttributes ra) {
        if (Texts.trim(name).length() < 2) throw new BusinessException("Vui lòng nhập tên khu vực.");
        List<String> list = provinces == null ? List.of() : provinces.stream().filter(ShippingZone.PROVINCES::contains).distinct().toList();
        if (list.isEmpty()) throw new BusinessException("Chọn ít nhất 1 tỉnh/thành.");
        if (fee < 0) throw new BusinessException("Phí ship không được âm.");
        ShippingZone z = id == null ? new ShippingZone() : zoneRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy khu vực."));
        // Một tỉnh chỉ thuộc một khu vực đang hoạt động
        for (ShippingZone other : zoneRepo.findByActiveTrueOrderBySortOrderAscIdAsc()) {
            if (other.getId().equals(z.getId())) continue;
            for (String p : list) if (other.covers(p)) throw new BusinessException(p + " đã thuộc khu vực \"" + other.getName() + "\".");
        }
        z.setName(Texts.trim(name, 100));
        z.setProvinces(String.join(", ", list));
        z.setFee(fee);
        z.setFreeThreshold(freeThreshold == null || freeThreshold <= 0 ? null : freeThreshold);
        z.setEta(Texts.emptyToNull(Texts.trim(eta, 50)));
        z.setSortOrder(sortOrder);
        z.setActive(id == null || active);
        zoneRepo.save(z);
        notifications.log(currentUser.get(), "settings.shipping_zone", z.getName() + ": " + fee + "đ, " + list.size() + " tỉnh");
        Flash.success(ra, "Đã lưu khu vực " + z.getName() + ".");
        return "redirect:/admin/shipping-zones";
    }

    @PostMapping("/shipping-zones/{id}/delete")
    @Transactional
    public String deleteZone(@PathVariable Long id, RedirectAttributes ra) {
        zoneRepo.findById(id).ifPresent(zoneRepo::delete);
        Flash.info(ra, "Đã xóa khu vực. Tỉnh không thuộc khu vực nào sẽ không giao hàng được (nếu còn khu vực khác).");
        return "redirect:/admin/shipping-zones";
    }

    /* ---------------- Sao lưu dữ liệu ---------------- */

    @GetMapping("/backup")
    public String backup(Model model) {
        model.addAttribute("files", backupService.list());
        model.addAttribute("supported", backupService.isSupported());
        model.addAttribute("database", backupService.databaseName());
        model.addAttribute("dir", backupService.dir().toString());
        model.addAttribute("title", "Sao lưu dữ liệu");
        return "admin/backup";
    }

    @PostMapping("/backup")
    public String createBackup(RedirectAttributes ra) {
        String name = backupService.create();
        notifications.log(currentUser.get(), "backup.create", name);
        Flash.success(ra, "Đã tạo bản sao lưu " + name + ".");
        return "redirect:/admin/backup";
    }

    @GetMapping("/backup/files/{name}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String name) {
        Path f = backupService.file(name);
        notifications.log(currentUser.get(), "backup.download", name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(f));
    }
}
