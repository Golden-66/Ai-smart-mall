package com.aimall.trade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import com.aimall.common.dto.OrderStatus;

/**
 * 补偿任务 (消息丢失 / 服务宕机时的兜底):
 * 1. 超过 2 分钟仍停留在 CREATED 的订单: 重新发布扣库存事件 (扣减幂等, 重复发布安全);
 * 2. 超过支付时限仍未支付的订单: 直接关单并归还库存 (兜底延迟队列消息丢失)。
 * 多实例同时执行也安全: 状态流转使用 CAS, 库存使用 stock_log 幂等。
 */
@Component
public class OrderCompensationJob {
    private static final Logger log = LoggerFactory.getLogger(OrderCompensationJob.class);

    private final OrderRepository repo;
    private final OrderService orderService;
    private final long timeoutSeconds;

    public OrderCompensationJob(OrderRepository repo, OrderService orderService,
                                @Value("${mall.order.timeout-seconds:1800}") long timeoutSeconds) {
        this.repo = repo;
        this.orderService = orderService;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void republishStuckOrders() {
        var stale = repo.findStale(List.of(OrderStatus.CREATED), LocalDateTime.now().minusMinutes(2), 100);
        for (var order : stale) {
            log.warn("订单 {} 长时间停留在 CREATED, 重新发布扣库存事件", order.orderNo());
            orderService.republishCreated(order);
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void cancelExpiredOrders() {
        var expired = repo.findStale(List.of(OrderStatus.CREATED, OrderStatus.PENDING_PAYMENT),
                LocalDateTime.now().minusSeconds(timeoutSeconds + 60), 100);
        for (var order : expired) {
            if (orderService.cancelInternal(order.orderNo(), "支付超时自动取消 (补偿任务)")) {
                log.warn("补偿任务关闭超时订单 {}", order.orderNo());
            }
        }
    }
}
