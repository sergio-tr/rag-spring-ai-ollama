package com.uniovi.rag.ollama;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Automatic Ollama model download (local or remote) via {@code POST /api/pull}.
 * <p>
 * In the future, the web API may call {@link OllamaModelProvisioningService#ensureModelPresent}
 * when the user switches models without restarting the backend.
 */
@ConfigurationProperties(prefix = "rag.ollama")
public class RagOllamaProperties {

    /**
     * When true, on startup {@code /api/tags} is checked and chat/embedding models from {@code spring.ai.ollama.*}
     * are pulled if missing.
     */
    private boolean autoPullEnabled = true;

    /**
     * Read timeout per {@code /api/pull} operation (large downloads).
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
