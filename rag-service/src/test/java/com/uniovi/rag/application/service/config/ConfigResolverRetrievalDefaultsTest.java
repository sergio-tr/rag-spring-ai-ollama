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
class ConfigResolverRetrievalDefaultsTest {

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
    void resolve_withoutLayers_usesDeploymentDefaultTopK8() {
        RagConfig out = resolver.resolve(null, null, null);
        assertEquals(8, out.topK());
        assertEquals(0.25, out.similarityThreshold());
    }

    @Test
    void resolve_presetRetrieval_winsOverUserDefaults() {
        UUID userId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        when(configurationSource.loadUserDefault(userId))
                .thenReturn(Optional.of(Map.of("topK", 12, "similarityThreshold", 0.4)));
        when(configurationSource.loadPresetProfileCompositionSources(userId, presetId))
                .thenReturn(
                        Optional.of(
                                new PresetProfileCompositionSources(
                                        Map.of("topK", 3, "similarityThreshold", 0.9, "useAdvisor", true),
                                        List.of(),
                                        List.of())));

        RagConfig out = resolver.resolve(userId, null, presetId, null, null);

        assertEquals(3, out.topK());
        assertEquals(0.9, out.similarityThreshold());
        assertEquals(true, out.useAdvisor());
    }

    @Test
    void resolve_lockedPresetRetrieval_overridesUserDefaults() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        when(configurationSource.loadUserDefault(userId))
                .thenReturn(Optional.of(Map.of("topK", 12, "similarityThreshold", 0.4)));
        when(configurationSource.loadPresetProfileCompositionSources(userId, presetId))
                .thenReturn(
                        Optional.of(
                                new PresetProfileCompositionSources(
                                        Map.of(
                                                "topK",
                                                3,
                                                "similarityThreshold",
                                                0.9,
                                                "retrievalParameterPolicy",
                                                "PRESET_LOCKED"),
                                        List.of(),
                                        List.of())));

        RagConfig out = resolver.resolve(userId, null, presetId, null, null);

        assertEquals(3, out.topK());
        assertEquals(0.9, out.similarityThreshold());
    }

    @Test
    void resolve_conversationOverride_winsOverLockedPreset() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        when(configurationSource.loadUserDefault(userId))
                .thenReturn(Optional.of(Map.of("topK", 12)));
        when(configurationSource.loadPresetProfileCompositionSources(userId, presetId))
                .thenReturn(
                        Optional.of(
                                new PresetProfileCompositionSources(
                                        Map.of("topK", 3, "lockRetrievalParameters", true),
                                        List.of(),
                                        List.of())));
        JsonNode conversationOverride = objectMapper.readTree("{\"topK\": 42}");

        RagConfig out = resolver.resolve(userId, null, presetId, conversationOverride, null);

        assertEquals(42, out.topK());
    }
}
