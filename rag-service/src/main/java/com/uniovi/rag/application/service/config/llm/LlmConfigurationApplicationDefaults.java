package com.uniovi.rag.application.service.config.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.Map;

/** Applies {@link LlmProperties} defaults onto a merged {@link LlmConfigurationLayer}. */
final class LlmConfigurationApplicationDefaults {

    private LlmConfigurationApplicationDefaults() {}

    static LlmConfigurationLayer applicationLayer(LlmProperties properties) {
        LlmConfigurationLayer layer = LlmConfigurationLayer.empty();
        layer.provider = properties.getDefaultProvider();
        LlmOllamaDefaults ollama = properties.getOllama();
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        layer.chatModel = firstNonBlank(layer.chatModel, ollama.getDefaultChatModel());
        layer.embeddingModel = firstNonBlank(layer.embeddingModel, ollama.getDefaultEmbeddingModel());
        if (properties.getDefaultProvider() == LlmProvider.OPENAI_COMPATIBLE) {
            layer.baseUrl = firstNonBlank(layer.baseUrl, openAi.getDefaultBaseUrl());
            layer.chatModel = firstNonBlank(layer.chatModel, openAi.getDefaultChatModel());
            layer.apiKeyEnv = firstNonBlank(layer.apiKeyEnv, openAi.getDefaultApiKeyEnv());
            if (layer.temperature == null) {
                layer.temperature = openAi.getDefaultTemperature();
            }
            if (layer.timeoutMs == null) {
                layer.timeoutMs = safeTimeout(openAi.getDefaultTimeoutMs());
            }
        } else {
            layer.baseUrl = firstNonBlank(layer.baseUrl, ollama.getDefaultBaseUrl());
            if (layer.temperature == null) {
                layer.temperature = ollama.getDefaultTemperature();
            }
            if (layer.timeoutMs == null) {
                layer.timeoutMs = safeTimeout(ollama.getDefaultTimeoutMs());
            }
        }
        return layer;
    }

    static ResolvedLlmConfig materialize(LlmConfigurationLayer merged, LlmProperties properties) {
        LlmProvider provider = merged.provider != null ? merged.provider : properties.getDefaultProvider();
        LlmOllamaDefaults ollama = properties.getOllama();
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();

        String baseUrl =
                firstNonBlank(
                        merged.baseUrl,
                        provider == LlmProvider.OPENAI_COMPATIBLE
                                ? openAi.getDefaultBaseUrl()
                                : ollama.getDefaultBaseUrl());
        String chatModel =
                firstNonBlank(
                        merged.chatModel,
                        provider == LlmProvider.OPENAI_COMPATIBLE
                                ? openAi.getDefaultChatModel()
                                : ollama.getDefaultChatModel());
        String embeddingModel = firstNonBlank(merged.embeddingModel, ollama.getDefaultEmbeddingModel());
        String apiKeyEnv =
                provider == LlmProvider.OPENAI_COMPATIBLE
                        ? firstNonBlank(merged.apiKeyEnv, openAi.getDefaultApiKeyEnv())
                        : merged.apiKeyEnv;
        String secretName = merged.secretName;
        Double temperature =
                merged.temperature != null
                        ? merged.temperature
                        : (provider == LlmProvider.OPENAI_COMPATIBLE
                                ? openAi.getDefaultTemperature()
                                : ollama.getDefaultTemperature());
        Integer timeoutMs =
                merged.timeoutMs != null
                        ? merged.timeoutMs
                        : safeTimeout(
                                provider == LlmProvider.OPENAI_COMPATIBLE
                                        ? openAi.getDefaultTimeoutMs()
                                        : ollama.getDefaultTimeoutMs());

        return new ResolvedLlmConfig(
                provider,
                baseUrl,
                chatModel,
                embeddingModel,
                apiKeyEnv,
                secretName,
                temperature,
                timeoutMs,
                merged.systemPrompt,
                merged.additionalParameters != null ? merged.additionalParameters : Map.of());
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private static int safeTimeout(long timeoutMs) {
        long bounded = Math.min(Integer.MAX_VALUE, Math.max(1L, timeoutMs));
        return (int) bounded;
    }
}
