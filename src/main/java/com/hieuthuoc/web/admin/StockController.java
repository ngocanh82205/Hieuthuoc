package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Web;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

/** Admin: duyệt phiếu kho, nhà cung cấp. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class StockController {
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/stock-approvals")
    @Transactional(readOnly = true)
    public String approvals(Model model) {
        model.addAttribute("title", "Duyệt phiếu kho");
        model.addAttribute("receipts", em.createQuery("select r from Receipt r left join fetch r.supplier left join fetch r.creator where r.status = :s order by r.id", Receipt.class)
                .setParameter("s", ApprovalStatus.PENDING).getResultList());
        model.addAttribute("adjustments", em.createQuery("select a from StockAdjustment a join fetch a.batch b join fetch b.product left join fetch a.user where a.status = :s order by a.id",
                StockAdjustment.class).setParameter("s", ApprovalStatus.PENDING).getResultList());
        return "admin/stock-approvals";
    }

    @GetMapping("/suppliers")
    @Transactional(readOnly = true)
    public String suppliers(@RequestParam(required = false) Long edit, Model model) {
        Map<Long, Long> receipts = new HashMap<>();
        for (Object[] r : em.createQuery("select r.supplier.id, count(r) from Receipt r where r.supplier is not null group by r.supplier.id", Object[].class).getResultList()) {
            receipts.put((Long) r[0], (Long) r[1]);
        }
        model.addAttribute("title", "Nhà cung cấp");
        model.addAttribute("list", em.createQuery("select s from Supplier s order by s.name", Supplier.class).getResultList());
        model.addAttribute("receipts", receipts);
        model.addAttribute("edit", edit != null ? em.find(Supplier.class, edit) : null);
        return "admin/suppliers";
    }

    @PostMapping({"/suppliers-save", "/suppliers-save/{id}"})
    @Transactional
    public String saveSupplier(@PathVariable(required = false) Long id, @RequestParam Map<String, String> in, RedirectAttributes ra) {
        String name = Texts.trim(in.get("name"));
        if (Texts.mbLen(name) < 3) throw new BusinessException("Vui lòng nhập tên nhà cung cấp.");
        String email = Texts.trim(in.get("email"));
        if (!email.isEmpty() && !Texts.isEmail(email)) throw new BusinessException("Email không hợp lệ.");
        Supplier s = id != null ? Web.found(em.find(Supplier.class, id)) : new Supplier();
        s.setName(Texts.trim(name, 200));
        s.setPhone(Texts.emptyToNull(Texts.trim(in.get("phone"), 20)));
        s.setEmail(email.isEmpty() ? null : email);
        s.setAddress(Texts.emptyToNull(Texts.trim(in.get("address"), 300)));
        s.setTaxCode(Texts.emptyToNull(Texts.trim(in.get("tax_code"), 20)));
        if (s.getId() == null) em.persist(s);
        notifications.log(currentUser.get(), "supplier.save", s.getName());
        Web.success(ra, "Đã lưu nhà cung cấp " + s.getName() + ".");
        return "redirect:/admin/suppliers";
    }
}
