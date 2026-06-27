package com.uniovi.rag.application.port.llm;

import java.util.Map;

/**
 * Provider-agnostic chat completion result.
 */
public record LlmChatResponse(
        String content,
        String model,
        String finishReason,
        LlmTokenUsage usage,
        Map<String, Object> rawMetadata) {

    public LlmChatResponse {
        content = content != null ? content : "";
        rawMetadata =
                rawMetadata != null && !rawMetadata.isEmpty() ? Map.copyOf(rawMetadata) : Map.of();
    }

    public static LlmChatResponse ofContent(String content) {
        return new LlmChatResponse(content, null, null, null, Map.of());
    }
}
