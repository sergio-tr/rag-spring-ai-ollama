package com.uniovi.rag.infrastructure.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Default Ollama-native endpoint and model settings ({@code /api/chat}, {@code /api/embed}).
 */
public class LlmOllamaDefaults {

    private String defaultBaseUrl = "http://localhost:11434";
    private String defaultChatModel = "gemma3:4b";
    private String defaultEmbeddingModel = "mxbai-embed-large:latest";
    private List<String> availableChatModels = new ArrayList<>();
    private List<String> availableEmbeddingModels = new ArrayList<>();
    private long defaultTimeoutMs = 60_000L;
    private double defaultTemperature = 0.1;

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public void setDefaultBaseUrl(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
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

    public List<String> getAvailableChatModels() {
        return availableChatModels;
    }

    public void setAvailableChatModels(List<String> availableChatModels) {
        this.availableChatModels = new ArrayList<>(LlmModelListNormalizer.fromPropertyValues(availableChatModels));
    }

    public List<String> getAvailableEmbeddingModels() {
        return availableEmbeddingModels;
    }

    public void setAvailableEmbeddingModels(List<String> availableEmbeddingModels) {
        this.availableEmbeddingModels =
                new ArrayList<>(LlmModelListNormalizer.fromPropertyValues(availableEmbeddingModels));
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

    void validate() {
        requireNonBlank(defaultBaseUrl, "rag.llm.ollama.default-base-url");
        requireNonBlank(defaultChatModel, "rag.llm.ollama.default-chat-model");
        requireNonBlank(defaultEmbeddingModel, "rag.llm.ollama.default-embedding-model");
        LlmPropertyValidation.requirePositiveTimeout(defaultTimeoutMs, "rag.llm.ollama.default-timeout-ms");
        LlmPropertyValidation.requireReasonableTemperature(defaultTemperature, "rag.llm.ollama.default-temperature");
    }

    private static void requireNonBlank(String value, String propertyKey) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyKey + " must not be blank");
        }
    }
}
