package com.uniovi.rag.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.port.ConversationRuntimeOverrideLoader;
import com.uniovi.rag.application.port.RagConfigurationResolver;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.indexing.ReindexImpactLevel;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.config.runtime.ResolvedConfigSnapshot;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.config.CompatibilityRulesConfiguration;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigResolverServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RagConfigurationResolver ragConfigurationResolver;
    private ProjectRepository projectRepository;
    private UserPersonalizationRepository userPersonalizationRepository;
    private ConversationRuntimeOverrideLoader conversationRuntimeOverrideLoader;
    private ConfigurationSourcePort configurationSourcePort;
    private ConfigResolverService service;

    private static RagConfig validCore() {
        RagFeatureConfiguration f = new RagFeatureConfiguration();
        f.setToolsEnabled(true);
        f.setMetadataEnabled(false);
        f.setPostRetrievalEnabled(false);
        f.setFunctionCallingEnabled(false);
        f.setUseRetrieval(true);
        return RagConfig.fromFeatureConfiguration(f, 10, 0.7, "llm", "emb", "c", "simple");
    }

    @BeforeEach
    void setUp() {
        ragConfigurationResolver = mock(RagConfigurationResolver.class);
        projectRepository = mock(ProjectRepository.class);
        userPersonalizationRepository = mock(UserPersonalizationRepository.class);
        conversationRuntimeOverrideLoader = mock(ConversationRuntimeOverrideLoader.class);
        configurationSourcePort = mock(ConfigurationSourcePort.class);
        CompatibilityValidator compatibilityValidator =
                new CompatibilityValidator(new CompatibilityRulesConfiguration().compatibilityRules());
        service =
                new ConfigResolverService(
                        ragConfigurationResolver,
                        projectRepository,
                        userPersonalizationRepository,
                        compatibilityValidator,
                        new ReindexImpactAnalyzer(),
                        new SystemPromptComposer(),
                        conversationRuntimeOverrideLoader,
                        configurationSourcePort);
    }

    @Test
    void resolvePassesEffectiveRuntimeOverrideToResolver() throws Exception {
        UUID user = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        ObjectNode runtime = MAPPER.createObjectNode().put("topK", 7);
        ObjectNode preset = MAPPER.createObjectNode().put("topK", 99);
        RuntimeConfigResolutionInput input =
                new RuntimeConfigResolutionInput(
                        user,
                        project,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(preset),
                        Optional.of(runtime),
                        Set.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
        when(ragConfigurationResolver.resolve(eq(user), eq(project), isNull(), isNull(), eq(runtime)))
                .thenReturn(validCore());

        ResolvedRuntimeConfig resolved = service.resolve(input);

        verify(ragConfigurationResolver).resolve(user, project, null, null, runtime);
        assertEquals(10, resolved.resolvedCoreConfig().topK());
        assertSame(resolved.toRagConfig(), resolved.configProjection());
    }

    @Test
    void resolveThrowsWhenCompatibilityErrors() throws Exception {
        RagConfig bad =
                RagConfig.applyJsonOverrides(
                        validCore(),
                        MAPPER.readTree(
                                "{\"functionCallingEnabled\": true, \"naiveFullCorpusInPromptEnabled\": true}"));
        when(ragConfigurationResolver.resolve(any(), any(), any(), any(), any())).thenReturn(bad);

        RuntimeConfigResolutionInput input =
                RuntimeConfigResolutionInput.forResolve(UUID.randomUUID(), UUID.randomUUID(), null);

        assertThrows(ResponseStatusException.class, () -> service.resolve(input));
    }

    @Test
    void previewReturnsInvalidConfigWithoutThrowing() throws Exception {
        RagConfig bad =
                RagConfig.applyJsonOverrides(
                        validCore(),
                        MAPPER.readTree(
                                "{\"functionCallingEnabled\": true, \"naiveFullCorpusInPromptEnabled\": true}"));
        when(ragConfigurationResolver.resolve(any(), any(), any(), any(), any())).thenReturn(bad);

        RuntimeConfigResolutionInput input =
                RuntimeConfigResolutionInput.forResolve(UUID.randomUUID(), UUID.randomUUID(), null);

        ResolvedRuntimeConfig previewed = service.preview(input);
        assertFalse(previewed.compatibility().valid());
    }

    @Test
    void previewComputesReindexWhenTouchedProfilesPresent() {
        when(ragConfigurationResolver.resolve(any(), any(), any(), any(), any())).thenReturn(validCore());

        RuntimeConfigResolutionInput input =
                new RuntimeConfigResolutionInput(
                        null,
                        null,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Set.of(ConfigProfileType.EMBEDDING),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());

        ResolvedRuntimeConfig previewed = service.preview(input);
        assertEquals(ReindexImpactLevel.HARD_REINDEX, previewed.reindexImpact().level());
    }

    @Test
    void resolveUsesNoReindexWhenNotPreviewStyleSignals() {
        when(ragConfigurationResolver.resolve(any(), any(), any(), any(), any())).thenReturn(validCore());

        RuntimeConfigResolutionInput input =
                RuntimeConfigResolutionInput.forResolve(UUID.randomUUID(), UUID.randomUUID(), null);

        ResolvedRuntimeConfig resolved = service.resolve(input);
        assertEquals(ReindexImpactLevel.NO_REINDEX, resolved.reindexImpact().level());
    }

    @Test
    void snapshotEqualityStableForSameFields() {
        RagConfig core = validCore();
        when(ragConfigurationResolver.resolve(any(), any(), any(), any(), any())).thenReturn(core);

        ResolvedRuntimeConfig r =
                service.resolve(RuntimeConfigResolutionInput.forResolve(null, null, null));
        UUID id = UUID.randomUUID();
        Instant at = Instant.parse("2020-01-01T00:00:00Z");
        ResolvedConfigSnapshot a =
                new ResolvedConfigSnapshot(
                        id,
                        at,
                        r,
                        r.capabilitySet(),
                        r.compatibility(),
                        r.reindexImpact(),
                        r.effectiveSystemPrompt(),
                        r.provenance());
        ResolvedConfigSnapshot b =
                new ResolvedConfigSnapshot(
                        id,
                        at,
                        r,
                        r.capabilitySet(),
                        r.compatibility(),
                        r.reindexImpact(),
                        r.effectiveSystemPrompt(),
                        r.provenance());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
