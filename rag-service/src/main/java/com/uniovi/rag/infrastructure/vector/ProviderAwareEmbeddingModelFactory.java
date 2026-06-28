package com.uniovi.rag.infrastructure.vector;

import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Builds {@link EmbeddingModel} instances that delegate to {@link com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService}
 * (provider resolved per call via {@link com.uniovi.rag.application.service.llm.LlmClientResolver}).
 */
@Component
public class ProviderAwareEmbeddingModelFactory {

    private final ProviderAwareEmbeddingService embeddingService;

    public ProviderAwareEmbeddingModelFactory(ProviderAwareEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public EmbeddingModel forModel(String modelId) {
        return embeddingService.embeddingModelFor(modelId);
    }

    /** Provider-aware model id used for dimension probes and embedding HTTP calls. */
    public String effectiveModelId(String requestedModelId) {
        return embeddingService.effectiveEmbeddingModelId(requestedModelId);
    }
}
