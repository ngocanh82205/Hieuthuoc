package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
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
import java.util.*;

/** Admin: duyệt phiếu kho, kho / chi nhánh, định mức tồn, công nợ nhà cung cấp. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminStockController {
    private final ReceiptRepository receiptRepo;
    private final StockAdjustmentRepository adjustmentRepo;
    private final WarehouseRepository warehouseRepo;
    private final BatchRepository batchRepo;
    private final ProductRepository productRepo;
    private final SupplierRepository supplierRepo;
    private final SupplierPaymentRepository paymentRepo;
    private final StockService stockService;
    private final InventoryService inventoryService;
    private final CategoryService categoryService;
    private final SettingService settings;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    /* ---------------- Duyệt phiếu kho ---------------- */

    @GetMapping("/stock-approvals")
    public String approvals(Model model) {
        model.addAttribute("receipts", receiptRepo.findAllByOrderByCreatedAtDescIdDesc().stream().filter(r -> r.getStatus() == ApprovalStatus.PENDING).toList());
        model.addAttribute("adjustments", adjustmentRepo.findByStatusOrderByIdAsc(ApprovalStatus.PENDING));
        model.addAttribute("title", "Duyệt phiếu kho");
        return "admin/stock-approvals";
    }

    /* ---------------- Kho / chi nhánh ---------------- */

    @GetMapping("/warehouses")
    public String warehouses(@RequestParam(required = false) Long edit, Model model) {
        List<Warehouse> list = warehouseRepo.findAllByOrderByMainDescNameAsc();
        Map<Long, Long> qty = new HashMap<>();
        Map<Long, Long> value = new HashMap<>();
        for (Warehouse w : list) {
            qty.put(w.getId(), batchRepo.totalQuantityIn(w, w.isMain()));
            value.put(w.getId(), batchRepo.totalValueIn(w, w.isMain()));
        }
        model.addAttribute("list", list);
        model.addAttribute("qty", qty);
        model.addAttribute("value", value);
        model.addAttribute("edit", edit == null ? new Warehouse() : warehouseRepo.findById(edit).orElse(new Warehouse()));
        model.addAttribute("title", "Kho / chi nhánh");
        return "admin/warehouses";
    }

    @PostMapping({"/warehouses", "/warehouses/{id}"})
    @Transactional
    public String saveWarehouse(@PathVariable(required = false) Long id, @RequestParam String name, @RequestParam(required = false) String address,
                                @RequestParam(defaultValue = "false") boolean sellable, @RequestParam(defaultValue = "false") boolean active,
                                RedirectAttributes ra) {
        String n = Texts.trim(name, 100);
        if (n.length() < 2) throw new BusinessException("Vui lòng nhập tên kho.");
        Optional<Warehouse> dup = warehouseRepo.findByNameIgnoreCase(n);
        if (dup.isPresent() && !dup.get().getId().equals(id)) throw new BusinessException("Tên kho đã tồn tại.");
        Warehouse w = id == null ? new Warehouse() : warehouseRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy kho."));
        if (id == null) active = true;
        if (w.isMain() && (!sellable || !active)) throw new BusinessException("Kho chính luôn hoạt động và được bán hàng.");
        w.setName(n);
        w.setAddress(Texts.emptyToNull(Texts.trim(address, 300)));
        w.setSellable(sellable);
        w.setActive(active);
        warehouseRepo.save(w);
        notifications.log(currentUser.get(), "stock.warehouse", n + (sellable ? " (bán hàng)" : " (dự trữ)"));
        Flash.success(ra, "Đã lưu kho " + n + ".");
        return "redirect:/admin/warehouses";
    }

    /* ---------------- Định mức tồn & ngưỡng cận hạn ---------------- */

    @GetMapping("/min-stock")
    public String minStock(@RequestParam(required = false) Long category, Model model) {
        List<Product> products = stockService.fill(new ArrayList<>(productRepo.findAllByOrderByNameAsc()));
        if (category != null) {
            Set<Long> ids = categoryRepoIds(category);
            products.removeIf(p -> p.getCategory() == null || !ids.contains(p.getCategory().getId()));
        }
        model.addAttribute("products", products);
        model.addAttribute("categories", categoryService.tree());
        model.addAttribute("category", category);
        model.addAttribute("nearDays", settings.getLong("near_expiry_days"));
        model.addAttribute("title", "Định mức tồn & cảnh báo cận hạn");
        return "admin/min-stock";
    }

    private Set<Long> categoryRepoIds(Long categoryId) {
        return categoryService.tree().stream().filter(c -> c.getId().equals(categoryId)).findFirst()
                .map(categoryService::descendantIds).orElse(Set.of());
    }

    @PostMapping("/min-stock")
    @Transactional
    public String saveMinStock(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        int changed = 0;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!e.getKey().startsWith("min_") || Texts.isBlank(e.getValue())) continue;
            int v;
            try {
                v = Integer.parseInt(e.getValue().trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException("Định mức phải là số nguyên.");
            }
            if (v < 0) throw new BusinessException("Định mức không được âm.");
            Product p = productRepo.findById(Long.valueOf(e.getKey().substring(4))).orElse(null);
            if (p != null && p.getMinStock() != v) {
                p.setMinStock(v);
                changed++;
            }
        }
        String near = params.get("near_expiry_days");
        if (!Texts.isBlank(near)) settings.save(Map.of("near_expiry_days", near.trim()));
        notifications.log(currentUser.get(), "stock.min_stock", changed + " sản phẩm; cận hạn " + near + " ngày");
        Flash.success(ra, "Đã lưu định mức tồn (" + changed + " sản phẩm thay đổi) và ngưỡng cận hạn.");
        return "redirect:/admin/min-stock" + (params.get("category") == null || params.get("category").isBlank() ? "" : "?category=" + params.get("category"));
    }

    /* ---------------- Công nợ nhà cung cấp ---------------- */

    @GetMapping("/suppliers/{id}")
    public String supplier(@PathVariable Long id, Model model) {
        Supplier s = supplierRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy nhà cung cấp."));
        List<InventoryService.DebtRow> rows = new ArrayList<>();
        for (Receipt r : receiptRepo.findBySupplierAndStatus(s, ApprovalStatus.APPROVED)) {
            LocalDate due = r.getApprovedAt() == null ? null : r.getApprovedAt().toLocalDate().plusDays(s.getTermDays());
            rows.add(new InventoryService.DebtRow(r, due, due != null && due.isBefore(LocalDate.now())));
        }
        rows.sort(Comparator.comparing((InventoryService.DebtRow d) -> d.receipt().getApprovedAt(), Comparator.nullsLast(Comparator.reverseOrder())));
        model.addAttribute("supplier", s);
        model.addAttribute("debt", inventoryService.debt(s));
        model.addAttribute("rows", rows);
        model.addAttribute("payments", paymentRepo.findBySupplierOrderByPaidDateDescIdDesc(s));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("title", "Công nợ: " + s.getName());
        return "admin/supplier";
    }

    @PostMapping("/suppliers/{id}/payments")
    @Transactional
    public String pay(@PathVariable Long id, @RequestParam long amount,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDate,
                      @RequestParam(required = false) String method, @RequestParam(required = false) String note, RedirectAttributes ra) {
        inventoryService.pay(id, amount, paidDate, method, note, currentUser.get());
        Flash.success(ra, "Đã ghi nhận phiếu chi trả nhà cung cấp.");
        return "redirect:/admin/suppliers/" + id;
    }
}
