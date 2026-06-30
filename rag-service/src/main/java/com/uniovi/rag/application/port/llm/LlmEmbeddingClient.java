package com.uniovi.rag.application.port.llm;

import com.uniovi.rag.domain.llm.LlmProvider;

/**
 * Outbound port for provider-agnostic text embeddings.
 * Implementations translate to Ollama {@code /api/embed} or OpenAI-compatible {@code /v1/embeddings}.
 */
public interface LlmEmbeddingClient {

    /**
     * Embeds all texts in the request using the configured model.
     */
    LlmEmbeddingResponse embed(LlmEmbeddingRequest request);

    /** Backend kind served by this client instance. */
    LlmProvider provider();
}
