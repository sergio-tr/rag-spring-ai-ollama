package com.uniovi.rag.application.service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.port.PresetProfileCompositionSources;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationRuntimeOverrideRetrievalTest {

    @Mock
    private ConfigurationSourcePort configurationSource;

    private ConfigResolver resolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        RagReasoningProperties reasoningProperties = new RagReasoningProperties();
        reasoningProperties.setStrategy("SIMPLE");
        resolver =
                new ConfigResolver(
                        featureConfig,
                        reasoningProperties,
                        configurationSource,
                        objectMapper,
                        new LlmProperties(),
                        8,
                        0.25);
    }

    @Test
    void runtimeOverrideTopK_beatsPreset() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        when(configurationSource.loadPresetProfileCompositionSources(userId, presetId))
                .thenReturn(
                        Optional.of(
                                new PresetProfileCompositionSources(
                                        Map.of("topK", 5, "similarityThreshold", 0.9),
                                        List.of(),
                                        List.of())));
        JsonNode conversationOverride = objectMapper.readTree("{\"topK\": 42, \"similarityThreshold\": 0.55}");

        RagConfig out = resolver.resolve(userId, null, presetId, conversationOverride, null);

        assertEquals(42, out.topK());
        assertEquals(0.55, out.similarityThreshold());
    }

    @Test
    void removingOverride_restoresPreset() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        when(configurationSource.loadPresetProfileCompositionSources(userId, presetId))
                .thenReturn(
                        Optional.of(
                                new PresetProfileCompositionSources(
                                        Map.of("topK", 5, "similarityThreshold", 0.9),
                                        List.of(),
                                        List.of())));

        RagConfig out = resolver.resolve(userId, null, presetId, null, null);

        assertEquals(5, out.topK());
        assertEquals(0.9, out.similarityThreshold());
    }

    @Test
    void assistantDefaultsMode_usesUserLayerDespitePreset() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        when(configurationSource.loadUserDefault(userId))
                .thenReturn(Optional.of(Map.of("topK", 12, "similarityThreshold", 0.4)));
        when(configurationSource.loadPresetProfileCompositionSources(userId, presetId))
                .thenReturn(
                        Optional.of(
                                new PresetProfileCompositionSources(
                                        Map.of("topK", 5, "similarityThreshold", 0.9),
                                        List.of(),
                                        List.of())));
        JsonNode conversationOverride = objectMapper.readTree("{\"retrievalOverrideMode\":\"assistant_defaults\"}");

        RagConfig out = resolver.resolve(userId, null, presetId, conversationOverride, null);

        assertEquals(12, out.topK());
        assertEquals(0.4, out.similarityThreshold());
    }

    @Test
    void projectSettingsMode_usesProjectLayerWithUserFallback() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        when(configurationSource.loadUserDefault(userId))
                .thenReturn(Optional.of(Map.of("topK", 12, "similarityThreshold", 0.4)));
        when(configurationSource.loadProject(userId, projectId))
                .thenReturn(Optional.of(Map.of("topK", 15)));
        when(configurationSource.loadPresetProfileCompositionSources(userId, presetId))
                .thenReturn(
                        Optional.of(
                                new PresetProfileCompositionSources(
                                        Map.of("topK", 5, "similarityThreshold", 0.9),
                                        List.of(),
                                        List.of())));
        JsonNode conversationOverride = objectMapper.readTree("{\"retrievalOverrideMode\":\"project_settings\"}");

        RagConfig out = resolver.resolve(userId, projectId, presetId, conversationOverride, null);

        assertEquals(15, out.topK());
        assertEquals(0.4, out.similarityThreshold());
    }
}
