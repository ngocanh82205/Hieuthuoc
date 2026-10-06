package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Jpql;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Kho: tồn kho theo lô, cảnh báo, khóa / hủy lô, truy vết thu hồi, kiểm kê, phiếu nhập. */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class InventoryController {
    private static final Pattern COUNT_KEY = Pattern.compile("^count\\[(\\d+)]$");
    private final InventoryService inventory;
    private final StockService stock;
    private final SettingService settings;
    private final ReportExportService export;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private Batch batch(Long id) {
        return Web.found(em.find(Batch.class, id));
    }

    private static ApprovalStatus status(String s) {
        try {
            return s == null || s.isBlank() ? null : ApprovalStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GetMapping("/inventory")
    @Transactional(readOnly = true)
    public String inventory(@RequestParam(required = false) String q, @RequestParam(required = false) String filter, Model model) {
        q = Texts.trim(q);
        String like = "%" + q.toLowerCase() + "%";
        // Tìm theo số lô: các lô khớp, nhóm theo sản phẩm
        Map<Long, List<Batch>> lots = q.isEmpty() ? Map.of()
                : em.createQuery("select b from Batch b where lower(b.batchNo) like :q order by b.expDate", Batch.class).setParameter("q", like).getResultList()
                .stream().collect(Collectors.groupingBy(Batch::getProductId, LinkedHashMap::new, Collectors.toList()));
        Jpql j = new Jpql("Product p", "p");
        if (!q.isEmpty() && lots.isEmpty()) {
            j.where("(lower(p.name) like :q or lower(coalesce(p.activeIngredient, '')) like :q)", "q", like);
        } else if (!q.isEmpty()) {
            j.where("(lower(p.name) like :q or lower(coalesce(p.activeIngredient, '')) like :q or p.id in :lots)", "q", like, "lots", lots.keySet());
        }
        List<Product> products = new ArrayList<>(j.list(em, Product.class, "p.name", 0));
        stock.fill(products, false);
        products = products.stream()
                .filter(p -> !"low".equals(filter) || p.isLowStock())
                .filter(p -> !"out".equals(filter) || p.getAvailable() <= 0)
                .sorted(Comparator.comparing((Product p) -> p.isLowStock() ? 0 : 1).thenComparing(Product::getName)).toList();
        Map<Long, LocalDate> nextExp = new HashMap<>();
        for (Object[] r : em.createQuery("select b.productId, min(b.expDate) from Batch b where b.quantity > 0 and b.expDate >= :t group by b.productId", Object[].class)
                .setParameter("t", LocalDate.now()).getResultList()) {
            nextExp.put((Long) r[0], (LocalDate) r[1]);
        }
        model.addAttribute("title", "Tồn kho");
        model.addAttribute("products", products);
        model.addAttribute("nextExp", nextExp);
        model.addAttribute("q", q);
        model.addAttribute("filter", filter);
        model.addAttribute("lots", lots);
        return "staff/inventory";
    }

    @GetMapping("/inventory/alerts")
    @Transactional(readOnly = true)
    public String alerts(Model model) {
        LocalDate today = LocalDate.now();
        int near = settings.getInt("near_expiry_days");
        List<Product> products = new ArrayList<>(em.createQuery("select p from Product p where p.active = true order by p.name", Product.class).getResultList());
        stock.fill(products, false);
        String base = "select b from Batch b join fetch b.product where b.quantity > 0 and ";
        model.addAttribute("title", "Cảnh báo kho");
        model.addAttribute("nearDays", near);
        model.addAttribute("expired", em.createQuery(base + "b.expDate < :t order by b.expDate", Batch.class).setParameter("t", today).getResultList());
        model.addAttribute("nearExpiry", em.createQuery(base + "b.expDate between :a and :b order by b.expDate", Batch.class)
                .setParameter("a", today).setParameter("b", today.plusDays(near)).getResultList());
        model.addAttribute("locked", em.createQuery(base + "b.locked = true order by b.expDate", Batch.class).getResultList());
        model.addAttribute("lowStock", products.stream().filter(Product::isLowStock).sorted(Comparator.comparingLong(Product::getOnHand)).toList());
        return "staff/alerts";
    }

    @GetMapping("/inventory/{id}")
    @Transactional(readOnly = true)
    public String batches(@PathVariable Long id, Model model) {
        Product product = Web.found(em.find(Product.class, id));
        stock.fill(product, false);
        List<Batch> batches = em.createQuery("select b from Batch b left join fetch b.supplier left join fetch b.receipt where b.productId = :p order by b.expDate", Batch.class)
                .setParameter("p", product.getId()).getResultList();
        List<Long> ids = batches.stream().map(Batch::getId).toList();
        Map<Long, Long> sold = new HashMap<>();
        List<StockAdjustment> adjustments = List.of();
        if (!ids.isEmpty()) {
            for (Object[] r : em.createQuery("select a.batchId, sum(a.quantity) from OrderItemBatch a where a.batchId in :ids group by a.batchId", Object[].class)
                    .setParameter("ids", ids).getResultList()) {
                sold.put((Long) r[0], ((Number) r[1]).longValue());
            }
            adjustments = em.createQuery("select a from StockAdjustment a join fetch a.batch left join fetch a.user where a.batch.id in :ids order by a.id desc", StockAdjustment.class)
                    .setParameter("ids", ids).setMaxResults(20).getResultList();
        }
        model.addAttribute("title", "Lô hàng: " + product.getName());
        model.addAttribute("product", product);
        model.addAttribute("batches", batches);
        model.addAttribute("sold", sold);
        model.addAttribute("adjustments", adjustments);
        model.addAttribute("nearDays", settings.getInt("near_expiry_days"));
        return "staff/batches";
    }

    @PostMapping("/batches/{id}/lock")
    @Transactional
    public String lock(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        Batch b = inventory.toggleLock(batch(id), currentUser.get(), reason);
        Web.success(ra, b.isLocked() ? "Đã khóa lô " + b.getBatchNo() + ", lô này sẽ không được xuất bán." : "Đã mở khóa lô " + b.getBatchNo() + ".");
        if (b.isLocked()) {
            List<Order> pending = inventory.pendingOrdersWithBatch(b);
            if (!pending.isEmpty()) {
                Web.warning(ra, "Các đơn đã soạn hàng từ lô này chưa giao, cần đổi lô: " + pending.stream().map(Order::getCode).collect(Collectors.joining(", ")) + ".");
            }
        }
        return "redirect:/staff/inventory/" + b.getProductId();
    }

    @PostMapping("/batches/{id}/adjust")
    @Transactional
    public String adjust(@PathVariable Long id, @RequestParam(defaultValue = "0") int quantity, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        Batch b = batch(id);
        StockAdjustment a = inventory.adjust(b, currentUser.get(), quantity, reason);
        if (a.getStatus() == ApprovalStatus.PENDING) {
            Web.info(ra, "Đã lập " + a.typeLabel().toLowerCase() + " lô " + b.getBatchNo() + " - chờ duyệt, tồn kho chưa thay đổi.");
        } else {
            Web.success(ra, "Đã cập nhật tồn kho lô " + b.getBatchNo() + ".");
        }
        return "redirect:/staff/inventory/" + b.getProductId();
    }

    @GetMapping("/batches/{id}/buyers")
    @Transactional(readOnly = true)
    public String buyers(@PathVariable Long id, Model model) {
        Batch b = batch(id);
        model.addAttribute("title", "Truy vết lô " + b.getBatchNo());
        model.addAttribute("batch", b);
        model.addAttribute("buyers", inventory.buyers(b));
        model.addAttribute("nearDays", settings.getInt("near_expiry_days"));
        return "staff/recall";
    }

    @PostMapping("/batches/{id}/notify-buyers")
    @Transactional
    public String notifyBuyers(@PathVariable Long id, @RequestParam(required = false) String message, RedirectAttributes ra) {
        int n = inventory.notifyRecall(batch(id), currentUser.get(), message);
        Web.success(ra, "Đã gửi thông báo thu hồi đến " + n + " khách hàng.");
        return "redirect:/staff/batches/" + id + "/buyers";
    }

    /* ---------------- Kiểm kê ---------------- */

    @GetMapping("/stocktake")
    @Transactional(readOnly = true)
    public String stocktake(Model model) {
        List<Batch> batches = em.createQuery("select b from Batch b join fetch b.product where b.quantity > 0 or b.updatedAt >= :d order by b.productId, b.expDate", Batch.class)
                .setParameter("d", LocalDateTime.now().minusDays(30)).getResultList();
        model.addAttribute("title", "Kiểm kê kho");
        model.addAttribute("batches", batches);
        model.addAttribute("pendingOut", stock.pendingOutMap(batches.stream().map(Batch::getId).toList()));
        model.addAttribute("recent", em.createQuery("select s from Stocktake s left join fetch s.user order by s.id desc", Stocktake.class).setMaxResults(5).getResultList());
        return "staff/stocktake";
    }

    @GetMapping("/stocktake/history")
    @Transactional(readOnly = true)
    public String stocktakeHistory(@RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("title", "Lịch sử kiểm kê");
        model.addAttribute("list", new Jpql("Stocktake s", "s").page(em, Stocktake.class, "s.id desc", 20, page));
        return "staff/stocktakes";
    }

    @GetMapping("/stocktake/{id}")
    @Transactional(readOnly = true)
    public String stocktakeShow(@PathVariable Long id, Model model) {
        Stocktake st = Web.found(em.find(Stocktake.class, id));
        model.addAttribute("title", "Phiếu kiểm kê " + st.getCode());
        model.addAttribute("st", st);
        int sum = st.getItems().stream().mapToInt(StocktakeItem::diff).sum();
        model.addAttribute("sumBook", st.getItems().stream().mapToInt(StocktakeItem::getBookQty).sum());
        model.addAttribute("sumPending", st.getItems().stream().mapToInt(StocktakeItem::getPendingOutQty).sum());
        model.addAttribute("sumCounted", st.getItems().stream().mapToInt(StocktakeItem::getCountedQty).sum());
        model.addAttribute("sumDiff", sum);
        return "staff/stocktake-show";
    }

    @GetMapping("/stocktake/{id}/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> stocktakeExport(@PathVariable Long id) throws IOException {
        Stocktake st = Web.found(em.find(Stocktake.class, id));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        export.stocktake(st, out);
        return Web.xlsx(out, "bien-ban-kiem-ke-" + st.getCode() + ".xlsx");
    }

    @PostMapping("/stocktake")
    @Transactional
    public String applyStocktake(@RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        Map<String, String> counted = new LinkedHashMap<>();
        params.forEach((k, v) -> {
            Matcher m = COUNT_KEY.matcher(k);
            if (m.matches()) counted.put(m.group(1), v.isEmpty() ? null : v.get(0));
        });
        User me = currentUser.get();
        Stocktake st = inventory.stocktake(counted, me, params.getFirst("note"));
        int n = st.getDiffLines();
        Web.success(ra, "Đã lập phiếu kiểm kê " + st.getCode() + " (" + st.getTotalLines() + " lô). "
                + (n == 0 ? "Tất cả khớp sổ sách."
                : me.hasPermission(StaffPermission.APPROVE_STOCK) ? "Đã điều chỉnh tồn kho cho " + n + " lô chênh lệch."
                : n + " lô chênh lệch đã lập phiếu điều chỉnh - chờ duyệt."));
        return "redirect:/staff/stocktake/" + st.getId();
    }

    /* ---------------- Phiếu hủy / điều chỉnh ---------------- */

    @GetMapping("/adjustments")
    @Transactional(readOnly = true)
    public String adjustments(@RequestParam(required = false) String status, HttpServletRequest req, Model model) {
        ApprovalStatus st = status(status);
        model.addAttribute("title", "Phiếu hủy / điều chỉnh kho");
        model.addAttribute("status", st);
        model.addAttribute("statuses", ApprovalStatus.values());
        model.addAttribute("list", new Jpql("StockAdjustment a", "a").when(st != null, "a.status = :s", "s", st)
                .list(em, StockAdjustment.class, st != null ? "a.id" : "a.id desc", 200));
        model.addAttribute("pendingCount", new Jpql("StockAdjustment a", "a").where("a.status = :s", "s", ApprovalStatus.PENDING).count(em));
        model.addAttribute("back", req.getRequestURI() + (req.getQueryString() != null ? "?" + req.getQueryString() : ""));
        return "staff/adjustments";
    }

    @PostMapping("/adjustments/{id}/decide")
    @Transactional
    public String decideAdjustment(@PathVariable Long id, @RequestParam(required = false) String decision, @RequestParam(required = false) String reason,
                                   @RequestParam(required = false) String back, RedirectAttributes ra) {
        boolean approve = "approve".equals(decision);
        StockAdjustment a = inventory.decideAdjustment(Web.found(em.find(StockAdjustment.class, id)), currentUser.get(), approve, reason);
        Web.success(ra, (approve ? "Đã duyệt " : "Đã từ chối ") + a.typeLabel().toLowerCase() + " lô " + a.getBatch().getBatchNo() + ".");
        return back != null && (back.startsWith("/admin/") || back.startsWith("/staff/")) ? "redirect:" + back : "redirect:/staff/adjustments";
    }

    /* ---------------- Phiếu nhập kho ---------------- */

    @GetMapping("/receipts")
    @Transactional(readOnly = true)
    public String receipts(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page, Model model) {
        ApprovalStatus st = status(status);
        model.addAttribute("title", "Phiếu nhập kho");
        model.addAttribute("status", st);
        model.addAttribute("statuses", ApprovalStatus.values());
        model.addAttribute("receipts", new Jpql("Receipt r", "r").when(st != null, "r.status = :s", "s", st).page(em, Receipt.class, "r.id desc", 30, page));
        return "staff/receipts";
    }

    @GetMapping("/receipts/new")
    @Transactional(readOnly = true)
    public String newReceipt(Model model) {
        List<Map<String, Object>> productData = new ArrayList<>();
        for (Product p : em.createQuery("select p from Product p order by p.name", Product.class).getResultList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("unit", p.getUnit());
            m.put("ingredient", p.getActiveIngredient());
            m.put("reg", p.getRegistrationNo());
            productData.add(m);
        }
        model.addAttribute("title", "Tạo phiếu nhập");
        model.addAttribute("suppliers", em.createQuery("select s from Supplier s order by s.name", Supplier.class).getResultList());
        model.addAttribute("productData", productData);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("tomorrow", LocalDate.now().plusDays(1));
        return "staff/receipt-form";
    }

    @PostMapping("/receipts")
    @Transactional
    public String createReceipt(@RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        Receipt r = inventory.createReceipt(new Form(params), currentUser.get());
        Web.success(ra, "Đã tạo phiếu nhập " + r.getCode() + ", chờ duyệt.");
        return "redirect:/staff/receipts/" + r.getId();
    }

    @GetMapping("/receipts/{id}")
    @Transactional(readOnly = true)
    public String receipt(@PathVariable Long id, Model model) {
        Receipt r = Web.found(em.find(Receipt.class, id));
        model.addAttribute("title", "Phiếu nhập " + r.getCode());
        model.addAttribute("receipt", r);
        model.addAttribute("clashes", r.getStatus() == ApprovalStatus.PENDING ? inventory.batchNoClashes(r) : Map.of());
        return "staff/receipt";
    }

    @PostMapping("/receipts/{id}/decide")
    @Transactional
    public String decideReceipt(@PathVariable Long id, @RequestParam(required = false) String decision, @RequestParam(required = false) String back, RedirectAttributes ra) {
        boolean approve = "approve".equals(decision);
        Receipt r = Web.found(em.find(Receipt.class, id));
        inventory.decideReceipt(r, currentUser.get(), approve);
        Web.success(ra, approve ? "Đã duyệt phiếu nhập, hàng đã vào kho." : "Đã từ chối phiếu nhập.");
        return back != null && back.startsWith("/admin/") ? "redirect:" + back : "redirect:/staff/receipts/" + r.getId();
    }
}
