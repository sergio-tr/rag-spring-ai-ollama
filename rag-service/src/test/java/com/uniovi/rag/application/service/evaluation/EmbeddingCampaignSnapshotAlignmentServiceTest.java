package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.KnowledgeIndexSnapshotLookupPort;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotLinkage;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.knowledge.IndexSnapshotEmbeddingLookup;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EmbeddingCampaignSnapshotAlignmentServiceTest {

    @Mock private KnowledgeIndexSnapshotLookupPort lookupPort;
    @Mock private KnowledgePipelineOrchestrator pipelineOrchestrator;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private LabIndexProfileOverrideFactory profileOverrideFactory;
    @Mock private EvaluationCorpusApplicationService corpusApplicationService;
    @Mock private ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;

    private EmbeddingCampaignSnapshotAlignmentService service;

    @BeforeEach
    void setUp() {
        service =
                new EmbeddingCampaignSnapshotAlignmentService(
                        lookupPort,
                        pipelineOrchestrator,
                        projectIndexProfileService,
                        profileOverrideFactory,
                        corpusApplicationService,
                        resolvedConfigSnapshotApplicationService);
    }

    @Test
    void alignFromCatalog_rejectsProvidedSnapshotWhenEmbeddingModelDoesNotMatch() {
        UUID corpusId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID hfSnap = UUID.randomUUID();
        UUID bgeSnap = UUID.randomUUID();

        when(lookupPort.findCorpusSnapshots(corpusId))
                .thenReturn(
                        List.of(
                                lookup(hfSnap, "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"),
                                lookup(bgeSnap, "bge-m3")));

        List<UUID> aligned =
                service.alignFromCatalog(
                        projectId,
                        corpusId,
                        List.of("bge-m3", "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"),
                        List.of(hfSnap, hfSnap));

        assertThat(aligned).containsExactly(bgeSnap, hfSnap);
    }

    @Test
    void ensureAligned_rebuildsWhenProvidedSnapshotDoesNotMatchModel() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID wrongSnap = UUID.randomUUID();
        UUID builtSnap = UUID.randomUUID();

        when(lookupPort.findCorpusSnapshots(corpusId))
                .thenReturn(List.of(lookup(wrongSnap, "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest")));
        when(lookupPort.findProjectSnapshots(projectId)).thenReturn(List.of());

        ProjectIndexProfile base = baseProfile(projectId);
        when(projectIndexProfileService.ensureDefault(projectId)).thenReturn(base);
        when(profileOverrideFactory.withEmbeddingModelId(eq(base), eq("bge-m3"))).thenReturn(base);
        when(profileOverrideFactory.buildEffectiveProfile(
                        eq(base),
                        eq(ExperimentalPresetCanonicalCatalog.IndexRequirements.none()),
                        any()))
                .thenReturn(base);
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(projectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "hash"));
        when(pipelineOrchestrator.rebuildScopeWithProfileOverride(
                        eq(projectId), any(), any(), any(), eq(corpusId), any(), any(), any()))
                .thenReturn(builtSnap);

        List<UUID> aligned =
                service.ensureAligned(
                        userId, projectId, corpusId, List.of("bge-m3"), List.of(wrongSnap));

        assertThat(aligned).containsExactly(builtSnap);
        verify(pipelineOrchestrator).rebuildScopeWithProfileOverride(
                eq(projectId), any(), any(), any(), eq(corpusId), any(), any(), any());
    }

    @Test
    void ensureAligned_throwsWhenSnapshotBuildFails() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(lookupPort.findCorpusSnapshots(corpusId)).thenReturn(List.of());
        when(lookupPort.findProjectSnapshots(projectId)).thenReturn(List.of());

        ProjectIndexProfile base = baseProfile(projectId);
        when(projectIndexProfileService.ensureDefault(projectId)).thenReturn(base);
        when(profileOverrideFactory.withEmbeddingModelId(eq(base), eq("bge-m3"))).thenReturn(base);
        when(profileOverrideFactory.buildEffectiveProfile(
                        eq(base),
                        eq(ExperimentalPresetCanonicalCatalog.IndexRequirements.none()),
                        any()))
                .thenReturn(base);
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(projectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "hash"));
        when(pipelineOrchestrator.rebuildScopeWithProfileOverride(
                        eq(projectId), any(), any(), any(), eq(corpusId), any(), any(), any()))
                .thenThrow(new IllegalStateException("embedding provider down"));

        assertThatThrownBy(
                        () ->
                                service.ensureAligned(
                                        userId, projectId, corpusId, List.of("bge-m3"), List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException r = (ResponseStatusException) ex;
                            assertThat(r.getReason()).contains("EMBEDDING_CAMPAIGN_SNAPSHOT_ALIGN_FAILED");
                            assertThat(r.getReason()).contains("bge-m3");
                        });
        verify(pipelineOrchestrator).rebuildScopeWithProfileOverride(
                eq(projectId), any(), any(), any(), eq(corpusId), any(), any(), any());
    }

    @Test
    void ensureAligned_reusesMatchingSnapshotWithoutRebuild() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID bgeSnap = UUID.randomUUID();

        when(lookupPort.findCorpusSnapshots(corpusId)).thenReturn(List.of(lookup(bgeSnap, "bge-m3")));
        when(lookupPort.findProjectSnapshots(projectId)).thenReturn(List.of());

        List<UUID> aligned =
                service.ensureAligned(userId, projectId, corpusId, List.of("bge-m3"), List.of(bgeSnap));

        assertThat(aligned).containsExactly(bgeSnap);
        verify(pipelineOrchestrator, never())
                .rebuildScopeWithProfileOverride(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static ProjectIndexProfile baseProfile(UUID projectId) {
        return new ProjectIndexProfile(
                projectId,
                MaterializationStrategy.CHUNK_LEVEL,
                false,
                "meta-v1",
                "mxbai-embed-large",
                400,
                10,
                "hash",
                Instant.now(),
                Instant.now());
    }

    private static IndexSnapshotEmbeddingLookup lookup(UUID id, String embeddingModelId) {
        return new IndexSnapshotEmbeddingLookup(id, Map.of("embeddingModelId", embeddingModelId));
    }
}
