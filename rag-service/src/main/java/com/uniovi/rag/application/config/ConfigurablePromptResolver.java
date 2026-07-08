package com.uniovi.rag.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.domain.config.PresetProfilePayloadMerge;
import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Resolves effective prompt content (custom override or platform default). */
@Service
public class ConfigurablePromptResolver {

    private final ConfigurationSourcePort configurationSource;
    private final ObjectMapper objectMapper;

    public ConfigurablePromptResolver(ConfigurationSourcePort configurationSource, ObjectMapper objectMapper) {
        this.configurationSource = configurationSource;
        this.objectMapper = objectMapper;
    }

    public String resolve(ConfigurablePromptGroup group, UUID userId, UUID projectId) {
        return resolve(group, userId, projectId, null, null, null);
    }

    public String resolve(
            ConfigurablePromptGroup group,
            UUID userId,
            UUID projectId,
            UUID presetId,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride) {
        Map<String, String> merged = mergedOverrides(userId, projectId, presetId, conversationRuntimeOverride, requestRuntimeOverride);
        if (group == ConfigurablePromptGroup.SYSTEM_INSTRUCTIONS) {
            String llmSystem = mergedLlmSystemPrompt(userId, projectId, presetId, conversationRuntimeOverride, requestRuntimeOverride);
            return llmSystem != null && !llmSystem.isBlank() ? llmSystem : group.defaultContent();
        }
        String override = merged.get(group.id());
        if (override != null && !override.isBlank()) {
            return override;
        }
        return group.defaultContent();
    }

    public String resolveSystem(ConfigurablePromptGroup group, UUID userId, UUID projectId) {
        String override = mergedOverrides(userId, projectId, null, null, null).get(group.id() + ".system");
        if (override != null && !override.isBlank()) {
            return override;
        }
        return group.defaultSystemContent();
    }

    public Map<String, String> mergedOverrides(
            UUID userId,
            UUID projectId,
            UUID presetId,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride) {
        Map<String, String> merged = new LinkedHashMap<>();
        mergeLayer(merged, configurationSource.loadSystemDefaults());
        if (userId != null) {
            mergeLayer(merged, configurationSource.loadUserDefault(userId));
        }
        if (userId != null && projectId != null) {
            mergeLayer(merged, configurationSource.loadProject(userId, projectId));
        }
        if (userId != null && presetId != null) {
            configurationSource
                    .loadPresetProfileCompositionSources(userId, presetId)
                    .ifPresent(
                            src -> {
                                List<Map<String, Object>> payloads = new ArrayList<>(src.orderedProfilePayloads());
                                Map<String, Object> presetMerged =
                                        PresetProfilePayloadMerge.merge(src.presetValues(), payloads);
                                mergeLayer(merged, Optional.of(presetMerged));
                            });
        }
        mergeRuntimeLayer(merged, conversationRuntimeOverride);
        mergeRuntimeLayer(merged, requestRuntimeOverride);
        return Map.copyOf(merged);
    }

    public String mergedOverridesFingerprintMaterial(
            UUID userId,
            UUID projectId,
            UUID presetId,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride) {
        Map<String, String> merged =
                mergedOverrides(userId, projectId, presetId, conversationRuntimeOverride, requestRuntimeOverride);
        if (merged.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        merged.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append('\n'));
        String llmSystem =
                mergedLlmSystemPrompt(userId, projectId, presetId, conversationRuntimeOverride, requestRuntimeOverride);
        if (llmSystem != null && !llmSystem.isBlank()) {
            sb.append("llmSystemPrompt=").append(llmSystem).append('\n');
        }
        return sb.toString();
    }

    private String mergedLlmSystemPrompt(
            UUID userId,
            UUID projectId,
            UUID presetId,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride) {
        Map<String, Object> merged = new LinkedHashMap<>();
        mergeObjectLayer(merged, configurationSource.loadSystemDefaults());
        if (userId != null) {
            mergeObjectLayer(merged, configurationSource.loadUserDefault(userId));
        }
        if (userId != null && projectId != null) {
            mergeObjectLayer(merged, configurationSource.loadProject(userId, projectId));
        }
        if (userId != null && presetId != null) {
            configurationSource
                    .loadPresetProfileCompositionSources(userId, presetId)
                    .ifPresent(
                            src -> {
                                List<Map<String, Object>> payloads = new ArrayList<>(src.orderedProfilePayloads());
                                mergeObjectLayer(
                                        merged,
                                        Optional.of(PresetProfilePayloadMerge.merge(src.presetValues(), payloads)));
                            });
        }
        mergeRuntimeObjectLayer(merged, conversationRuntimeOverride);
        mergeRuntimeObjectLayer(merged, requestRuntimeOverride);
        Object raw = merged.get(LlmConfigurationKeys.SYSTEM_PROMPT);
        return raw instanceof String s ? s : null;
    }

    private static void mergeLayer(Map<String, String> target, Optional<Map<String, Object>> layer) {
        layer.map(PromptOverrideKeys::extractOverrides).ifPresent(target::putAll);
    }

    private static void mergeObjectLayer(Map<String, Object> target, Optional<Map<String, Object>> layer) {
        layer.ifPresent(target::putAll);
    }

    private void mergeRuntimeLayer(Map<String, String> target, JsonNode runtimeOverride) {
        if (runtimeOverride == null || runtimeOverride.isNull()) {
            return;
        }
        Map<String, Object> asMap = objectMapper.convertValue(runtimeOverride, Map.class);
        mergeLayer(target, Optional.of(asMap));
    }

    private void mergeRuntimeObjectLayer(Map<String, Object> target, JsonNode runtimeOverride) {
        if (runtimeOverride == null || runtimeOverride.isNull()) {
            return;
        }
        target.putAll(objectMapper.convertValue(runtimeOverride, Map.class));
    }
}
