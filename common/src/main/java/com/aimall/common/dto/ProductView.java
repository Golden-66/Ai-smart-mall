package com.aimall.common.dto;

import java.math.BigDecimal;
import java.util.Map;

/** SPU 维度的商品视图。 */
public record ProductView(
        Long id,
        String name,
        String brand,
        String category,
        String modelNo,
        String tags,
        String description,
        Map<String, Object> specs,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer totalStock) {
}
