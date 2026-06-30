package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaModelProvisioningService;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Checks connectivity to Ollama ({@code GET /api/tags}) and that required models exist when the effective
 * chat or embedding provider is {@link LlmProvider#OLLAMA_NATIVE}.
 */
@Service
public class OllamaConnectivityChecker {

    private final RagHealthProperties healthProperties;
    private final LlmProperties llmProperties;
    private final OllamaApiClient ollamaApiClient;
    private final OllamaModelProvisioningService provisioningService;

    public OllamaConnectivityChecker(
            RagHealthProperties healthProperties,
            LlmProperties llmProperties,
            OllamaApiClient ollamaApiClient,
            OllamaModelProvisioningService provisioningService) {
        this.healthProperties = healthProperties;
        this.llmProperties = llmProperties;
        this.ollamaApiClient = ollamaApiClient;
        this.provisioningService = provisioningService;
    }

    /**
     * @return true if Ollama responds successfully to {@code /api/tags}
     */
    public boolean isOllamaReachable() {
        if (!healthProperties.isOllamaEnabled()) {
            return true;
        }
        try {
            return ollamaApiClient.ping();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Call at the start of the query pipeline: ping + ensure/pull Ollama models when required.
     */
    public void prepareForQuery(String chatModelOverride) {
        prepareForQuery(chatModelOverride, true, true);
    }

    /**
     * @param requireOllamaChat when {@code true} and chat provider is Ollama, ensures chat model exists
     * @param requireOllamaEmbedding when {@code true} and embedding provider is Ollama, ensures embedding model exists
     */
    public void prepareForQuery(String chatModelOverride, boolean requireOllamaChat, boolean requireOllamaEmbedding) {
        boolean needChat =
                requireOllamaChat && llmProperties.getEffectiveDefaultChatProvider() == LlmProvider.OLLAMA_NATIVE;
        boolean needEmbedding =
                requireOllamaEmbedding
                        && llmProperties.getEffectiveDefaultEmbeddingProvider() == LlmProvider.OLLAMA_NATIVE;
        if (!needChat && !needEmbedding) {
            return;
        }
        if (!healthProperties.isOllamaEnabled()) {
            return;
        }
        try {
            if (!ollamaApiClient.ping()) {
                throw RagServiceException.llmUnavailable(
                        new IOException("Ollama no respondió correctamente a GET /api/tags (comprueba spring.ai.ollama.base-url)"));
            }
        } catch (IOException e) {
            throw RagServiceException.llmUnavailable(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RagServiceException.llmUnavailable(e);
        }
        if (needChat && needEmbedding) {
            provisioningService.ensureChatAndEmbeddingModelsPresent(chatModelOverride);
        } else if (needEmbedding) {
            provisioningService.ensureEmbeddingModelPresent();
        } else {
            provisioningService.ensureChatModelPresent(chatModelOverride);
        }
    }

    public boolean requiresOllamaChatForDefaults() {
        return llmProperties.getEffectiveDefaultChatProvider() == LlmProvider.OLLAMA_NATIVE;
    }

    public boolean requiresOllamaEmbeddingForDefaults() {
        return llmProperties.getEffectiveDefaultEmbeddingProvider() == LlmProvider.OLLAMA_NATIVE;
    }
}
