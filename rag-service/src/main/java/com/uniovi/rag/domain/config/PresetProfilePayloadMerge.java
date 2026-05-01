package com.uniovi.rag.domain.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure merge of preset {@code values} with ordered {@code config_profile.payload} maps. Later maps overwrite
 * keys from earlier ones; profile order follows the caller-provided list order.
 */
public final class PresetProfilePayloadMerge {

    private PresetProfilePayloadMerge() {}

    /**
     * @param presetValues            base map (may be empty, never null)
     * @param orderedProfilePayloads profile payloads in merge order (ordinal order is enforced by the caller)
     */
    public static Map<String, Object> merge(Map<String, Object> presetValues, List<Map<String, Object>> orderedProfilePayloads) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (presetValues != null && !presetValues.isEmpty()) {
            out.putAll(presetValues);
        }
        if (orderedProfilePayloads != null) {
            for (Map<String, Object> layer : orderedProfilePayloads) {
                if (layer != null && !layer.isEmpty()) {
                    out.putAll(layer);
                }
            }
        }
        return out;
    }
}
