package com.uniovi.rag.infrastructure.llm.ollama;

/**
 * Result of probing an Ollama model as an embedding endpoint ({@code /api/embed} or legacy {@code /api/embeddings}).
 */
public record OllamaEmbeddingProbeResult(
        boolean ok,
        /** Operator-facing detail (HTTP status, endpoint tried, parse errors). */
        String technicalDetail,
        /** Short message suitable for product UI mapping. */
        String userMessage) {

    public static OllamaEmbeddingProbeResult success() {
        return new OllamaEmbeddingProbeResult(true, null, null);
    }

    public static OllamaEmbeddingProbeResult failure(String technicalDetail, String userMessage) {
        return new OllamaEmbeddingProbeResult(
                false,
                technicalDetail != null ? technicalDetail : "Embedding probe failed",
                userMessage != null ? userMessage : "Embedding probe failed");
    }
}
