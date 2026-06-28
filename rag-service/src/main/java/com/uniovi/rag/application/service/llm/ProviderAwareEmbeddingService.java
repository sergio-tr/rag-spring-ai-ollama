package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

/**
 * Resolves {@link LlmEmbeddingClient} from the effective {@link ResolvedLlmConfig} and embeds text batches.
 * Uses {@link OrchestrationLlmConfigScope} during RAG orchestration; otherwise application defaults.
 * <p>
 * {@link LlmProvider#OPENAI_COMPATIBLE} always routes to {@code ResolvedLlmConfig#embeddingModel()} (LiteLLM /
 * {@code LITELLM_EMBEDDING_MODEL}); legacy {@code spring.ai.ollama.embedding.model} values from index profiles are
 * ignored. {@link LlmProvider#OLLAMA_NATIVE} uses the requested profile model when present.
 */
@Service
public class ProviderAwareEmbeddingService {

    private final LlmClientResolver clientResolver;
    private final ResolvedLlmConfigResolver configResolver;

    public ProviderAwareEmbeddingService(LlmClientResolver clientResolver, ResolvedLlmConfigResolver configResolver) {
        this.clientResolver = clientResolver;
        this.configResolver = configResolver;
    }

    public LlmEmbeddingResponse embed(String modelId, List<String> texts) {
        Objects.requireNonNull(texts, "texts");
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("texts must not be empty");
        }
        ResolvedLlmConfig config = resolveEffectiveConfig();
        LlmEmbeddingClient client = clientResolver.resolveEmbeddingClient(config);
        String effectiveModelId = effectiveEmbeddingModelId(modelId);
        return client.embed(new LlmEmbeddingRequest(effectiveModelId, texts, null, java.util.Map.of()));
    }

    public EmbeddingResponse embedToSpringResponse(String modelId, List<String> texts) {
        LlmEmbeddingResponse response = embed(modelId, texts);
        List<Embedding> embeddings = new java.util.ArrayList<>();
        for (int i = 0; i < response.embeddings().size(); i++) {
            embeddings.add(new Embedding(response.embeddings().get(i), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    public ResolvedLlmConfig resolveEffectiveConfig() {
        return OrchestrationLlmConfigScope.current()
                .orElseGet(() -> configResolver.resolve(null, null, null));
    }

    /**
     * Effective embedding model for indexing, probes, and embed calls.
     *
     * <ul>
     *   <li>{@link LlmProvider#OPENAI_COMPATIBLE} → {@code ResolvedLlmConfig.embeddingModel()} (LiteLLM /v1/embeddings)
     *   <li>{@link LlmProvider#OLLAMA_NATIVE} → {@code requestedModelId} when set, else config default (Ollama /api/embed)
     * </ul>
     */
    public String effectiveEmbeddingModelId(String requestedModelId) {
        ResolvedLlmConfig config = resolveEffectiveConfig();
        if (config.embeddingProvider() == LlmProvider.OPENAI_COMPATIBLE) {
            return requireNonBlank(
                    config.embeddingModel(),
                    "rag.llm.openai-compatible.default-embedding-model must be set when provider is OPENAI_COMPATIBLE");
        }
        if (requestedModelId != null && !requestedModelId.isBlank()) {
            return requestedModelId.trim();
        }
        return requireNonBlank(
                config.embeddingModel(),
                "rag.llm.ollama.default-embedding-model must be set when provider is OLLAMA_NATIVE");
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    /** Spring AI {@link org.springframework.ai.embedding.EmbeddingModel} adapter for a fixed model id. */
    public org.springframework.ai.embedding.EmbeddingModel embeddingModelFor(String modelId) {
        String effectiveModelId = modelId != null ? modelId.trim() : "";
        if (effectiveModelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        return new ProviderBoundEmbeddingModel(effectiveModelId, this);
    }

    private static final class ProviderBoundEmbeddingModel implements org.springframework.ai.embedding.EmbeddingModel {

        private final String modelId;
        private final ProviderAwareEmbeddingService embeddingService;

        private ProviderBoundEmbeddingModel(String modelId, ProviderAwareEmbeddingService embeddingService) {
            this.modelId = modelId;
            this.embeddingService = embeddingService;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> texts = request.getInstructions();
            return embeddingService.embedToSpringResponse(modelId, texts);
        }

        @Override
        public float[] embed(Document document) {
            String text = document != null ? document.getText() : "";
            return embeddingService
                    .embedToSpringResponse(modelId, List.of(text != null ? text : ""))
                    .getResult()
                    .getOutput();
        }
    }
}
