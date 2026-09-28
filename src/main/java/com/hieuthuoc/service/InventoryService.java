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

    @Getter
    @Setter
    public static class ReceiptForm {
        private Long supplierId;
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
                batchRepo.save(b);
            }
        }
        r.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        r.setApprovedBy(admin);
        r.setApprovedAt(LocalDateTime.now());
        if (r.getCreatedBy() != null) {
            notifications.notify(r.getCreatedBy(), "Phiếu nhập " + r.getCode() + (approve ? " đã được duyệt" : " bị từ chối"), "/staff/receipts/" + r.getId());
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
        notifications.log(user, b.isLocked() ? "batch.lock" : "batch.unlock",
                b.getProduct().getName() + " - lô " + b.getBatchNo() + (b.isLocked() ? ": " + reason : ""));
        return b;
    }

    /** Hủy thuốc (số âm) hoặc điều chỉnh kiểm kê. Chỉ admin được điều chỉnh tăng. */
    public Batch adjust(Long batchId, User user, int quantity, String reason) {
        Batch b = batch(batchId);
        if (quantity == 0) throw new BusinessException("Số lượng điều chỉnh phải khác 0.");
        if (Texts.isBlank(reason)) throw new BusinessException("Vui lòng nhập lý do.");
        if (b.getQuantity() + quantity < 0) throw new BusinessException("Lô chỉ còn " + b.getQuantity() + " " + b.getProduct().getUnit() + ".");
        if (quantity > 0 && user.getRole() != Role.ADMIN) throw new BusinessException("Chỉ admin được điều chỉnh tăng tồn kho.");
        b.setQuantity(b.getQuantity() + quantity);
        StockAdjustment a = new StockAdjustment();
        a.setBatch(b);
        a.setQuantity(quantity);
        a.setReason(Texts.trim(reason, 300));
        a.setUser(user);
        adjustmentRepo.save(a);
        notifications.log(user, "batch.adjust", b.getProduct().getName() + " - lô " + b.getBatchNo() + ": " + (quantity > 0 ? "+" : "") + quantity + " (" + reason + ")");
        return b;
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
