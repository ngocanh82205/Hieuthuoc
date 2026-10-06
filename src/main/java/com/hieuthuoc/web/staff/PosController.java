package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Jpql;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Bán hàng tại quầy (POS). */
@Controller
@RequestMapping("/staff/pos")
@RequiredArgsConstructor
public class PosController {
    private final PosService pos;
    private final ProductService products;
    private final StockService stock;
    private final SafetyService safety;
    private final SettingService settings;
    private final CustomerService customers;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    @Transactional(readOnly = true)
    public String index(@RequestParam(required = false) String q, @RequestParam(required = false) Long done, Model model) {
        q = Texts.trim(q);
        PosService.View v = pos.view();
        List<Product> results = new ArrayList<>();
        if (!q.isEmpty()) {
            ProductService.Filter f = new ProductService.Filter();
            f.q = q;
            f.sort = "name";
            results.addAll(products.search(f).stream().limit(12).toList());
            stock.fill(results, false);
        }
        List<SafetyService.Warning> warnings = v.getCustomer() != null
                ? safety.check(v.getCustomer(), v.getLines().stream().map(PosService.Line::getProduct).toList(), safety.recentProducts(v.getCustomer())) : List.of();
        model.addAttribute("title", "Bán hàng tại quầy");
        model.addAttribute("q", q);
        model.addAttribute("results", results);
        model.addAttribute("pos", v);
        model.addAttribute("warnings", warnings);
        model.addAttribute("hasDanger", warnings.stream().anyMatch(SafetyService.Warning::isDanger));
        model.addAttribute("done", done != null ? em.find(Order.class, done) : null);
        return "staff/pos";
    }

    /** Lịch sử bán tại quầy: hóa đơn POS theo ngày + tổng hợp đối soát (tiền mặt / chuyển khoản). */
    @GetMapping("/history")
    @Transactional(readOnly = true)
    public String history(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                          @RequestParam(required = false) String payment, @RequestParam(defaultValue = "1") int page, Model model) {
        LocalDate t = Texts.isBlank(to) ? LocalDate.now() : LocalDate.parse(to);
        LocalDate f = Texts.isBlank(from) ? t : LocalDate.parse(from);
        if (f.isAfter(t)) {
            LocalDate x = f;
            f = t;
            t = x;
        }
        PaymentMethod pm = "CASH".equals(payment) ? PaymentMethod.CASH : "BANK_TRANSFER".equals(payment) ? PaymentMethod.BANK_TRANSFER : null;
        Jpql base = new Jpql("Order o", "o").where("o.channel = 'POS'")
                .where("o.createdAt >= :a and o.createdAt < :b", "a", f.atStartOfDay(), "b", t.plusDays(1).atStartOfDay())
                .when(pm != null, "o.paymentMethod = :pm", "pm", pm);
        long count = 0, revenue = 0, cash = 0, bank = 0;
        for (Order o : base.list(em, Order.class, null, 0)) {
            if (o.getStatus() == OrderStatus.CANCELLED) continue;
            count++;
            revenue += o.getTotal();
            if (o.getPaymentMethod() == PaymentMethod.CASH) cash += o.getTotal();
            if (o.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) bank += o.getTotal();
        }
        long cancelled = base.list(em, Order.class, null, 0).stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        int days = settings.getInt("pos_cancel_days");
        model.addAttribute("title", "Lịch sử bán tại quầy");
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("payment", pm);
        model.addAttribute("payments", List.of(PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER));
        model.addAttribute("orders", base.page(em, Order.class, "o.id desc", 30, page));
        model.addAttribute("cancelled", cancelled);
        model.addAttribute("pending", em.createQuery("select r from BillCancelRequest r join fetch r.order left join fetch r.requester where r.status = :s order by r.id",
                BillCancelRequest.class).setParameter("s", ApprovalStatus.PENDING).getResultList());
        model.addAttribute("count", count);
        model.addAttribute("revenue", revenue);
        model.addAttribute("cash", cash);
        model.addAttribute("bank", bank);
        model.addAttribute("cancelDays", days);
        return "staff/pos-history";
    }

    /** Chi tiết hóa đơn bán tại quầy (không phải đơn hàng online cần xử lý giao hàng). */
    @GetMapping("/bills/{id}")
    @Transactional(readOnly = true)
    public String bill(@PathVariable Long id, Model model) {
        Order o = Web.found(em.find(Order.class, id));
        if (!o.isPos()) throw new BusinessException("Không tìm thấy hóa đơn.", 404);
        model.addAttribute("title", "Hóa đơn " + o.getCode());
        model.addAttribute("order", o);
        return "staff/pos-bill";
    }

    @PostMapping("/orders/{id}/cancel-request")
    @Transactional
    public String requestCancel(@PathVariable Long id, @RequestParam MultiValueMap<String, String> params, HttpServletRequest req, RedirectAttributes ra) {
        Order o = Web.found(em.find(Order.class, id));
        pos.requestCancel(o, currentUser.get(), new Form(params));
        Web.success(ra, "Đã gửi phiếu hủy hóa đơn " + o.getCode() + " - chờ quản trị viên duyệt.");
        return Web.back(req, "/staff/pos/history");
    }

    @PostMapping("/cancel-requests/{id}/approve")
    @Transactional
    public String approveCancel(@PathVariable Long id, @RequestParam(required = false) String note, HttpServletRequest req, RedirectAttributes ra) {
        BillCancelRequest r = Web.found(em.find(BillCancelRequest.class, id));
        pos.approveCancel(r, currentUser.get(), note);
        Web.success(ra, "Đã hủy hóa đơn " + r.getOrder().getCode() + " và ghi nhận hoàn tiền cho khách.");
        return Web.back(req, "/staff/pos/history");
    }

    @PostMapping("/add")
    public String add(@RequestParam(name = "product_id", defaultValue = "0") long productId, @RequestParam(name = "unit_id", defaultValue = "0") long unitId,
                      @RequestParam(defaultValue = "1") int qty, @RequestParam(required = false) String q, RedirectAttributes ra) {
        pos.add(productId, unitId, qty);
        if (!Texts.isBlank(q)) ra.addAttribute("q", q);
        return "redirect:/staff/pos";
    }

    @PostMapping("/update")
    public String update(@RequestParam(required = false) String key, @RequestParam(defaultValue = "0") int qty) {
        pos.update(key == null ? "" : key, qty);
        return "redirect:/staff/pos";
    }

    @PostMapping("/customer")
    @Transactional(readOnly = true)
    public String customer(@RequestParam(required = false) String clear, @RequestParam(required = false) String phone, RedirectAttributes ra) {
        if (clear != null) {
            pos.setCustomer(null);
            return "redirect:/staff/pos";
        }
        User u = pos.findCustomer(phone);
        pos.setCustomer(u.getId());
        Web.success(ra, "Khách hàng: " + u.getFullName() + " - " + OrderService.num(u.getPoints()) + " điểm, hạng " + customers.tier(u).getLabel() + ".");
        return "redirect:/staff/pos";
    }

    @PostMapping("/checkout")
    @Transactional
    public String checkout(@RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        Order o = pos.checkout(currentUser.get(), new Form(params));
        Web.success(ra, "Đã lập hóa đơn " + o.getCode() + " - " + OrderService.money(o.getTotal()) + ".");
        ra.addAttribute("done", o.getId());
        return "redirect:/staff/pos";
    }

    @PostMapping("/clear")
    public String clear() {
        pos.clear();
        return "redirect:/staff/pos";
    }
}
