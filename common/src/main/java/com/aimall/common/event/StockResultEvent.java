package com.aimall.common.event;

/** product-service -> trade-service: 库存扣减结果。 */
public record StockResultEvent(String orderNo, boolean success, String reason) {
}
