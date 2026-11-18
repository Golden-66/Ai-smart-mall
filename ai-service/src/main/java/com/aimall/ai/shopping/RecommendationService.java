package com.aimall.ai.shopping;

import com.aimall.ai.client.ProductClient;
import com.aimall.ai.llm.AiPrompts;
import com.aimall.ai.llm.Llm;
import com.aimall.ai.model.RecommendationResult;
import com.aimall.ai.model.RecommendedItem;
import com.aimall.ai.model.RequirementSlots;
import com.aimall.common.dto.SkuView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 推荐 = 大模型 (Function Calling 检索 + 挑选 + 写理由) + 后端校验 + 规则兜底。
 * 大模型给出的 skuId 一律回查商品服务, 价格/库存/品类/预算/配置任一不满足就丢弃,
 * 全部丢弃时退化为纯规则检索, 保证推荐结果可信且始终有结果可用。
 */
@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final int MAX_ITEMS = 3;

    public record Recommendation(String summary, List<RecommendedItem> items) {
    }

    private final Llm llm;
    private final ProductTools tools;
    private final ProductClient products;

    public RecommendationService(Llm llm, ProductTools tools, ProductClient products) {
        this.llm = llm;
        this.tools = tools;
        this.products = products;
    }

    public Recommendation recommend(RequirementSlots req) {
        RecommendationResult result = null;
        try {
            result = llm.structured(AiPrompts.RECOMMEND_SYSTEM, "用户的购物需求：\n" + req.describe(),
                    RecommendationResult.class, tools);
        } catch (Exception e) {
            log.warn("LLM 推荐失败, 使用规则兜底: {}", e.getMessage());
        }

        List<RecommendedItem> items = validate(req, result);
        String summary = result != null && result.summary() != null && !result.summary().isBlank()
                ? result.summary().trim() : null;
        if (items.isEmpty()) {
            items = fallback(req);
            summary = items.isEmpty() ? null : "按您的预算和用途，为您筛选了下面几款配置较高、库存充足的商品：";
        }
        return new Recommendation(summary, items);
    }

    // ------------------------------------------------------------------ 校验
    public static boolean satisfies(RequirementSlots r, SkuView s) {
        return r.accepts(s);
    }

    private List<RecommendedItem> validate(RequirementSlots req, RecommendationResult result) {
        if (result == null || result.picks() == null) {
            return List.of();
        }
        List<RecommendationResult.Pick> picks = result.picks().stream()
                .filter(p -> p != null && p.skuId() != null).limit(6).toList();
        if (picks.isEmpty()) {
            return List.of();
        }
        Map<Long, SkuView> live = new HashMap<>();
        for (SkuView s : products.skus(picks.stream().map(RecommendationResult.Pick::skuId).distinct().toList())) {
            live.put(s.skuId(), s);
        }
        List<RecommendedItem> out = new ArrayList<>();
        Set<Long> seenProducts = new HashSet<>();
        for (RecommendationResult.Pick p : picks) {
            SkuView s = live.get(p.skuId());
            if (!satisfies(req, s) || !seenProducts.add(s.productId())) {
                continue;
            }
            out.add(toItem(out.size() + 1, s, p.reason()));
            if (out.size() >= MAX_ITEMS) {
                break;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 兜底
    private List<RecommendedItem> fallback(RequirementSlots req) {
        List<String> tags = req.usageTags();
        List<SkuView> found = search(req, tags.isEmpty() ? null : tags.get(0));
        if (found.isEmpty() && !tags.isEmpty()) {
            found = search(req, null); // 用途标签过窄时放宽
        }
        List<RecommendedItem> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (SkuView s : found) {
            if (satisfies(req, s) && seen.add(s.productId())) {
                out.add(toItem(out.size() + 1, s, "在预算内配置较高，库存充足"));
                if (out.size() >= MAX_ITEMS) {
                    break;
                }
            }
        }
        return out;
    }

    private List<SkuView> search(RequirementSlots req, String tag) {
        String brand = req.brands() != null && req.brands().size() == 1 ? req.brands().get(0) : null;
        return products.search(null, req.category(), brand, tag, req.budgetMin(), req.budgetMax(),
                req.minRamGb(), req.minStorageGb(), "price_desc", 12);
    }

    private static RecommendedItem toItem(int index, SkuView s, String reason) {
        return new RecommendedItem(index, s.skuId(), s.productId(), s.productName(), s.skuName(), s.modelNo(),
                s.price(), s.stock(), reason == null ? "" : reason.trim());
    }
}
