package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {
    private final ProductRepository productRepo;
    private final StockService stockService;
    private final VoucherService voucherService;
    private final SettingService settings;

    @Getter
    public static class Line {
        private final Product product;
        private final int quantity;
        private String error;

        Line(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        public long getLineTotal() {
            return product.getPrice() * quantity;
        }
    }

    @Getter
    public static class View {
        private final List<Line> lines = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private long subtotal;
        private long discountable;
        private boolean rxRequired;
        private Voucher voucher;
        private String voucherError;
        private long discount;
        private long shippingFee;
        private long total;

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    /** Kiểm tra khả năng thêm vào giỏ; trả về thông báo lỗi hoặc null nếu hợp lệ. */
    public String checkAdd(Product p, int newQty) {
        if (p == null || !p.isActive()) return "Sản phẩm không tồn tại.";
        if (!p.getDrugType().isSellableOnline()) return "Thuốc kiểm soát đặc biệt không bán online. Vui lòng đến trực tiếp nhà thuốc.";
        stockService.fill(p);
        if (p.getAvailable() <= 0) return p.getName() + " đang tạm hết hàng.";
        if (newQty > p.getAvailable()) return "Chỉ còn " + p.getAvailable() + " " + p.getUnit() + " " + p.getName() + " trong kho.";
        if (p.getMaxPerOrder() != null && newQty > p.getMaxPerOrder()) {
            return "Mỗi đơn chỉ được mua tối đa " + p.getMaxPerOrder() + " " + p.getUnit() + " " + p.getName() + ".";
        }
        return null;
    }

    public View build(Cart cart, ShippingMethod shippingMethod) {
        View v = new View();
        List<Product> products = new ArrayList<>(productRepo.findAllById(cart.getItems().keySet()));
        stockService.fill(products);
        for (Product p : products) {
            if (!p.isActive()) continue;
            Line line = new Line(p, cart.quantityOf(p.getId()));
            if (!p.getDrugType().isSellableOnline()) line.error = "Thuốc kiểm soát đặc biệt không bán online";
            else if (p.getAvailable() <= 0) line.error = "Sản phẩm tạm hết hàng";
            else if (line.quantity > p.getAvailable()) line.error = "Chỉ còn " + p.getAvailable() + " " + p.getUnit();
            else if (p.getMaxPerOrder() != null && line.quantity > p.getMaxPerOrder()) line.error = "Tối đa " + p.getMaxPerOrder() + " " + p.getUnit() + " mỗi đơn";
            if (line.error != null) v.errors.add(p.getName() + ": " + line.error);
            v.lines.add(line);
            v.subtotal += line.getLineTotal();
            if (p.getDrugType().isPrescription()) v.rxRequired = true;
            else v.discountable += line.getLineTotal();
        }
        VoucherService.Result r = voucherService.validate(cart.getVoucherCode(), v.discountable);
        v.voucher = r.voucher();
        v.voucherError = r.error();
        v.discount = r.discount();
        v.shippingFee = v.lines.isEmpty() ? 0 : settings.shippingFee(shippingMethod, v.subtotal - v.discount);
        v.total = v.subtotal - v.discount + v.shippingFee;
        return v;
    }
}
