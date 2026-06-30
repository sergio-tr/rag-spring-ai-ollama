package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.catalog.EvaluationModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Provider-aware model availability for Lab baselines. Uses Ollama tag probes only for
 * {@link LlmProvider#OLLAMA_NATIVE}; catalog membership for {@link LlmProvider#OPENAI_COMPATIBLE}.
 */
@Component
public class EvaluationModelAvailabilityGate {

    private final OllamaModelCatalogClient ollamaModelCatalogClient;
    private final EvaluationModelCatalogService evaluationModelCatalogService;
    private final ResolvedLlmConfigResolver configResolver;

    public EvaluationModelAvailabilityGate(
            OllamaModelCatalogClient ollamaModelCatalogClient,
            EvaluationModelCatalogService evaluationModelCatalogService,
            ResolvedLlmConfigResolver configResolver) {
        this.ollamaModelCatalogClient = ollamaModelCatalogClient;
        this.evaluationModelCatalogService = evaluationModelCatalogService;
        this.configResolver = configResolver;
    }

    public boolean isChatModelAvailable(UUID userId, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
        return switch (config.chatProvider()) {
            case OLLAMA_NATIVE -> ollamaModelCatalogClient.isModelAvailable(modelId);
            case OPENAI_COMPATIBLE -> isCatalogModel(userId, LlmModelCapability.CHAT, config.chatProvider(), modelId);
        };
    }

    public boolean isEmbeddingModelAvailable(UUID userId, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
        return switch (config.embeddingProvider()) {
            case OLLAMA_NATIVE -> ollamaModelCatalogClient.isModelAvailable(modelId);
            case OPENAI_COMPATIBLE ->
                    isCatalogEmbedding(userId, config.embeddingProvider(), modelId);
        };
    }

    private boolean isCatalogModel(UUID userId, LlmModelCapability capability, LlmProvider provider, String modelId) {
        LlmCatalogResponseDto catalog = evaluationModelCatalogService.listForUser(userId, capability, false);
        if (catalog == null || catalog.models() == null) {
            return false;
        }
        String want = modelId.trim();
        return catalog.models().stream()
                .anyMatch(m -> m.provider() == provider && want.equals(m.modelName()) && m.available());
    }

    private boolean isCatalogEmbedding(UUID userId, LlmProvider provider, String modelId) {
        LlmCatalogResponseDto catalog =
                evaluationModelCatalogService.listForUser(userId, LlmModelCapability.EMBEDDING, false);
        if (catalog == null || catalog.models() == null) {
            return false;
        }
        String want = modelId.trim();
        return catalog.models().stream()
                .filter(m -> m.provider() == provider && want.equals(m.modelName()) && m.available())
                .anyMatch(m -> Boolean.TRUE.equals(m.compatibleWithCurrentVectorStore()));
    }
}
