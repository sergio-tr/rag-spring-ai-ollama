package com.uniovi.rag.infrastructure.llm;

import com.uniovi.rag.domain.llm.LlmProvider;

/**
 * Default OpenAI-compatible / LiteLLM settings ({@code POST /v1/chat/completions}).
 * API secrets are referenced by environment variable name only — never stored in properties.
 */
public class LlmOpenAiCompatibleDefaults {

    private String defaultBaseUrl = "";
    private String defaultApiKeyEnv = "OPENAI_COMPATIBLE_API_KEY";
    private String defaultChatModel = "";
    private String defaultEmbeddingModel = "";
    private long defaultTimeoutMs = 60_000L;
    private double defaultTemperature = 0.1;

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public void setDefaultBaseUrl(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String getDefaultApiKeyEnv() {
        return defaultApiKeyEnv;
    }

    public void setDefaultApiKeyEnv(String defaultApiKeyEnv) {
        this.defaultApiKeyEnv = defaultApiKeyEnv;
    }

    public String getDefaultChatModel() {
        return defaultChatModel;
    }

    public void setDefaultChatModel(String defaultChatModel) {
        this.defaultChatModel = defaultChatModel;
    }

    public String getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    public void setDefaultEmbeddingModel(String defaultEmbeddingModel) {
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public double getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(double defaultTemperature) {
        this.defaultTemperature = defaultTemperature;
    }

    /**
     * Validates settings that must always be configured (env var name), even when the active provider is Ollama.
     */
    void validateApiKeyEnvConfigured() {
        if (defaultApiKeyEnv == null || defaultApiKeyEnv.isBlank()) {
            throw new IllegalStateException("rag.llm.openai-compatible.default-api-key-env must not be blank");
        }
    }

    /**
     * Validates endpoint and model settings when {@link LlmProvider#OPENAI_COMPATIBLE} is the active provider.
     */
    void validateWhenActive() {
        validateApiKeyEnvConfigured();
        if (defaultBaseUrl == null || defaultBaseUrl.isBlank()) {
            throw new IllegalStateException("rag.llm.openai-compatible.default-base-url must not be blank when provider is OPENAI_COMPATIBLE");
        }
        if (defaultChatModel == null || defaultChatModel.isBlank()) {
            throw new IllegalStateException("rag.llm.openai-compatible.default-chat-model must not be blank when provider is OPENAI_COMPATIBLE");
        }
        LlmPropertyValidation.requirePositiveTimeout(defaultTimeoutMs, "rag.llm.openai-compatible.default-timeout-ms");
        LlmPropertyValidation.requireReasonableTemperature(defaultTemperature, "rag.llm.openai-compatible.default-temperature");
    }
}
