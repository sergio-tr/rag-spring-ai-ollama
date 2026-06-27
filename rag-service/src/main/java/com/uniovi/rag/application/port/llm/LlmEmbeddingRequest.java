package com.uniovi.rag.application.port.llm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-agnostic embedding request (batch of texts for a single model).
 */
public record LlmEmbeddingRequest(
        String model, List<String> texts, Integer timeoutMs, Map<String, Object> additionalParameters) {

    public LlmEmbeddingRequest {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        Objects.requireNonNull(texts, "texts");
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("texts must not be empty");
        }
        texts = List.copyOf(texts);
        additionalParameters =
                additionalParameters != null && !additionalParameters.isEmpty()
                        ? Map.copyOf(additionalParameters)
                        : Map.of();
    }

    public static LlmEmbeddingRequest of(String model, List<String> texts) {
        return new LlmEmbeddingRequest(model, texts, null, Map.of());
    }

    public static LlmEmbeddingRequest ofSingle(String model, String text) {
        return new LlmEmbeddingRequest(model, List.of(text != null ? text : ""), null, Map.of());
    }
}
