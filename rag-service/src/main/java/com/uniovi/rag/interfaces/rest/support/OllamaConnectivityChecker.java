package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaModelProvisioningService;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Checks connectivity to Ollama ({@code GET /api/tags}) and that required models exist
 * (embedding + chat, with optional chat model override from the lab). If models are missing and
 * {@code rag.ollama.auto-pull-enabled=true}, delegates to {@link OllamaModelProvisioningService}
 * to run {@code POST /api/pull} against {@code spring.ai.ollama.base-url}
 * (Docker container or remote host).
 * <p>
 * Failure detection from exception messages remains in {@link ConnectivityFailureDetector}.
 */
@Service
public class OllamaConnectivityChecker {

    private final RagHealthProperties healthProperties;
    private final OllamaApiClient ollamaApiClient;
    private final OllamaModelProvisioningService provisioningService;

    public OllamaConnectivityChecker(
            RagHealthProperties healthProperties,
            OllamaApiClient ollamaApiClient,
            OllamaModelProvisioningService provisioningService) {
        this.healthProperties = healthProperties;
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
     * Call at the start of the query pipeline: ping + ensure/pull embedding and chat models.
     *
     * @param chatModelOverride chat model for this request (lab); {@code null} uses Spring AI default
     */
    public void prepareForQuery(String chatModelOverride) {
        prepareForQuery(chatModelOverride, true);
    }

    /**
     * @param includeChatModel when {@code false}, only the embedding model is required (OpenAI-compatible chat + Ollama retrieval).
     */
    public void prepareForQuery(String chatModelOverride, boolean includeChatModel) {
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
        if (includeChatModel) {
            provisioningService.ensureChatAndEmbeddingModelsPresent(chatModelOverride);
        } else {
            provisioningService.ensureEmbeddingModelPresent();
        }
    }
}
