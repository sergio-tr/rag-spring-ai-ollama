package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import com.uniovi.rag.application.exception.RagServiceException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provisions Ollama models via API ({@code /api/pull}) for names configured in Spring AI
 * and, in the future, for models chosen in the UI.
 */
@Service
public class OllamaModelProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelProvisioningService.class);

    public enum State {
        /** Runner has not finished or is still in progress. */
        PENDING,
        /** Downloading models. */
        PULLING,
        /** Ready for API traffic or provisioning skipped (tests / auto-pull disabled). */
        READY,
        /** Unrecoverable error (network, permissions, etc.); check logs. */
        FAILED
    }

    private final RagHealthProperties healthProperties;
    private final RagOllamaProperties ollamaProperties;
    private final OllamaApiClient ollamaApiClient;
    private final String chatModel;
    private final String embeddingModel;

    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
    private volatile String lastError;

    /** Prevents concurrent pulls (startup vs requests / lab) against the same Ollama instance. */
    private final ReentrantLock modelPullLock = new ReentrantLock();

    public OllamaModelProvisioningService(
            RagHealthProperties healthProperties,
            RagOllamaProperties ollamaProperties,
            OllamaApiClient ollamaApiClient,
            @Value("${spring.ai.ollama.chat.model:gemma3:4b}") String chatModel,
            @Value("${spring.ai.ollama.embedding.model:mxbai-embed-large}") String embeddingModel) {
        this.healthProperties = healthProperties;
        this.ollamaProperties = ollamaProperties;
        this.ollamaApiClient = ollamaApiClient;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    void init() {
        if (!healthProperties.isOllamaEnabled() || !ollamaProperties.isAutoPullEnabled()) {
            state.set(State.READY);
        }
    }

    /**
     * Invoked at startup: pulls chat and embedding models if missing.
     * <p>When {@link InterruptedException} is caught, {@link Thread#interrupt()} is invoked to preserve the
     * interrupted status; the method does not rethrow so application startup can complete in a degraded mode.
     */
    @SuppressWarnings("java:S2142")
    public void ensureConfiguredModelsAtStartup() {
        if (!healthProperties.isOllamaEnabled() || !ollamaProperties.isAutoPullEnabled()) {
            return;
        }
        if (state.get() == State.READY) {
            return;
        }
        modelPullLock.lock();
        try {
            state.set(State.PULLING);
            lastError = null;
            Set<String> installed = new HashSet<>(ollamaApiClient.listModelNames());
            List<String> missing = new ArrayList<>();
            for (String required : requiredModelsInOrder()) {
                if (!installed.contains(required)) {
                    missing.add(required);
                }
            }
            if (missing.isEmpty()) {
                log.info("Ollama: required models already present (chat={}, embedding={})", chatModel, embeddingModel);
                state.set(State.READY);
                return;
            }
            long pullTimeout = ollamaProperties.getPullReadTimeoutMs();
            for (String model : missing) {
                log.info("Ollama: downloading missing model '{}' (POST /api/pull on {})", model, "spring.ai.ollama.base-url");
                ollamaApiClient.pullModel(model, pullTimeout);
                installed.add(model);
            }
            state.set(State.READY);
            log.info("Ollama: model provisioning completed.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = e.getMessage();
            log.warn(
                    "Ollama: model provisioning interrupted; /api/** may stay degraded until restart. Cause: {}",
                    e.getMessage());
            log.debug("Ollama model provisioning interrupted", e);
            state.set(State.FAILED);
        } catch (Exception e) {
            lastError = e.getMessage();
            // WARN + message: degraded state (Ollama down); full stack only at DEBUG to avoid noisy CI / default logs.
            log.warn("Ollama: model provisioning failed; /api/** will return 503 until Ollama is fixed or the app is restarted. Cause: {}",
                    e.getMessage());
            log.debug("Ollama model provisioning failure", e);
            state.set(State.FAILED);
        } finally {
            modelPullLock.unlock();
        }
    }

    /**
     * Before each query (and when the lab changes the chat model): ensures embedding and effective chat models exist;
     * if {@code rag.ollama.auto-pull-enabled=true}, runs {@code POST /api/pull}
     * against {@code spring.ai.ollama.base-url} (container or remote).
     *
     * @param chatModelOverride user-selected chat model; if null, {@code spring.ai.ollama.chat.model} is used
     */
    public void ensureChatAndEmbeddingModelsPresent(String chatModelOverride) {
        if (!healthProperties.isOllamaEnabled()) {
            return;
        }
        String effectiveChat = (chatModelOverride != null && !chatModelOverride.isBlank())
                ? chatModelOverride.trim()
                : chatModel;
        modelPullLock.lock();
        try {
            Set<String> installed = new HashSet<>(ollamaApiClient.listModelNames());
            List<String> missing = new ArrayList<>();
            if (!installed.contains(embeddingModel)) {
                missing.add(embeddingModel);
            }
            if (!installed.contains(effectiveChat)) {
                missing.add(effectiveChat);
            }
            if (missing.isEmpty()) {
                return;
            }
            if (!ollamaProperties.isAutoPullEnabled()) {
                throw RagServiceException.ollamaModelNotInstalled(
                        new IOException("Models missing in Ollama and auto-pull is disabled: " + missing));
            }
            long pullTimeout = ollamaProperties.getPullReadTimeoutMs();
            for (String model : missing) {
                log.info("Ollama: required model '{}' missing, running POST /api/pull (effective chat={}, embedding={})",
                        model, effectiveChat, embeddingModel);
                ollamaApiClient.pullModel(model, pullTimeout);
                installed.add(model);
            }
        } catch (IOException e) {
            throw RagServiceException.llmUnavailable(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RagServiceException.llmUnavailable(e);
        } finally {
            modelPullLock.unlock();
        }
    }

    /**
     * Pulls a model on demand (e.g. model selected only in the UI). Honors {@code auto-pull-enabled}.
     */
    public void ensureModelPresent(String modelName) throws IOException, InterruptedException {
        if (!healthProperties.isOllamaEnabled()) {
            return;
        }
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        modelPullLock.lock();
        try {
            Set<String> installed = ollamaApiClient.listModelNames();
            if (installed.contains(modelName)) {
                return;
            }
            if (!ollamaProperties.isAutoPullEnabled()) {
                throw new IOException("Model '" + modelName + "' not present and rag.ollama.auto-pull-enabled=false");
            }
            log.info("Ollama: on-demand download of model '{}'", modelName);
            ollamaApiClient.pullModel(modelName, ollamaProperties.getPullReadTimeoutMs());
        } finally {
            modelPullLock.unlock();
        }
    }

    public boolean isReadyForApiTraffic() {
        return state.get() == State.READY;
    }

    public State getState() {
        return state.get();
    }

    public String getLastError() {
        return lastError;
    }

    private LinkedHashSet<String> requiredModelsInOrder() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(chatModel);
        set.add(embeddingModel);
        return set;
    }
}
