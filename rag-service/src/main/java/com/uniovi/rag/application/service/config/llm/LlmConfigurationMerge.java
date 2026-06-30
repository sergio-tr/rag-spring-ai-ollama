package com.uniovi.rag.application.service.config.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;

/** Pure cascade merge for per-layer LLM settings. No I/O. */
public final class LlmConfigurationMerge {

    private LlmConfigurationMerge() {}

    public static LlmConfigurationLayer mergeCascade(
            LlmConfigurationLayer applicationDefaults,
            Optional<Map<String, Object>> systemJson,
            Optional<Map<String, Object>> userJson,
            Optional<Map<String, Object>> projectJson,
            Optional<Map<String, Object>> presetProfileLayer,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride,
            ObjectMapper objectMapper) {
        LlmConfigurationLayer merged =
                LlmConfigurationLayer.empty().mergeOver(applicationDefaults);
        merged = applyOptionalMap(merged, systemJson, objectMapper);
        merged = applyOptionalMap(merged, userJson, objectMapper);
        merged = applyOptionalMap(merged, projectJson, objectMapper);
        merged = applyOptionalMap(merged, presetProfileLayer, objectMapper);
        merged = applyJsonNodeIfPresent(merged, conversationRuntimeOverride);
        merged = applyJsonNodeIfPresent(merged, requestRuntimeOverride);
        return merged;
    }

    private static LlmConfigurationLayer applyJsonNodeIfPresent(LlmConfigurationLayer base, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return base;
        }
        if (node.isObject() && node.isEmpty()) {
            return base;
        }
        return base.mergeOver(LlmConfigurationLayer.fromJson(node));
    }

    private static LlmConfigurationLayer applyOptionalMap(
            LlmConfigurationLayer base, Optional<Map<String, Object>> layer, ObjectMapper objectMapper) {
        return layer.filter(m -> m != null && !m.isEmpty())
                .map(m -> base.mergeOver(LlmConfigurationLayer.fromMap(m, objectMapper)))
                .orElse(base);
    }
}
