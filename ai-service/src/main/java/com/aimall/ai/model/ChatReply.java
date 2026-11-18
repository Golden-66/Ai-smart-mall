package com.aimall.ai.model;

import java.util.List;

public record ChatReply(
        String conversationId,
        String reply,
        String stage,
        List<RecommendedItem> recommendations,
        PendingOrder pendingOrder,
        String orderNo,
        List<KnowledgeSource> sources) {
}
