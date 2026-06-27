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
    private LlmOllamaDefaults ollama = new LlmOllamaDefaults();
    private LlmOpenAiCompatibleDefaults openAiCompatible = new LlmOpenAiCompatibleDefaults();

    public LlmProvider getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(LlmProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
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
    }
}
