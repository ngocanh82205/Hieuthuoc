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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tư vấn khách hàng, kiểm duyệt đánh giá, bài viết sức khỏe. */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffContentController {
    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final ReviewRepository reviewRepo;
    private final PostRepository postRepo;
    private final ChatService chatService;
    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final ProductQuestionRepository questionRepo;
    private final CallbackRequestRepository callbackRepo;
    private final CustomerCareService care;
    private final SafetyService safety;
    private final UserRepository userRepo;

    /* ---------------- Tư vấn ---------------- */

    @GetMapping("/consultations")
    public String consultations(@RequestParam(defaultValue = "false") boolean closed, @RequestParam(defaultValue = "all") String view, Model model) {
        User me = currentUser.get();
        List<Conversation> list = switch (view) {
            case "mine" -> conversationRepo.findByClosedAndPharmacistOrderByUpdatedAtDesc(closed, me);
            case "unassigned" -> conversationRepo.findByClosedAndPharmacistIsNullOrderByUpdatedAtDesc(closed);
            default -> conversationRepo.findByClosedOrderByUpdatedAtDesc(closed);
        };
        model.addAttribute("view", view);
        model.addAttribute("online", chatService.onlineStaff());
        Map<Long, Message> last = new HashMap<>();
        for (Conversation c : list) messageRepo.findFirstByConversationOrderByIdDesc(c).ifPresent(m -> last.put(c.getId(), m));
        model.addAttribute("list", list);
        model.addAttribute("last", last);
        model.addAttribute("closed", closed);
        model.addAttribute("title", "Tư vấn khách hàng");
        return "staff/consultations";
    }

    @GetMapping("/consultations/{id}")
    public String consultation(@PathVariable Long id, Model model) {
        Conversation c = chatService.get(id);
        List<Order> orders = orderRepo.findByUserOrderByCreatedAtDescIdDesc(c.getCustomer());
        model.addAttribute("conv", c);
        model.addAttribute("customer", c.getCustomer());
        model.addAttribute("messages", chatService.messages(c, 0));
        model.addAttribute("orders", orders.subList(0, Math.min(5, orders.size())));
        model.addAttribute("products", productRepo.findByActiveTrueOrderByNameAsc().stream().filter(p -> p.getDrugType().isSellableOnline()).toList());
        List<Product> recent = safety.recentProducts(c.getCustomer());
        model.addAttribute("recent", recent);
        model.addAttribute("warnings", safety.check(c.getCustomer(), recent, List.of()));
        model.addAttribute("staffList", userRepo.findByRoleInOrderByRoleAscFullNameAsc(List.of(Role.PHARMACIST, Role.ADMIN)));
        model.addAttribute("title", "Tư vấn: " + c.getCustomer().getFullName());
        return "staff/consultation";
    }

    @GetMapping("/consultations/{id}/messages")
    @ResponseBody
    public Map<String, List<ChatService.MessageDto>> messages(@PathVariable Long id, @RequestParam(defaultValue = "0") long after) {
        return Map.of("messages", chatService.messages(chatService.get(id), after));
    }

    @PostMapping("/consultations/{id}/messages")
    @ResponseBody
    public Map<String, Object> send(@PathVariable Long id, @RequestParam(required = false) String body,
                                    @RequestParam(required = false) MultipartFile image) {
        chatService.staffSend(id, currentUser.get(), body, image);
        return Map.of("ok", true);
    }

    @PostMapping("/consultations/{id}/claim")
    @Transactional
    public String claim(@PathVariable Long id, RedirectAttributes ra) {
        chatService.claim(id, currentUser.get());
        Flash.success(ra, "Bạn đã nhận cuộc tư vấn này.");
        return "redirect:/staff/consultations/" + id;
    }

    @PostMapping("/consultations/{id}/transfer")
    @Transactional
    public String transfer(@PathVariable Long id, @RequestParam Long toUserId, RedirectAttributes ra) {
        chatService.transfer(id, currentUser.get(), toUserId);
        Flash.success(ra, "Đã chuyển cuộc tư vấn.");
        return "redirect:/staff/consultations/" + id;
    }

    /** Gửi giỏ hàng tư vấn: danh sách "productId:unitId" + số lượng. */
    @PostMapping("/consultations/{id}/suggest")
    @Transactional
    public String suggest(@PathVariable Long id, @RequestParam(value = "items", required = false) List<String> items,
                          @RequestParam(value = "qtys", required = false) List<Integer> qtys,
                          @RequestParam(required = false) String note, RedirectAttributes ra) {
        chatService.sendSuggestedCart(id, currentUser.get(), items == null ? List.of() : items, qtys == null ? List.of() : qtys, note);
        Flash.success(ra, "Đã gửi giỏ hàng tư vấn cho khách.");
        return "redirect:/staff/consultations/" + id;
    }

    /* ---------------- Cập nhật thông tin chuyên môn sản phẩm (quyền Nội dung) ---------------- */

    private void requireContent() {
        if (!currentUser.get().hasPermission(StaffPermission.CONTENT)) throw new BusinessException("Bạn chưa được cấp quyền quản lý nội dung.");
    }

    @GetMapping("/products")
    public String products(@RequestParam(required = false) String q, Model model) {
        String k = Texts.trim(q).toLowerCase();
        model.addAttribute("products", productRepo.findAllByOrderByNameAsc().stream()
                .filter(p -> k.isEmpty() || p.getName().toLowerCase().contains(k)
                        || (p.getActiveIngredient() != null && p.getActiveIngredient().toLowerCase().contains(k))).toList());
        model.addAttribute("q", q);
        model.addAttribute("title", "Thông tin sản phẩm");
        return "staff/products";
    }

    @GetMapping("/products/{id}/info")
    public String productInfo(@PathVariable Long id, Model model) {
        requireContent();
        model.addAttribute("product", productRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy sản phẩm.")));
        model.addAttribute("title", "Cập nhật thông tin chuyên môn");
        return "staff/product-info";
    }

    @PostMapping("/products/{id}/info")
    @Transactional
    public String saveProductInfo(@PathVariable Long id, @RequestParam(required = false) String description,
                                  @RequestParam(required = false) String usageInstruction, @RequestParam(required = false) String contraindications,
                                  @RequestParam(required = false) String sideEffects, @RequestParam(required = false) String activeIngredient,
                                  @RequestParam(required = false) String strength, RedirectAttributes ra) {
        requireContent();
        Product p = productRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy sản phẩm."));
        p.setDescription(Texts.emptyToNull(Texts.trim(description, 2000)));
        p.setUsageInstruction(Texts.emptyToNull(Texts.trim(usageInstruction, 2000)));
        p.setContraindications(Texts.emptyToNull(Texts.trim(contraindications, 1000)));
        p.setSideEffects(Texts.emptyToNull(Texts.trim(sideEffects, 1000)));
        p.setActiveIngredient(Texts.emptyToNull(Texts.trim(activeIngredient, 200)));
        p.setStrength(Texts.emptyToNull(Texts.trim(strength, 100)));
        notifications.log(currentUser.get(), "product.info", p.getName());
        Flash.success(ra, "Đã cập nhật thông tin chuyên môn của " + p.getName() + ".");
        return "redirect:/staff/products";
    }

    @PostMapping("/consultations/{id}/close")
    @Transactional
    public String close(@PathVariable Long id) {
        chatService.toggleClosed(id, currentUser.get());
        return "redirect:/staff/consultations/" + id;
    }

    /* ---------------- Hỏi đáp sản phẩm ---------------- */

    @GetMapping("/questions")
    public String questions(@RequestParam(defaultValue = "false") boolean all, Model model) {
        model.addAttribute("list", all ? questionRepo.findTop200ByOrderByCreatedAtDesc() : questionRepo.findByAnswerIsNullAndHiddenFalseOrderByCreatedAtAsc());
        model.addAttribute("all", all);
        model.addAttribute("title", "Hỏi đáp sản phẩm");
        return "staff/questions";
    }

    @PostMapping("/questions/{id}/answer")
    @Transactional
    public String answer(@PathVariable Long id, @RequestParam String answer, RedirectAttributes ra) {
        care.answer(id, currentUser.get(), answer);
        Flash.success(ra, "Đã trả lời câu hỏi.");
        return "redirect:/staff/questions";
    }

    @PostMapping("/questions/{id}/toggle")
    @Transactional
    public String toggleQuestion(@PathVariable Long id) {
        requireContent();
        care.toggleQuestionHidden(id, currentUser.get());
        return "redirect:/staff/questions?all=true";
    }

    /* ---------------- Yêu cầu gọi lại ---------------- */

    @GetMapping("/callbacks")
    public String callbacks(@RequestParam(defaultValue = "false") boolean done, Model model) {
        model.addAttribute("list", done ? callbackRepo.findTop100ByDoneOrderByHandledAtDesc(true) : callbackRepo.findByDoneOrderByCreatedAtAsc(false));
        model.addAttribute("done", done);
        model.addAttribute("title", "Yêu cầu gọi lại");
        return "staff/callbacks";
    }

    @PostMapping("/callbacks/{id}/done")
    @Transactional
    public String callbackDone(@PathVariable Long id, @RequestParam(required = false) String result, RedirectAttributes ra) {
        care.completeCallback(id, currentUser.get(), result);
        Flash.success(ra, "Đã đánh dấu hoàn thành cuộc gọi.");
        return "redirect:/staff/callbacks";
    }

    /* ---------------- Đánh giá ---------------- */

    @GetMapping("/reviews")
    public String reviews(Model model) {
        model.addAttribute("reviews", reviewRepo.findTop200ByOrderByCreatedAtDesc());
        model.addAttribute("title", "Kiểm duyệt đánh giá");
        return "staff/reviews";
    }

    @PostMapping("/reviews/{id}/toggle")
    @Transactional
    public String toggleReview(@PathVariable Long id) {
        requireContent();
        Review r = reviewRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đánh giá."));
        r.setHidden(!r.isHidden());
        notifications.log(currentUser.get(), r.isHidden() ? "review.hide" : "review.show", "Đánh giá #" + id);
        return "redirect:/staff/reviews";
    }

    /* ---------------- Bài viết ---------------- */

    @GetMapping("/posts")
    public String posts(Model model) {
        model.addAttribute("posts", postRepo.findAllByOrderByCreatedAtDesc());
        model.addAttribute("title", "Bài viết sức khỏe");
        return "staff/posts";
    }

    @GetMapping("/posts/new")
    public String newPost(Model model) {
        requireContent();
        model.addAttribute("post", new Post());
        model.addAttribute("title", "Viết bài mới");
        return "staff/post-form";
    }

    @GetMapping("/posts/{id}/edit")
    public String editPost(@PathVariable Long id, Model model) {
        requireContent();
        model.addAttribute("post", postRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy bài viết.")));
        model.addAttribute("title", "Sửa bài viết");
        return "staff/post-form";
    }

    @PostMapping({"/posts", "/posts/{id}"})
    @Transactional
    public String savePost(@PathVariable(required = false) Long id, @RequestParam String title,
                           @RequestParam(required = false) String summary, @RequestParam String content,
                           @RequestParam(defaultValue = "false") boolean published, RedirectAttributes ra) {
        requireContent();
        if (Texts.trim(title).length() < 5 || Texts.trim(content).length() < 20) {
            throw new BusinessException("Tiêu đề tối thiểu 5 ký tự, nội dung tối thiểu 20 ký tự.");
        }
        Post p = id == null ? new Post() : postRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy bài viết."));
        if (id == null) {
            String slug = Texts.slugify(title);
            if (postRepo.existsBySlug(slug)) slug += "-" + Long.toString(System.currentTimeMillis(), 36);
            p.setSlug(slug);
            p.setAuthor(currentUser.get());
        }
        p.setTitle(Texts.trim(title, 200));
        p.setSummary(Texts.emptyToNull(Texts.trim(summary, 500)));
        p.setContent(content.trim());
        p.setPublished(published);
        postRepo.save(p);
        notifications.log(currentUser.get(), "post.save", p.getTitle());
        Flash.success(ra, "Đã lưu bài viết.");
        return "redirect:/staff/posts";
    }

    @PostMapping("/posts/{id}/delete")
    @Transactional
    public String deletePost(@PathVariable Long id, RedirectAttributes ra) {
        requireContent();
        postRepo.deleteById(id);
        notifications.log(currentUser.get(), "post.delete", "#" + id);
        Flash.info(ra, "Đã xóa bài viết.");
        return "redirect:/staff/posts";
    }
}
