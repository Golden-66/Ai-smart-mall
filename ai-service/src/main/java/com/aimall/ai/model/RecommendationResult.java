package com.aimall.ai.model;

import java.util.List;

/** 大模型在调用工具检索后给出的推荐结果 (必须再经过后端校验才会采纳)。 */
public record RecommendationResult(String summary, List<Pick> picks) {

    public record Pick(Long skuId, String reason) {
    }
}
