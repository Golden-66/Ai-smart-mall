package com.aimall.ai.shopping;

import com.aimall.ai.client.ProductClient;
import com.aimall.common.dto.ProductDetail;
import com.aimall.common.dto.SkuView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 暴露给大模型的工具 (Function Calling)。
 * 只提供"查询"类工具: 价格、库存、对比。创建订单不暴露给模型,
 * 必须经过后端参数校验 + 用户明确确认, 见 ShoppingAssistant。
 */
@Component
public class ProductTools {
    private static final Logger log = LoggerFactory.getLogger(ProductTools.class);

    private final ProductClient products;

    public ProductTools(ProductClient products) {
        this.products = products;
    }

    @Tool(description = "按条件检索在售且有库存的商品 SKU, 返回 SKU 列表 (含 skuId、型号、配置、价格、库存、用途标签)。"
            + "category 取值: 手机、笔记本、耳机、平板、智能手表。")
    public List<SkuView> searchProducts(
            @ToolParam(description = "商品品类: 手机/笔记本/耳机/平板/智能手表") String category,
            @ToolParam(description = "关键词, 匹配名称/型号/品牌/描述", required = false) String keyword,
            @ToolParam(description = "用途标签, 如 办公/游戏/拍照/学习/运动/降噪/设计/编程", required = false) String usage,
            @ToolParam(description = "最低价格(元)", required = false) BigDecimal minPrice,
            @ToolParam(description = "最高价格(元)", required = false) BigDecimal maxPrice,
            @ToolParam(description = "最低内存(GB)", required = false) Integer minRamGb,
            @ToolParam(description = "最低存储(GB)", required = false) Integer minStorageGb,
            @ToolParam(description = "品牌", required = false) String brand,
            @ToolParam(description = "排序: price_asc 或 price_desc, 默认 price_desc", required = false) String sort,
            @ToolParam(description = "返回数量, 默认 8, 最大 12", required = false) Integer limit) {
        int n = limit == null ? 8 : Math.max(1, Math.min(limit, 12));
        try {
            return products.search(keyword, category, brand, usage, minPrice, maxPrice, minRamGb, minStorageGb,
                    "price_asc".equals(sort) ? "price_asc" : "price_desc", n);
        } catch (Exception e) {
            log.warn("searchProducts tool failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Tool(description = "按 skuId 列表查询最新的价格和库存。推荐前必须用它核实。")
    public List<SkuView> getSkuPriceAndStock(@ToolParam(description = "SKU ID 列表, 最多 10 个") List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        try {
            return products.skus(skuIds.stream().distinct().limit(10).toList());
        } catch (Exception e) {
            log.warn("getSkuPriceAndStock tool failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Tool(description = "对比多个 SKU 的详细参数 (处理器/屏幕/电池/续航等), 用于生成对比清单。最多 4 个。")
    public List<Map<String, Object>> compareSkus(@ToolParam(description = "要对比的 SKU ID 列表") List<Long> skuIds) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (skuIds == null || skuIds.isEmpty()) {
            return out;
        }
        try {
            List<SkuView> skus = products.skus(skuIds.stream().distinct().limit(4).toList());
            Set<Long> productIds = new LinkedHashSet<>();
            skus.forEach(s -> productIds.add(s.productId()));
            Map<Long, ProductDetail> details = new LinkedHashMap<>();
            for (Long pid : productIds) {
                details.put(pid, products.detail(pid));
            }
            for (SkuView s : skus) {
                ProductDetail d = details.get(s.productId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("skuId", s.skuId());
                row.put("name", s.productName() + " " + s.skuName());
                row.put("modelNo", s.modelNo());
                row.put("price", s.price());
                row.put("stock", s.stock());
                row.put("tags", s.tags());
                row.put("specs", d == null ? Map.of() : d.product().specs());
                out.add(row);
            }
        } catch (Exception e) {
            log.warn("compareSkus tool failed: {}", e.getMessage());
        }
        return out;
    }
}
