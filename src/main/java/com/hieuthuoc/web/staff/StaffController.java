package com.hieuthuoc.web.staff;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Flash;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Dược sĩ / nhân viên: tổng quan, duyệt đơn thuốc, xử lý đơn hàng. */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {
    private final PrescriptionRepository prescriptionRepo;
    private final OrderRepository orderRepo;
    private final ConversationRepository conversationRepo;
    private final ProductRepository productRepo;
    private final BatchRepository batchRepo;
    private final OrderService orderService;
    private final StockService stockService;
    private final SettingService settings;
    private final CurrentUser currentUser;
    private final ProductQuestionRepository questionRepo;
    private final CallbackRequestRepository callbackRepo;

    @GetMapping
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        List<Product> products = stockService.fill(new ArrayList<>(productRepo.findByActiveTrueOrderByNameAsc()));
        model.addAttribute("pendingRx", prescriptionRepo.countByStatus(ApprovalStatus.PENDING));
        model.addAttribute("pendingOrders", orderRepo.countByStatusIn(EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED)));
        model.addAttribute("inProgress", orderRepo.countByStatusIn(EnumSet.of(OrderStatus.PREPARING, OrderStatus.SHIPPING)));
        model.addAttribute("returns", orderRepo.countByReturnStatus(ReturnStatus.REQUESTED));
        model.addAttribute("openChats", conversationRepo.countByClosedFalse());
        model.addAttribute("openQuestions", questionRepo.countByAnswerIsNullAndHiddenFalse());
        model.addAttribute("openCallbacks", callbackRepo.countByDoneFalse());
        model.addAttribute("lowStock", products.stream().filter(Product::isLowStock).count());
        model.addAttribute("nearExpiry", batchRepo.findNearExpiry(today, today.plusDays(settings.getLong("near_expiry_days"))).size());
        model.addAttribute("expired", batchRepo.findExpired(today).size());
        model.addAttribute("rxQueue", prescriptionRepo.findTop5ByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING));
        model.addAttribute("recentOrders", orderRepo.findTop8ByStatusInOrderByCreatedAtAsc(
                EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING)));
        model.addAttribute("title", "Tổng quan");
        return "staff/dashboard";
    }

    /* ---------------- Duyệt đơn thuốc ---------------- */

    @GetMapping("/prescriptions")
    public String prescriptions(@RequestParam(defaultValue = "PENDING") ApprovalStatus status, Model model) {
        model.addAttribute("list", status == ApprovalStatus.PENDING
                ? prescriptionRepo.findByStatusOrderByCreatedAtAsc(status)
                : prescriptionRepo.findTop200ByStatusOrderByReviewedAtDesc(status));
        model.addAttribute("status", status);
        model.addAttribute("title", "Duyệt đơn thuốc");
        return "staff/prescriptions";
    }

    @GetMapping("/prescriptions/{id}")
    public String prescription(@PathVariable Long id, Model model) {
        Prescription rx = prescriptionRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn thuốc."));
        Order o = rx.getOrder();
        Map<Long, Long> available = new HashMap<>();
        Map<Long, List<Product>> equivalents = new HashMap<>();
        if (o != null) {
            for (OrderItem it : o.getItems()) {
                available.put(it.getId(), stockService.fill(it.getProduct()).getAvailable());
                String ing = it.getProduct().getActiveIngredient();
                equivalents.put(it.getId(), ing == null ? List.of()
                        : stockService.fill(new ArrayList<>(productRepo.findTop4ByActiveTrueAndActiveIngredientIgnoreCaseAndIdNot(ing, it.getProduct().getId()))));
            }
        } else {
            model.addAttribute("products", stockService.fill(new ArrayList<>(productRepo.findSellable())));
        }
        model.addAttribute("rx", rx);
        model.addAttribute("order", o);
        model.addAttribute("available", available);
        model.addAttribute("equivalents", equivalents);
        model.addAttribute("previous", o == null ? List.of() : o.getPrescriptions().stream().filter(p -> !p.getId().equals(rx.getId())).toList());
        model.addAttribute("title", "Đơn thuốc #" + rx.getId());
        return "staff/prescription";
    }

    @PostMapping("/prescriptions/{id}/approve")
    @Transactional
    public String approve(@PathVariable Long id, @ModelAttribute OrderService.RxApproval form, RedirectAttributes ra) {
        Order o = orderService.approvePrescription(id, currentUser.get(), form);
        Flash.success(ra, o.getStatus() == OrderStatus.AWAITING_CUSTOMER
                ? "Đã duyệt/lên đơn " + o.getCode() + ". Đang chờ khách xác nhận."
                : "Đã duyệt đơn thuốc cho đơn hàng " + o.getCode() + ".");
        return "redirect:/staff/prescriptions";
    }

    @PostMapping("/prescriptions/{id}/reject")
    @Transactional
    public String reject(@PathVariable Long id, @RequestParam String rejectReason, RedirectAttributes ra) {
        orderService.rejectPrescription(id, currentUser.get(), rejectReason);
        Flash.info(ra, "Đã từ chối đơn thuốc và thông báo cho khách hàng.");
        return "redirect:/staff/prescriptions";
    }

    /** Sổ bán thuốc kê đơn. */
    @GetMapping("/rx-log")
    public String rxLog(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        Model model) {
        if (to == null) to = LocalDate.now();
        if (from == null) from = to.minusDays(30);
        model.addAttribute("rows", prescriptionRepo.findByStatusAndReviewedAtBetweenOrderByReviewedAtDesc(
                ApprovalStatus.APPROVED, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("title", "Sổ bán thuốc kê đơn");
        return "staff/rx-log";
    }

    /* ---------------- Đơn hàng ---------------- */

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) OrderStatus status,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) PaymentMethod payment,
                         @RequestParam(defaultValue = "false") boolean returns,
                         @RequestParam(defaultValue = "1") int page,
                         Model model) {
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (payment != null) ps.add(cb.equal(root.get("paymentMethod"), payment));
            if (returns) ps.add(cb.equal(root.get("returnStatus"), ReturnStatus.REQUESTED));
            if (!Texts.isBlank(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("code")), like), cb.like(cb.lower(root.get("recipient")), like),
                        cb.like(root.get("phone"), like), cb.like(cb.lower(root.get("user").get("email")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Order> p = orderRepo.findAll(spec, PageRequest.of(Math.max(page, 1) - 1, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<String, Long> counts = new HashMap<>();
        for (Object[] r : orderRepo.countGroupByStatus()) counts.put(((OrderStatus) r[0]).name(), (Long) r[1]);
        model.addAttribute("page", p);
        model.addAttribute("counts", counts);
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        model.addAttribute("payment", payment);
        model.addAttribute("returns", returns);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("returnCount", orderRepo.countByReturnStatus(ReturnStatus.REQUESTED));
        model.addAttribute("title", "Quản lý đơn hàng");
        return "staff/orders";
    }

    private Order order(Long id) {
        return orderRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable Long id, Model model) {
        Order o = order(id);
        model.addAttribute("order", o);
        model.addAttribute("transitions", o.getStatus().staffTransitions());
        model.addAttribute("title", "Đơn hàng " + o.getCode());
        return "staff/order";
    }

    @PostMapping("/orders/{id}/status")
    @Transactional
    public String changeStatus(@PathVariable Long id, @RequestParam OrderStatus to, @RequestParam(required = false) String note,
                               RedirectAttributes ra) {
        Order o = orderService.changeStatus(id, to, currentUser.get(), note);
        Flash.success(ra, "Đơn " + o.getCode() + ": đã chuyển sang \"" + to.getLabel() + "\".");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/orders/{id}/paid")
    @Transactional
    public String markPaid(@PathVariable Long id, RedirectAttributes ra) {
        orderService.markPaid(id, currentUser.get());
        Flash.success(ra, "Đã xác nhận thanh toán.");
        return "redirect:/staff/orders/" + id;
    }

    @PostMapping("/orders/{id}/return")
    @Transactional
    public String handleReturn(@PathVariable Long id, @RequestParam String decision,
                               @RequestParam(defaultValue = "false") boolean restock,
                               @RequestParam(required = false) String note, RedirectAttributes ra) {
        orderService.handleReturn(id, currentUser.get(), "approve".equals(decision), restock, note);
        Flash.success(ra, "Đã xử lý yêu cầu đổi/trả.");
        return "redirect:/staff/orders/" + id;
    }

    @GetMapping("/orders/{id}/invoice")
    public String invoice(@PathVariable Long id, Model model) {
        Order o = order(id);
        model.addAttribute("order", o);
        model.addAttribute("title", "Hóa đơn " + o.getCode());
        return "staff/invoice";
    }
}
