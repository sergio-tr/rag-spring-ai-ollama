package com.uniovi.rag.application.service.config.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.domain.config.PresetProfilePayloadMerge;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves effective {@link ResolvedLlmConfig} for a user/project using the same cascade as {@code ConfigResolver}:
 * application properties → system → user → project → preset/profile → runtime overrides.
 */
@Service
public class ResolvedLlmConfigResolver {

    private final ConfigurationSourcePort configurationSource;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    public ResolvedLlmConfigResolver(
            ConfigurationSourcePort configurationSource, LlmProperties llmProperties, ObjectMapper objectMapper) {
        this.configurationSource = configurationSource;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * @param userId optional; when null, only application + system (+ runtime) layers apply
     */
    public ResolvedLlmConfig resolve(UUID userId, UUID projectId, JsonNode runtimeOverride) {
        return resolve(userId, projectId, null, null, runtimeOverride);
    }

    public ResolvedLlmConfig resolve(
            UUID userId,
            UUID projectId,
            UUID presetId,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride) {
        LlmConfigurationLayer applicationDefaults = LlmConfigurationApplicationDefaults.applicationLayer(llmProperties);

        Optional<Map<String, Object>> system = configurationSource.loadSystemDefaults();
        Optional<Map<String, Object>> user =
                userId != null ? configurationSource.loadUserDefault(userId) : Optional.empty();
        Optional<Map<String, Object>> project =
                (userId != null && projectId != null)
                        ? configurationSource.loadProject(userId, projectId)
                        : Optional.empty();

        Optional<Map<String, Object>> presetProfileLayer = Optional.empty();
        if (userId != null && presetId != null) {
            presetProfileLayer =
                    configurationSource
                            .loadPresetProfileCompositionSources(userId, presetId)
                            .map(
                                    src -> {
                                        List<Map<String, Object>> payloads =
                                                new ArrayList<>(src.orderedProfilePayloads());
                                        return PresetProfilePayloadMerge.merge(src.presetValues(), payloads);
                                    })
                            .filter(m -> !m.isEmpty());
        }

        LlmConfigurationLayer merged =
                LlmConfigurationMerge.mergeCascade(
                        applicationDefaults,
                        system,
                        user,
                        project,
                        presetProfileLayer,
                        conversationRuntimeOverride,
                        requestRuntimeOverride,
                        objectMapper);

        ResolvedLlmConfig resolved = LlmConfigurationApplicationDefaults.materialize(merged, llmProperties);
        resolved.validate();
        return resolved;
    }

    /**
     * Same terminal conversation merge semantics as orchestrated RAG config resolution.
     */
    public ResolvedLlmConfig resolveForOrchestratedExecute(
            UUID userId,
            UUID projectId,
            UUID presetId,
            JsonNode terminalConversationMergedOverride,
            Optional<String> chatModelOverride) {
        JsonNode requestOverride = null;
        if (chatModelOverride != null && chatModelOverride.isPresent() && !chatModelOverride.get().isBlank()) {
            requestOverride = objectMapper.createObjectNode().put("llmModel", chatModelOverride.get().trim());
        }
        return resolve(userId, projectId, presetId, terminalConversationMergedOverride, requestOverride);
    }
}
