package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.KnowledgeOperationKind;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexKind;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeConfigurationIntegrationServiceTest {

    @Mock
    private ConfigResolverService configResolverService;

    @Mock
    private KnowledgeBuildProjectionMapper knowledgeBuildProjectionMapper;

    @Mock
    private ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;

    @Mock
    private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;

    @Mock
    private ReindexService reindexService;

    @InjectMocks
    private KnowledgeConfigurationIntegrationService knowledgeConfigurationIntegrationService;

    @Test
    void previewRebuild_doesNotPersistResolvedConfigSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        KnowledgeConfigurationOperationInput input =
                new KnowledgeConfigurationOperationInput(
                        projectId,
                        CorpusScope.PROJECT_SHARED,
                        null,
                        KnowledgeOperationKind.PREVIEW,
                        null,
                        null,
                        null,
                        Set.of(),
                        userId,
                        null);

        ResolvedRuntimeConfig resolved = minimalResolved();
        when(configResolverService.preview(any())).thenReturn(resolved);
        KnowledgeBuildProjection projection =
                new KnowledgeBuildProjection(
                        1,
                        MaterializationStrategy.CHUNK_LEVEL,
                        400,
                        0,
                        "embed",
                        false,
                        ReindexImpact.none(),
                        null,
                        "abc");
        when(knowledgeBuildProjectionMapper.fromResolvedRuntimeConfig(resolved)).thenReturn(projection);

        KnowledgeRebuildPreviewResult result = knowledgeConfigurationIntegrationService.previewRebuild(input);

        assertThat(result.decision().kind()).isEqualTo(KnowledgeReindexKind.NO_OP);
        verify(resolvedConfigSnapshotApplicationService, never()).persistForKnowledgeExecute(any(), any(), any(), any(), any(), any());
        verify(resolvedConfigSnapshotApplicationService, never()).persistIngestionDefaultSnapshot(any(), any(), any());
    }

    private static ResolvedRuntimeConfig minimalResolved() {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        RagConfig core = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "a", "b", "c", "simple");
        return new ResolvedRuntimeConfig(
                core,
                CapabilitySet.fromRagConfig(core),
                CompatibilityResult.ok(),
                ReindexImpact.none(),
                SystemPromptLayers.empty(),
                "x",
                new ConfigProvenance(null, null, null, List.of(), null, null),
                core);
    }
}
