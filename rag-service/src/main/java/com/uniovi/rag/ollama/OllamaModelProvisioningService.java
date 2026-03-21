package com.uniovi.rag.ollama;

import com.uniovi.rag.health.RagHealthProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import com.uniovi.rag.exception.RagServiceException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Aprovisiona modelos Ollama vía API ({@code /api/pull}) para los nombres configurados en Spring AI
 * y, en el futuro, para modelos elegidos por el usuario en la UI.
 */
@Service
public class OllamaModelProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelProvisioningService.class);

    public enum State {
        /** Aún no ha corrido el runner o está en curso. */
        PENDING,
        /** Descargando modelos. */
        PULLING,
        /** Listo para tráfico API o aprovisionamiento omitido (tests / auto-pull desactivado). */
        READY,
        /** Error irrecuperable (red, permisos, etc.); revisar logs. */
        FAILED
    }

    private final RagHealthProperties healthProperties;
    private final RagOllamaProperties ollamaProperties;
    private final OllamaApiClient ollamaApiClient;
    private final String chatModel;
    private final String embeddingModel;

    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
    private volatile String lastError;

    /** Evita pulls concurrentes (arranque vs peticiones / lab) sobre el mismo Ollama. */
    private final ReentrantLock modelPullLock = new ReentrantLock();

    public OllamaModelProvisioningService(
            RagHealthProperties healthProperties,
            RagOllamaProperties ollamaProperties,
            OllamaApiClient ollamaApiClient,
            @Value("${spring.ai.ollama.chat.model}") String chatModel,
            @Value("${spring.ai.ollama.embedding.model}") String embeddingModel) {
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
     * Invocado al arranque: descarga los modelos de chat y embedding configurados si faltan.
     */
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
                log.info("Ollama: modelos requeridos ya presentes (chat={}, embedding={})", chatModel, embeddingModel);
                state.set(State.READY);
                return;
            }
            long pullTimeout = ollamaProperties.getPullReadTimeoutMs();
            for (String model : missing) {
                log.info("Ollama: descargando modelo faltante '{}' (POST /api/pull en {})", model, "spring.ai.ollama.base-url");
                ollamaApiClient.pullModel(model, pullTimeout);
                installed.add(model);
            }
            state.set(State.READY);
            log.info("Ollama: aprovisionamiento de modelos completado.");
        } catch (Exception e) {
            lastError = e.getMessage();
            log.error("Ollama: fallo al aprovisionar modelos; la API /api/** quedará en 503 hasta reiniciar o corregir Ollama.", e);
            state.set(State.FAILED);
        } finally {
            modelPullLock.unlock();
        }
    }

    /**
     * Antes de cada consulta (y cuando el lab cambia el modelo de chat): comprueba que existen el modelo de
     * embedding y el de chat efectivo; si {@code rag.ollama.auto-pull-enabled=true}, lanza {@code POST /api/pull}
     * contra el mismo {@code spring.ai.ollama.base-url} (contenedor o remoto).
     *
     * @param chatModelOverride modelo de chat elegido por el usuario; si es null, se usa {@code spring.ai.ollama.chat.model}
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
                        new IOException("Faltan modelos en Ollama y auto-pull está desactivado: " + missing));
            }
            long pullTimeout = ollamaProperties.getPullReadTimeoutMs();
            for (String model : missing) {
                log.info("Ollama: modelo requerido ausente '{}', ejecutando POST /api/pull (chat efectivo={}, embedding={})",
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
     * Descarga un modelo bajo demanda (p. ej. modelo elegido solo en la UI). Respeta {@code auto-pull-enabled}.
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
            log.info("Ollama: descarga bajo demanda del modelo '{}'", modelName);
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
