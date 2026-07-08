package com.uniovi.rag.domain.config.prompt;

import java.util.LinkedHashMap;
import java.util.Map;

/** Namespacing for prompt override keys in {@code rag_configuration.values}. */
public final class PromptOverrideKeys {

    public static final String OVERRIDES_MAP_KEY = "promptOverrides";
    public static final String TASK_LLM_OVERRIDES_MAP_KEY = "taskLlmOverrides";
    public static final String OVERRIDE_PREFIX = "promptOverride.";

    private PromptOverrideKeys() {}

    public static String overrideKey(String groupId) {
        return OVERRIDE_PREFIX + groupId;
    }

    public static boolean isPromptOverrideKey(String key) {
        return key != null && (key.startsWith(OVERRIDE_PREFIX) || OVERRIDES_MAP_KEY.equals(key));
    }

    public static boolean isTaskLlmOverrideKey(String key) {
        return key != null && TASK_LLM_OVERRIDES_MAP_KEY.equals(key);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> extractOverrides(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        Object nested = values.get(OVERRIDES_MAP_KEY);
        if (nested instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() instanceof String id && e.getValue() instanceof String text) {
                    if (!text.isBlank()) {
                        out.put(id, text);
                    }
                }
            }
        }
        for (ConfigurablePromptGroup group : ConfigurablePromptGroup.values()) {
            if (group.usesLlmSystemPromptKey()) {
                continue;
            }
            String flatKey = overrideKey(group.id());
            Object raw = values.get(flatKey);
            if (raw instanceof String text && !text.isBlank()) {
                out.putIfAbsent(group.id(), text);
            }
        }
        return Map.copyOf(out);
    }
}
