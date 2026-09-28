package com.hieuthuoc;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kiểm tra các quy tắc nghiệp vụ chính trên dữ liệu mẫu (H2 in-memory). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "app.upload-dir=target/test-uploads"
})
@Transactional
class BusinessRulesTest {
    @Autowired ProductRepository productRepo;
    @Autowired UserRepository userRepo;
    @Autowired OrderRepository orderRepo;
    @Autowired BatchRepository batchRepo;
    @Autowired CartService cartService;
    @Autowired VoucherService voucherService;
    @Autowired OrderService orderService;
    @Autowired StockService stockService;
    @Autowired SafetyService safetyService;
    @Autowired PosService posService;
    @Autowired InventoryService inventoryService;
    @Autowired ChatService chatService;
    @Autowired ConversationRepository conversationRepo;
    @Autowired PrescriptionRepository prescriptionRepo;

    private Product product(String name) {
        return productRepo.findAll().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void specialDrugCannotBeAddedToCart() {
        Product p = product("Seduxen 5mg");
        assertThat(cartService.checkAdd(new Cart(), p, p.findUnit(0L), 1)).contains("không bán online");
    }

    @Test
    void maxPerOrderIsEnforced() {
        Product smecta = product("Smecta hương cam");
        assertThat(cartService.checkAdd(new Cart(), smecta, smecta.findUnit(0L), smecta.getMaxPerOrder() + 1)).contains("tối đa");
        assertThat(cartService.checkAdd(new Cart(), smecta, smecta.findUnit(0L), 1)).isNull();
    }

    @Test
    void voucherDoesNotApplyToPrescriptionDrugs() {
        Cart cart = new Cart();
        cart.add(product("Augmentin 625mg").getId(), 0L, 1);
        cart.setVoucherCode("WELCOME10");
        CartService.View v = cartService.build(cart, ShippingMethod.DELIVERY, null);
        assertThat(v.isRxRequired()).isTrue();
        assertThat(v.getDiscount()).isZero();
        assertThat(v.getVoucherError()).contains("thuốc kê đơn");
    }

    @Test
    void prescriptionOrderRequiresImage() {
        User customer = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();
        Cart cart = new Cart();
        cart.add(product("Augmentin 625mg").getId(), 0L, 1);
        OrderService.CheckoutForm f = new OrderService.CheckoutForm();
        f.setRecipient("Trần Văn An");
        f.setPhone("0912345678");
        f.setAddress("45 Lê Văn Lương, Thanh Xuân, Hà Nội");
        assertThatThrownBy(() -> orderService.placeOrder(customer, cart, f, null))
                .isInstanceOf(OrderService.CheckoutException.class)
                .hasMessageContaining("đơn thuốc");
    }

    @Test
    void fefoTakesEarliestExpiryAndSkipsExpiredBatches() {
        User customer = userRepo.findByEmailIgnoreCase("binh@gmail.com").orElseThrow();
        User pharmacist = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        Product eff = product("Efferalgan 500mg viên sủi"); // có lô cận hạn L24002X
        Cart cart = new Cart();
        cart.add(eff.getId(), 0L, 2);
        OrderService.CheckoutForm f = new OrderService.CheckoutForm();
        f.setRecipient("Lê Thị Bình");
        f.setPhone("0987654321");
        f.setShippingMethod(ShippingMethod.PICKUP);
        Order o = orderService.placeOrder(customer, cart, f, null);
        long before = stockService.fill(eff).getOnHand();

        orderService.changeStatus(o.getId(), OrderStatus.CONFIRMED, pharmacist, null);
        orderService.changeStatus(o.getId(), OrderStatus.PREPARING, pharmacist, null);

        List<OrderItemBatch> alloc = o.getItems().get(0).getAllocations();
        assertThat(alloc).hasSize(1);
        assertThat(alloc.get(0).getBatch().getBatchNo()).isEqualTo("L24002X");
        assertThat(stockService.fill(eff).getOnHand()).isEqualTo(before - 2);

        // Hủy khi đang chuẩn bị -> hoàn kho đúng lô
        orderService.changeStatus(o.getId(), OrderStatus.CANCELLED, pharmacist, "Khách đổi ý");
        assertThat(stockService.fill(eff).getOnHand()).isEqualTo(before);
    }

    @Test
    void invalidTransitionIsRejected() {
        User pharmacist = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        Order pending = orderRepo.findAll().stream().filter(o -> o.getStatus() == OrderStatus.PENDING).findFirst().orElseThrow();
        assertThatThrownBy(() -> orderService.changeStatus(pending.getId(), OrderStatus.COMPLETED, pharmacist, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void boxUnitConvertsToBaseUnitsForStock() {
        User customer = userRepo.findByEmailIgnoreCase("binh@gmail.com").orElseThrow();
        User pharmacist = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        Product pana = product("Panadol Extra"); // đơn vị gốc Vỉ, Hộp = 15 vỉ
        UnitOption box = pana.getUnitOptions().stream().filter(u -> u.factor() > 1).findFirst().orElseThrow();
        assertThat(box.factor()).isEqualTo(15);
        long before = stockService.fill(pana).getOnHand();
        long availBefore = pana.getAvailable();
        Cart cart = new Cart();
        cart.add(pana.getId(), box.id(), 1);
        OrderService.CheckoutForm f = new OrderService.CheckoutForm();
        f.setRecipient("Lê Thị Bình");
        f.setPhone("0987654321");
        f.setShippingMethod(ShippingMethod.PICKUP);
        Order o = orderService.placeOrder(customer, cart, f, null);
        assertThat(o.getSubtotal()).isEqualTo(box.price());
        assertThat(stockService.fill(pana).getAvailable()).isEqualTo(availBefore - 15);
        orderService.changeStatus(o.getId(), OrderStatus.CONFIRMED, pharmacist, null);
        orderService.changeStatus(o.getId(), OrderStatus.PREPARING, pharmacist, null);
        assertThat(stockService.fill(pana).getOnHand()).isEqualTo(before - 15);
    }

    @Test
    void pointsReduceTotalAndAreRefundedOnCancel() {
        User customer = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();
        int pointsBefore = customer.getPoints();
        assertThat(pointsBefore).isGreaterThan(0);
        Cart cart = new Cart();
        cart.add(product("Omega-3 Fish Oil 1000mg").getId(), 0L, 1);
        cart.setUsePoints(true);
        OrderService.CheckoutForm f = new OrderService.CheckoutForm();
        f.setRecipient("Trần Văn An");
        f.setPhone("0912345678");
        f.setShippingMethod(ShippingMethod.PICKUP);
        Order o = orderService.placeOrder(customer, cart, f, null);
        assertThat(o.getPointsUsedValue()).isGreaterThan(0);
        assertThat(o.getTotal()).isEqualTo(o.getSubtotal() - o.getPointsDiscountValue());
        assertThat(customer.getPoints()).isEqualTo(pointsBefore - o.getPointsUsedValue());
        orderService.cancelByCustomer(o, customer, "đổi ý");
        assertThat(customer.getPoints()).isEqualTo(pointsBefore);
    }

    @Test
    void customerCanCancelWhilePreparingAndStockIsRestored() {
        User customer = userRepo.findByEmailIgnoreCase("chau@gmail.com").orElseThrow();
        User pharmacist = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        Product smecta = product("Smecta hương cam");
        long before = stockService.fill(smecta).getOnHand();
        Cart cart = new Cart();
        cart.add(smecta.getId(), 0L, 1);
        OrderService.CheckoutForm f = new OrderService.CheckoutForm();
        f.setRecipient("Hoàng Minh Châu");
        f.setPhone("0934567890");
        f.setShippingMethod(ShippingMethod.PICKUP);
        Order o = orderService.placeOrder(customer, cart, f, null);
        orderService.changeStatus(o.getId(), OrderStatus.CONFIRMED, pharmacist, null);
        orderService.changeStatus(o.getId(), OrderStatus.PREPARING, pharmacist, null);
        assertThat(stockService.fill(smecta).getOnHand()).isEqualTo(before - 1);
        orderService.cancelByCustomer(o, customer, "không cần nữa");
        assertThat(stockService.fill(smecta).getOnHand()).isEqualTo(before);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void allergyAndInteractionWarnings() {
        User an = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow(); // dị ứng Aspirin, viêm dạ dày
        List<SafetyService.Warning> w = safetyService.check(an, List.of(product("Ibuprofen 400mg")), List.of());
        assertThat(w).anyMatch(x -> x.level().equals("danger") && x.message().contains("DỊ ỨNG"));
        assertThat(w).anyMatch(x -> x.message().contains("dạ dày"));
        List<SafetyService.Warning> w2 = safetyService.check(null, List.of(product("Decolgen ND")), List.of(product("Concor 5mg")));
        assertThat(w2).anyMatch(x -> x.message().contains("TƯƠNG TÁC"));
    }

    @Test
    void posSaleUsesSharedStockAndAddsPoints() {
        User staff = userRepo.findByEmailIgnoreCase("duocsi2@hieuthuoc.vn").orElseThrow();
        User chau = userRepo.findByEmailIgnoreCase("chau@gmail.com").orElseThrow();
        Product smecta = product("Smecta hương cam");
        long before = stockService.fill(smecta).getOnHand();
        int points = chau.getPoints();
        PosCart cart = new PosCart();
        posService.add(cart, smecta.getId(), 0L, 3);
        cart.setCustomerId(chau.getId());
        Order o = posService.checkout(cart, staff, new PosService.CheckoutForm());
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(o.isPos()).isTrue();
        assertThat(stockService.fill(smecta).getOnHand()).isEqualTo(before - 3);
        assertThat(chau.getPoints()).isGreaterThan(points);
    }

    @Test
    void posPrescriptionDrugRequiresRxInfo() {
        User staff = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        PosCart cart = new PosCart();
        posService.add(cart, product("Seduxen 5mg").getId(), 0L, 1);
        assertThatThrownBy(() -> posService.checkout(cart, staff, new PosService.CheckoutForm()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("kê đơn");
    }

    @Test
    void stocktakeRecordsDifferencesAndIncreaseNeedsPermission() {
        User manager = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        User staff = userRepo.findByEmailIgnoreCase("duocsi2@hieuthuoc.vn").orElseThrow();
        Batch b = batchRepo.findByProductOrderByExpDateAsc(product("Berberin 100mg")).get(0);
        int qty = b.getQuantity();
        assertThatThrownBy(() -> inventoryService.stocktake(java.util.Map.of(b.getId(), qty + 5), staff, null))
                .isInstanceOf(BusinessException.class);
        assertThat(inventoryService.stocktake(java.util.Map.of(b.getId(), qty - 2), staff, "test")).isEqualTo(1);
        assertThat(b.getQuantity()).isEqualTo(qty - 2);
        assertThat(inventoryService.stocktake(java.util.Map.of(b.getId(), qty), manager, "test")).isEqualTo(1);
        assertThat(b.getQuantity()).isEqualTo(qty);
    }

    @Test
    void prescriptionApprovalRequiresChecklistAndValidDate() {
        User pharmacist = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        Prescription rx = prescriptionRepo.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING).get(0);
        OrderService.RxApproval f = new OrderService.RxApproval();
        f.setPatientName("Trần Văn An");
        f.setDoctorName("BS. Tuấn");
        f.setRxDate(java.time.LocalDate.now());
        assertThatThrownBy(() -> orderService.approvePrescription(rx.getId(), pharmacist, f)).hasMessageContaining("kiểm tra");
        f.setChecks(List.of("valid", "date", "sign", "match"));
        f.setRxDate(java.time.LocalDate.now().minusDays(30));
        assertThatThrownBy(() -> orderService.approvePrescription(rx.getId(), pharmacist, f)).hasMessageContaining("hết hiệu lực");
        f.setRxDate(java.time.LocalDate.now());
        Order o = orderService.approvePrescription(rx.getId(), pharmacist, f);
        assertThat(rx.getPharmacist()).isEqualTo(pharmacist);
        assertThat(rx.getReviewedAt()).isNotNull();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void suggestedCartCanBeAddedToCustomerCart() {
        User pharmacist = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        Conversation c = conversationRepo.findAll().get(0);
        Product vitc = product("Viên sủi Vitamin C 1000mg");
        SuggestedCart sc = chatService.sendSuggestedCart(c.getId(), pharmacist, List.of(vitc.getId() + ":0"), List.of(2), "uống sau ăn");
        Cart cart = new Cart();
        List<String> skipped = chatService.addSuggestedToCart(sc.getId(), c.getCustomer(), cart, cartService);
        assertThat(skipped).isEmpty();
        assertThat(cart.quantityOf(Cart.key(vitc.getId(), 0L))).isEqualTo(2);
    }

    @Test
    void refundRequiresPermission() {
        User noRefund = userRepo.findByEmailIgnoreCase("duocsi2@hieuthuoc.vn").orElseThrow();
        assertThat(noRefund.hasPermission(StaffPermission.REFUND)).isFalse();
        Order done = orderRepo.findAll().stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).findFirst().orElseThrow();
        done.setReturnStatus(ReturnStatus.REQUESTED);
        assertThatThrownBy(() -> orderService.handleReturn(done.getId(), noRefund, true, true, "ok")).hasMessageContaining("quyền");
    }
}
