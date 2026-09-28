package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Kho: tồn kho theo lô, cảnh báo, hủy/khóa lô, truy vết thu hồi, phiếu nhập. */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffInventoryController {
    private final ProductRepository productRepo;
    private final BatchRepository batchRepo;
    private final ReceiptRepository receiptRepo;
    private final SupplierRepository supplierRepo;
    private final StockAdjustmentRepository adjustmentRepo;
    private final OrderItemBatchRepository allocationRepo;
    private final StockService stockService;
    private final InventoryService inventoryService;
    private final SettingService settings;
    private final CurrentUser currentUser;
    private final WarehouseRepository warehouseRepo;
    private final TransferSlipRepository transferRepo;

    @GetMapping("/inventory")
    public String inventory(@RequestParam(required = false) String q, @RequestParam(required = false) String filter, Model model) {
        List<Product> products = stockService.fill(new ArrayList<>(productRepo.findAllByOrderByNameAsc()));
        if (!Texts.isBlank(q)) {
            String k = q.trim().toLowerCase();
            products.removeIf(p -> !p.getName().toLowerCase().contains(k)
                    && (p.getActiveIngredient() == null || !p.getActiveIngredient().toLowerCase().contains(k)));
        }
        if ("low".equals(filter)) products.removeIf(p -> !p.isLowStock());
        if ("out".equals(filter)) products.removeIf(p -> p.getAvailable() > 0);
        products.sort(Comparator.comparing((Product p) -> !p.isLowStock()).thenComparing(Product::getName));
        LocalDate today = LocalDate.now();
        Map<Long, LocalDate> nextExp = new HashMap<>();
        for (Product p : products) nextExp.put(p.getId(), batchRepo.nextExpiry(p.getId(), today));
        model.addAttribute("products", products);
        model.addAttribute("nextExp", nextExp);
        model.addAttribute("q", q);
        model.addAttribute("filter", filter);
        model.addAttribute("title", "Tồn kho");
        return "staff/inventory";
    }

    @GetMapping("/inventory/alerts")
    public String alerts(Model model) {
        LocalDate today = LocalDate.now();
        long nearDays = settings.getLong("near_expiry_days");
        List<Product> products = stockService.fill(new ArrayList<>(productRepo.findByActiveTrueOrderByNameAsc()));
        model.addAttribute("expired", batchRepo.findExpired(today));
        model.addAttribute("nearExpiry", batchRepo.findNearExpiry(today, today.plusDays(nearDays)));
        model.addAttribute("locked", batchRepo.findLocked());
        model.addAttribute("lowStock", products.stream().filter(Product::isLowStock).sorted(Comparator.comparingLong(Product::getOnHand)).toList());
        model.addAttribute("nearDays", nearDays);
        model.addAttribute("title", "Cảnh báo kho");
        return "staff/alerts";
    }

    @GetMapping("/inventory/{productId}")
    public String batches(@PathVariable Long productId, Model model) {
        Product p = productRepo.findById(productId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy sản phẩm."));
        stockService.fill(p);
        List<Batch> batches = batchRepo.findByProductOrderByExpDateAsc(p);
        Map<Long, Long> sold = new HashMap<>();
        for (Batch b : batches) sold.put(b.getId(), allocationRepo.soldFromBatch(b));
        model.addAttribute("product", p);
        model.addAttribute("batches", batches);
        model.addAttribute("sold", sold);
        model.addAttribute("adjustments", adjustmentRepo.findTop20ByBatchProductOrderByIdDesc(p));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("title", "Lô hàng: " + p.getName());
        return "staff/batches";
    }

    @PostMapping("/batches/{id}/lock")
    @Transactional
    public String lock(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        Batch b = inventoryService.toggleLock(id, currentUser.get(), reason);
        Flash.success(ra, b.isLocked() ? "Đã khóa lô " + b.getBatchNo() + ", lô này sẽ không được xuất bán." : "Đã mở khóa lô " + b.getBatchNo() + ".");
        return "redirect:/staff/inventory/" + b.getProduct().getId();
    }

    @PostMapping("/batches/{id}/adjust")
    @Transactional
    public String adjust(@PathVariable Long id, @RequestParam int quantity, @RequestParam String reason, RedirectAttributes ra) {
        StockAdjustment a = inventoryService.adjust(id, currentUser.get(), quantity, reason);
        Batch b = a.getBatch();
        if (a.getStatus() == ApprovalStatus.PENDING) Flash.info(ra, "Đã lập " + a.getTypeLabel().toLowerCase() + " lô " + b.getBatchNo() + " - chờ admin / quản lý duyệt, tồn kho chưa thay đổi.");
        else Flash.success(ra, "Đã cập nhật tồn kho lô " + b.getBatchNo() + ".");
        return "redirect:/staff/inventory/" + b.getProduct().getId();
    }

    @GetMapping("/batches/{id}/buyers")
    public String buyers(@PathVariable Long id, Model model) {
        Batch b = inventoryService.batch(id);
        model.addAttribute("batch", b);
        model.addAttribute("buyers", allocationRepo.findByBatchWithOrder(b));
        model.addAttribute("title", "Truy vết lô " + b.getBatchNo());
        return "staff/recall";
    }

    @PostMapping("/batches/{id}/notify-buyers")
    @Transactional
    public String notifyBuyers(@PathVariable Long id, @RequestParam String message, RedirectAttributes ra) {
        int n = inventoryService.notifyRecall(id, currentUser.get(), message);
        Flash.success(ra, "Đã gửi thông báo đến " + n + " khách hàng.");
        return "redirect:/staff/batches/" + id + "/buyers";
    }

    /* ---------------- Kiểm kê ---------------- */

    @GetMapping("/stocktake")
    public String stocktake(@RequestParam(required = false) Long warehouse, Model model) {
        List<Batch> list = new ArrayList<>(batchRepo.findForStocktake());
        if (warehouse != null) {
            Warehouse w = warehouseRepo.findById(warehouse).orElse(null);
            if (w != null) list.removeIf(b -> b.getWarehouse() == null ? !w.isMain() : !b.getWarehouse().getId().equals(w.getId()));
        }
        model.addAttribute("batches", list);
        model.addAttribute("warehouses", warehouseRepo.findAllByOrderByMainDescNameAsc());
        model.addAttribute("warehouse", warehouse);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("title", "Kiểm kê kho");
        return "staff/stocktake";
    }

    @PostMapping("/stocktake")
    @Transactional
    public String applyStocktake(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        Map<Long, Integer> counted = new HashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("count_") && !Texts.isBlank(v)) {
                try {
                    counted.put(Long.valueOf(k.substring(6)), Integer.parseInt(v.trim()));
                } catch (NumberFormatException e) {
                    throw new BusinessException("Số lượng kiểm kê không hợp lệ: " + v);
                }
            }
        });
        User me = currentUser.get();
        int n = inventoryService.stocktake(counted, me, params.get("note"));
        Flash.success(ra, n == 0 ? "Kiểm kê khớp sổ sách, không có chênh lệch."
                : me.hasPermission(StaffPermission.APPROVE_STOCK) ? "Đã điều chỉnh tồn kho theo kiểm kê cho " + n + " lô."
                : "Đã lập phiếu điều chỉnh kiểm kê cho " + n + " lô - chờ admin / quản lý duyệt.");
        return "redirect:/staff/stocktake";
    }

    /* ---------------- Phiếu hủy / điều chỉnh ---------------- */

    @GetMapping("/adjustments")
    public String adjustments(@RequestParam(required = false) ApprovalStatus status, Model model) {
        List<StockAdjustment> list = status == null ? adjustmentRepo.findTop200ByOrderByIdDesc() : adjustmentRepo.findByStatusOrderByIdAsc(status);
        model.addAttribute("list", list);
        model.addAttribute("status", status);
        model.addAttribute("statuses", ApprovalStatus.values());
        model.addAttribute("pendingCount", adjustmentRepo.countByStatus(ApprovalStatus.PENDING));
        model.addAttribute("title", "Phiếu hủy / điều chỉnh kho");
        return "staff/adjustments";
    }

    @PostMapping("/adjustments/{id}/decide")
    @Transactional
    public String decideAdjustment(@PathVariable Long id, @RequestParam String decision, @RequestParam(required = false) String reason,
                                   @RequestParam(defaultValue = "/staff/adjustments?status=PENDING") String back, RedirectAttributes ra) {
        boolean approve = "approve".equals(decision);
        StockAdjustment a = inventoryService.decideAdjustment(id, currentUser.get(), approve, reason);
        Flash.success(ra, (approve ? "Đã duyệt " : "Đã từ chối ") + a.getTypeLabel().toLowerCase() + " lô " + a.getBatch().getBatchNo() + ".");
        return "redirect:" + (back.startsWith("/admin/") || back.startsWith("/staff/") ? back : "/staff/adjustments");
    }

    /* ---------------- Chuyển kho ---------------- */

    @GetMapping("/transfers")
    public String transfers(Model model) {
        model.addAttribute("slips", transferRepo.findAllByOrderByIdDesc());
        model.addAttribute("title", "Phiếu chuyển kho");
        return "staff/transfers";
    }

    @GetMapping("/transfers/new")
    public String newTransfer(@RequestParam(required = false) Long from, Model model) {
        List<Warehouse> whs = warehouseRepo.findByActiveTrueOrderByMainDescNameAsc();
        if (whs.size() < 2) throw new BusinessException("Cần ít nhất 2 kho đang hoạt động để chuyển kho (Quản trị > Kho / chi nhánh).");
        Warehouse f = from == null ? whs.get(0) : warehouseRepo.findById(from).orElse(whs.get(0));
        model.addAttribute("warehouses", whs);
        model.addAttribute("from", f);
        model.addAttribute("batches", batchRepo.findTransferable(f, f.isMain(), LocalDate.now()));
        model.addAttribute("title", "Tạo phiếu chuyển kho");
        return "staff/transfer-form";
    }

    @PostMapping("/transfers")
    @Transactional
    public String createTransfer(@RequestParam Long fromId, @RequestParam Long toId, @RequestParam Map<String, String> params, RedirectAttributes ra) {
        Map<Long, Integer> qty = new HashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("qty_") && !Texts.isBlank(v)) {
                try {
                    qty.put(Long.valueOf(k.substring(4)), Integer.parseInt(v.trim()));
                } catch (NumberFormatException e) {
                    throw new BusinessException("Số lượng không hợp lệ: " + v);
                }
            }
        });
        TransferSlip slip = inventoryService.transfer(fromId, toId, qty, params.get("note"), currentUser.get());
        Flash.success(ra, "Đã chuyển kho " + slip.getCode() + ": " + slip.getTotalQuantity() + " đơn vị từ " + slip.getFromWarehouse().getName()
                + " sang " + slip.getToWarehouse().getName() + ".");
        return "redirect:/staff/transfers";
    }

    /* ---------------- Phiếu nhập kho ---------------- */

    @GetMapping("/receipts")
    public String receipts(Model model) {
        model.addAttribute("receipts", receiptRepo.findAllByOrderByCreatedAtDescIdDesc());
        model.addAttribute("title", "Phiếu nhập kho");
        return "staff/receipts";
    }

    @GetMapping("/receipts/new")
    public String newReceipt(Model model) {
        model.addAttribute("suppliers", supplierRepo.findAllByOrderByNameAsc());
        model.addAttribute("warehouses", warehouseRepo.findByActiveTrueOrderByMainDescNameAsc());
        model.addAttribute("products", productRepo.findAllByOrderByNameAsc());
        model.addAttribute("title", "Tạo phiếu nhập");
        return "staff/receipt-form";
    }

    @PostMapping("/receipts")
    @Transactional
    public String createReceipt(@ModelAttribute InventoryService.ReceiptForm form, RedirectAttributes ra) {
        Receipt r = inventoryService.createReceipt(form, currentUser.get());
        Flash.success(ra, "Đã tạo phiếu nhập " + r.getCode() + ", chờ admin duyệt.");
        return "redirect:/staff/receipts/" + r.getId();
    }

    @GetMapping("/receipts/{id}")
    public String receipt(@PathVariable Long id, Model model) {
        Receipt r = receiptRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy phiếu nhập."));
        model.addAttribute("receipt", r);
        model.addAttribute("title", "Phiếu nhập " + r.getCode());
        return "staff/receipt";
    }

    @PostMapping("/receipts/{id}/decide")
    @Transactional
    public String decide(@PathVariable Long id, @RequestParam String decision, RedirectAttributes ra) {
        boolean approve = "approve".equals(decision);
        inventoryService.decideReceipt(id, currentUser.get(), approve);
        Flash.success(ra, approve ? "Đã duyệt phiếu nhập, hàng đã vào kho." : "Đã từ chối phiếu nhập.");
        return "redirect:/staff/receipts/" + id;
    }
}
