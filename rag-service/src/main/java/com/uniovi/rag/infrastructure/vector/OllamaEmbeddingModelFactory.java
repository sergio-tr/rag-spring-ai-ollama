package com.uniovi.rag.infrastructure.vector;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Builds short-lived {@link OllamaEmbeddingModel} instances for arbitrary Ollama embedding tags (Lab + per-snapshot retrieval).
 */
@Component
public class OllamaEmbeddingModelFactory {

    private final OllamaApi ollamaApi;
    private final ObservationRegistry observationRegistry;

    public OllamaEmbeddingModelFactory(OllamaApi ollamaApi, ObservationRegistry observationRegistry) {
        this.ollamaApi = ollamaApi;
        this.observationRegistry = observationRegistry;
    }

    public EmbeddingModel forModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId");
        }
        String trimmed = modelId.trim();
        OllamaOptions opts = OllamaOptions.builder().model(trimmed).build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(opts)
                .observationRegistry(observationRegistry)
                .build();
    }
}
