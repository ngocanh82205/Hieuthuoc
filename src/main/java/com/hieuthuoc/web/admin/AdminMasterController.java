package com.hieuthuoc.web.admin;

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

import java.util.*;

/** Admin: danh mục hoạt chất, thương hiệu / nhà sản xuất, quy tắc tương tác thuốc. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMasterController {
    private final IngredientRepository ingredientRepo;
    private final ManufacturerRepository manufacturerRepo;
    private final DrugInteractionRepository interactionRepo;
    private final ProductRepository productRepo;
    private final CatalogService catalogService;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    @PostMapping("/masters/sync")
    @Transactional
    public String sync(@RequestParam(defaultValue = "/admin/ingredients") String back, RedirectAttributes ra) {
        int n = catalogService.syncMasters();
        Flash.success(ra, n == 0 ? "Danh mục đã đầy đủ." : "Đã bổ sung " + n + " hoạt chất / thương hiệu từ dữ liệu sản phẩm.");
        return "redirect:" + (back.equals("/admin/manufacturers") ? back : "/admin/ingredients");
    }

    /* ---------------- Hoạt chất ---------------- */

    @GetMapping("/ingredients")
    public String ingredients(@RequestParam(required = false) Long edit, @RequestParam(defaultValue = "") String q, Model model) {
        List<Ingredient> list = new ArrayList<>(ingredientRepo.findAllByOrderByNameAsc());
        if (!q.isBlank()) list.removeIf(i -> !i.getName().toLowerCase().contains(q.trim().toLowerCase())
                && (i.getDrugGroup() == null || !i.getDrugGroup().toLowerCase().contains(q.trim().toLowerCase())));
        Map<Long, Long> counts = new HashMap<>();
        List<Product> products = productRepo.findAll();
        for (Ingredient i : list) {
            counts.put(i.getId(), products.stream()
                    .filter(p -> CatalogService.splitIngredients(p.getActiveIngredient()).stream().anyMatch(x -> x.equalsIgnoreCase(i.getName()))).count());
        }
        model.addAttribute("list", list);
        model.addAttribute("counts", counts);
        model.addAttribute("q", q);
        model.addAttribute("edit", edit == null ? new Ingredient() : ingredientRepo.findById(edit).orElse(new Ingredient()));
        model.addAttribute("title", "Hoạt chất");
        return "admin/ingredients";
    }

    @PostMapping({"/ingredients", "/ingredients/{id}"})
    @Transactional
    public String saveIngredient(@PathVariable(required = false) Long id, @RequestParam String name,
                                 @RequestParam(required = false) String drugGroup, @RequestParam(required = false) String note, RedirectAttributes ra) {
        String n = Texts.trim(name, 150);
        if (n.length() < 2) throw new BusinessException("Vui lòng nhập tên hoạt chất.");
        Optional<Ingredient> dup = ingredientRepo.findByNameIgnoreCase(n);
        if (dup.isPresent() && !dup.get().getId().equals(id)) throw new BusinessException("Hoạt chất đã tồn tại.");
        Ingredient i = id == null ? new Ingredient() : ingredientRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy hoạt chất."));
        int renamed = i.getName() == null ? 0 : catalogService.renameIngredient(i.getName(), n);
        i.setName(n);
        i.setDrugGroup(Texts.emptyToNull(Texts.trim(drugGroup, 150)));
        i.setNote(Texts.emptyToNull(Texts.trim(note, 500)));
        ingredientRepo.save(i);
        notifications.log(currentUser.get(), "product.ingredient", n + (renamed > 0 ? " (đổi tên trên " + renamed + " sản phẩm)" : ""));
        Flash.success(ra, "Đã lưu hoạt chất" + (renamed > 0 ? ", cập nhật tên trên " + renamed + " sản phẩm." : "."));
        return "redirect:/admin/ingredients";
    }

    @PostMapping("/ingredients/{id}/delete")
    @Transactional
    public String deleteIngredient(@PathVariable Long id, RedirectAttributes ra) {
        Ingredient i = ingredientRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy hoạt chất."));
        if (catalogService.countProductsWithIngredient(i.getName()) > 0) throw new BusinessException("Hoạt chất đang được dùng trong sản phẩm.");
        ingredientRepo.delete(i);
        Flash.info(ra, "Đã xóa hoạt chất.");
        return "redirect:/admin/ingredients";
    }

    /* ---------------- Thương hiệu / nhà sản xuất ---------------- */

    @GetMapping("/manufacturers")
    public String manufacturers(@RequestParam(required = false) Long edit, Model model) {
        List<Manufacturer> list = manufacturerRepo.findAllByOrderByNameAsc();
        Map<Long, Long> counts = new HashMap<>();
        List<Product> products = productRepo.findAll();
        for (Manufacturer m : list) counts.put(m.getId(), products.stream().filter(p -> m.getName().equalsIgnoreCase(p.getManufacturer())).count());
        model.addAttribute("list", list);
        model.addAttribute("counts", counts);
        model.addAttribute("edit", edit == null ? new Manufacturer() : manufacturerRepo.findById(edit).orElse(new Manufacturer()));
        model.addAttribute("title", "Thương hiệu / Nhà sản xuất");
        return "admin/manufacturers";
    }

    @PostMapping({"/manufacturers", "/manufacturers/{id}"})
    @Transactional
    public String saveManufacturer(@PathVariable(required = false) Long id, @RequestParam String name,
                                   @RequestParam(required = false) String country, @RequestParam(required = false) String website,
                                   @RequestParam(required = false) String note, RedirectAttributes ra) {
        String n = Texts.trim(name, 150);
        if (n.length() < 2) throw new BusinessException("Vui lòng nhập tên thương hiệu / nhà sản xuất.");
        Optional<Manufacturer> dup = manufacturerRepo.findByNameIgnoreCase(n);
        if (dup.isPresent() && !dup.get().getId().equals(id)) throw new BusinessException("Tên đã tồn tại.");
        Manufacturer m = id == null ? new Manufacturer() : manufacturerRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy."));
        int renamed = m.getName() == null ? 0 : catalogService.renameManufacturer(m.getName(), n);
        m.setName(n);
        m.setCountry(Texts.emptyToNull(Texts.trim(country, 80)));
        m.setWebsite(Texts.emptyToNull(Texts.trim(website, 200)));
        m.setNote(Texts.emptyToNull(Texts.trim(note, 500)));
        manufacturerRepo.save(m);
        notifications.log(currentUser.get(), "product.manufacturer", n);
        Flash.success(ra, "Đã lưu" + (renamed > 0 ? ", cập nhật tên trên " + renamed + " sản phẩm." : "."));
        return "redirect:/admin/manufacturers";
    }

    @PostMapping("/manufacturers/{id}/delete")
    @Transactional
    public String deleteManufacturer(@PathVariable Long id, RedirectAttributes ra) {
        Manufacturer m = manufacturerRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy."));
        if (productRepo.findAll().stream().anyMatch(p -> m.getName().equalsIgnoreCase(p.getManufacturer()))) {
            throw new BusinessException("Thương hiệu đang được dùng trong sản phẩm.");
        }
        manufacturerRepo.delete(m);
        Flash.info(ra, "Đã xóa.");
        return "redirect:/admin/manufacturers";
    }

    /* ---------------- Tương tác thuốc ---------------- */

    @GetMapping("/interactions")
    public String interactions(@RequestParam(required = false) Long edit, Model model) {
        model.addAttribute("list", interactionRepo.findAllByOrderByIngredientAAscIngredientBAsc());
        model.addAttribute("edit", edit == null ? new DrugInteraction() : interactionRepo.findById(edit).orElse(new DrugInteraction()));
        model.addAttribute("ingredients", ingredientRepo.findAllByOrderByNameAsc());
        model.addAttribute("title", "Cảnh báo tương tác thuốc");
        return "admin/interactions";
    }

    @PostMapping({"/interactions", "/interactions/{id}"})
    @Transactional
    public String saveInteraction(@PathVariable(required = false) Long id, @RequestParam String ingredientA, @RequestParam String ingredientB,
                                  @RequestParam String level, @RequestParam String message, RedirectAttributes ra) {
        String a = Texts.trim(ingredientA, 100).toLowerCase();
        String b = Texts.trim(ingredientB, 100).toLowerCase();
        if (a.length() < 3 || b.length() < 3) throw new BusinessException("Nhập từ khóa hoạt chất A và B (tối thiểu 3 ký tự).");
        if (a.equals(b)) throw new BusinessException("Hai hoạt chất phải khác nhau.");
        if (!List.of("danger", "warning", "info").contains(level)) throw new BusinessException("Mức độ không hợp lệ.");
        String msg = Texts.trim(message, 500);
        if (msg.length() < 10) throw new BusinessException("Vui lòng mô tả tương tác và khuyến cáo.");
        DrugInteraction d = id == null ? new DrugInteraction() : interactionRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy."));
        d.setIngredientA(a);
        d.setIngredientB(b);
        d.setLevel(level);
        d.setMessage(msg);
        interactionRepo.save(d);
        notifications.log(currentUser.get(), "product.interaction", a + " + " + b + " (" + level + ")");
        Flash.success(ra, "Đã lưu quy tắc tương tác " + a + " + " + b + ".");
        return "redirect:/admin/interactions";
    }

    @PostMapping("/interactions/{id}/delete")
    @Transactional
    public String deleteInteraction(@PathVariable Long id, RedirectAttributes ra) {
        interactionRepo.findById(id).ifPresent(d -> {
            interactionRepo.delete(d);
            notifications.log(currentUser.get(), "product.interaction_delete", d.getIngredientA() + " + " + d.getIngredientB());
        });
        Flash.info(ra, "Đã xóa quy tắc.");
        return "redirect:/admin/interactions";
    }
}
