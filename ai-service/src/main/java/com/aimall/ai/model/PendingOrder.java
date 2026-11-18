package com.aimall.ai.model;

import java.math.BigDecimal;

/** 等待用户确认的订单草稿: 价格由后端实时查询得出, 不信任大模型给出的任何金额。 */
public record PendingOrder(
        String token,
        Long skuId,
        String productName,
        String skuName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal total) {
}
