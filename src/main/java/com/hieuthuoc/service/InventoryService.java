package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Kho: phiếu nhập & duyệt, khóa lô, hủy / điều chỉnh, kiểm kê, thu hồi. */
@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationService notifications;
    private final StockService stock;
    private final MailService mail;

    @PersistenceContext
    private EntityManager em;

    private static String clean(String v, int max) {
        String t = Texts.trim(v);
        return t.isEmpty() ? null : Texts.limit(t, max, "");
    }

    private static String fmt(LocalDate d) {
        return d == null ? "" : d.format(DF);
    }

    private static LocalDate parseDate(String s, String prefix) {
        try {
            return LocalDate.parse(s.trim().substring(0, 10));
        } catch (RuntimeException e) {
            throw new BusinessException(prefix + "ngày không hợp lệ.");
        }
    }

    /* ======================= Phiếu nhập ======================= */

    /** Nhân viên tạo phiếu nhập (chờ người có quyền duyệt phiếu kho duyệt). rows[i][product_id, batch_no, mfg_date, exp_date, quantity, import_price] */
    public Receipt createReceipt(Form f, User user) {
        List<ReceiptItem> items = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        List<Map<String, String>> rows = f.rows("rows");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int n = i + 1;
            if (Texts.isBlank(row.get("product_id"))) continue;
            int qty = Texts.toInt(row.get("quantity"), 0);
            if (Texts.trim(row.get("batch_no")).isEmpty() || Texts.isBlank(row.get("exp_date")) || qty <= 0) {
                throw new BusinessException("Dòng " + n + ": cần số lô, hạn dùng và số lượng > 0.");
            }
            LocalDate exp = parseDate(row.get("exp_date"), "Dòng " + n + ": ");
            LocalDate mfg = !Texts.isBlank(row.get("mfg_date")) ? parseDate(row.get("mfg_date"), "Dòng " + n + ": ") : null;
            LocalDate today = LocalDate.now();
            if (mfg != null && mfg.isAfter(today)) throw new BusinessException("Dòng " + n + ": ngày sản xuất không được sau ngày hôm nay.");
            if (mfg != null && !mfg.isBefore(exp)) throw new BusinessException("Dòng " + n + ": ngày sản xuất phải trước hạn dùng.");
            if (!exp.isAfter(today)) throw new BusinessException("Dòng " + n + ": không nhập hàng đã hết hạn.");
            Long pid = Texts.toLong(row.get("product_id"));
            Product p = pid == null ? null : em.find(Product.class, pid);
            if (p == null) throw new BusinessException("Dòng " + n + ": sản phẩm không tồn tại.");
            String batchNo = Texts.trim(row.get("batch_no"), 50);
            String dupKey = p.getId() + "|" + batchNo.toLowerCase();
            if (seen.containsKey(dupKey)) {
                throw new BusinessException("Dòng " + n + ": trùng lô " + batchNo + " của " + p.getName() + " với dòng " + seen.get(dupKey)
                        + " - hãy gộp số lượng vào một dòng.");
            }
            seen.put(dupKey, n);
            assertSameLot(p, batchNo, exp, mfg, "Dòng " + n + ": ");
            ReceiptItem it = new ReceiptItem();
            it.setProduct(p);
            it.setBatchNo(batchNo);
            it.setMfgDate(mfg);
            it.setExpDate(exp);
            it.setQuantity(qty);
            it.setImportPrice(Math.max(0, Texts.toInt(row.get("import_price"), 0)));
            items.add(it);
        }
        if (items.isEmpty()) throw new BusinessException("Phiếu nhập cần ít nhất 1 sản phẩm.");
        Receipt r = new Receipt();
        r.setCode(Texts.code("PN"));
        r.setCreator(user);
        r.setStatus(ApprovalStatus.PENDING);
        r.setNote(clean(f.get("note"), 500));
        Long sid = f.longVal("supplier_id");
        r.setSupplier(sid == null ? null : em.find(Supplier.class, sid));
        for (ReceiptItem it : items) {
            it.setReceipt(r);
            r.getItems().add(it);
        }
        em.persist(r);
        notifications.log(user, "receipt.create", r.getCode());
        notifications.notifyPermission("APPROVE_STOCK", "Phiếu nhập " + r.getCode() + " chờ duyệt", "/staff/receipts/" + r.getId());
        return r;
    }

    /**
     * Số lô là duy nhất trong từng thuốc: cùng thuốc + cùng số lô thì phải cùng NSX / HSD với lô đã có trong kho.
     * (Thuốc khác trùng số lô là hợp lệ - mỗi hãng tự đánh số lô.)
     */
    private void assertSameLot(Product p, String batchNo, LocalDate exp, LocalDate mfg, String prefix) {
        List<Batch> l = em.createQuery("select b from Batch b where b.product.id = :p and lower(b.batchNo) = :n order by b.id", Batch.class)
                .setParameter("p", p.getId()).setParameter("n", batchNo.toLowerCase()).setMaxResults(1).getResultList();
        if (l.isEmpty()) return;
        Batch old = l.get(0);
        if (!old.getExpDate().equals(exp) || (mfg != null && old.getMfgDate() != null && !old.getMfgDate().equals(mfg))) {
            throw new BusinessException(prefix + "lô " + batchNo + " của " + p.getName() + " đã có trong kho với NSX " + fmt(old.getMfgDate())
                    + " / HSD " + fmt(old.getExpDate()) + " - cùng một lô thì ngày phải trùng khớp, hãy kiểm tra lại số lô hoặc ngày.");
        }
    }

    /** Dòng của phiếu có số lô trùng với thuốc khác (trong phiếu hoặc trong kho): item_id => [tên thuốc...] - để cảnh báo người duyệt. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> batchNoClashes(Receipt r) {
        Map<Long, List<String>> out = new HashMap<>();
        for (ReceiptItem it : r.getItems()) {
            String key = it.getBatchNo().toLowerCase();
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (ReceiptItem x : r.getItems()) {
                if (!x.getProduct().getId().equals(it.getProduct().getId()) && x.getBatchNo().toLowerCase().equals(key)) names.add(x.getProduct().getName());
            }
            for (Batch b : em.createQuery("select b from Batch b join fetch b.product where b.product.id <> :p and lower(b.batchNo) = :n", Batch.class)
                    .setParameter("p", it.getProduct().getId()).setParameter("n", key).getResultList()) {
                names.add(b.getProduct().getName());
            }
            if (!names.isEmpty()) out.put(it.getId(), new ArrayList<>(names));
        }
        return out;
    }

    /** Duyệt phiếu nhập -> tạo các lô hàng trong kho; từ chối -> không nhập. */
    public Receipt decideReceipt(Receipt r, User admin, boolean approve) {
        if (r.getStatus() != ApprovalStatus.PENDING) throw new BusinessException("Phiếu nhập không ở trạng thái chờ duyệt.");
        if (!admin.hasPermission(StaffPermission.APPROVE_STOCK)) throw BusinessException.forbidden("Bạn chưa được cấp quyền duyệt phiếu nhập.");
        if (approve) {
            // Kiểm tra lại khi duyệt (chặn cả phiếu lập trước khi có ràng buộc ngày)
            for (ReceiptItem it : r.getItems()) {
                String name = it.getProduct() != null ? it.getProduct().getName() : "";
                if (it.getMfgDate() != null && it.getMfgDate().isAfter(LocalDate.now())) {
                    throw new BusinessException(name + " - lô " + it.getBatchNo() + ": ngày sản xuất " + fmt(it.getMfgDate())
                            + " ở tương lai. Hãy từ chối phiếu này và lập lại phiếu nhập.");
                }
                if (!it.getExpDate().isAfter(LocalDate.now())) {
                    throw new BusinessException(name + " - lô " + it.getBatchNo() + ": đã hết hạn dùng, không thể nhập kho.");
                }
                assertSameLot(it.getProduct(), it.getBatchNo(), it.getExpDate(), it.getMfgDate(), "");
            }
        }
        // Khóa phiếu: bấm duyệt 2 lần / 2 người cùng duyệt thì lần sau bị chặn, không tạo lô trùng
        em.refresh(r, LockModeType.PESSIMISTIC_WRITE);
        if (r.getStatus() != ApprovalStatus.PENDING) throw new BusinessException("Phiếu nhập " + r.getCode() + " đã được xử lý.");
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
                em.persist(b);
            }
        }
        r.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        r.setApprover(admin);
        r.setApprovedAt(LocalDateTime.now().withNano(0));
        if (r.getCreator() != null && !r.getCreator().getId().equals(admin.getId())) {
            notifications.notify(r.getCreator(), "Phiếu nhập " + r.getCode() + (approve ? " đã được duyệt" : " bị từ chối"), "/staff/receipts/" + r.getId());
        }
        notifications.log(admin, approve ? "receipt.approve" : "receipt.reject", r.getCode());
        return r;
    }

    /* ======================= Lô hàng ======================= */

    /** Khóa / mở khóa lô (thu hồi, nghi ngờ chất lượng): lô khóa không được xuất bán. */
    public Batch toggleLock(Batch b, User user, String reason) {
        if (!b.isLocked() && Texts.trim(reason).isEmpty()) {
            throw new BusinessException("Vui lòng nhập lý do khóa lô (VD: thu hồi theo công văn...).");
        }
        boolean locking = !b.isLocked();
        b.setLocked(locking);
        b.setLockReason(locking ? Texts.trim(reason, 300) : null);
        notifications.log(user, locking ? "batch.lock" : "batch.unlock", b.getProduct().getName() + " - lô " + b.getBatchNo() + (locking ? ": " + reason : ""));
        if (locking) {
            for (Order o : pendingOrdersWithBatch(b)) {
                notifications.notifyPermission("ORDER", "Đơn " + o.getCode() + " có hàng thuộc lô " + b.getBatchNo() + " vừa bị khóa - đổi lô trước khi giao",
                        "/staff/orders/" + o.getId());
            }
        }
        return b;
    }

    /** Đơn đang soạn / đã đóng gói (chưa giao đi) có hàng xuất từ lô này. */
    @Transactional(readOnly = true)
    public List<Order> pendingOrdersWithBatch(Batch b) {
        return em.createQuery("select distinct o from Order o join o.items i join i.allocations a where o.status in :st and a.batch.id = :b order by o.id", Order.class)
                .setParameter("st", List.of(OrderStatus.PREPARING, OrderStatus.PACKED)).setParameter("b", b.getId()).getResultList();
    }

    /**
     * Phiếu hủy (số âm) / điều chỉnh (số dương). Có quyền duyệt phiếu kho: áp dụng ngay;
     * không có: tạo phiếu chờ duyệt (chưa trừ tồn).
     */
    public StockAdjustment adjust(Batch b, User user, int quantity, String reason) {
        if (quantity == 0) throw new BusinessException("Số lượng điều chỉnh phải khác 0.");
        if (Texts.trim(reason).isEmpty()) throw new BusinessException("Vui lòng nhập lý do.");
        if (b.getQuantity() + quantity < 0) throw new BusinessException("Lô chỉ còn " + b.getQuantity() + " " + b.getProduct().getUnit() + ".");
        StockAdjustment a = newAdjustment(b, quantity, Texts.trim(reason, 300), quantity < 0 ? "WRITE_OFF" : "MANUAL", user);
        if (user.hasPermission(StaffPermission.APPROVE_STOCK)) apply(a, user);
        if (a.getStatus() == ApprovalStatus.PENDING) {
            notifications.notifyPermission("APPROVE_STOCK", (quantity < 0 ? "Phiếu hủy " : "Phiếu điều chỉnh ") + b.getProduct().getName() + " lô "
                    + b.getBatchNo() + " chờ duyệt", "/admin/stock-approvals");
        }
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
        em.persist(a);
        return a;
    }

    private void apply(StockAdjustment a, User approver) {
        Batch b = em.find(Batch.class, a.getBatch().getId(), LockModeType.PESSIMISTIC_WRITE);
        em.refresh(b);
        if (b.getQuantity() + a.getQuantity() < 0) {
            throw new BusinessException("Lô " + b.getBatchNo() + " hiện chỉ còn " + b.getQuantity() + " - không đủ để trừ " + (-a.getQuantity()) + ".");
        }
        b.setQuantity(b.getQuantity() + a.getQuantity());
        a.setStatus(ApprovalStatus.APPROVED);
        a.setApprover(approver);
        a.setApprovedAt(LocalDateTime.now().withNano(0));
    }

    public StockAdjustment decideAdjustment(StockAdjustment a, User approver, boolean approve, String reason) {
        if (!approver.hasPermission(StaffPermission.APPROVE_STOCK)) throw BusinessException.forbidden("Bạn chưa được cấp quyền duyệt phiếu kho.");
        if (!approve && Texts.trim(reason).isEmpty()) throw new BusinessException("Vui lòng nhập lý do từ chối.");
        // Khóa phiếu: bấm duyệt 2 lần thì lần sau thấy đã xử lý, không cộng / trừ kho lần nữa
        em.refresh(a, LockModeType.PESSIMISTIC_WRITE);
        if (a.getStatus() != ApprovalStatus.PENDING) throw new BusinessException("Phiếu đã được xử lý.");
        if (approve) {
            apply(a, approver);
        } else {
            a.setStatus(ApprovalStatus.REJECTED);
            a.setApprover(approver);
            a.setApprovedAt(LocalDateTime.now().withNano(0));
            a.setRejectReason(Texts.trim(reason, 300));
        }
        if (a.getUser() != null && !a.getUser().getId().equals(approver.getId())) {
            notifications.notify(a.getUser(), a.typeLabel() + " lô " + a.getBatch().getBatchNo() + (approve ? " đã được duyệt" : " bị từ chối: " + reason), "/staff/adjustments");
        }
        notifications.log(approver, approve ? "stock.approve" : "stock.reject", a.typeLabel() + " #" + a.getId() + " - " + a.getBatch().getProduct().getName()
                + " lô " + a.getBatch().getBatchNo() + " (" + (a.getQuantity() > 0 ? "+" : "") + a.getQuantity() + ")");
        return a;
    }

    /**
     * Lập phiếu kiểm kê: ghi lại mọi lô đã kiểm (ô bỏ trống = khớp sổ sách); lô chênh lệch sinh phiếu điều chỉnh
     * (người có quyền duyệt phiếu kho thì áp dụng ngay, còn lại chờ duyệt).
     *
     * @param counted batch_id => số thực đếm
     */
    public Stocktake stocktake(Map<String, String> counted, User user, String note0) {
        String note = note0 != null && !note0.trim().isEmpty() ? Texts.trim(note0, 150) : null;
        boolean approver = user.hasPermission(StaffPermission.APPROVE_STOCK);
        for (String real : counted.values()) {
            if (real != null && !real.isEmpty() && (!real.trim().matches("-?\\d+(\\.\\d+)?") || Double.parseDouble(real.trim()) < 0)) {
                throw new BusinessException("Số lượng kiểm kê không hợp lệ: " + real);
            }
        }
        Stocktake st = new Stocktake();
        st.setCode(Texts.code("KK"));
        st.setUser(user);
        st.setNote(note);
        em.persist(st);
        String reason = "Kiểm kê " + st.getCode() + (note != null ? " - " + note : "");
        int total = 0, diffs = 0;
        List<Long> ids = counted.keySet().stream().map(Texts::toLong).filter(Objects::nonNull).toList();
        Map<Long, Batch> batches = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Batch b : em.createQuery("select b from Batch b join fetch b.product where b.id in :ids", Batch.class).setParameter("ids", ids)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList()) batches.put(b.getId(), b);
        }
        Map<Long, Long> pendingOut = stock.pendingOutMap(ids);
        for (Map.Entry<String, String> e : counted.entrySet()) {
            Long id = Texts.toLong(e.getKey());
            Batch b = id == null ? null : batches.get(id);
            if (b == null) throw BusinessException.notFound("Không tìm thấy lô hàng.");
            // Hàng đã xuất cho đơn đang soạn / đóng gói vẫn nằm trên kệ: phải có = sổ sách + chờ giao
            int pending = (int) (long) pendingOut.getOrDefault(b.getId(), 0L);
            int expected = b.getQuantity() + pending;
            String rv = e.getValue();
            int real = rv == null || rv.isEmpty() ? expected : (int) Double.parseDouble(rv.trim());
            if (real < pending) {
                throw new BusinessException(b.getProduct().getName() + " - lô " + b.getBatchNo() + ": thực đếm " + real + " ít hơn " + pending
                        + " đã soạn cho đơn chờ giao. Kiểm tra lại cả hàng ở khu đóng gói.");
            }
            int bookQty = b.getQuantity();
            StockAdjustment adjustment = null;
            if (real != expected) {
                adjustment = newAdjustment(b, real - expected, reason + " (sổ sách " + bookQty + (pending > 0 ? " + " + pending + " chờ giao" : "")
                        + ", thực tế " + real + ")", "STOCKTAKE", user);
                if (approver) apply(adjustment, user);
                diffs++;
            }
            StocktakeItem si = new StocktakeItem();
            si.setStocktake(st);
            si.setBatch(b);
            si.setProduct(b.getProduct());
            si.setProductName(b.getProduct().getName());
            si.setUnit(b.getProduct().getUnit());
            si.setBatchNo(b.getBatchNo());
            si.setExpDate(b.getExpDate());
            si.setBookQty(bookQty);
            si.setPendingOutQty(pending);
            si.setCountedQty(real);
            si.setAdjustment(adjustment);
            st.getItems().add(si);
            em.persist(si);
            total++;
        }
        st.setTotalLines(total);
        st.setDiffLines(diffs);
        if (st.getDiffLines() > 0 && !approver) {
            notifications.notifyPermission("APPROVE_STOCK", "Phiếu điều chỉnh kiểm kê " + st.getCode() + " (" + st.getDiffLines() + " lô) chờ duyệt", "/admin/stock-approvals");
        }
        notifications.log(user, "inventory.stocktake", st.getCode() + ": " + st.getTotalLines() + " lô, " + st.getDiffLines() + " lô chênh lệch"
                + (approver || st.getDiffLines() == 0 ? "" : " - chờ duyệt"));
        return st;
    }

    /* ======================= Thu hồi ======================= */

    @Transactional(readOnly = true)
    public List<OrderItemBatch> buyers(Batch b) {
        return em.createQuery("select a from OrderItemBatch a join fetch a.orderItem oi join fetch oi.order o join fetch o.user where a.batch.id = :b"
                + " order by o.createdAt desc", OrderItemBatch.class).setParameter("b", b.getId()).getResultList();
    }

    /** Gửi thông báo thu hồi (web popup + email) tới tất cả khách đã mua lô. */
    public int notifyRecall(Batch b, User user, String message0) {
        String message = Texts.trim(message0, 400);
        if (Texts.mbLen(message) < 10) throw new BusinessException("Vui lòng nhập nội dung thông báo.");
        Map<Long, User> buyers = new LinkedHashMap<>();
        for (OrderItemBatch a : buyers(b)) {
            User u = a.getOrderItem().getOrder().getUser();
            if (u != null && !u.isLocked()) buyers.putIfAbsent(u.getId(), u);
        }
        for (User u : buyers.values()) {
            String text = "[Thu hồi thuốc] " + b.getProduct().getName() + " lô " + b.getBatchNo() + ": " + message;
            notifications.notify(u, text, "/consult", true);
            mail.send(u, "Thông báo thu hồi thuốc " + b.getProduct().getName(),
                    List.of(text, "Vui lòng ngừng sử dụng và liên hệ nhà thuốc để được hỗ trợ đổi / hoàn tiền."), "Liên hệ dược sĩ", mail.url("/consult"));
        }
        notifications.log(user, "batch.recall_notify", b.getProduct().getName() + " lô " + b.getBatchNo() + " - " + buyers.size() + " khách");
        return buyers.size();
    }
}
