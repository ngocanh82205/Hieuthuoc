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

    private Product product(String name) {
        return productRepo.findAll().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void specialDrugCannotBeAddedToCart() {
        assertThat(cartService.checkAdd(product("Seduxen 5mg"), 1)).contains("không bán online");
    }

    @Test
    void maxPerOrderIsEnforced() {
        Product smecta = product("Smecta hương cam");
        assertThat(cartService.checkAdd(smecta, smecta.getMaxPerOrder() + 1)).contains("tối đa");
        assertThat(cartService.checkAdd(smecta, 1)).isNull();
    }

    @Test
    void voucherDoesNotApplyToPrescriptionDrugs() {
        Cart cart = new Cart();
        cart.getItems().put(product("Augmentin 625mg").getId(), 1);
        cart.setVoucherCode("WELCOME10");
        CartService.View v = cartService.build(cart, ShippingMethod.DELIVERY);
        assertThat(v.isRxRequired()).isTrue();
        assertThat(v.getDiscount()).isZero();
        assertThat(v.getVoucherError()).contains("thuốc kê đơn");
    }

    @Test
    void prescriptionOrderRequiresImage() {
        User customer = userRepo.findByEmailIgnoreCase("khachhang@gmail.com").orElseThrow();
        Cart cart = new Cart();
        cart.getItems().put(product("Augmentin 625mg").getId(), 1);
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
        cart.getItems().put(eff.getId(), 2);
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
}
