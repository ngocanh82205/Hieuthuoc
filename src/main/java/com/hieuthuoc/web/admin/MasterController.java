package com.hieuthuoc.web.admin;

import com.hieuthuoc.entity.DrugInteraction;
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

import java.util.LinkedHashMap;
import java.util.Map;

/** Admin: quy tắc tương tác thuốc (gợi ý hoạt chất lấy từ dữ liệu sản phẩm). */
@Controller
@RequestMapping("/admin/interactions")
@RequiredArgsConstructor
public class MasterController {
    public static final Map<String, String> LEVELS = new LinkedHashMap<>();

    static {
        LEVELS.put("danger", "Nghiêm trọng");
        LEVELS.put("warning", "Thận trọng");
        LEVELS.put("info", "Lưu ý");
    }

    private final CatalogService catalog;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    @Transactional(readOnly = true)
    public String interactions(@RequestParam(required = false) Long edit, Model model) {
        model.addAttribute("title", "Tương tác thuốc");
        model.addAttribute("list", em.createQuery("select d from DrugInteraction d order by d.ingredientA", DrugInteraction.class).getResultList());
        model.addAttribute("levels", LEVELS);
        model.addAttribute("ingredients", catalog.ingredientSuggestions().stream().map(String::toLowerCase).toList());
        model.addAttribute("edit", edit != null ? em.find(DrugInteraction.class, edit) : null);
        return "admin/interactions";
    }

    @PostMapping({"", "/{id}"})
    @Transactional
    public String save(@PathVariable(required = false) Long id, @RequestParam Map<String, String> in, RedirectAttributes ra) {
        String a = Texts.trim(in.get("ingredient_a")).toLowerCase();
        String b = Texts.trim(in.get("ingredient_b")).toLowerCase();
        String level = Texts.trim(in.get("level"));
        String msg = Texts.trim(in.get("message"));
        if (Texts.mbLen(a) < 3 || Texts.mbLen(b) < 3) throw new BusinessException("Nhập từ khóa hoạt chất A và B (tối thiểu 3 ký tự).");
        if (a.equals(b)) throw new BusinessException("Hai hoạt chất phải khác nhau.");
        if (!LEVELS.containsKey(level)) throw new BusinessException("Mức độ không hợp lệ.");
        if (Texts.mbLen(msg) < 10) throw new BusinessException("Vui lòng mô tả tương tác và khuyến cáo.");
        DrugInteraction d = id != null ? Web.found(em.find(DrugInteraction.class, id)) : new DrugInteraction();
        d.setIngredientA(Texts.trim(a, 100));
        d.setIngredientB(Texts.trim(b, 100));
        d.setLevel(level);
        d.setMessage(Texts.trim(msg, 500));
        if (d.getId() == null) em.persist(d);
        notifications.log(currentUser.get(), "product.interaction", a + " + " + b + " (" + level + ")");
        Web.success(ra, "Đã lưu quy tắc tương tác.");
        return "redirect:/admin/interactions";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        DrugInteraction d = Web.found(em.find(DrugInteraction.class, id));
        em.remove(d);
        notifications.log(currentUser.get(), "product.interaction_delete", d.getIngredientA() + " + " + d.getIngredientB());
        Web.info(ra, "Đã xóa quy tắc.");
        return "redirect:/admin/interactions";
    }
}
