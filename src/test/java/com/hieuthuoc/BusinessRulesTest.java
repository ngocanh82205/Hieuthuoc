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
        "app.upload-dir=target/test-uploads",
        "app.backup-dir=target/test-backups"
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
    @Autowired StockAdjustmentRepository adjustmentRepo;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired SupplierRepository supplierRepo;
    @Autowired PromotionRepository promotionRepo;
    @Autowired ShippingZoneRepository zoneRepo;
    @Autowired CategoryRepository categoryRepo;
    @Autowired ProductService productService;
    @Autowired CatalogService catalogService;
    @Autowired SettingService settingService;
    @Autowired ReportService reportService;
    @Autowired ReportExportService reportExportService;
    @Autowired BackupService backupService;
    @Autowired AccountService accountService;

    @Autowired StaffRoleRepository staffRoleRepo;
    @Autowired AiAssistantService aiAssistant;
    @Autowired WorkScheduleService workSchedule;
    @Autowired PayrollService payrollService;
    @Autowired WorkShiftRepository workShiftRepo;
    @Autowired ShiftAssignmentRepository shiftAssignmentRepo;
    @Autowired PayrollRepository payrollRepo;

    private WorkShift shift(String name) {
        return workShiftRepo.findAll().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }
    @Autowired MessageRepository messageRepo;

    /** Khách nhắn, trợ lý trả lời (đồng bộ trong test); trả về hội thoại. */
    private Conversation askAi(User customer, String text) {
        Conversation c = chatService.customerSend(customer, text, null);
        aiAssistant.respond(c.getId());
        return c;
    }

    private String lastBody(Conversation c, String kind) {
        List<Message> ms = messageRepo.findByConversationAndIdGreaterThanOrderByIdAsc(c, 0L);
        for (int i = ms.size() - 1; i >= 0; i--) if (kind.equals(ms.get(i).getKind())) return ms.get(i).getBody();
        return null;
    }

    private User newCustomer(String phone) {
        AccountService.RegisterForm f = new AccountService.RegisterForm();
        f.setFullName("Khách Mới");
        f.setPhone(phone);
        f.setPassword("123456");
        f.setPasswordConfirm("123456");
        return accountService.register(f);
    }

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
    void stocktakeByStaffNeedsApprovalManagerAppliesDirectly() {
        User manager = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        User kho = userRepo.findByEmailIgnoreCase("duocsi3@hieuthuoc.vn").orElseThrow();
        Batch b = batchRepo.findByProductOrderByExpDateAsc(product("Berberin 100mg")).get(0);
        int qty = b.getQuantity();
        // Dược sĩ (không có quyền duyệt phiếu kho): lập phiếu điều chỉnh kiểm kê, chưa đổi tồn
        assertThat(inventoryService.stocktake(java.util.Map.of(b.getId(), qty - 2), kho, "test")).isEqualTo(1);
        assertThat(b.getQuantity()).isEqualTo(qty);
        StockAdjustment pending = adjustmentRepo.findByStatusOrderByIdAsc(ApprovalStatus.PENDING).stream()
                .filter(a -> a.getBatch().getId().equals(b.getId())).findFirst().orElseThrow();
        assertThatThrownBy(() -> inventoryService.decideAdjustment(pending.getId(), kho, true, null)).hasMessageContaining("quyền");
        inventoryService.decideAdjustment(pending.getId(), manager, true, null);
        assertThat(b.getQuantity()).isEqualTo(qty - 2);
        // Quản lý kiểm kê: áp dụng ngay
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

    /* ======================= Admin ======================= */

    @Test
    void staffRoleControlsPermissionsAndUrls() {
        User kho = userRepo.findByEmailIgnoreCase("duocsi3@hieuthuoc.vn").orElseThrow();
        User cskh = userRepo.findByEmailIgnoreCase("duocsi4@hieuthuoc.vn").orElseThrow();
        // Các dược sĩ dùng chung vai trò "Dược sĩ", đều có CCHN
        User manager = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        for (User ds : List.of(kho, cskh)) {
            assertThat(ds.getStaffRole().getName()).isEqualTo("Dược sĩ");
            assertThat(ds.getLicenseNo()).isNotBlank();
            assertThat(ds.hasPermission(StaffPermission.RX_REVIEW)).isTrue();
            assertThat(ds.hasPermission(StaffPermission.INVENTORY)).isTrue();
            assertThat(ds.hasPermission(StaffPermission.CONSULT)).isTrue();
            // Duyệt phiếu kho, hoàn tiền, nội dung: chỉ dược sĩ quản lý / admin
            assertThat(ds.hasPermission(StaffPermission.APPROVE_STOCK)).isFalse();
            assertThat(ds.hasPermission(StaffPermission.REFUND)).isFalse();
            assertThat(ds.hasPermission(StaffPermission.CONTENT)).isFalse();
        }
        assertThat(manager.hasPermission(StaffPermission.APPROVE_STOCK)).isTrue();
        assertThat(manager.hasPermission(StaffPermission.REFUND)).isTrue();
        assertThat(com.hieuthuoc.config.StaffAccessInterceptor.required("/staff/prescriptions/5")).isEqualTo(StaffPermission.RX_REVIEW);
        assertThat(com.hieuthuoc.config.StaffAccessInterceptor.required("/staff/stocktake")).isEqualTo(StaffPermission.INVENTORY);
        assertThat(com.hieuthuoc.config.StaffAccessInterceptor.required("/staff")).isNull();
        // Người không có quyền duyệt đơn thuốc (VD vai trò Biên tập viên) không được duyệt
        User editor = new User();
        editor.setRole(Role.PHARMACIST);
        editor.setFullName("Biên tập viên");
        editor.setEmail("editor@x.vn");
        editor.setPasswordHash("x");
        editor.setStaffRole(staffRoleRepo.findByNameIgnoreCase("Biên tập viên").orElseThrow());
        userRepo.save(editor);
        Prescription rx = prescriptionRepo.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING).get(0);
        assertThatThrownBy(() -> orderService.rejectPrescription(rx.getId(), editor, "Không hợp lệ")).hasMessageContaining("quyền");
        // Quyền cấp thêm ngoài vai trò
        cskh.setPermissions("CONTENT");
        assertThat(cskh.hasPermission(StaffPermission.CONTENT)).isTrue();
    }

    @Test
    void writeOffByWarehouseStaffWaitsForApproval() {
        User kho = userRepo.findByEmailIgnoreCase("duocsi3@hieuthuoc.vn").orElseThrow();
        User admin = userRepo.findByEmailIgnoreCase("admin@hieuthuoc.vn").orElseThrow();
        Batch b = batchRepo.findByProductOrderByExpDateAsc(product("Omeprazol 20mg")).get(0);
        int qty = b.getQuantity();
        StockAdjustment a = inventoryService.adjust(b.getId(), kho, -2, "Vỡ hộp");
        assertThat(a.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(b.getQuantity()).isEqualTo(qty);
        assertThatThrownBy(() -> inventoryService.decideAdjustment(a.getId(), admin, false, "")).hasMessageContaining("lý do");
        inventoryService.decideAdjustment(a.getId(), admin, true, null);
        assertThat(b.getQuantity()).isEqualTo(qty - 2);
        assertThat(a.getApprovedBy()).isEqualTo(admin);
    }

    @Test
    void transferToReserveWarehouseRemovesSellableStock() {
        User kho = userRepo.findByEmailIgnoreCase("duocsi3@hieuthuoc.vn").orElseThrow();
        Warehouse main = warehouseRepo.findFirstByMainTrue().orElseThrow();
        Warehouse reserve = warehouseRepo.findAllByOrderByMainDescNameAsc().stream().filter(w -> !w.isMain()).findFirst().orElseThrow();
        Product p = product("Berberin 100mg");
        long before = stockService.fill(p).getOnHand();
        Batch b = batchRepo.findByProductOrderByExpDateAsc(p).stream().filter(x -> x.getWarehouse() == null && !x.isExpired() && x.getQuantity() > 5).findFirst().orElseThrow();
        TransferSlip slip = inventoryService.transfer(main.getId(), reserve.getId(), java.util.Map.of(b.getId(), 5), "test", kho);
        assertThat(slip.getItems()).hasSize(1);
        assertThat(slip.getItems().get(0).getTargetBatch().getWarehouse()).isEqualTo(reserve);
        assertThat(slip.getItems().get(0).getTargetBatch().getBatchNo()).isEqualTo(b.getBatchNo());
        assertThat(stockService.fill(p).getOnHand()).isEqualTo(before - 5);
        // Không chuyển quá tồn của lô
        assertThatThrownBy(() -> inventoryService.transfer(main.getId(), reserve.getId(), java.util.Map.of(b.getId(), 100000), null, kho))
                .hasMessageContaining("chỉ còn");
    }

    @Test
    void supplierDebtAndPayments() {
        User admin = userRepo.findByEmailIgnoreCase("admin@hieuthuoc.vn").orElseThrow();
        Supplier s = supplierRepo.findAllByOrderByNameAsc().get(0);
        InventoryService.Debt d = inventoryService.debt(s);
        assertThat(d.balance()).isEqualTo(d.purchased() - d.paid());
        assertThatThrownBy(() -> inventoryService.pay(s.getId(), d.balance() + 1, null, "Tiền mặt", null, admin)).hasMessageContaining("vượt");
        inventoryService.pay(s.getId(), 1000, null, "Tiền mặt", "test", admin);
        assertThat(inventoryService.debt(s).balance()).isEqualTo(d.balance() - 1000);
    }

    @Test
    void voucherTargetingByTierAndNewCustomer() {
        User an = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();
        User fresh = newCustomer("0977000111");
        assertThat(voucherService.validate("VIPVANG", 500000, fresh, java.util.Map.of()).error()).contains("hạng Vàng");
        assertThat(voucherService.validate("VIPVANG", 500000, null, java.util.Map.of()).error()).contains("đăng nhập");
        assertThat(voucherService.validate("MOIDEN25K", 200000, an, java.util.Map.of()).error()).contains("lần đầu");
        assertThat(voucherService.validate("MOIDEN25K", 200000, fresh, java.util.Map.of()).discount()).isEqualTo(25000);
        // Mã giới hạn danh mục Vitamin
        Product vitC = product("Viên sủi Vitamin C 1000mg");
        assertThat(voucherService.validate("VITAMIN15", 300000, fresh, java.util.Map.of(product("Smecta hương cam").getCategory().getId(), 300000L)).error())
                .contains("danh mục");
        assertThat(voucherService.validate("VITAMIN15", 300000, fresh, java.util.Map.of(vitC.getCategory().getId(), 300000L)).discount()).isEqualTo(40000);
    }

    @Test
    void promotionsFlashComboAndGift() {
        Product vitC = product("Viên sủi Vitamin C 1000mg");
        Product canxi = product("Canxi D3 Corbiere");
        Product omega = product("Omega-3 Fish Oil 1000mg");
        Promotion flash = promotionRepo.findAll().stream().filter(p -> p.getType().equals(Promotion.FLASH_SALE)).findFirst().orElseThrow();
        Cart cart = new Cart();
        cart.add(vitC.getId(), 0L, 1);
        cart.add(canxi.getId(), 0L, 1);
        cart.add(omega.getId(), 0L, 2);
        CartService.View v = cartService.build(cart, ShippingMethod.PICKUP, null);
        CartService.Line line = v.getLines().stream().filter(l -> l.getProduct().getId().equals(vitC.getId())).findFirst().orElseThrow();
        assertThat(line.getUnitPrice()).isEqualTo(flash.getSalePrice());
        assertThat(v.getPromoDiscount()).isEqualTo(20000);
        assertThat(v.getGifts()).hasSize(1);
        assertThat(v.getGifts().get(0).product().getName()).isEqualTo("Khẩu trang y tế 4 lớp");
        assertThat(v.getTotal()).isEqualTo(v.getSubtotal() - 20000);
        // Đặt hàng: dòng quà giá 0, suất flash sale được trừ
        User customer = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();
        int sold = flash.getSoldCount();
        OrderService.CheckoutForm f = new OrderService.CheckoutForm();
        f.setRecipient("Trần Văn An");
        f.setPhone("0912345678");
        f.setShippingMethod(ShippingMethod.PICKUP);
        Order o = orderService.placeOrder(customer, cart, f, null);
        assertThat(o.getItems().stream().anyMatch(i -> i.getPrice() == 0 && i.getProductName().contains("quà tặng"))).isTrue();
        assertThat(o.getPromoDiscountValue()).isEqualTo(20000);
        assertThat(flash.getSoldCount()).isEqualTo(sold + 1);
        // Thuốc kê đơn không được khuyến mãi
        assertThat(PromotionService.eligible(product("Augmentin 625mg"))).isFalse();
    }

    @Test
    void shippingFeeByZoneAndProvinceRequired() {
        assertThat(settingService.shippingFee(ShippingMethod.DELIVERY, 100000, "Hà Nội")).isEqualTo(15000);
        assertThat(settingService.shippingFee(ShippingMethod.DELIVERY, 350000, "Hà Nội")).isZero();
        assertThat(settingService.shippingFee(ShippingMethod.DELIVERY, 350000, "Cà Mau")).isEqualTo(30000);
        User customer = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();
        Cart cart = new Cart();
        cart.add(product("Smecta hương cam").getId(), 0L, 1);
        OrderService.CheckoutForm f = new OrderService.CheckoutForm();
        f.setRecipient("Trần Văn An");
        f.setPhone("0912345678");
        f.setAddress("45 Lê Văn Lương, Thanh Xuân");
        assertThatThrownBy(() -> orderService.placeOrder(customer, cart, f, null)).hasMessageContaining("tỉnh/thành");
        f.setProvince("Hà Nội");
        Order o = orderService.placeOrder(customer, cart, f, null);
        assertThat(o.getProvince()).isEqualTo("Hà Nội");
        assertThat(o.getShippingFee()).isEqualTo(15000);
    }

    @Test
    void refundNeedsApprovalByPermittedStaff() {
        User customer = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();
        User noRefund = userRepo.findByEmailIgnoreCase("duocsi2@hieuthuoc.vn").orElseThrow();
        User admin = userRepo.findByEmailIgnoreCase("admin@hieuthuoc.vn").orElseThrow();
        Cart cart = new Cart();
        cart.add(product("Smecta hương cam").getId(), 0L, 1);
        OrderService.CheckoutForm f = new OrderService.CheckoutForm();
        f.setRecipient("Trần Văn An");
        f.setPhone("0912345678");
        f.setShippingMethod(ShippingMethod.PICKUP);
        Order o = orderService.placeOrder(customer, cart, f, null);
        o.setPaymentStatus(PaymentStatus.PAID);
        orderService.cancelByCustomer(o, customer, "Đổi ý");
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThatThrownBy(() -> orderService.approveRefund(o.getId(), noRefund, o.getTotal(), "CK")).hasMessageContaining("quyền");
        orderService.approveRefund(o.getId(), admin, o.getTotal(), "CK Vietcombank FT123");
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(o.getRefundedBy()).isEqualTo(admin);
    }

    @Test
    void assignOrderToStaffWithOrderPermission() {
        User admin = userRepo.findByEmailIgnoreCase("admin@hieuthuoc.vn").orElseThrow();
        User cskh = userRepo.findByEmailIgnoreCase("duocsi4@hieuthuoc.vn").orElseThrow();
        User editor = new User();
        editor.setRole(Role.PHARMACIST);
        editor.setFullName("Biên tập viên");
        editor.setEmail("bt@x.vn");
        editor.setPasswordHash("x");
        editor.setStaffRole(staffRoleRepo.findByNameIgnoreCase("Biên tập viên").orElseThrow());
        userRepo.save(editor);
        Order o = orderRepo.findAll().get(0);
        orderService.assign(o.getId(), admin, cskh.getId());
        assertThat(o.getAssignedTo()).isEqualTo(cskh);
        assertThatThrownBy(() -> orderService.assign(o.getId(), admin, editor.getId())).hasMessageContaining("quyền");
    }

    @Test
    void multiLevelCategoryFilterIncludesChildren() {
        Category drugs = categoryRepo.findBySlug("thuoc").orElseThrow();
        List<Product> list = productService.search(new ProductService.Filter(null, drugs, null, null, null, false, "name", true));
        assertThat(list).extracting(Product::getName).contains("Amlodipin 5mg Stada", "Panadol Extra");
        assertThat(list).extracting(Product::getName).doesNotContain("Omega-3 Fish Oil 1000mg");
        assertThat(product("Amlodipin 5mg Stada").getCategory().getParent().getParent()).isEqualTo(drugs);
    }

    @Test
    void manualEquivalentAllowsSubstitution() {
        Product a = product("Panadol Extra");
        Product b = product("Decolgen ND");
        assertThat(catalogService.isSubstitutable(a, b)).isFalse();
        a.getEquivalents().add(b);
        assertThat(catalogService.isSubstitutable(a, b)).isTrue();
        assertThat(catalogService.isSubstitutable(b, a)).isTrue();
        assertThat(catalogService.equivalents(b, true)).contains(a);
    }

    @Test
    void excelExportImportRoundTrip() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        catalogService.exportProducts(out);
        long count = productRepo.count();
        var file = new org.springframework.mock.web.MockMultipartFile("file", "sp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        CatalogService.ImportResult r = catalogService.importProducts(file);
        assertThat(r.created()).isZero();
        assertThat(r.updated()).isEqualTo((int) count);
        assertThat(productRepo.count()).isEqualTo(count);
    }

    @Test
    void reportsAndExports() throws Exception {
        java.time.LocalDate to = java.time.LocalDate.now();
        ReportService.Report r = reportService.build(to.minusDays(29), to, 90);
        assertThat(r.getByChannel()).hasSize(2);
        assertThat(r.getByChannel().stream().mapToLong(ReportService.ChannelRow::revenue).sum()).isEqualTo(r.getRevenue());
        assertThat(r.getNewCustomers() + r.getReturningCustomers()).isEqualTo(r.getCustomersOrdered());
        assertThat(r.getByMonth()).isNotEmpty();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        reportExportService.exportReport(to.minusDays(29), to, out);
        assertThat(out.size()).isGreaterThan(1000);
        out.reset();
        reportExportService.exportNational(to.minusDays(29), to, out);
        assertThat(out.size()).isGreaterThan(1000);
    }

    @Test
    void backupCreatesFile() {
        String name = backupService.create();
        assertThat(backupService.list()).extracting(BackupService.BackupFile::name).contains(name);
        assertThatThrownBy(() -> backupService.file("../data/x.zip")).isInstanceOf(BusinessException.class);
    }

    @Test
    void loyaltyTiersAreConfigurable() {
        settingService.save(java.util.Map.of("tier_bac_min", "1000"));
        assertThat(MemberTier.of(1500)).isEqualTo(MemberTier.BAC);
        settingService.save(java.util.Map.of("tier_bac_min", "2000000"));
        assertThat(MemberTier.of(1500)).isEqualTo(MemberTier.DONG);
    }

    /* ======================= Trợ lý AI trước dược sĩ ======================= */

    @Test
    void aiAnswersFaqFirstWithoutPharmacist() {
        User c1 = newCustomer("0977000301");
        Conversation c = askAi(c1, "Xin chào");
        assertThat(c.isAiMode()).isTrue();
        assertThat(c.getPharmacist()).isNull();
        assertThat(lastBody(c, "AI")).contains("tra cứu đơn hàng");
        askAi(c1, "phi ship bao nhieu vay");   // gõ không dấu
        assertThat(lastBody(c, "AI")).contains("Nội thành Hà Nội");
        askAi(c1, "Giao hàng về Cà Mau mất bao lâu?");
        assertThat(lastBody(c, "AI")).contains("Cà Mau").contains("Miền Nam").contains("30.000");
        askAi(c1, "Chính sách đổi trả thế nào?");
        assertThat(lastBody(c, "AI")).contains("ngày");
        assertThat(c.isAiMode()).isTrue();
    }

    @Test
    void aiLooksUpOwnOrdersOnly() {
        User an = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();
        Order mine = orderRepo.findByUserOrderByCreatedAtDescIdDesc(an).get(0);
        Conversation c = askAi(an, "Kiểm tra giúp mình đơn " + mine.getCode());
        assertThat(lastBody(c, "AI")).contains(mine.getCode()).contains(mine.getStatus().getLabel());
        Order other = orderRepo.findAll().stream().filter(o -> !o.getUser().getId().equals(an.getId())).findFirst().orElseThrow();
        askAi(an, "đơn " + other.getCode() + " sao rồi");
        assertThat(lastBody(c, "AI")).contains("không tìm thấy");
    }

    @Test
    void aiHandsOffOnRedFlagsSpecialGroupsAndPrescriptionDrugs() {
        Conversation a = askAi(newCustomer("0977000302"), "Mẹ tôi đột nhiên khó thở và đau ngực");
        assertThat(a.isAiMode()).isFalse();
        assertThat(lastBody(a, "AI")).contains("115");
        assertThat(lastBody(a, "SYSTEM")).contains("Đã chuyển");
        assertThat(a.getHandoffReason()).contains("khẩn");
        assertThat(a.getAiSummary()).contains("khó thở");

        Conversation b = askAi(newCustomer("0977000303"), "Mình đang mang thai uống thuốc cảm được không");
        assertThat(b.isAiMode()).isFalse();
        assertThat(b.getHandoffReason()).contains("Đối tượng đặc biệt");

        Conversation d = askAi(newCustomer("0977000304"), "Augmentin uống ngày mấy viên?");
        assertThat(d.isAiMode()).isFalse();
        assertThat(d.getHandoffReason()).contains("kê đơn");
    }

    @Test
    void aiTriagesSymptomsThenHandsOffWithSummary() {
        User u = newCustomer("0977000305");
        Conversation c = askAi(u, "Tôi bị đau đầu");
        assertThat(c.isAiMode()).isTrue();
        assertThat(lastBody(c, "AI")).contains("Tuổi");
        askAi(u, "30 tuổi, bị 2 ngày, không dị ứng, không dùng thuốc gì");
        assertThat(c.isAiMode()).isFalse();
        assertThat(c.getAiSummary()).contains("30 tuổi");
    }

    @Test
    void aiSafetyCheckUsesHealthProfile() {
        User an = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();  // dị ứng Aspirin
        Conversation c = askAi(an, "Panadol Extra giá bao nhiêu? Mình muốn mua thêm Ibuprofen");
        assertThat(c.isAiMode()).isFalse();
    }

    @Test
    void customerButtonAndStaffReplyEndAiMode() {
        User u = newCustomer("0977000306");
        Conversation c = askAi(u, "Xin chào");
        aiAssistant.requestHandoff(u);
        assertThat(c.isAiMode()).isFalse();
        assertThat(c.getHandoffReason()).contains("yêu cầu");

        User u2 = newCustomer("0977000307");
        Conversation c2 = askAi(u2, "Xin chào");
        User ds = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        chatService.staffSend(c2.getId(), ds, "Chào bạn, mình là dược sĩ Lan", null);
        assertThat(c2.isAiMode()).isFalse();
        assertThat(c2.getPharmacist()).isEqualTo(ds);
        // Khách nhắn tiếp: trợ lý không trả lời chen vào
        int before = messageRepo.findByConversationAndIdGreaterThanOrderByIdAsc(c2, 0L).size();
        askAi(u2, "Cảm ơn dược sĩ");
        assertThat(messageRepo.findByConversationAndIdGreaterThanOrderByIdAsc(c2, 0L)).hasSize(before + 1);
    }

    @Test
    void aiCanBeDisabled() {
        settingService.save(java.util.Map.of("ai_enabled", "0"));
        Conversation c = askAi(newCustomer("0977000308"), "Xin chào");
        assertThat(c.isAiMode()).isFalse();
        assertThat(lastBody(c, "AI")).isNull();
        settingService.save(java.util.Map.of("ai_enabled", "1"));
    }

    /* ======================= Lịch làm & lương ======================= */

    @Test
    void scheduleRejectsOverlappingShifts() {
        User admin = userRepo.findByEmailIgnoreCase("admin@hieuthuoc.vn").orElseThrow();
        User ds = userRepo.findByEmailIgnoreCase("duocsi2@hieuthuoc.vn").orElseThrow();
        java.time.LocalDate d = java.time.LocalDate.now().plusDays(25);
        workSchedule.assign(ds.getId(), d, shift("Ca sáng").getId(), null, admin);
        // Ca chiều 14h-22h trùng giờ ca sáng 7h-15h
        assertThatThrownBy(() -> workSchedule.assign(ds.getId(), d, shift("Ca chiều").getId(), null, admin)).hasMessageContaining("trùng giờ");
        // Khách hàng không được xếp ca
        User customer = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();
        assertThatThrownBy(() -> workSchedule.assign(customer.getId(), d, shift("Ca sáng").getId(), null, admin)).hasMessageContaining("nhân viên");
        // Sao chép tuần
        java.time.LocalDate monday = WorkScheduleService.monday(d);
        assertThat(workSchedule.copyWeek(monday, monday.plusWeeks(1), admin)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void checkInAndOutRecordAttendance() {
        User admin = userRepo.findByEmailIgnoreCase("admin@hieuthuoc.vn").orElseThrow();
        User ds = new User();
        ds.setRole(Role.PHARMACIST);
        ds.setFullName("DS Test Chấm Công");
        ds.setEmail("chamcong@x.vn");
        ds.setPasswordHash("x");
        userRepo.save(ds);
        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusMinutes(30).withSecond(0).withNano(0);
        WorkShift sh = workShiftRepo.save(new WorkShift("Ca test", start.toLocalTime(), start.toLocalTime().plusHours(3), 0, 10000, "info"));
        assertThatThrownBy(() -> workSchedule.checkOut(ds)).hasMessageContaining("chưa vào ca");
        ShiftAssignment a = workSchedule.assign(ds.getId(), start.toLocalDate(), sh.getId(), null, admin);
        workSchedule.checkIn(ds);
        assertThat(a.getCheckInAt()).isNotNull();
        assertThat(a.getLateMinutes()).isBetween(29L, 31L);
        assertThatThrownBy(() -> workSchedule.checkIn(ds)).hasMessageContaining("Không có ca");
        workSchedule.checkOut(ds);
        assertThat(a.isCompleted()).isTrue();
        // Sửa công bắt buộc có lý do
        assertThatThrownBy(() -> workSchedule.adjust(a.getId(), a.getStartAt(), a.getEndAt(), "", admin)).hasMessageContaining("lý do");
        workSchedule.adjust(a.getId(), a.getStartAt(), a.getEndAt(), "Quên chấm", admin);
        assertThat(a.getPaidMinutes()).isEqualTo(180);
    }

    @Test
    void payrollCalculation() {
        User m = new User();
        m.setFullName("Lương tháng");
        m.setSalaryType("MONTHLY");
        m.setBaseSalary(13_000_000L);
        m.setAllowance(500_000L);
        java.time.LocalDate d = java.time.LocalDate.now().minusMonths(1).withDayOfMonth(10);
        ShiftAssignment a1 = new ShiftAssignment(m, d, shift("Ca sáng"));
        a1.setCheckInAt(a1.getStartAt());
        a1.setCheckOutAt(a1.getEndAt());
        ShiftAssignment a2 = new ShiftAssignment(m, d.plusDays(1), shift("Ca chiều"));
        a2.setCheckInAt(a2.getStartAt().plusMinutes(15));   // muộn 15 phút
        a2.setCheckOutAt(a2.getEndAt());
        ShiftAssignment a3 = new ShiftAssignment(m, d.plusDays(2), shift("Ca sáng"));     // vắng
        PayrollLine l = payrollService.calculate(m, List.of(a1, a2, a3));
        assertThat(l.getScheduledShifts()).isEqualTo(3);
        assertThat(l.getShiftsWorked()).isEqualTo(2);
        assertThat(l.getAbsentShifts()).isEqualTo(1);
        assertThat(l.getLateCount()).isEqualTo(1);
        assertThat(l.getBaseAmount()).isEqualTo(1_000_000);          // 13tr x 2 / 26
        assertThat(l.getShiftAllowance()).isEqualTo(30_000);           // phụ cấp ca chiều
        assertThat(l.getFixedAllowance()).isEqualTo(500_000);
        assertThat(l.getLatePenalty()).isEqualTo(20_000);
        assertThat(l.getInsurance()).isEqualTo(1_365_000);           // 10,5%
        assertThat(l.getTotal()).isEqualTo(1_000_000 + 30_000 + 500_000 - 20_000 - 1_365_000);

        User h = new User();
        h.setFullName("Lương giờ");
        h.setSalaryType("HOURLY");
        h.setHourlyRate(50_000L);
        ShiftAssignment b = new ShiftAssignment(h, d, shift("Ca sáng"));
        b.setCheckInAt(b.getStartAt());
        b.setCheckOutAt(b.getEndAt());
        PayrollLine lh = payrollService.calculate(h, List.of(b));
        assertThat(lh.getWorkedMinutes()).isEqualTo(450);                // 8h - 30 phút nghỉ
        assertThat(lh.getBaseAmount()).isEqualTo(375_000);
        assertThat(lh.getInsurance()).isZero();
    }

    @Test
    void payrollWorkflow() {
        User admin = userRepo.findByEmailIgnoreCase("admin@hieuthuoc.vn").orElseThrow();
        User ds = userRepo.findByEmailIgnoreCase("duocsi@hieuthuoc.vn").orElseThrow();
        java.time.YearMonth last = java.time.YearMonth.now().minusMonths(1);
        // Dữ liệu mẫu: bảng lương tháng trước đã trả, không tính lại được
        Payroll paid = payrollRepo.findByMonth(last.toString()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(Payroll.PAID);
        assertThat(paid.getLines()).hasSize(4);
        assertThatThrownBy(() -> payrollService.generate(last, admin)).hasMessageContaining("đã chốt");
        assertThat(payrollService.myPayslips(ds)).isNotEmpty();
        // Tháng này: nháp -> thưởng -> chốt
        Payroll p = payrollService.generate(java.time.YearMonth.now(), admin);
        assertThat(p.getStatus()).isEqualTo(Payroll.DRAFT);
        PayrollLine l = p.getLines().stream().filter(x -> x.getUser().getId().equals(ds.getId())).findFirst().orElseThrow();
        long before = l.getTotal();
        payrollService.updateLine(l.getId(), 500_000, 0, "Thưởng doanh số", admin);
        assertThat(l.getTotal()).isEqualTo(before + 500_000);
        // Tính lại giữ nguyên thưởng
        Payroll again = payrollService.generate(java.time.YearMonth.now(), admin);
        assertThat(again.getLines().stream().filter(x -> x.getUser().getId().equals(ds.getId())).findFirst().orElseThrow().getBonus()).isEqualTo(500_000);
        payrollService.approve(again.getId(), admin);
        assertThatThrownBy(() -> payrollService.updateLine(l.getId(), 0, 0, null, admin)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> payrollService.generate(java.time.YearMonth.now().plusMonths(1), admin)).hasMessageContaining("chưa tới");
    }
}
