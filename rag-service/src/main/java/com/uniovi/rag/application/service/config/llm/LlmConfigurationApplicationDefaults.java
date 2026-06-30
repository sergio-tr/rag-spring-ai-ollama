package com.uniovi.rag.application.service.config.llm;

import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.Map;

/** Applies {@link LlmProperties} defaults onto a merged {@link LlmConfigurationLayer}. */
final class LlmConfigurationApplicationDefaults {

    private LlmConfigurationApplicationDefaults() {}

    static LlmConfigurationLayer applicationLayer(LlmProperties properties) {
        LlmConfigurationLayer layer = LlmConfigurationLayer.empty();
        LlmProvider chatProvider = properties.getEffectiveDefaultChatProvider();
        LlmProvider embeddingProvider = properties.getEffectiveDefaultEmbeddingProvider();
        layer.chatProvider = chatProvider;
        layer.embeddingProvider = embeddingProvider;

        applyChatDefaults(layer, properties, chatProvider);
        applyEmbeddingDefaults(layer, properties, embeddingProvider);
        return layer;
    }

    private static void applyChatDefaults(
            LlmConfigurationLayer layer, LlmProperties properties, LlmProvider chatProvider) {
        if (chatProvider == LlmProvider.OPENAI_COMPATIBLE) {
            LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
            layer.baseUrl = coalesce(layer.baseUrl, openAi.getDefaultBaseUrl());
            layer.chatModel = coalesce(layer.chatModel, openAi.getDefaultChatModel());
            layer.apiKeyEnv = coalesce(layer.apiKeyEnv, openAi.getDefaultApiKeyEnv());
            if (layer.temperature == null) {
                layer.temperature = openAi.getDefaultTemperature();
            }
            if (layer.timeoutMs == null) {
                layer.timeoutMs = safeTimeout(openAi.getDefaultTimeoutMs());
            }
        } else {
            LlmOllamaDefaults ollama = properties.getOllama();
            layer.baseUrl = coalesce(layer.baseUrl, ollama.getDefaultBaseUrl());
            layer.chatModel = coalesce(layer.chatModel, ollama.getDefaultChatModel());
            if (layer.temperature == null) {
                layer.temperature = ollama.getDefaultTemperature();
            }
            if (layer.timeoutMs == null) {
                layer.timeoutMs = safeTimeout(ollama.getDefaultTimeoutMs());
            }
        }
    }

    private static void applyEmbeddingDefaults(
            LlmConfigurationLayer layer, LlmProperties properties, LlmProvider embeddingProvider) {
        if (embeddingProvider == LlmProvider.OPENAI_COMPATIBLE) {
            LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
            layer.embeddingModel = coalesce(layer.embeddingModel, openAi.getDefaultEmbeddingModel());
            layer.apiKeyEnv = coalesce(layer.apiKeyEnv, openAi.getDefaultApiKeyEnv());
            if (layer.baseUrl == null || layer.baseUrl.isBlank()) {
                layer.baseUrl = coalesce(layer.baseUrl, openAi.getDefaultBaseUrl());
            }
        } else {
            LlmOllamaDefaults ollama = properties.getOllama();
            layer.embeddingModel = coalesce(layer.embeddingModel, ollama.getDefaultEmbeddingModel());
            if (layer.baseUrl == null || layer.baseUrl.isBlank()) {
                layer.baseUrl = coalesce(layer.baseUrl, ollama.getDefaultBaseUrl());
            }
        }
    }

    static ResolvedLlmConfig materialize(LlmConfigurationLayer merged, LlmProperties properties) {
        return materialize(merged, properties, null);
    }

    static ResolvedLlmConfig materialize(
            LlmConfigurationLayer merged, LlmProperties properties, LlmModelCatalogPort modelCatalog) {
        LlmProvider chatProvider = resolveChatProvider(merged, properties);
        LlmProvider embeddingProvider = resolveEmbeddingProvider(merged, properties);

        String chatModel = resolveChatModel(merged, properties, chatProvider, modelCatalog);
        String embeddingModel = resolveEmbeddingModel(merged, properties, embeddingProvider, modelCatalog);
        String baseUrl = resolveBaseUrl(merged, properties, chatProvider, embeddingProvider);
        String apiKeyEnv = resolveApiKeyEnv(merged, properties, chatProvider, embeddingProvider);
        Double temperature = resolveTemperature(merged, properties, chatProvider);
        Integer timeoutMs = resolveTimeoutMs(merged, properties, chatProvider);

        return new ResolvedLlmConfig(
                chatProvider,
                embeddingProvider,
                baseUrl,
                chatModel,
                embeddingModel,
                apiKeyEnv,
                merged.secretName,
                temperature,
                timeoutMs,
                merged.systemPrompt,
                merged.additionalParameters != null ? merged.additionalParameters : Map.of());
    }

    private static String resolveChatModel(
            LlmConfigurationLayer merged,
            LlmProperties properties,
            LlmProvider chatProvider,
            LlmModelCatalogPort modelCatalog) {
        String validated = validatedModelForProvider(
                modelCatalog, chatProvider, merged.chatModel, LlmModelCapability.CHAT);
        if (validated != null) {
            return validated;
        }
        if (chatProvider == LlmProvider.OPENAI_COMPATIBLE) {
            return properties.getOpenAiCompatible().getDefaultChatModel();
        }
        return properties.getOllama().getDefaultChatModel();
    }

    private static String resolveEmbeddingModel(
            LlmConfigurationLayer merged,
            LlmProperties properties,
            LlmProvider embeddingProvider,
            LlmModelCatalogPort modelCatalog) {
        String validated = validatedModelForProvider(
                modelCatalog, embeddingProvider, merged.embeddingModel, LlmModelCapability.EMBEDDING);
        if (validated != null) {
            return validated;
        }
        if (embeddingProvider == LlmProvider.OPENAI_COMPATIBLE) {
            return properties.getOpenAiCompatible().getDefaultEmbeddingModel();
        }
        return properties.getOllama().getDefaultEmbeddingModel();
    }

    /**
     * Accepts a merged model only when it is registered for the effective provider. Legacy {@code llmModel} /
     * {@code embeddingModel} values from the RagConfig cascade that belong to another provider are ignored.
     */
    private static String validatedModelForProvider(
            LlmModelCatalogPort modelCatalog,
            LlmProvider provider,
            String candidate,
            LlmModelCapability capability) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = candidate.trim();
        if (modelCatalog == null) {
            return trimmed;
        }
        return modelCatalog.find(provider, trimmed, capability).isPresent() ? trimmed : null;
    }

    private static String resolveBaseUrl(
            LlmConfigurationLayer merged,
            LlmProperties properties,
            LlmProvider chatProvider,
            LlmProvider embeddingProvider) {
        if (merged.baseUrl != null && !merged.baseUrl.isBlank()) {
            return merged.baseUrl.trim();
        }
        if (chatProvider == LlmProvider.OPENAI_COMPATIBLE) {
            return properties.getOpenAiCompatible().getDefaultBaseUrl();
        }
        if (embeddingProvider == LlmProvider.OPENAI_COMPATIBLE) {
            return properties.getOpenAiCompatible().getDefaultBaseUrl();
        }
        return properties.getOllama().getDefaultBaseUrl();
    }

    private static String resolveApiKeyEnv(
            LlmConfigurationLayer merged,
            LlmProperties properties,
            LlmProvider chatProvider,
            LlmProvider embeddingProvider) {
        if (merged.apiKeyEnv != null && !merged.apiKeyEnv.isBlank()) {
            return merged.apiKeyEnv.trim();
        }
        if (chatProvider == LlmProvider.OPENAI_COMPATIBLE || embeddingProvider == LlmProvider.OPENAI_COMPATIBLE) {
            return properties.getOpenAiCompatible().getDefaultApiKeyEnv();
        }
        return null;
    }

    private static Double resolveTemperature(
            LlmConfigurationLayer merged, LlmProperties properties, LlmProvider chatProvider) {
        if (merged.temperature != null) {
            return merged.temperature;
        }
        if (chatProvider == LlmProvider.OPENAI_COMPATIBLE) {
            return properties.getOpenAiCompatible().getDefaultTemperature();
        }
        return properties.getOllama().getDefaultTemperature();
    }

    private static Integer resolveTimeoutMs(
            LlmConfigurationLayer merged, LlmProperties properties, LlmProvider chatProvider) {
        if (merged.timeoutMs != null) {
            return merged.timeoutMs;
        }
        if (chatProvider == LlmProvider.OPENAI_COMPATIBLE) {
            return safeTimeout(properties.getOpenAiCompatible().getDefaultTimeoutMs());
        }
        return safeTimeout(properties.getOllama().getDefaultTimeoutMs());
    }

    private static LlmProvider resolveChatProvider(LlmConfigurationLayer merged, LlmProperties properties) {
        if (merged.chatProvider != null) {
            return merged.chatProvider;
        }
        if (!properties.hasExplicitProviderSplit()) {
            return properties.getUniformStackProvider();
        }
        return properties.getEffectiveDefaultChatProvider();
    }

    private static LlmProvider resolveEmbeddingProvider(LlmConfigurationLayer merged, LlmProperties properties) {
        if (merged.embeddingProvider != null) {
            return merged.embeddingProvider;
        }
        if (!properties.hasExplicitProviderSplit()) {
            return properties.getUniformStackProvider();
        }
        return properties.getEffectiveDefaultEmbeddingProvider();
    }

    private static String coalesce(String primary, String fallback) {
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
