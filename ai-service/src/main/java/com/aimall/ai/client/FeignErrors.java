package com.aimall.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;

public final class FeignErrors {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FeignErrors() {
    }

    /** 提取下游 Result.message, 解析失败则返回兜底文案。 */
    public static String message(FeignException e) {
        try {
            JsonNode node = MAPPER.readTree(e.contentUTF8());
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "服务暂时不可用, 请稍后再试";
    }
}
