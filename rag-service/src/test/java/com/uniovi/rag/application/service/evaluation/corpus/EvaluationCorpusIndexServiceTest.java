package com.uniovi.rag.application.service.evaluation.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotLinkage;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetRunGroupKey;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.application.service.runtime.config.IndexCompatibilityResult;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EvaluationCorpusIndexServiceTest {

    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    @Mock private ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;

    @InjectMocks private EvaluationCorpusIndexService service;

    @Test
    void prepareIndex_passesResolvedConfigSnapshotLinkageToRebuild() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID configSnapshotId = UUID.randomUUID();
        UUID builtSnapshotId = UUID.randomUUID();
        String configHash = "a".repeat(64);

        stubReadyCorpus(userId, corpusId, indexProjectId);
        ProjectIndexProfile profile = defaultProfile(indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(profile);
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any())).thenReturn(Optional.empty());
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(configSnapshotId, configHash));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        eq(indexProjectId),
                        eq(CorpusScope.PROJECT_SHARED),
                        isNull(),
                        eq(KnowledgeSnapshotOwnerType.EVALUATION_CORPUS),
                        eq(corpusId),
                        eq(configSnapshotId),
                        eq(configHash),
                        eq(profile)))
                .thenReturn(builtSnapshotId);
        KnowledgeIndexSnapshotEntity built = mock(KnowledgeIndexSnapshotEntity.class);
        when(built.getId()).thenReturn(builtSnapshotId);
        when(built.getIndexProfileHash()).thenReturn(profile.profileHash());
        when(knowledgeSnapshotService.findCorpusSnapshots(corpusId)).thenReturn(List.of(built));

        EvaluationCorpusIndexPrepareResult result = service.prepareIndex(userId, corpusId);

        assertThat(result.status()).isEqualTo(EvaluationCorpusIndexPrepareResult.IndexBuildStatus.BUILT);
        assertThat(result.knowledgeIndexSnapshotId()).isEqualTo(builtSnapshotId);
        assertThat(result.resolvedConfigSnapshotId()).isEqualTo(configSnapshotId);
    }

    @Test
    void prepareForPresetRequirements_reusesCompatibleSnapshotWithoutRebuild() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        stubReadyCorpus(userId, corpusId, indexProjectId);

        KnowledgeIndexSnapshotEntity compatible = mock(KnowledgeIndexSnapshotEntity.class);
        when(compatible.getId()).thenReturn(snapshotId);
        when(compatible.getResolvedConfigSnapshotId()).thenReturn(UUID.randomUUID());
        when(compatible.getResolvedConfigHash()).thenReturn("hash");
        when(compatible.getIndexProfileHash()).thenReturn("profile-hash");
        when(compatible.getIndexProfileJsonb()).thenReturn(Map.of("materializationStrategy", "CHUNK_LEVEL"));
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any()))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Predicate<KnowledgeIndexSnapshotEntity> predicate = inv.getArgument(1);
                            return predicate.test(compatible) ? Optional.of(compatible) : Optional.empty();
                        });

        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                        com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode.P3);

        EvaluationCorpusIndexPrepareResult result =
                service.prepareForPresetRequirements(
                        userId, corpusId, LabPresetRunGroupKey.CHUNK_LEVEL, req, null, true);

        assertThat(result.status()).isEqualTo(EvaluationCorpusIndexPrepareResult.IndexBuildStatus.REUSED);
        assertThat(result.knowledgeIndexSnapshotId()).isEqualTo(snapshotId);
        verify(knowledgePipelineOrchestrator, never())
                .rebuildScopeWithProfileOverride(
                        any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void prepareIndex_mapsResolvedConfigLinkageFailureToControlledError() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        stubReadyCorpus(userId, corpusId, indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(defaultProfile(indexProjectId));
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any())).thenReturn(Optional.empty());
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "c".repeat(64)));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(
                        new IllegalArgumentException(
                                "resolved_config_snapshot linkage required for knowledge_index_snapshot"));

        assertThatThrownBy(() -> service.prepareIndex(userId, corpusId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabCorpusReasonCodes.RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE));
    }

    @Test
    void prepareForPresetRequirements_buildsWhenNoCompatibleSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID builtId = UUID.randomUUID();
        stubReadyCorpus(userId, corpusId, indexProjectId);
        ProjectIndexProfile base = defaultProfile(indexProjectId);
        ProjectIndexProfile effective = defaultProfile(indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(base);
        when(labIndexProfileOverrideFactory.buildEffectiveProfile(eq(base), any(), eq(LabPresetRunGroupKey.HYBRID_METADATA)))
                .thenReturn(effective);
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any())).thenReturn(Optional.empty());
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "d".repeat(64)));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        any(), any(), any(), any(), any(), any(), any(), eq(effective)))
                .thenReturn(builtId);
        KnowledgeIndexSnapshotEntity built = mock(KnowledgeIndexSnapshotEntity.class);
        when(built.getId()).thenReturn(builtId);
        when(built.getIndexProfileHash()).thenReturn(effective.profileHash());
        when(knowledgeSnapshotService.findCorpusSnapshots(corpusId)).thenReturn(List.of(built));

        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(
                        com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode.P8);

        EvaluationCorpusIndexPrepareResult result =
                service.prepareForPresetRequirements(
                        userId, corpusId, LabPresetRunGroupKey.HYBRID_METADATA, req, null, true);

        assertThat(result.status()).isEqualTo(EvaluationCorpusIndexPrepareResult.IndexBuildStatus.BUILT);
        assertThat(result.knowledgeIndexSnapshotId()).isEqualTo(builtId);
    }

    private void stubReadyCorpus(UUID userId, UUID corpusId, UUID indexProjectId) {
        KnowledgeDocumentEntity readyDoc = mock(KnowledgeDocumentEntity.class);
        when(evaluationCorpusApplicationService.requireReadyContext(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, indexProjectId, List.of(UUID.randomUUID()), List.of(readyDoc)));
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(1);
    }

    private static ProjectIndexProfile defaultProfile(UUID projectId) {
        return new ProjectIndexProfile(
                projectId,
                MaterializationStrategy.HYBRID,
                true,
                "meta-v1",
                "mxbai-embed-large",
                400,
                10,
                ProjectIndexProfile.computeProfileHash(
                        MaterializationStrategy.HYBRID, true, "meta-v1", "mxbai-embed-large", 400, 10),
                Instant.now(),
                Instant.now());
    }
}
