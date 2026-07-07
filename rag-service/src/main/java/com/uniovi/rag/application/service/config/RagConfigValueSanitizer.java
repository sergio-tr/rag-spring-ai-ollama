package com.uniovi.rag.application.service.config;

import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters RAG config maps to keys allowed at USER_DEFAULT / PROJECT level and in presets.
 */
public final class RagConfigValueSanitizer {

    private static final Logger log = LoggerFactory.getLogger(RagConfigValueSanitizer.class);

    public static final Set<String> ALLOWED_KEYS = Set.of(
            "expansionEnabled",
            "nerEnabled",
            "toolsEnabled",
            "metadataEnabled",
            "reasoningEnabled",
            "rankerEnabled",
            "postRetrievalEnabled",
            "functionCallingEnabled",
            "functionCallingBackendProposalEnabled",
            "functionCallingNativeProviderEnabled",
            "useRetrieval",
            "useAdvisor",
            "clarificationEnabled",
            "memoryEnabled",
            "adaptiveRoutingEnabled",
            "judgeEnabled",
            "deterministicToolRoutingEnabled",
            "topK",
            "similarityThreshold",
            "materializationStrategy",
            "llmModel",
            "embeddingModel",
            "embeddingEncodingFormat",
            "embeddingDimensions",
            "embeddingTimeoutSeconds",
            "embeddingBatchSize",
            "embeddingMaxInputChars",
            "embeddingNormalize",
            "embeddingTruncate",
            "classifierModelId",
            "reasoningStrategy",
            "naiveFullCorpusInPromptEnabled",
            "naiveFullCorpusMaxChars",
            "advancedRetrievalMaxContextChars",
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

    /** Keys that must never be persisted - use {@link LlmConfigurationKeys#API_KEY_ENV} or {@link LlmConfigurationKeys#SECRET_NAME}. */
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
        List<String> dropped = new ArrayList<>();
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
            } else {
                dropped.add(key);
            }
        }
        if (!dropped.isEmpty() && log.isDebugEnabled()) {
            log.debug("RagConfigValueSanitizer dropped unsupported keys: {}", dropped);
        }
        return out;
    }

    /** Keys present in {@code body} that would be removed by {@link #sanitize(Map)}. */
    public static List<String> droppedKeys(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return List.of();
        }
        List<String> dropped = new ArrayList<>();
        for (String key : body.keySet()) {
            if (FORBIDDEN_SECRET_KEYS.contains(key)) {
                continue;
            }
            if (!ALLOWED_KEYS.contains(key) && !PromptOverrideKeys.isPromptOverrideKey(key)) {
                dropped.add(key);
            }
        }
        return List.copyOf(dropped);
    }

    public static boolean isAllowedKey(String key) {
        return ALLOWED_KEYS.contains(key) || PromptOverrideKeys.isPromptOverrideKey(key);
    }
}
