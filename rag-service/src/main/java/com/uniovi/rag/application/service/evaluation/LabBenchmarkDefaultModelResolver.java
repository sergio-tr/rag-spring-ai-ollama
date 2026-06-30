package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves effective LLM and embedding model ids for lab benchmark runs when the start request omits them.
 * Defaults come from the resolved properties catalog, not hardcoded Spring AI demo tags.
 */
@Service
public class LabBenchmarkDefaultModelResolver {

    private final ResolvedLlmConfigResolver configResolver;

    public LabBenchmarkDefaultModelResolver(ResolvedLlmConfigResolver configResolver) {
        this.configResolver = configResolver;
    }

    public String resolveLlmModelId(UUID userId, String requestOverride) {
        if (requestOverride != null && !requestOverride.isBlank()) {
            return requestOverride.trim();
        }
        ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
        return blankToNull(config.chatModel());
    }

    public String resolveEmbeddingModelId(UUID userId, String requestOverride) {
        if (requestOverride != null && !requestOverride.isBlank()) {
            return requestOverride.trim();
        }
        ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
        return blankToNull(config.embeddingModel());
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
