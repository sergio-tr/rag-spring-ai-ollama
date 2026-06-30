package com.uniovi.rag.application.port.llm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-agnostic embedding result. Vectors are aligned with {@link LlmEmbeddingRequest#texts()} order.
 */
public record LlmEmbeddingResponse(String model, List<float[]> embeddings, Map<String, Object> rawMetadata) {

    public LlmEmbeddingResponse {
        Objects.requireNonNull(embeddings, "embeddings");
        if (embeddings.isEmpty()) {
            throw new IllegalArgumentException("embeddings must not be empty");
        }
        embeddings = List.copyOf(embeddings);
        rawMetadata =
                rawMetadata != null && !rawMetadata.isEmpty() ? Map.copyOf(rawMetadata) : Map.of();
    }
}
