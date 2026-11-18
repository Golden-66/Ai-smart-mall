package com.aimall.common.dto;

import java.math.BigDecimal;

/** SKU 维度的商品视图 (价格/库存以 product-service 为准)。 */
public record SkuView(
        Long skuId,
        Long productId,
        String productName,
        String brand,
        String category,
        String modelNo,
        String skuName,
        Integer ramGb,
        Integer storageGb,
        BigDecimal price,
        Integer stock,
        String tags) {
}
