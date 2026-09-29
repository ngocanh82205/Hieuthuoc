package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {
    private final BatchRepository batchRepo;
    private final ReceiptRepository receiptRepo;
    private final ProductRepository productRepo;
    private final SupplierRepository supplierRepo;
    private final StockAdjustmentRepository adjustmentRepo;
    private final OrderItemBatchRepository allocationRepo;
    private final NotificationService notifications;
    private final CustomerCareService care;
    private final WarehouseRepository warehouseRepo;
    private final TransferSlipRepository transferRepo;
    private final SupplierPaymentRepository paymentRepo;
    private final StockService stockService;

    @Getter
    @Setter
    public static class ReceiptForm {
        private Long supplierId;
        private Long warehouseId;
        private String note;
        private List<Row> rows = new ArrayList<>();

        @Getter
        @Setter
        public static class Row {
            private Long productId;
            private String batchNo;
            private LocalDate mfgDate;
            private LocalDate expDate;
            private Integer quantity;
            private Long importPrice;
        }
    }

    public Batch batch(Long id) {
        return batchRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy lô hàng."));
    }

    /** Nhân viên tạo phiếu nhập (chờ admin duyệt). */
    public Receipt createReceipt(ReceiptForm form, User user) {
        Receipt r = new Receipt();
        r.setCode(Texts.code("PN"));
        r.setCreatedBy(user);
        r.setNote(Texts.emptyToNull(Texts.trim(form.getNote(), 500)));
        if (form.getSupplierId() != null) r.setSupplier(supplierRepo.findById(form.getSupplierId()).orElse(null));
        if (form.getWarehouseId() != null) r.setWarehouse(warehouseRepo.findById(form.getWarehouseId()).orElse(null));
        int i = 0;
        for (ReceiptForm.Row row : form.getRows()) {
            i++;
            if (row.getProductId() == null) continue;
            if (Texts.isBlank(row.getBatchNo()) || row.getExpDate() == null || row.getQuantity() == null || row.getQuantity() <= 0) {
                throw new BusinessException("Dòng " + i + ": cần số lô, hạn dùng và số lượng > 0.");
            }
            if (row.getMfgDate() != null && !row.getMfgDate().isBefore(row.getExpDate())) {
                throw new BusinessException("Dòng " + i + ": ngày sản xuất phải trước hạn dùng.");
            }
            if (!row.getExpDate().isAfter(LocalDate.now())) throw new BusinessException("Dòng " + i + ": không nhập hàng đã hết hạn.");
            ReceiptItem it = new ReceiptItem();
            it.setReceipt(r);
            it.setProduct(productRepo.findById(row.getProductId()).orElseThrow(() -> new BusinessException("Sản phẩm không tồn tại.")));
            it.setBatchNo(Texts.trim(row.getBatchNo(), 50));
            it.setMfgDate(row.getMfgDate());
            it.setExpDate(row.getExpDate());
            it.setQuantity(row.getQuantity());
            it.setImportPrice(row.getImportPrice() == null ? 0 : Math.max(0, row.getImportPrice()));
            r.getItems().add(it);
        }
        if (r.getItems().isEmpty()) throw new BusinessException("Phiếu nhập cần ít nhất 1 sản phẩm.");
        receiptRepo.save(r);
        notifications.log(user, "receipt.create", r.getCode());
        notifications.notifyAdmins("Phiếu nhập " + r.getCode() + " chờ duyệt", "/staff/receipts/" + r.getId());
        return r;
    }

    /** Admin duyệt phiếu nhập -> tạo các lô hàng trong kho. */
    public Receipt decideReceipt(Long id, User admin, boolean approve) {
        Receipt r = receiptRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy phiếu nhập."));
        if (r.getStatus() != ApprovalStatus.PENDING) throw new BusinessException("Phiếu nhập không ở trạng thái chờ duyệt.");
        if (!admin.hasPermission(StaffPermission.APPROVE_STOCK)) throw new BusinessException("Bạn chưa được cấp quyền duyệt phiếu nhập.", 403);
        if (approve) {
            for (ReceiptItem it : r.getItems()) {
                Batch b = new Batch();
                b.setProduct(it.getProduct());
                b.setBatchNo(it.getBatchNo());
                b.setMfgDate(it.getMfgDate());
                b.setExpDate(it.getExpDate());
                b.setQuantity(it.getQuantity());
                b.setImportPrice(it.getImportPrice());
                b.setSupplier(r.getSupplier());
                b.setReceipt(r);
                b.setWarehouse(r.getWarehouse());
                batchRepo.save(b);
            }
        }
        if (approve) {
            batchRepo.flush();
            r.getItems().stream().map(ReceiptItem::getProduct).distinct().forEach(care::notifyBackInStock);
        }
        r.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        r.setApprovedBy(admin);
        r.setApprovedAt(LocalDateTime.now());
        if (r.getCreatedBy() != null) {
            notifications.notify(r.getCreatedBy(), "Phiếu nhập " + r.getCode() + (approve ? " đã được duyệt" : " bị từ chối"), "/staff/receipts/" + r.getId());
        }
        if (approve && r.getSupplier() != null) {
            notifications.log(admin, "supplier.debt", r.getSupplier().getName() + ": +" + r.getTotal() + " (" + r.getCode() + ")");
        }
        notifications.log(admin, approve ? "receipt.approve" : "receipt.reject", r.getCode());
        return r;
    }

    /** Khóa / mở khóa lô (thu hồi, nghi ngờ chất lượng). */
    public Batch toggleLock(Long batchId, User user, String reason) {
        Batch b = batch(batchId);
        if (!b.isLocked() && Texts.isBlank(reason)) throw new BusinessException("Vui lòng nhập lý do khóa lô (VD: thu hồi theo công văn...).");
        b.setLocked(!b.isLocked());
        b.setLockReason(b.isLocked() ? Texts.trim(reason, 300) : null);
        if (!b.isLocked()) {
            batchRepo.flush();
            care.notifyBackInStock(b.getProduct());
        }
        notifications.log(user, b.isLocked() ? "batch.lock" : "batch.unlock",
                b.getProduct().getName() + " - lô " + b.getBatchNo() + (b.isLocked() ? ": " + reason : ""));
        return b;
    }

    /**
     * Phiếu hủy thuốc (số âm) / điều chỉnh (số dương). Người có quyền duyệt phiếu kho: áp dụng ngay.
     * Người không có quyền duyệt phiếu kho: tạo phiếu chờ admin / quản lý duyệt (chưa trừ tồn).
     */
    public StockAdjustment adjust(Long batchId, User user, int quantity, String reason) {
        Batch b = batch(batchId);
        if (quantity == 0) throw new BusinessException("Số lượng điều chỉnh phải khác 0.");
        if (Texts.isBlank(reason)) throw new BusinessException("Vui lòng nhập lý do.");
        if (b.getQuantity() + quantity < 0) throw new BusinessException("Lô chỉ còn " + b.getQuantity() + " " + b.getProduct().getUnit() + ".");
        StockAdjustment a = newAdjustment(b, quantity, Texts.trim(reason, 300), quantity < 0 ? "WRITE_OFF" : "MANUAL", user);
        if (user.hasPermission(StaffPermission.APPROVE_STOCK)) apply(a, user);
        else notifications.notifyAdmins((quantity < 0 ? "Phiếu hủy " : "Phiếu điều chỉnh ") + b.getProduct().getName() + " lô " + b.getBatchNo()
                + " chờ duyệt", "/admin/stock-approvals");
        notifications.log(user, "batch.adjust", b.getProduct().getName() + " - lô " + b.getBatchNo() + ": " + (quantity > 0 ? "+" : "") + quantity
                + " (" + reason + ")" + (a.getStatus() == ApprovalStatus.PENDING ? " - chờ duyệt" : ""));
        return a;
    }

    private StockAdjustment newAdjustment(Batch b, int quantity, String reason, String type, User user) {
        StockAdjustment a = new StockAdjustment();
        a.setBatch(b);
        a.setQuantity(quantity);
        a.setReason(reason);
        a.setType(type);
        a.setUser(user);
        a.setStatus(ApprovalStatus.PENDING);
        return adjustmentRepo.save(a);
    }

    /** Áp dụng phiếu vào tồn kho. */
    private void apply(StockAdjustment a, User approver) {
        Batch b = a.getBatch();
        if (b.getQuantity() + a.getQuantity() < 0) {
            throw new BusinessException("Lô " + b.getBatchNo() + " hiện chỉ còn " + b.getQuantity() + " - không đủ để trừ " + (-a.getQuantity()) + ".");
        }
        b.setQuantity(b.getQuantity() + a.getQuantity());
        a.setStatus(ApprovalStatus.APPROVED);
        a.setApprovedBy(approver);
        a.setApprovedAt(LocalDateTime.now());
        if (a.getQuantity() > 0) {
            batchRepo.flush();
            care.notifyBackInStock(b.getProduct());
        }
    }

    /** Admin / quản lý duyệt hoặc từ chối phiếu hủy / điều chỉnh kiểm kê. */
    public StockAdjustment decideAdjustment(Long id, User approver, boolean approve, String reason) {
        StockAdjustment a = adjustmentRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy phiếu."));
        if (a.getStatusValue() != ApprovalStatus.PENDING) throw new BusinessException("Phiếu đã được xử lý.");
        if (!approver.hasPermission(StaffPermission.APPROVE_STOCK)) throw new BusinessException("Bạn chưa được cấp quyền duyệt phiếu kho.", 403);
        if (approve) {
            apply(a, approver);
        } else {
            if (Texts.isBlank(reason)) throw new BusinessException("Vui lòng nhập lý do từ chối.");
            a.setStatus(ApprovalStatus.REJECTED);
            a.setApprovedBy(approver);
            a.setApprovedAt(LocalDateTime.now());
            a.setRejectReason(Texts.trim(reason, 300));
        }
        if (a.getUser() != null && !a.getUser().getId().equals(approver.getId())) {
            notifications.notify(a.getUser(), a.getTypeLabel() + " lô " + a.getBatch().getBatchNo() + (approve ? " đã được duyệt" : " bị từ chối: " + reason),
                    "/staff/adjustments");
        }
        notifications.log(approver, approve ? "stock.approve" : "stock.reject", a.getTypeLabel() + " #" + a.getId() + " - " + a.getBatch().getProduct().getName()
                + " lô " + a.getBatch().getBatchNo() + " (" + (a.getQuantity() > 0 ? "+" : "") + a.getQuantity() + ")");
        return a;
    }

    /**
     * Kiểm kê: nhập số lượng thực đếm cho từng lô; chênh lệch được lập thành phiếu điều chỉnh kiểm kê.
     * Người có quyền duyệt phiếu kho: áp dụng ngay; nhân viên khác: phiếu chờ duyệt. Trả về số lô có chênh lệch.
     */
    public int stocktake(Map<Long, Integer> counted, User user, String note) {
        String reason = "Kiểm kê " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + (Texts.isBlank(note) ? "" : " - " + Texts.trim(note, 150));
        boolean approver = user.hasPermission(StaffPermission.APPROVE_STOCK);
        int n = 0;
        for (Map.Entry<Long, Integer> e : counted.entrySet()) {
            if (e.getValue() == null) continue;
            if (e.getValue() < 0) throw new BusinessException("Số lượng kiểm kê không được âm.");
            Batch b = batch(e.getKey());
            int diff = e.getValue() - b.getQuantity();
            if (diff == 0) continue;
            StockAdjustment a = newAdjustment(b, diff, reason + " (sổ sách " + b.getQuantity() + ", thực tế " + e.getValue() + ")", "STOCKTAKE", user);
            if (approver) apply(a, user);
            n++;
        }
        if (n > 0 && !approver) notifications.notifyAdmins("Phiếu điều chỉnh kiểm kê (" + n + " lô) chờ duyệt", "/admin/stock-approvals");
        notifications.log(user, "inventory.stocktake", n + " lô chênh lệch" + (approver ? "" : " - chờ duyệt"));
        return n;
    }

    /* ======================= Chuyển kho ======================= */

    /** Chuyển hàng giữa các kho: tách lô sang kho đích (giữ số lô, hạn dùng, giá nhập). */
    public TransferSlip transfer(Long fromId, Long toId, Map<Long, Integer> quantities, String note, User user) {
        Warehouse from = warehouseRepo.findById(fromId == null ? -1 : fromId).orElseThrow(() -> new BusinessException("Chọn kho xuất."));
        Warehouse to = warehouseRepo.findById(toId == null ? -1 : toId).orElseThrow(() -> new BusinessException("Chọn kho nhận."));
        if (from.getId().equals(to.getId())) throw new BusinessException("Kho xuất và kho nhận phải khác nhau.");
        if (!to.isActive()) throw new BusinessException("Kho nhận đang ngừng hoạt động.");
        TransferSlip slip = new TransferSlip();
        slip.setCode(Texts.code("CK"));
        slip.setFromWarehouse(from);
        slip.setToWarehouse(to);
        slip.setCreatedBy(user);
        slip.setNote(Texts.emptyToNull(Texts.trim(note, 500)));
        Map<Long, Integer> movedOutOfSale = new java.util.HashMap<>();
        for (Map.Entry<Long, Integer> e : quantities.entrySet()) {
            Integer q = e.getValue();
            if (q == null || q == 0) continue;
            if (q < 0) throw new BusinessException("Số lượng chuyển không được âm.");
            Batch src = batch(e.getKey());
            boolean inFrom = src.getWarehouse() == null ? from.isMain() : src.getWarehouse().getId().equals(from.getId());
            if (!inFrom) throw new BusinessException("Lô " + src.getBatchNo() + " không thuộc " + from.getName() + ".");
            if (src.isLocked()) throw new BusinessException("Lô " + src.getBatchNo() + " đang bị khóa, không được chuyển.");
            if (q > src.getQuantity()) throw new BusinessException("Lô " + src.getBatchNo() + " chỉ còn " + src.getQuantity() + ".");
            src.setQuantity(src.getQuantity() - q);
            Batch dst = batchRepo.findFirstByProductAndBatchNoIgnoreCaseAndWarehouse(src.getProduct(), src.getBatchNo(), to).orElse(null);
            if (dst == null && to.isMain()) {
                dst = batchRepo.findByProductOrderByExpDateAsc(src.getProduct()).stream()
                        .filter(x -> x.getWarehouse() == null && x.getBatchNo().equalsIgnoreCase(src.getBatchNo())).findFirst().orElse(null);
            }
            if (dst == null) {
                dst = new Batch();
                dst.setProduct(src.getProduct());
                dst.setBatchNo(src.getBatchNo());
                dst.setMfgDate(src.getMfgDate());
                dst.setExpDate(src.getExpDate());
                dst.setImportPrice(src.getImportPrice());
                dst.setSupplier(src.getSupplier());
                dst.setReceipt(src.getReceipt());
                dst.setWarehouse(to);
                dst = batchRepo.save(dst);
            }
            dst.setQuantity(dst.getQuantity() + q);
            TransferItem it = new TransferItem();
            it.setSlip(slip);
            it.setSourceBatch(src);
            it.setTargetBatch(dst);
            it.setQuantity(q);
            slip.getItems().add(it);
            if (from.isSellable() && !to.isSellable()) movedOutOfSale.merge(src.getProduct().getId(), q, Integer::sum);
        }
        if (slip.getItems().isEmpty()) throw new BusinessException("Nhập số lượng cần chuyển cho ít nhất 1 lô.");
        // Không được chuyển hàng đang giữ chỗ cho đơn ra khỏi kho bán
        batchRepo.flush();
        for (Map.Entry<Long, Integer> e : movedOutOfSale.entrySet()) {
            Product p = stockService.fill(productRepo.findById(e.getKey()).orElseThrow());
            if (p.getAvailable() < 0) {
                throw new BusinessException("\"" + p.getName() + "\" đang giữ chỗ cho đơn hàng - chỉ được chuyển tối đa " + (p.getAvailable() + e.getValue())
                        + " " + p.getUnit() + " ra khỏi kho bán.");
            }
        }
        transferRepo.save(slip);
        if (!from.isSellable() && to.isSellable()) {
            slip.getItems().stream().map(i -> i.getTargetBatch().getProduct()).distinct().forEach(care::notifyBackInStock);
        }
        notifications.log(user, "stock.transfer", slip.getCode() + ": " + from.getName() + " → " + to.getName() + " (" + slip.getTotalQuantity() + ")");
        return slip;
    }

    /* ======================= Công nợ nhà cung cấp ======================= */

    public record DebtRow(Receipt receipt, LocalDate dueDate, boolean overdue) {
    }

    public record Debt(long purchased, long paid, long balance, long overdue) {
    }

    @Transactional(readOnly = true)
    public Debt debt(Supplier s) {
        long purchased = receiptRepo.findBySupplierAndStatus(s, ApprovalStatus.APPROVED).stream().mapToLong(Receipt::getTotal).sum();
        long paid = paymentRepo.totalPaid(s);
        long balance = purchased - paid;
        // Nợ quá hạn: phần phiếu nhập đã quá hạn thanh toán chưa được trả (trả trước cho phiếu cũ trước - FIFO)
        long dueAmount = receiptRepo.findBySupplierAndStatus(s, ApprovalStatus.APPROVED).stream()
                .filter(r -> r.getApprovedAt() != null && r.getApprovedAt().toLocalDate().plusDays(s.getTermDays()).isBefore(LocalDate.now()))
                .mapToLong(Receipt::getTotal).sum();
        return new Debt(purchased, paid, balance, Math.max(0, Math.min(balance, dueAmount - paid)));
    }

    public SupplierPayment pay(Long supplierId, long amount, LocalDate date, String method, String note, User user) {
        Supplier s = supplierRepo.findById(supplierId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy nhà cung cấp."));
        if (amount <= 0) throw new BusinessException("Số tiền phải lớn hơn 0.");
        long balance = debt(s).balance();
        if (amount > balance) throw new BusinessException("Số tiền trả vượt công nợ hiện tại (" + String.format("%,d", balance).replace(',', '.') + " đ).");
        SupplierPayment p = new SupplierPayment();
        p.setSupplier(s);
        p.setAmount(amount);
        p.setPaidDate(date == null ? LocalDate.now() : date);
        p.setMethod(Texts.emptyToNull(Texts.trim(method, 30)));
        p.setNote(Texts.emptyToNull(Texts.trim(note, 300)));
        p.setCreatedBy(user);
        paymentRepo.save(p);
        notifications.log(user, "supplier.payment", s.getName() + ": " + amount);
        return p;
    }

    /** Gửi thông báo thu hồi tới tất cả khách đã mua lô. */
    public int notifyRecall(Long batchId, User user, String message) {
        Batch b = batch(batchId);
        message = Texts.trim(message, 400);
        if (message.length() < 10) throw new BusinessException("Vui lòng nhập nội dung thông báo.");
        Set<User> buyers = new LinkedHashSet<>();
        for (OrderItemBatch a : allocationRepo.findByBatchWithOrder(b)) buyers.add(a.getOrderItem().getOrder().getUser());
        for (User u : buyers) {
            notifications.notify(u, "[Thu hồi thuốc] " + b.getProduct().getName() + " lô " + b.getBatchNo() + ": " + message, "/consult");
        }
        notifications.log(user, "batch.recall_notify", b.getProduct().getName() + " lô " + b.getBatchNo() + " - " + buyers.size() + " khách");
        return buyers.size();
    }
}
