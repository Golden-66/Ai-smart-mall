package com.aimall.common.dto;

import java.math.BigDecimal;

public record OrderItemView(
        Long skuId,
        Long productId,
        String productName,
        String skuName,
        BigDecimal price,
        Integer quantity) {
}
