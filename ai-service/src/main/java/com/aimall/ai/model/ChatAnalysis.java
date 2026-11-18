package com.aimall.ai.model;

/** 大模型对用户一句话的结构化理解结果 (Spring AI 结构化输出)。 */
public record ChatAnalysis(
        Intent intent,
        RequirementSlots requirement,
        Integer selectedIndex,
        Integer quantity,
        String modelNo,
        String question) {
}
