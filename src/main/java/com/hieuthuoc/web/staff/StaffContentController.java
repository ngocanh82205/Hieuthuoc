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

    /* ---------------- Tư vấn ---------------- */

    @GetMapping("/consultations")
    public String consultations(@RequestParam(defaultValue = "false") boolean closed, Model model) {
        List<Conversation> list = conversationRepo.findByClosedOrderByUpdatedAtDesc(closed);
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
        model.addAttribute("post", new Post());
        model.addAttribute("title", "Viết bài mới");
        return "staff/post-form";
    }

    @GetMapping("/posts/{id}/edit")
    public String editPost(@PathVariable Long id, Model model) {
        model.addAttribute("post", postRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy bài viết.")));
        model.addAttribute("title", "Sửa bài viết");
        return "staff/post-form";
    }

    @PostMapping({"/posts", "/posts/{id}"})
    @Transactional
    public String savePost(@PathVariable(required = false) Long id, @RequestParam String title,
                           @RequestParam(required = false) String summary, @RequestParam String content,
                           @RequestParam(defaultValue = "false") boolean published, RedirectAttributes ra) {
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
        postRepo.deleteById(id);
        notifications.log(currentUser.get(), "post.delete", "#" + id);
        Flash.info(ra, "Đã xóa bài viết.");
        return "redirect:/staff/posts";
    }
}
