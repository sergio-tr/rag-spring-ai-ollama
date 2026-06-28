package com.uniovi.rag.application.service.config.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.domain.config.PresetProfilePayloadMerge;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelUsageContext;
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
    private final LlmModelCatalogPort modelCatalog;

    public ResolvedLlmConfigResolver(
            ConfigurationSourcePort configurationSource,
            LlmProperties llmProperties,
            ObjectMapper objectMapper,
            LlmModelCatalogPort modelCatalog) {
        this.configurationSource = configurationSource;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.modelCatalog = modelCatalog;
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

        ResolvedLlmConfig resolved =
                LlmConfigurationApplicationDefaults.materialize(merged, llmProperties, modelCatalog);
        if (isApplicationOnlyResolution(userId, presetId, conversationRuntimeOverride, requestRuntimeOverride)) {
            validateApplicationDefaults(resolved);
        }
        resolved.validate();
        return resolved;
    }

    private boolean isApplicationOnlyResolution(
            UUID userId,
            UUID presetId,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride) {
        return userId == null
                && presetId == null
                && (conversationRuntimeOverride == null || conversationRuntimeOverride.isNull())
                && (requestRuntimeOverride == null || requestRuntimeOverride.isNull());
    }

    private void validateApplicationDefaults(ResolvedLlmConfig resolved) {
        if (!llmProperties.hasExplicitProviderSplit()) {
            LlmProvider uniform = llmProperties.getUniformStackProvider();
            if (resolved.chatProvider() != uniform || resolved.embeddingProvider() != uniform) {
                throw new IllegalStateException(
                        "Application LLM providers must be uniform ("
                                + uniform
                                + ") when only rag.llm.default-provider is configured; got chat="
                                + resolved.chatProvider()
                                + " embedding="
                                + resolved.embeddingProvider());
            }
        }
        if (resolved.chatModel() != null && !resolved.chatModel().isBlank()) {
            modelCatalog.assertUsable(
                    resolved.chatProvider(),
                    resolved.chatModel(),
                    LlmModelCapability.CHAT,
                    LlmModelUsageContext.SYSTEM_DEFAULT);
        }
        if (resolved.embeddingModel() != null && !resolved.embeddingModel().isBlank()) {
            modelCatalog.assertUsable(
                    resolved.embeddingProvider(),
                    resolved.embeddingModel(),
                    LlmModelCapability.EMBEDDING,
                    LlmModelUsageContext.SYSTEM_DEFAULT);
        }
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
