package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.BatchRepository;
import com.hieuthuoc.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quản lý tồn kho theo lô.
 * - Tồn thực tế (onHand) = tổng số lượng các lô còn hạn và không bị khóa.
 * - Giữ chỗ (reserved) = số lượng trong các đơn chưa soạn hàng (chờ duyệt / chờ xác nhận / đã xác nhận).
 * - Khi đơn chuyển sang "Đang chuẩn bị" mới trừ kho thật theo FEFO (hết hạn trước - xuất trước).
 */
@Service
@RequiredArgsConstructor
public class StockService {
    private final BatchRepository batchRepo;
    private final OrderItemRepository orderItemRepo;

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) map.put((Long) r[0], ((Number) r[1]).longValue());
        return map;
    }

    /** Điền onHand / reserved / available vào danh sách sản phẩm. */
    @Transactional(readOnly = true)
    public <T extends Collection<Product>> T fill(T products) {
        Map<Long, Long> onHand = toMap(batchRepo.sumOnHandByProduct(LocalDate.now()));
        Map<Long, Long> reserved = toMap(orderItemRepo.sumQuantityByProduct(OrderStatus.RESERVING));
        for (Product p : products) {
            p.setOnHand(onHand.getOrDefault(p.getId(), 0L));
            p.setReserved(reserved.getOrDefault(p.getId(), 0L));
            p.setAvailable(p.getOnHand() - p.getReserved());
        }
        return products;
    }

    public Product fill(Product p) {
        fill(List.of(p));
        return p;
    }

    /** Xuất kho theo FEFO cho toàn bộ đơn hàng; ghi nhận lô đã xuất để truy vết. */
    @Transactional
    public void allocateFefo(Order order) {
        LocalDate today = LocalDate.now();
        for (OrderItem item : order.getItems()) {
            int need = item.getQuantity();
            for (Batch b : batchRepo.findSellableFefo(item.getProduct().getId(), today)) {
                if (need <= 0) break;
                int take = Math.min(need, b.getQuantity());
                b.setQuantity(b.getQuantity() - take);
                OrderItemBatch a = new OrderItemBatch();
                a.setOrderItem(item);
                a.setBatch(b);
                a.setQuantity(take);
                item.getAllocations().add(a);
                need -= take;
            }
            if (need > 0) {
                throw new BusinessException("Không đủ tồn kho hợp lệ cho \"" + item.getProductName() + "\" (thiếu " + need + " " + item.getUnit() + ").");
            }
        }
    }

    /** Hoàn lại số lượng về đúng các lô đã xuất (khi hủy đơn / nhận trả hàng). */
    @Transactional
    public void restore(Order order) {
        for (OrderItem item : order.getItems()) {
            for (OrderItemBatch a : item.getAllocations()) {
                a.getBatch().setQuantity(a.getBatch().getQuantity() + a.getQuantity());
            }
            item.getAllocations().clear();
        }
    }
}
