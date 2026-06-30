package com.uniovi.rag.infrastructure.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Toggles and timeouts for strict Actuator health checks (Ollama + classifier).
 */
@ConfigurationProperties(prefix = "rag.health")
public class RagHealthProperties {

    /**
     * When false, the Ollama check is skipped and reported as UP (used in tests).
     */
    private boolean ollamaEnabled = true;

    /**
     * When false, the classifier check is skipped and reported as UP (used in tests).
     */
    private boolean classifierEnabled = true;

    /**
     * If true, {@code /api/tags} must list both chat and embedding models from configuration.
     */
    private boolean ollamaVerifyModels = true;

    /**
     * If true, classifier /health must report {@code model: loaded}. Set false in Docker dev when the
     * default model fails to load (e.g. corrupt volume) so the JVM still becomes ready; classification may fall back.
     */
    private boolean classifierRequireModelLoaded = true;

    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 3000;

    public boolean isOllamaEnabled() {
        return ollamaEnabled;
    }

    public void setOllamaEnabled(boolean ollamaEnabled) {
        this.ollamaEnabled = ollamaEnabled;
    }

    public boolean isClassifierEnabled() {
        return classifierEnabled;
    }

    public void setClassifierEnabled(boolean classifierEnabled) {
        this.classifierEnabled = classifierEnabled;
    }

    public boolean isOllamaVerifyModels() {
        return ollamaVerifyModels;
    }

    public void setOllamaVerifyModels(boolean ollamaVerifyModels) {
        this.ollamaVerifyModels = ollamaVerifyModels;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * When true, readiness stays UP if only OpenAI-compatible embeddings fail (chat-only degraded mode).
     */
    private boolean chatOnlyMode = false;

    public boolean isChatOnlyMode() {
        return chatOnlyMode;
    }

    public void setChatOnlyMode(boolean chatOnlyMode) {
        this.chatOnlyMode = chatOnlyMode;
    }

    public boolean isClassifierRequireModelLoaded() {
        return classifierRequireModelLoaded;
    }

    public void setClassifierRequireModelLoaded(boolean classifierRequireModelLoaded) {
        this.classifierRequireModelLoaded = classifierRequireModelLoaded;
    }
}
