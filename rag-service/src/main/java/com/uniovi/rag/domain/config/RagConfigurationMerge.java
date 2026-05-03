package com.uniovi.rag.domain.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.Map;
import java.util.Optional;

/**
 * Pure cascade merge for {@link RagConfig}: system, user, project, optional preset/profile map, conversation
 * JSON, then request terminal JSON. No I/O; safe for unit tests without Spring.
 */
public final class RagConfigurationMerge {

    private RagConfigurationMerge() {
    }

    /**
     * Applies layers in order: system, user, project, then {@code runtimeOverride} JSON (chat-level).
     */
    public static RagConfig mergeCascade(
            RagConfig base,
            Optional<Map<String, Object>> systemJson,
            Optional<Map<String, Object>> userJson,
            Optional<Map<String, Object>> projectJson,
            JsonNode runtimeOverride,
            ObjectMapper objectMapper) {
        return mergeCascade(
                base,
                systemJson,
                userJson,
                projectJson,
                Optional.empty(),
                null,
                runtimeOverride,
                objectMapper);
    }

    /**
     * Full cascade: system, user, project, optional preset/profile merged map, conversation override, then
     * terminal request override (wins on key conflicts over conversation).
     */
    public static RagConfig mergeCascade(
            RagConfig base,
            Optional<Map<String, Object>> systemJson,
            Optional<Map<String, Object>> userJson,
            Optional<Map<String, Object>> projectJson,
            Optional<Map<String, Object>> presetProfileLayer,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride,
            ObjectMapper objectMapper) {
        RagConfig c = base;
        c = applyOptionalMap(c, systemJson, objectMapper);
        c = applyOptionalMap(c, userJson, objectMapper);
        c = applyOptionalMap(c, projectJson, objectMapper);
        c = applyOptionalMap(c, presetProfileLayer, objectMapper);
        c = applyJsonNodeIfPresent(c, conversationRuntimeOverride);
        c = applyJsonNodeIfPresent(c, requestRuntimeOverride);
        return c;
    }

    private static RagConfig applyJsonNodeIfPresent(RagConfig base, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return base;
        }
        if (node.isObject() && node.isEmpty()) {
            return base;
        }
        return RagConfig.applyJsonOverrides(base, node);
    }

    private static RagConfig applyOptionalMap(
            RagConfig base, Optional<Map<String, Object>> layer, ObjectMapper objectMapper) {
        return layer.filter(m -> m != null && !m.isEmpty())
                .map(m -> applyJsonMap(base, m, objectMapper))
                .orElse(base);
    }

    private static RagConfig applyJsonMap(RagConfig base, Map<String, Object> values, ObjectMapper objectMapper) {
        JsonNode node = objectMapper.valueToTree(values);
        return RagConfig.applyJsonOverrides(base, node);
    }
}
