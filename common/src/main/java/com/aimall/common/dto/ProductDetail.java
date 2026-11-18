package com.aimall.common.dto;

import java.util.List;

/** 商品详情 = SPU + 全部 SKU, 整体缓存到 Redis。 */
public record ProductDetail(ProductView product, List<SkuView> skus) {
}
