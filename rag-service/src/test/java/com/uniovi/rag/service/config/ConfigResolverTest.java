package com.uniovi.rag.service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.domain.runtime.RagConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigResolverTest {

    @Mock
    private ConfigurationSourcePort configurationSource;

    private final RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
    private final RagReasoningProperties reasoningProperties = new RagReasoningProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConfigResolver resolver;

    private final UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private final UUID projectId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        featureConfig.setExpansionEnabled(true);
        featureConfig.setNerEnabled(false);
        featureConfig.setToolsEnabled(true);
        reasoningProperties.setStrategy("SIMPLE");
        resolver = new ConfigResolver(
                featureConfig,
                reasoningProperties,
                configurationSource,
                objectMapper,
                10,
                0.7,
                "base-chat",
                "base-embed"
        );
    }

    @Test
    void resolve_systemJson_overridesFeatureDefaults() {
        when(configurationSource.loadSystemDefaults())
                .thenReturn(Optional.of(Map.of("topK", 3, "similarityThreshold", 0.5, "llmModel", "sys-llm")));

        RagConfig out = resolver.resolve(null, null, null);

        assertEquals(3, out.topK());
        assertEquals(0.5, out.similarityThreshold());
        assertEquals("sys-llm", out.llmModel());
        assertTrue(out.expansionEnabled());
        verify(configurationSource, never()).loadUserDefault(any());
        verify(configurationSource, never()).loadProject(any(), any());
    }

    @Test
    void resolve_userDoesNotEraseUnsetFieldsFromSystem() {
        when(configurationSource.loadSystemDefaults())
                .thenReturn(Optional.of(Map.of("topK", 7)));
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(Optional.of(Map.of("llmModel", "user-only-llm")));

        RagConfig out = resolver.resolve(userId, null, null);

        assertEquals(7, out.topK());
        assertEquals("user-only-llm", out.llmModel());
    }

    @Test
    void resolve_projectOverridesUserForSameField() {
        when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());

        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(Optional.of(Map.of("topK", 2, "llmModel", "user-llm")));

        when(configurationSource.loadProject(eq(userId), eq(projectId)))
                .thenReturn(Optional.of(Map.of("topK", 9)));

        RagConfig out = resolver.resolve(userId, projectId, null);

        assertEquals(9, out.topK());
        assertEquals("user-llm", out.llmModel());
    }

    @Test
    void resolve_runtimeJson_appliesLast() throws Exception {
        when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());
        when(configurationSource.loadUserDefault(eq(userId))).thenReturn(Optional.empty());
        when(configurationSource.loadProject(eq(userId), eq(projectId))).thenReturn(Optional.empty());

        JsonNode runtime = objectMapper.readTree("{\"topK\": 42, \"nerEnabled\": true}");
        RagConfig out = resolver.resolve(userId, projectId, runtime);

        assertEquals(42, out.topK());
        assertTrue(out.nerEnabled());
    }

    @Test
    void resolve_withoutUserId_skipsUserAndProjectLayers() {
        when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());

        RagConfig out = resolver.resolve(null, projectId, null);

        assertEquals(10, out.topK());
        verify(configurationSource).loadSystemDefaults();
        verify(configurationSource, never()).loadUserDefault(any());
        verify(configurationSource, never()).loadProject(any(), any());
    }

    @Test
    void resolve_userLayerOptional_fallsBackToBase() {
        when(configurationSource.loadSystemDefaults()).thenReturn(Optional.empty());
        when(configurationSource.loadUserDefault(eq(userId))).thenReturn(Optional.empty());

        RagConfig out = resolver.resolve(userId, null, null);

        assertEquals(10, out.topK());
        assertEquals("base-chat", out.llmModel());
        assertFalse(out.nerEnabled());
    }
}
