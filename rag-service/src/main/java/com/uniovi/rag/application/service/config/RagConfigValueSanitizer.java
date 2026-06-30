package com.uniovi.rag.application.service.config;

import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Filters RAG config maps to keys allowed at USER_DEFAULT / PROJECT level and in presets.
 */
public final class RagConfigValueSanitizer {

    public static final Set<String> ALLOWED_KEYS = Set.of(
            "expansionEnabled",
            "nerEnabled",
            "toolsEnabled",
            "metadataEnabled",
            "reasoningEnabled",
            "rankerEnabled",
            "postRetrievalEnabled",
            "functionCallingEnabled",
            "useRetrieval",
            "useAdvisor",
            "topK",
            "similarityThreshold",
            "llmModel",
            "embeddingModel",
            "classifierModelId",
            "reasoningStrategy",
            "naiveFullCorpusInPromptEnabled",
            "naiveFullCorpusMaxChars",
            "corpusGroundedDirectWorkflow",
            LlmConfigurationKeys.PROVIDER,
            LlmConfigurationKeys.BASE_URL,
            LlmConfigurationKeys.API_KEY_ENV,
            LlmConfigurationKeys.SECRET_NAME,
            LlmConfigurationKeys.TEMPERATURE,
            LlmConfigurationKeys.TIMEOUT_MS,
            LlmConfigurationKeys.SYSTEM_PROMPT,
            LlmConfigurationKeys.ADDITIONAL_PARAMETERS,
            PromptOverrideKeys.OVERRIDES_MAP_KEY,
            PromptOverrideKeys.TASK_LLM_OVERRIDES_MAP_KEY);

    /** Keys that must never be persisted — use {@link LlmConfigurationKeys#API_KEY_ENV} or {@link LlmConfigurationKeys#SECRET_NAME}. */
    public static final Set<String> FORBIDDEN_SECRET_KEYS = Set.of(
            "apiKey",
            "llmApiKey",
            "openaiApiKey",
            "api_key",
            "llm_api_key",
            "secret",
            "password",
            "bearerToken",
            "accessToken");

    private RagConfigValueSanitizer() {}

    public static Map<String, Object> sanitize(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey();
            if (FORBIDDEN_SECRET_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                        "Forbidden configuration key '"
                                + key
                                + "': store only llmApiKeyEnv or llmSecretName, never the secret value");
            }
            if (ALLOWED_KEYS.contains(key)) {
                out.put(key, e.getValue());
            } else if (PromptOverrideKeys.isPromptOverrideKey(key)) {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    public static boolean isAllowedKey(String key) {
        return ALLOWED_KEYS.contains(key) || PromptOverrideKeys.isPromptOverrideKey(key);
    }
}
