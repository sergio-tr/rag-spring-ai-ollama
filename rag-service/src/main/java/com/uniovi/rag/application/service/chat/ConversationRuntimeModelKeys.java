package com.uniovi.rag.application.service.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chat model selection is persisted on {@code conversations.llm_model} and {@code conversations.classifier_model_id}.
 * Those values must not be duplicated inside {@code runtime_override_jsonb} to avoid contradictory sources.
 */
public final class ConversationRuntimeModelKeys {

    public static final String LLM_MODEL = "llmModel";
    public static final String CLASSIFIER_MODEL_ID = "classifierModelId";

    private ConversationRuntimeModelKeys() {}

    /** Returns a mutable copy with {@link #LLM_MODEL} and {@link #CLASSIFIER_MODEL_ID} entries removed. */
    public static Map<String, Object> copyWithoutModelKeys(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getKey() == null || isModelSelectionKey(e.getKey())) {
                    continue;
                }
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    public static boolean isModelSelectionKey(String key) {
        return LLM_MODEL.equals(key) || CLASSIFIER_MODEL_ID.equals(key);
    }
}
