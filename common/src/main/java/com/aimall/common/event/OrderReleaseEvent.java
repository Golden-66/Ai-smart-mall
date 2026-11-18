package com.aimall.common.event;

import java.util.List;

/** trade-service -> product-service: 订单取消/超时, 归还库存。 */
public record OrderReleaseEvent(String orderNo, List<StockItem> items, String reason) {
}
