package com.aimall.ai.model;

import java.math.BigDecimal;

public record RecommendedItem(
        int index,
        Long skuId,
        Long productId,
        String productName,
        String skuName,
        String modelNo,
        BigDecimal price,
        Integer stock,
        String reason) {
}
