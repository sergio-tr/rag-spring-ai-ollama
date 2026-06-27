package com.uniovi.rag.domain.llm;

import java.util.Map;
import java.util.Objects;

/**
 * Effective LLM settings after cascade merge (application defaults → system → user → project → preset → runtime).
 * Never contains a resolved API secret — only the environment variable or secret name reference.
 */
public record ResolvedLlmConfig(
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

    public ResolvedLlmConfig {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(additionalParameters, "additionalParameters");
        additionalParameters = Map.copyOf(additionalParameters);
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
        if (provider == LlmProvider.OPENAI_COMPATIBLE) {
            if ((apiKeyEnv == null || apiKeyEnv.isBlank()) && (secretName == null || secretName.isBlank())) {
                throw new IllegalStateException(
                        "OpenAI-compatible provider requires llmApiKeyEnv or llmSecretName in resolved configuration");
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
     * Ensures the configured env var / secret name is set and non-empty in the process environment.
     */
    public void requireApiKeyEnvResolvable() {
        if (provider != LlmProvider.OPENAI_COMPATIBLE) {
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
