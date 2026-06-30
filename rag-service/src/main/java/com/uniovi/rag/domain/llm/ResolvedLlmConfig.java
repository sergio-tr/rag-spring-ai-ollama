package com.uniovi.rag.domain.llm;

import java.util.Map;
import java.util.Objects;

/**
 * Effective LLM settings after cascade merge (application defaults → system → user → project → preset → runtime).
 * Chat and embedding providers are explicit; with only {@code rag.llm.default-provider} both are uniform.
 * Hybrid mode requires explicit {@code default-chat-provider} / {@code default-embedding-provider} (or per-layer overrides).
 * Never contains a resolved API secret — only the environment variable or secret name reference.
 */
public record ResolvedLlmConfig(
        LlmProvider chatProvider,
        LlmProvider embeddingProvider,
        String baseUrl,
        String chatModel,
        String embeddingModel,
        String apiKeyEnv,
        String secretName,
        Double temperature,
        Integer timeoutMs,
        String systemPrompt,
        Map<String, Object> additionalParameters) {

    public ResolvedLlmConfig {
        Objects.requireNonNull(chatProvider, "chatProvider");
        Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        Objects.requireNonNull(additionalParameters, "additionalParameters");
        additionalParameters = Map.copyOf(additionalParameters);
    }

    /** Legacy alias for {@link #chatProvider()}; prefer {@link #chatProvider()} or {@link #embeddingProvider()}. */
    public LlmProvider provider() {
        return chatProvider;
    }

    /** True when chat and embedding use the same provider (normal product default). */
    public boolean uniformProviders() {
        return chatProvider == embeddingProvider;
    }

    public boolean usesOpenAiCompatibleChat() {
        return chatProvider == LlmProvider.OPENAI_COMPATIBLE;
    }

    public boolean usesOpenAiCompatibleEmbedding() {
        return embeddingProvider == LlmProvider.OPENAI_COMPATIBLE;
    }

    public boolean requiresOllamaNativeChat() {
        return chatProvider == LlmProvider.OLLAMA_NATIVE;
    }

    public boolean requiresOllamaNativeEmbedding() {
        return embeddingProvider == LlmProvider.OLLAMA_NATIVE;
    }

    /** Both capabilities share the same provider (tests and uniform configuration). */
    public static ResolvedLlmConfig uniform(
            LlmProvider provider,
            String baseUrl,
            String chatModel,
            String embeddingModel,
            String apiKeyEnv,
            String secretName,
            Double temperature,
            Integer timeoutMs,
            String systemPrompt,
            Map<String, Object> additionalParameters) {
        return new ResolvedLlmConfig(
                provider,
                provider,
                baseUrl,
                chatModel,
                embeddingModel,
                apiKeyEnv,
                secretName,
                temperature,
                timeoutMs,
                systemPrompt,
                additionalParameters);
    }

    /**
     * Structural validation after merge. Does not read {@code System.getenv} — use {@link #requireApiKeyEnvResolvable()} at call time.
     */
    public void validate() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Resolved LLM baseUrl must not be blank");
        }
        if (chatModel == null || chatModel.isBlank()) {
            throw new IllegalStateException("Resolved LLM chatModel must not be blank");
        }
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalStateException("Resolved LLM embeddingModel must not be blank");
        }
        if (timeoutMs != null && timeoutMs <= 0) {
            throw new IllegalStateException("llmTimeoutMs must be positive");
        }
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalStateException("llmTemperature must be between 0.0 and 2.0");
        }
        if (usesOpenAiCompatibleChat() || usesOpenAiCompatibleEmbedding()) {
            if ((apiKeyEnv == null || apiKeyEnv.isBlank()) && (secretName == null || secretName.isBlank())) {
                throw new IllegalStateException(
                        "OpenAI-compatible chat or embedding requires llmApiKeyEnv or llmSecretName in resolved configuration");
            }
        }
    }

    /**
     * @return environment variable name to read for Bearer auth (prefers {@code apiKeyEnv}, then {@code secretName})
     */
    public String effectiveApiKeyEnv() {
        if (apiKeyEnv != null && !apiKeyEnv.isBlank()) {
            return apiKeyEnv.trim();
        }
        if (secretName != null && !secretName.isBlank()) {
            return secretName.trim();
        }
        return null;
    }

    /**
     * Ensures the configured env var / secret name is set and non-empty in the process environment when OpenAI-compatible is used.
     */
    public void requireApiKeyEnvResolvable() {
        if (!usesOpenAiCompatibleChat() && !usesOpenAiCompatibleEmbedding()) {
            return;
        }
        String envName = effectiveApiKeyEnv();
        if (envName == null || envName.isBlank()) {
            throw new IllegalStateException("OpenAI-compatible LLM requires llmApiKeyEnv or llmSecretName");
        }
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "API key environment variable is not set or empty: " + envName
                            + " (configure the secret externally; never store keys in the database)");
        }
    }
}
