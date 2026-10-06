package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tồn kho theo lô:
 * - Thực tế (on hand) = tổng lô còn hạn, không khóa.
 *   Kênh online chỉ tính lô còn hạn dùng tối thiểu N ngày (online_min_shelf_days) vì hàng còn phải soạn và giao;
 *   lô cận hạn hơn chỉ bán tại quầy (dược sĩ tư vấn trực tiếp).
 * - Giữ chỗ (reserved) = số lượng trong các đơn chưa soạn hàng.
 * - Khả dụng = thực tế - giữ chỗ.
 * Xuất kho theo FEFO (hết hạn trước xuất trước).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockService {
    private final SettingService settings;
    private final Sql sql;

    @PersistenceContext
    private EntityManager em;

    /** Ngày hết hạn sớm nhất được bán: online cần còn hạn tối thiểu N ngày, tại quầy chỉ cần còn hạn. */
    public LocalDate minExpDate(boolean online) {
        return LocalDate.now().plusDays(online ? Math.max(0, settings.getInt("online_min_shelf_days")) : 0);
    }

    public Map<Long, Long> onHandMap(Collection<Long> productIds, boolean online) {
        Map<String, Object> p = new HashMap<>();
        p.put("minExp", minExpDate(online));
        String where = "";
        if (productIds != null) {
            if (productIds.isEmpty()) return new HashMap<>();
            where = " and b.product_id in (:ids)";
            p.put("ids", productIds);
        }
        return sql.longMap("select b.product_id as pid, sum(b.quantity) as qty from batches b where b.locked = 0 and b.exp_date >= :minExp"
                + where + " group by b.product_id", p);
    }

    public Map<Long, Long> reservedMap(Collection<Long> productIds) {
        Map<String, Object> p = new HashMap<>();
        p.put("st", OrderStatus.RESERVING);
        String where = "";
        if (productIds != null) {
            if (productIds.isEmpty()) return new HashMap<>();
            where = " and oi.product_id in (:ids)";
            p.put("ids", productIds);
        }
        return sql.longMap("select oi.product_id as pid, sum(oi.quantity * oi.unit_factor) as qty from order_items oi join orders o on o.id = oi.order_id"
                + " where o.status in (:st)" + where + " group by oi.product_id", p);
    }

    /**
     * Điền onHand / reserved / available vào sản phẩm.
     * online = true: số bán được online (bỏ lô cận hạn); false: tồn bán được tại quầy / xem kho.
     */
    public <T extends Collection<Product>> T fill(T products, boolean online) {
        List<Long> ids = products.stream().map(Product::getId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return products;
        Map<Long, Long> onHand = onHandMap(ids, online);
        Map<Long, Long> reserved = reservedMap(ids);
        for (Product p : products) {
            p.setOnHand(onHand.getOrDefault(p.getId(), 0L));
            p.setReserved(reserved.getOrDefault(p.getId(), 0L));
            p.setAvailable(p.getOnHand() - p.getReserved());
        }
        return products;
    }

    public <T extends Collection<Product>> T fill(T products) {
        return fill(products, true);
    }

    public Product fill(Product p, boolean online) {
        fill(List.of(p), online);
        return p;
    }

    public Product fill(Product p) {
        return fill(p, true);
    }

    public long available(Product p, boolean online) {
        fill(p, online);
        return p.getAvailable();
    }

    /** Lô bán được của 1 sản phẩm theo FEFO (khóa FOR UPDATE). */
    public List<Batch> fefoBatches(Long productId, boolean online) {
        return em.createQuery("select b from Batch b where b.product.id = :pid and b.locked = false and b.expDate >= :minExp"
                        + " and b.quantity > 0 order by b.expDate asc, b.id asc", Batch.class)
                .setParameter("pid", productId).setParameter("minExp", minExpDate(online))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    /**
     * Xuất kho cho đơn hàng: nếu nhân viên chọn/quét lô cho dòng hàng thì lấy lô đó trước,
     * phần còn thiếu lấy tiếp theo FEFO. Ghi lô đã xuất vào order_item_batches (truy vết thu hồi).
     * Đơn online chỉ xuất lô còn đủ hạn dùng tối thiểu; hóa đơn tại quầy được xuất lô cận hạn.
     *
     * @param preferred orderItemId => lô
     */
    public void allocate(Order order, Map<Long, Batch> preferred) {
        boolean online = !order.isPos();
        for (OrderItem item : order.getItems()) {
            if (!item.getAllocations().isEmpty()) continue;
            int need = item.baseQuantity();
            List<Batch> candidates = new ArrayList<>(fefoBatches(item.getProduct().getId(), online));
            Batch pick = preferred == null ? null : preferred.get(item.getId());
            if (pick != null) {
                if (!pick.getProduct().getId().equals(item.getProduct().getId())) {
                    throw new BusinessException("Lô " + pick.getBatchNo() + " không phải của sản phẩm \"" + item.getProductName() + "\".");
                }
                if (pick.isLocked() || pick.getExpDate().isBefore(minExpDate(online)) || pick.getQuantity() <= 0) {
                    throw new BusinessException("Lô " + pick.getBatchNo() + " đã khóa, hết hạn / cận hạn hoặc hết hàng - không được xuất.");
                }
                candidates.removeIf(b -> b.getId().equals(pick.getId()));
                candidates.add(0, pick);
            }
            need = take(item, candidates, need);
            if (need > 0) {
                throw new BusinessException("Không đủ tồn kho hợp lệ cho \"" + item.getProductName() + "\" (thiếu " + need + " " + item.getProduct().getUnit() + ")"
                        + (online ? " - đơn online chỉ xuất lô còn hạn dùng từ " + settings.getInt("online_min_shelf_days") + " ngày." : "."));
            }
        }
    }

    public void allocate(Order order) {
        allocate(order, Map.of());
    }

    /** Trừ lần lượt các lô ứng viên cho dòng hàng, ghi lô đã xuất; trả về số lượng còn thiếu. */
    private int take(OrderItem item, List<Batch> candidates, int need) {
        for (Batch b : candidates) {
            if (need <= 0) break;
            int take = Math.min(need, b.getQuantity());
            if (take <= 0) continue;
            b.setQuantity(b.getQuantity() - take);
            OrderItemBatch a = new OrderItemBatch();
            a.setOrderItem(item);
            a.setBatch(b);
            a.setQuantity(take);
            item.getAllocations().add(a);
            em.persist(a);
            need -= take;
        }
        return need;
    }

    /** Các phần hàng của đơn đã xuất từ lô đang bị khóa (thu hồi / nghi ngờ chất lượng). */
    public List<OrderItemBatch> lockedAllocations(Order order) {
        return em.createQuery("select a from OrderItemBatch a join fetch a.batch b join fetch a.orderItem oi"
                        + " where oi.order.id = :oid and b.locked = true order by a.id", OrderItemBatch.class)
                .setParameter("oid", order.getId()).getResultList();
    }

    /**
     * Đổi phần hàng xuất từ lô bị khóa sang lô hợp lệ khác (FEFO): hàng lô khóa trả về đúng lô đó (để cách ly / hủy),
     * xuất lại số lượng tương ứng từ lô khác. Không đủ hàng thay thế thì báo lỗi, giữ nguyên.
     *
     * @return mô tả thay đổi
     */
    public List<String> swapLockedBatches(Order order) {
        boolean online = !order.isPos();
        List<String> changes = new ArrayList<>();
        Map<OrderItem, List<OrderItemBatch>> byItem = lockedAllocations(order).stream()
                .collect(Collectors.groupingBy(OrderItemBatch::getOrderItem, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<OrderItem, List<OrderItemBatch>> e : byItem.entrySet()) {
            OrderItem item = e.getKey();
            int need = 0;
            for (OrderItemBatch a : e.getValue()) {
                Batch b = em.find(Batch.class, a.getBatch().getId(), LockModeType.PESSIMISTIC_WRITE);
                b.setQuantity(b.getQuantity() + a.getQuantity());
                changes.add(item.getProductName() + ": trả " + a.getQuantity() + " về lô khóa " + b.getBatchNo());
                need += a.getQuantity();
                item.getAllocations().remove(a);
                em.remove(a);
            }
            Set<OrderItemBatch> before = new HashSet<>(item.getAllocations());
            int missing = take(item, new ArrayList<>(fefoBatches(item.getProduct().getId(), online)), need);
            if (missing > 0) {
                throw new BusinessException("Không đủ hàng ở lô khác để thay cho \"" + item.getProductName() + "\" (thiếu " + missing + "). "
                        + "Hãy hủy đơn hoặc chờ nhập hàng.");
            }
            for (OrderItemBatch n : item.getAllocations()) {
                if (!before.contains(n)) changes.add(item.getProductName() + ": xuất " + n.getQuantity() + " từ lô " + n.getBatch().getBatchNo());
            }
        }
        return changes;
    }

    /** Hoàn lại số lượng về đúng các lô đã xuất (khi hủy đơn / nhận trả hàng). */
    public void restore(Order order) {
        for (OrderItem item : order.getItems()) {
            for (OrderItemBatch a : new ArrayList<>(item.getAllocations())) {
                Batch b = em.find(Batch.class, a.getBatch().getId(), LockModeType.PESSIMISTIC_WRITE);
                b.setQuantity(b.getQuantity() + a.getQuantity());
            }
            item.getAllocations().clear();
        }
        em.flush();
    }

    /**
     * Hàng đã trừ tồn (đã xuất lô) cho đơn đang soạn / đóng gói nhưng vẫn còn trong nhà thuốc: batchId => số lượng.
     * Kiểm kê phải cộng phần này vào sổ sách, nếu không hàng chờ giao trên kệ bị tính thành thừa.
     */
    public Map<Long, Long> pendingOutMap(Collection<Long> batchIds) {
        Map<String, Object> p = new HashMap<>();
        p.put("st", List.of(OrderStatus.PREPARING, OrderStatus.PACKED));
        String where = "";
        if (batchIds != null) {
            if (batchIds.isEmpty()) return new HashMap<>();
            where = " and oib.batch_id in (:ids)";
            p.put("ids", batchIds);
        }
        return sql.longMap("select oib.batch_id as bid, sum(oib.quantity) as qty from order_item_batches oib"
                + " join order_items oi on oi.id = oib.order_item_id join orders o on o.id = oi.order_id"
                + " where o.status in (:st)" + where + " group by oib.batch_id", p);
    }

    /** Giá vốn của đơn theo các lô đã xuất (số lượng x giá nhập). */
    public long costOf(Order order) {
        return sql.scalar("select coalesce(sum(oib.quantity * b.import_price), 0) from order_items oi"
                + " join order_item_batches oib on oib.order_item_id = oi.id join batches b on b.id = oib.batch_id where oi.order_id = :oid",
                Map.of("oid", order.getId()));
    }

    public boolean isAllocated(Order order) {
        return sql.scalar("select count(*) from order_item_batches oib join order_items oi on oi.id = oib.order_item_id where oi.order_id = :oid",
                Map.of("oid", order.getId())) > 0;
    }
}
