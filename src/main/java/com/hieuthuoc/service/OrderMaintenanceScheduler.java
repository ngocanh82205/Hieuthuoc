package com.hieuthuoc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Lệnh định kỳ orders:expire-abandoned của bản Laravel (mỗi 15 phút): tự hủy các đơn bỏ ngang để nhả hàng đang giữ chỗ -
 * đơn bị từ chối đơn thuốc mà khách không tải lại, đơn thanh toán online chưa trả tiền, báo giá dược sĩ lên mà khách chưa xác nhận.
 * Mỗi đơn hủy trong một giao dịch riêng (OrderService là @Transactional) để lỗi ở một đơn không ảnh hưởng đơn khác.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMaintenanceScheduler {
    private final OrderService orders;

    @Scheduled(fixedDelayString = "${app.orders.expire-interval-ms:900000}", initialDelay = 60_000)
    public void expireAbandoned() {
        int rx = sweep(orders.abandonedRxRejectedIds(), orders::cancelAbandonedRxRejected);
        int unpaid = sweep(orders.unpaidOnlineIds(), orders::cancelUnpaidOnline);
        int quotes = sweep(orders.unconfirmedQuoteIds(), orders::cancelUnconfirmedQuote);
        if (rx + unpaid + quotes > 0) {
            log.info("Đã tự hủy {} đơn (từ chối đơn thuốc: {}, chưa thanh toán: {}, báo giá: {})", rx + unpaid + quotes, rx, unpaid, quotes);
        }
    }

    private static int sweep(List<Long> ids, Function<Long, Boolean> cancel) {
        int n = 0;
        for (Long id : ids) {
            try {
                if (Boolean.TRUE.equals(cancel.apply(id))) n++;
            } catch (RuntimeException e) {
                log.warn("Không tự hủy được đơn #{}: {}", id, e.getMessage());
            }
        }
        return n;
    }
}
