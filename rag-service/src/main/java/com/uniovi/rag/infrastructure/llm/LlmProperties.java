package com.uniovi.rag.infrastructure.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-default LLM provider settings (Phase 2). Does not replace legacy {@code spring.ai.ollama.*}
 * used by the current RAG runtime; those remain the source of truth until provider routing is wired.
 */
@ConfigurationProperties(prefix = "rag.llm")
public class LlmProperties {

    private LlmProvider defaultProvider = LlmProvider.OLLAMA_NATIVE;
    private LlmProvider defaultChatProvider;
    private LlmProvider defaultEmbeddingProvider;
    private LlmOllamaDefaults ollama = new LlmOllamaDefaults();
    private LlmOpenAiCompatibleDefaults openAiCompatible = new LlmOpenAiCompatibleDefaults();

    public LlmProvider getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(LlmProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public LlmProvider getDefaultChatProvider() {
        return defaultChatProvider;
    }

    public void setDefaultChatProvider(LlmProvider defaultChatProvider) {
        this.defaultChatProvider = defaultChatProvider;
    }

    public LlmProvider getDefaultEmbeddingProvider() {
        return defaultEmbeddingProvider;
    }

    public void setDefaultEmbeddingProvider(LlmProvider defaultEmbeddingProvider) {
        this.defaultEmbeddingProvider = defaultEmbeddingProvider;
    }

    /** Effective chat provider: {@code default-chat-provider} or {@code default-provider}. */
    public LlmProvider getEffectiveDefaultChatProvider() {
        return defaultChatProvider != null ? defaultChatProvider : defaultProvider;
    }

    /** Effective embedding provider: {@code default-embedding-provider} or {@code default-provider}. */
    public LlmProvider getEffectiveDefaultEmbeddingProvider() {
        return defaultEmbeddingProvider != null ? defaultEmbeddingProvider : defaultProvider;
    }

    /**
     * True when {@code default-chat-provider} or {@code default-embedding-provider} is set explicitly in properties.
     * Hybrid stack mode requires this; otherwise {@link #getDefaultProvider()} governs both chat and embeddings.
     */
    public boolean hasExplicitProviderSplit() {
        return defaultChatProvider != null || defaultEmbeddingProvider != null;
    }

    /**
     * Product default: one provider for the whole RAG stack unless {@link #hasExplicitProviderSplit()}.
     */
    public LlmProvider getUniformStackProvider() {
        return defaultProvider;
    }

    /** Default chat model for {@link #getEffectiveDefaultChatProvider()} only - never cross-fallback to the other provider. */
    public String effectiveDefaultChatModel() {
        if (getEffectiveDefaultChatProvider() == LlmProvider.OPENAI_COMPATIBLE) {
            return openAiCompatible.getDefaultChatModel();
        }
        return ollama.getDefaultChatModel();
    }

    /** Default embedding model for {@link #getEffectiveDefaultEmbeddingProvider()} only. */
    public String effectiveDefaultEmbeddingModel() {
        if (getEffectiveDefaultEmbeddingProvider() == LlmProvider.OPENAI_COMPATIBLE) {
            return openAiCompatible.getDefaultEmbeddingModel();
        }
        return ollama.getDefaultEmbeddingModel();
    }

    /** Default base URL for the effective chat provider (single baseUrl in {@code ResolvedLlmConfig}). */
    public String effectiveDefaultBaseUrl() {
        if (getEffectiveDefaultChatProvider() == LlmProvider.OPENAI_COMPATIBLE) {
            return openAiCompatible.getDefaultBaseUrl();
        }
        return ollama.getDefaultBaseUrl();
    }

    public LlmOllamaDefaults getOllama() {
        return ollama;
    }

    public void setOllama(LlmOllamaDefaults ollama) {
        this.ollama = ollama != null ? ollama : new LlmOllamaDefaults();
    }

    public LlmOpenAiCompatibleDefaults getOpenAiCompatible() {
        return openAiCompatible;
    }

    public void setOpenAiCompatible(LlmOpenAiCompatibleDefaults openAiCompatible) {
        this.openAiCompatible = openAiCompatible != null ? openAiCompatible : new LlmOpenAiCompatibleDefaults();
    }

    /**
     * Validates bound properties. OpenAI-compatible endpoint/model checks run only when that provider is active.
     */
    public void validate() {
        if (defaultProvider == null) {
            throw new IllegalStateException("rag.llm.default-provider must not be null");
        }
        ollama.validate();
        openAiCompatible.validateApiKeyEnvConfigured();
        if (defaultProvider == LlmProvider.OPENAI_COMPATIBLE) {
            openAiCompatible.validateWhenActive();
        }
        if (!hasExplicitProviderSplit()
                && getEffectiveDefaultChatProvider() != defaultProvider) {
            throw new IllegalStateException(
                    "rag.llm.default-chat-provider must not diverge from rag.llm.default-provider unless embedding provider is also set explicitly");
        }
        if (!hasExplicitProviderSplit()
                && getEffectiveDefaultEmbeddingProvider() != defaultProvider) {
            throw new IllegalStateException(
                    "rag.llm.default-embedding-provider must not diverge from rag.llm.default-provider unless chat provider is also set explicitly");
        }
        if (getEffectiveDefaultEmbeddingProvider() == LlmProvider.OPENAI_COMPATIBLE
                && (openAiCompatible.getDefaultEmbeddingModel() == null
                        || openAiCompatible.getDefaultEmbeddingModel().isBlank())) {
            throw new IllegalStateException(
                    "rag.llm.openai-compatible.default-embedding-model must not be blank when embedding provider is OPENAI_COMPATIBLE");
        }
    }
}
