package com.uniovi.rag.application.service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.port.RagConfigurationResolver;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.domain.config.PresetProfilePayloadMerge;
import com.uniovi.rag.domain.config.RagConfigurationMerge;
import com.uniovi.rag.domain.runtime.RagConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves {@link RagConfig} using the 4-level cascade: system defaults, user, project, runtime JSON.
 * Delegates persistence reads to {@link ConfigurationSourcePort} and pure merge to {@link RagConfigurationMerge}.
 */
@Service
public class ConfigResolver implements RagConfigurationResolver {

    private final RagFeatureConfiguration featureConfig;
    private final RagReasoningProperties reasoningProperties;
    private final ConfigurationSourcePort configurationSource;
    private final ObjectMapper objectMapper;
    private final int topK;
    private final double similarityThreshold;
    private final String chatModel;
    private final String embeddingModel;

    public ConfigResolver(
            RagFeatureConfiguration featureConfig,
            RagReasoningProperties reasoningProperties,
            ConfigurationSourcePort configurationSource,
            ObjectMapper objectMapper,
            @Value("${spring.ai.ollama.top-k:10}") int topK,
            @Value("${spring.ai.ollama.similarity-threshold:0.7}") double similarityThreshold,
            @Value("${spring.ai.ollama.chat.model:gemma3:4b}") String chatModel,
            @Value("${spring.ai.ollama.embedding.model:mxbai-embed-large}") String embeddingModel) {
        this.featureConfig = featureConfig;
        this.reasoningProperties = reasoningProperties;
        this.configurationSource = configurationSource;
        this.objectMapper = objectMapper;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    /**
     * @param userId          optional; when null, only system (+ runtime) layers apply
     * @param projectId       optional; requires userId for project layer
     * @param runtimeOverride optional JSON with fields overriding the merged config (chat-level)
     */
    @Override
    public RagConfig resolve(UUID userId, UUID projectId, JsonNode runtimeOverride) {
        return resolve(userId, projectId, null, null, runtimeOverride);
    }

    @Override
    public RagConfig resolve(
            UUID userId,
            UUID projectId,
            UUID presetId,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride) {
        RagConfig base =
                RagConfig.fromFeatureConfiguration(
                        featureConfig,
                        topK,
                        similarityThreshold,
                        chatModel,
                        embeddingModel,
                        "default",
                        reasoningProperties.getStrategy() != null ? reasoningProperties.getStrategy() : "SIMPLE");

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

        return RagConfigurationMerge.mergeCascade(
                base,
                system,
                user,
                project,
                presetProfileLayer,
                conversationRuntimeOverride,
                requestRuntimeOverride,
                objectMapper);
    }
}
