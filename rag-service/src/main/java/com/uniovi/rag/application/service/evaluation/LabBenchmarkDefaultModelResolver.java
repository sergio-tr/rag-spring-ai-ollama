package com.uniovi.rag.application.service.evaluation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves effective LLM and embedding model ids for lab benchmark runs when the start request omits them.
 */
@Service
public class LabBenchmarkDefaultModelResolver {

    private final String defaultChatModel;
    private final String defaultEmbeddingModel;

    public LabBenchmarkDefaultModelResolver(
            @Value("${spring.ai.ollama.chat.model:gemma3:4b}") String defaultChatModel,
            @Value("${spring.ai.ollama.embedding.model:mxbai-embed-large:latest}") String defaultEmbeddingModel) {
        this.defaultChatModel = defaultChatModel;
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }

    public String resolveLlmModelId(String requestOverride) {
        if (requestOverride != null && !requestOverride.isBlank()) {
            return requestOverride.trim();
        }
        return blankToNull(defaultChatModel);
    }

    public String resolveEmbeddingModelId(String requestOverride) {
        if (requestOverride != null && !requestOverride.isBlank()) {
            return requestOverride.trim();
        }
        return blankToNull(defaultEmbeddingModel);
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
