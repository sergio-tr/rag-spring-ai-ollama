package com.uniovi.rag.domain.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Merge semantics for USER/PROJECT settings persistence (patch, not full replace). */
public final class SettingsConfigurationMerge {

    private SettingsConfigurationMerge() {}

    /**
     * Merges {@code patch} into {@code stored}. A null value removes the key (revert to inherited).
     */
    public static Map<String, Object> mergePatch(Map<String, Object> stored, Map<String, Object> patch) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (stored != null && !stored.isEmpty()) {
            merged.putAll(stored);
        }
        if (patch == null || patch.isEmpty()) {
            return Map.copyOf(merged);
        }
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (entry.getValue() == null) {
                merged.remove(key);
            } else {
                merged.put(key, entry.getValue());
            }
        }
        return Map.copyOf(merged);
    }
}
