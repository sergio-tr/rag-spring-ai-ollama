package com.uniovi.rag.api;

import com.uniovi.rag.exception.RagServiceException;
import com.uniovi.rag.health.RagHealthProperties;
import com.uniovi.rag.ollama.OllamaApiClient;
import com.uniovi.rag.ollama.OllamaModelProvisioningService;
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
        provisioningService.ensureChatAndEmbeddingModelsPresent(chatModelOverride);
    }
}
