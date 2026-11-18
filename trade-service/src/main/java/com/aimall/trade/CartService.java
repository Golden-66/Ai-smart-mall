package com.aimall.trade;

import com.aimall.common.dto.OrderItemReq;
import com.aimall.common.dto.OrderView;
import com.aimall.common.dto.SkuView;
import com.aimall.common.web.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 购物车存放在 Redis Hash: cart:{userId} -> {skuId: quantity}, 读取时再向商品服务取实时价格与库存。 */
@Service
public class CartService {

    public record CartItemView(Long skuId, Long productId, String productName, String skuName,
                               BigDecimal price, Integer quantity, Integer stock, BigDecimal subtotal) {
    }

    public record CartView(List<CartItemView> items, BigDecimal totalAmount) {
    }

    private static final int MAX_QTY = 99;
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redis;
    private final ProductClient productClient;
    private final OrderService orderService;

    public CartService(StringRedisTemplate redis, ProductClient productClient, OrderService orderService) {
        this.redis = redis;
        this.productClient = productClient;
        this.orderService = orderService;
    }

    private static String key(long userId) {
        return "cart:" + userId;
    }

    public CartView list(long userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(key(userId));
        if (entries.isEmpty()) {
            return new CartView(List.of(), BigDecimal.ZERO);
        }
        List<Long> ids = entries.keySet().stream().map(k -> Long.parseLong(k.toString())).toList();
        Map<Long, SkuView> skus = new java.util.HashMap<>();
        for (SkuView s : productClient.skus(ids)) {
            skus.put(s.skuId(), s);
        }
        List<CartItemView> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            long skuId = Long.parseLong(e.getKey().toString());
            int qty = Integer.parseInt(e.getValue().toString());
            SkuView s = skus.get(skuId);
            if (s == null) {
                redis.opsForHash().delete(key(userId), e.getKey()); // 商品已下架, 清理
                continue;
            }
            BigDecimal subtotal = s.price().multiply(BigDecimal.valueOf(qty));
            total = total.add(subtotal);
            items.add(new CartItemView(skuId, s.productId(), s.productName(), s.skuName(), s.price(), qty, s.stock(), subtotal));
        }
        return new CartView(items, total);
    }

    public void add(long userId, long skuId, int quantity) {
        if (quantity < 1 || quantity > MAX_QTY) {
            throw new BizException("数量需在 1-" + MAX_QTY + " 之间");
        }
        if (productClient.skus(List.of(skuId)).isEmpty()) {
            throw new BizException(404, "商品不存在");
        }
        Long now = redis.opsForHash().increment(key(userId), String.valueOf(skuId), quantity);
        if (now != null && now > MAX_QTY) {
            redis.opsForHash().put(key(userId), String.valueOf(skuId), String.valueOf(MAX_QTY));
        }
        redis.expire(key(userId), TTL);
    }

    public void setQuantity(long userId, long skuId, int quantity) {
        if (quantity <= 0) {
            remove(userId, skuId);
            return;
        }
        if (quantity > MAX_QTY) {
            throw new BizException("数量不能超过 " + MAX_QTY);
        }
        redis.opsForHash().put(key(userId), String.valueOf(skuId), String.valueOf(quantity));
        redis.expire(key(userId), TTL);
    }

    public void remove(long userId, long skuId) {
        redis.opsForHash().delete(key(userId), String.valueOf(skuId));
    }

    /** 购物车结算: 创建订单成功后才清空购物车。 */
    public OrderView checkout(long userId, String idempotencyKey) {
        Map<Object, Object> entries = redis.opsForHash().entries(key(userId));
        if (entries.isEmpty()) {
            throw new BizException("购物车为空");
        }
        List<OrderItemReq> items = new ArrayList<>();
        entries.forEach((k, v) -> items.add(new OrderItemReq(Long.parseLong(k.toString()), Integer.parseInt(v.toString()))));
        OrderView order = orderService.create(userId, items, "购物车结算", idempotencyKey);
        redis.delete(key(userId));
        return order;
    }
}
