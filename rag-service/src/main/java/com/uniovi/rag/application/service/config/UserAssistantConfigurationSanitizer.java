package com.uniovi.rag.application.service.config;

import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Strips deprecated USER Assistant Configuration keys on save (A1). */
public final class UserAssistantConfigurationSanitizer {

    /** Keys that must not be persisted at USER Assistant Configuration layer. */
    public static final Set<String> USER_DEPRECATED_KEYS = Set.of(
            LlmConfigurationKeys.CHAT_MODEL,
            LlmConfigurationKeys.TEMPERATURE,
            "temperature",
            LlmConfigurationKeys.ADDITIONAL_PARAMETERS,
            "materializationStrategy",
            "expansionEnabled",
            "nerEnabled",
            "toolsEnabled",
            "metadataEnabled",
            "useRetrieval",
            "judgeEnabled",
            "reasoningEnabled",
            "rankerEnabled",
            "postRetrievalEnabled",
            "functionCallingEnabled",
            "useAdvisor");

    private UserAssistantConfigurationSanitizer() {}

    public static Map<String, Object> sanitizeForUserSave(Map<String, Object> body) {
        Map<String, Object> base = RagConfigValueSanitizer.sanitize(body);
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : base.entrySet()) {
            if (!USER_DEPRECATED_KEYS.contains(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return Map.copyOf(out);
    }
}
