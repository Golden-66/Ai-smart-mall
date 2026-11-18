package com.aimall.common.event;

import java.util.List;

/** trade-service -> product-service: 请求扣减库存。 */
public record OrderCreatedEvent(String orderNo, Long userId, List<StockItem> items) {
}
