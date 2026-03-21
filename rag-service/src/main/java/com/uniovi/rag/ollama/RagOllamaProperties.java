package com.uniovi.rag.ollama;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Descarga automática de modelos en Ollama (local o remoto) vía {@code POST /api/pull}.
 * <p>
 * En el futuro, la API web podrá llamar a {@link OllamaModelProvisioningService#ensureModelPresent}
 * cuando el usuario cambie de modelo sin reiniciar el backend.
 */
@ConfigurationProperties(prefix = "rag.ollama")
public class RagOllamaProperties {

    /**
     * Si es true, al arranque se comprueba {@code /api/tags} y se descargan los modelos de chat y
     * embedding configurados en {@code spring.ai.ollama.*} si faltan.
     */
    private boolean autoPullEnabled = true;

    /**
     * Tiempo máximo de lectura por operación {@code /api/pull} (descargas grandes).
     */
    private long pullReadTimeoutMs = 1_800_000L;

    public boolean isAutoPullEnabled() {
        return autoPullEnabled;
    }

    public void setAutoPullEnabled(boolean autoPullEnabled) {
        this.autoPullEnabled = autoPullEnabled;
    }

    public long getPullReadTimeoutMs() {
        return pullReadTimeoutMs;
    }

    public void setPullReadTimeoutMs(long pullReadTimeoutMs) {
        this.pullReadTimeoutMs = pullReadTimeoutMs;
    }
}
