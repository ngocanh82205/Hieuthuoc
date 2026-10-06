package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.ProductRepository;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/** Bán hàng tại quầy (POS). */
@Controller
@RequestMapping("/staff/pos")
@RequiredArgsConstructor
public class PosController {
    private final PosCart posCart;
    private final PosService posService;
    private final ProductService productService;
    private final ProductRepository productRepo;
    private final SafetyService safety;
    private final CurrentUser currentUser;

    private void requirePos() {
        if (!currentUser.get().hasPermission(StaffPermission.POS)) throw new BusinessException("Bạn chưa được cấp quyền bán hàng tại quầy.");
    }

    @GetMapping
    public String pos(@RequestParam(required = false) String q, Model model) {
        requirePos();
        PosService.View v = posService.view(posCart);
        List<Product> results = Texts.isBlank(q) ? List.of()
                : productService.search(new ProductService.Filter(q, null, null, null, null, false, "name", true)).stream().limit(12).toList();
        model.addAttribute("q", q);
        model.addAttribute("results", results);
        model.addAttribute("pos", v);
        model.addAttribute("warnings", v.getCustomer() == null ? List.of()
                : safety.check(v.getCustomer(), v.getLines().stream().map(PosService.Line::getProduct).toList(), safety.recentProducts(v.getCustomer())));
        model.addAttribute("title", "Bán hàng tại quầy");
        return "staff/pos";
    }

    @PostMapping("/add")
    public String add(@RequestParam Long productId, @RequestParam(defaultValue = "0") Long unitId, @RequestParam(defaultValue = "1") int qty,
                      @RequestParam(required = false) String q) {
        requirePos();
        posService.add(posCart, productId, unitId, qty);
        return "redirect:/staff/pos" + (Texts.isBlank(q) ? "" : "?q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8));
    }

    @PostMapping("/update")
    public String update(@RequestParam String key, @RequestParam int qty) {
        requirePos();
        if (qty <= 0) posCart.getItems().remove(key);
        else if (posCart.getItems().containsKey(key)) posCart.getItems().put(key, Math.min(qty, 9999));
        return "redirect:/staff/pos";
    }

    @PostMapping("/customer")
    @Transactional(readOnly = true)
    public String customer(@RequestParam(required = false) String phone, @RequestParam(defaultValue = "false") boolean clear, RedirectAttributes ra) {
        requirePos();
        if (clear) {
            posCart.setCustomerId(null);
        } else {
            User u = posService.findCustomer(phone);
            posCart.setCustomerId(u.getId());
            Flash.success(ra, "Khách hàng: " + u.getFullName() + " - " + u.getPoints() + " điểm.");
        }
        return "redirect:/staff/pos";
    }

    @PostMapping("/checkout")
    @Transactional
    public String checkout(@ModelAttribute PosService.CheckoutForm form, RedirectAttributes ra) {
        Order o = posService.checkout(posCart, currentUser.get(), form);
        Flash.success(ra, "Đã lập hóa đơn " + o.getCode() + " - " + String.format("%,d", o.getTotal()).replace(',', '.') + " đ.");
        return "redirect:/staff/pos?done=" + o.getId();
    }

    @PostMapping("/clear")
    public String clear() {
        posCart.clear();
        return "redirect:/staff/pos";
    }
}
