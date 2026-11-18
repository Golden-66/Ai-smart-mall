package com.aimall.trade;

import com.aimall.common.dto.OrderItemReq;
import com.aimall.common.dto.OrderStatus;
import com.aimall.common.dto.OrderView;
import com.aimall.common.dto.SkuView;
import com.aimall.common.event.MqConstants;
import com.aimall.common.event.OrderCreatedEvent;
import com.aimall.common.event.OrderPayEvent;
import com.aimall.common.event.OrderReleaseEvent;
import com.aimall.common.event.OrderTimeoutMessage;
import com.aimall.common.event.StockItem;
import com.aimall.common.event.StockResultEvent;
import com.aimall.common.web.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单核心流程 (最终一致性):
 * 1. create : 校验 SKU + 以服务端价格计算金额 -> 落库(CREATED) -> 发布 OrderCreatedEvent + 超时延迟消息
 * 2. product-service 异步扣减库存 -> StockResultEvent -> onStockResult: CREATED -> PENDING_PAYMENT / CANCELLED
 * 3. pay    : 写支付单 -> 发布 OrderPayEvent -> onPay (模拟渠道回调): PENDING_PAYMENT -> PAID
 * 4. cancel / 超时: CREATED|PENDING_PAYMENT -> CANCELLED, 并发布 OrderReleaseEvent 归还库存
 */
@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_LINES = 20;
    private static final int MAX_QTY = 99;

    private final OrderRepository repo;
    private final ProductClient productClient;
    private final RabbitTemplate rabbit;
    private final StringRedisTemplate redis;

    public OrderService(OrderRepository repo, ProductClient productClient, RabbitTemplate rabbit,
                        StringRedisTemplate redis) {
        this.repo = repo;
        this.productClient = productClient;
        this.rabbit = rabbit;
        this.redis = redis;
    }

    // ================================================================ 创建订单
    public OrderView create(long userId, List<OrderItemReq> items, String remark, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return doCreate(userId, items, remark);
        }
        // 幂等: 同一个 Idempotency-Key 只会创建一笔订单 (防止重复点击 / 网络重试)
        String key = "idem:order:" + userId + ":" + idempotencyKey;
        Boolean first = redis.opsForValue().setIfAbsent(key, "PENDING", Duration.ofMinutes(10));
        if (!Boolean.TRUE.equals(first)) {
            String value = redis.opsForValue().get(key);
            if (value == null || "PENDING".equals(value)) {
                throw new BizException(409, "订单正在处理中, 请勿重复提交");
            }
            return get(userId, value);
        }
        try {
            OrderView order = doCreate(userId, items, remark);
            redis.opsForValue().set(key, order.orderNo(), Duration.ofHours(24));
            return order;
        } catch (RuntimeException e) {
            redis.delete(key);
            throw e;
        }
    }

    private OrderView doCreate(long userId, List<OrderItemReq> reqItems, String remark) {
        Map<Long, Integer> merged = mergeItems(reqItems);
        Map<Long, SkuView> skus = new LinkedHashMap<>();
        for (SkuView s : productClient.skus(List.copyOf(merged.keySet()))) {
            skus.put(s.skuId(), s);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderRepository.ItemRow> rows = new ArrayList<>();
        List<StockItem> stockItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : merged.entrySet()) {
            SkuView sku = skus.get(e.getKey());
            if (sku == null) {
                throw new BizException(404, "商品不存在: skuId=" + e.getKey());
            }
            if (sku.stock() < e.getValue()) {
                throw new BizException(409, "库存不足: " + sku.productName() + " " + sku.skuName()
                        + " (剩余 " + sku.stock() + ")");
            }
            total = total.add(sku.price().multiply(BigDecimal.valueOf(e.getValue())));
            rows.add(new OrderRepository.ItemRow(sku.skuId(), sku.productId(), sku.productName(),
                    sku.skuName(), sku.price(), e.getValue()));
            stockItems.add(new StockItem(sku.skuId(), e.getValue()));
        }

        String orderNo = newOrderNo();
        repo.createOrder(orderNo, userId, total, remark, rows);

        publishCreated(orderNo, userId, stockItems);
        try {
            // 延迟消息发往默认交换机, routing key 即队列名
            rabbit.convertAndSend("", MqConstants.Q_ORDER_TIMEOUT_DELAY, new OrderTimeoutMessage(orderNo));
        } catch (RuntimeException e) {
            log.warn("发送订单超时消息失败, 将由补偿任务兜底: {}", orderNo, e);
        }
        return get(userId, orderNo);
    }

    private static Map<Long, Integer> mergeItems(List<OrderItemReq> reqItems) {
        if (reqItems == null || reqItems.isEmpty()) {
            throw new BizException("订单商品不能为空");
        }
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemReq it : reqItems) {
            if (it == null || it.skuId() == null || it.quantity() == null || it.quantity() < 1 || it.quantity() > MAX_QTY) {
                throw new BizException("商品数量需在 1-" + MAX_QTY + " 之间");
            }
            merged.merge(it.skuId(), it.quantity(), Integer::sum);
        }
        if (merged.size() > MAX_LINES) {
            throw new BizException("单笔订单最多 " + MAX_LINES + " 种商品");
        }
        if (merged.values().stream().anyMatch(q -> q > MAX_QTY)) {
            throw new BizException("同一商品数量不能超过 " + MAX_QTY);
        }
        return merged;
    }

    private static String newOrderNo() {
        return "O" + LocalDateTime.now().format(NO_FMT) + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    void publishCreated(String orderNo, long userId, List<StockItem> items) {
        rabbit.convertAndSend(MqConstants.EXCHANGE, MqConstants.RK_ORDER_CREATED, new OrderCreatedEvent(orderNo, userId, items));
    }

    /** 补偿任务使用: 重新发布 "订单已创建" 事件 (库存扣减是幂等的)。 */
    public void republishCreated(OrderRepository.OrderRow order) {
        List<StockItem> items = repo.findItems(order.orderNo()).stream()
                .map(i -> new StockItem(i.skuId(), i.quantity())).toList();
        publishCreated(order.orderNo(), order.userId(), items);
    }

    // ================================================================ 查询
    public OrderView get(long userId, String orderNo) {
        OrderRepository.OrderRow o = repo.findOrder(orderNo).orElseThrow(() -> new BizException(404, "订单不存在"));
        if (o.userId() != userId) {
            throw new BizException(404, "订单不存在"); // 不暴露他人订单是否存在
        }
        return OrderRepository.toView(o, repo.findItems(orderNo));
    }

    public List<OrderView> list(long userId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 50));
        return repo.listByUser(userId, safeSize, (Math.max(page, 1) - 1) * safeSize).stream()
                .map(o -> OrderRepository.toView(o, repo.findItems(o.orderNo()))).toList();
    }

    // ================================================================ 支付
    public Map<String, Object> pay(long userId, String orderNo) {
        OrderView order = get(userId, orderNo);
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new BizException(409, "当前订单状态不允许支付: " + order.statusText());
        }
        repo.insertPaymentIfAbsent(orderNo, userId, order.totalAmount());
        rabbit.convertAndSend(MqConstants.EXCHANGE, MqConstants.RK_ORDER_PAY, new OrderPayEvent(orderNo, userId, order.totalAmount()));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderNo", orderNo);
        resp.put("payStatus", "PAYING");
        resp.put("message", "支付请求已受理, 结果将异步通知, 请稍后查询订单状态");
        return resp;
    }

    /** 模拟支付渠道的异步回调。 */
    public void onPay(OrderPayEvent e) {
        if (repo.markPaid(e.orderNo()) == 1) {
            repo.updatePaymentStatus(e.orderNo(), 1);
            log.info("订单 {} 支付成功", e.orderNo());
            return;
        }
        var order = repo.findOrder(e.orderNo());
        if (order.isPresent() && order.get().status() == OrderStatus.PAID) {
            return; // 重复回调
        }
        repo.updatePaymentStatus(e.orderNo(), 2);
        log.warn("订单 {} 支付到账时订单已不可支付 (需退款)", e.orderNo());
    }

    // ================================================================ 取消 / 超时
    public void cancelByUser(long userId, String orderNo) {
        OrderView order = get(userId, orderNo);
        if (!cancelInternal(orderNo, "用户取消")) {
            throw new BizException(409, "当前订单状态不允许取消: " + order.statusText());
        }
    }

    /** @return true 表示本次确实完成了取消。 */
    public boolean cancelInternal(String orderNo, String reason) {
        int n = repo.casStatus(orderNo, List.of(OrderStatus.CREATED, OrderStatus.PENDING_PAYMENT),
                OrderStatus.CANCELLED, reason);
        if (n == 1) {
            publishRelease(orderNo, reason);
            return true;
        }
        return false;
    }

    private void publishRelease(String orderNo, String reason) {
        List<StockItem> items = repo.findItems(orderNo).stream()
                .map(i -> new StockItem(i.skuId(), i.quantity())).toList();
        rabbit.convertAndSend(MqConstants.EXCHANGE, MqConstants.RK_ORDER_RELEASE, new OrderReleaseEvent(orderNo, items, reason));
    }

    public void onTimeout(OrderTimeoutMessage m) {
        if (cancelInternal(m.orderNo(), "支付超时自动取消")) {
            log.info("订单 {} 支付超时, 已自动取消并归还库存", m.orderNo());
        }
    }

    // ================================================================ 库存结果
    public void onStockResult(StockResultEvent e) {
        if (e.success()) {
            int n = repo.casStatus(e.orderNo(), List.of(OrderStatus.CREATED), OrderStatus.PENDING_PAYMENT, null);
            if (n == 0) {
                var order = repo.findOrder(e.orderNo());
                if (order.isPresent() && order.get().status() == OrderStatus.CANCELLED) {
                    // 订单在库存扣减结果返回前就被取消了: 迟到的扣减需要归还
                    publishRelease(e.orderNo(), "订单已取消, 归还迟到的库存扣减");
                }
            }
        } else {
            repo.casStatus(e.orderNo(), List.of(OrderStatus.CREATED), OrderStatus.CANCELLED,
                    e.reason() == null ? "库存不足" : e.reason());
        }
    }
}
