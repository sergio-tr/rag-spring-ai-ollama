package com.uniovi.rag.api;

import com.uniovi.rag.exception.RagServiceException;
import com.uniovi.rag.health.RagHealthProperties;
import com.uniovi.rag.ollama.OllamaApiClient;
import com.uniovi.rag.ollama.OllamaModelProvisioningService;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Comprueba conectividad con Ollama ({@code GET /api/tags}) y que existan los modelos necesarios
 * (embedding + chat, con posible override del modelo de chat desde el lab). Si faltan y
 * {@code rag.ollama.auto-pull-enabled=true}, delega en {@link OllamaModelProvisioningService}
 * para ejecutar {@code POST /api/pull} contra el mismo {@code spring.ai.ollama.base-url}
 * (contenedor Docker o host remoto).
 * <p>
 * La detección de fallos a partir de mensajes de excepción sigue en {@link ConnectivityFailureDetector}.
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
     * @return true si Ollama responde con éxito a {@code /api/tags}
     */
    public boolean isOllamaReachable() {
        if (!healthProperties.isOllamaEnabled()) {
            return true;
        }
        try {
            return ollamaApiClient.ping();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Debe invocarse al inicio del pipeline de consulta: ping + comprobar/descargar modelos de embedding y chat.
     *
     * @param chatModelOverride modelo de chat elegido para esta petición (lab); {@code null} usa el configurado en Spring AI
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
