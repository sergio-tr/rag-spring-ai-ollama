package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusIndexPrepareResult;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusIndexService;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class LabEvaluationSnapshotServiceTest {

    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    @Mock private EvaluationCorpusIndexService evaluationCorpusIndexService;
    @Mock private CorpusAvailabilityGate corpusAvailabilityGate;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ObjectProvider<LabJobProgressTracker> labJobProgressTracker;

    private LabEvaluationSnapshotService service;

    @BeforeEach
    void setUp() {
        service =
                new LabEvaluationSnapshotService(
                        knowledgeSnapshotService,
                        knowledgePipelineOrchestrator,
                        projectIndexProfileService,
                        labIndexProfileOverrideFactory,
                        evaluationCorpusIndexService,
                        corpusAvailabilityGate,
                        knowledgeIndexSnapshotRepository,
                        evaluationRunRepository,
                        projectRepository,
                        labJobProgressTracker);
    }

    @Test
    void resolveUserId_usesScalarLookupWhenRunUserNotLoaded() {
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);

        when(evaluationRunRepository.findUserIdByRunId(runId)).thenReturn(Optional.of(userId));

        assertThat(service.resolveUserId(run)).isEqualTo(userId);
    }

    @Test
    void prepareSnapshotIfNeeded_routesCorpusAutoReindexThroughEvaluationCorpusIndexService() {
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        run.setAggregatesJson(
                Map.of(
                        "autoReindexPolicy",
                        Map.of(
                                "enabled", true,
                                "allowActiveSnapshotMutation", true,
                                "reuseCompatibleActiveSnapshot", true,
                                "failOnReindexFailure", true)));

        when(evaluationRunRepository.findUserIdByRunId(runId)).thenReturn(Optional.of(userId));
        when(evaluationRunRepository.findCorpusIdByRunId(runId)).thenReturn(Optional.of(corpusId));
        when(evaluationRunRepository.findEffectiveProjectIdByRunId(runId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any())).thenReturn(Optional.empty());
        when(knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId)).thenReturn(Optional.empty());

        ExperimentalPresetCanonicalCatalog.IndexRequirements requirements =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(RagExperimentalPresetCode.P3);
        when(evaluationCorpusIndexService.prepareForPresetRequirements(
                        eq(userId),
                        eq(corpusId),
                        eq(LabPresetRunGroupKey.CHUNK_LEVEL),
                        eq(requirements),
                        eq(null),
                        eq(true)))
                .thenReturn(
                        EvaluationCorpusIndexPrepareResult.built(
                                snapshotId, UUID.randomUUID(), "cfg-hash", "profile-hash"));

        LabEvaluationSnapshotService.AutoReindexPolicy policy =
                LabEvaluationSnapshotService.AutoReindexPolicy.fromRun(run);
        LabEvaluationSnapshotService.PrepareResult result =
                service.prepareSnapshotIfNeeded(
                        run, LabPresetRunGroupKey.CHUNK_LEVEL, requirements, policy, null);

        assertThat(result.status()).isEqualTo("BUILT");
        assertThat(result.snapshot()).isNotNull();
        assertThat(result.snapshot().snapshotId()).isEqualTo(snapshotId);
        verify(evaluationCorpusIndexService)
                .prepareForPresetRequirements(
                        eq(userId),
                        eq(corpusId),
                        eq(LabPresetRunGroupKey.CHUNK_LEVEL),
                        eq(requirements),
                        eq(null),
                        eq(true));
    }

    @Test
    void resolveCompatibleSnapshot_marksExplicitZeroRowSnapshotIncompatible() {
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        KnowledgeIndexSnapshotEntity explicit = new KnowledgeIndexSnapshotEntity();
        try {
            var f = KnowledgeIndexSnapshotEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(explicit, snapshotId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set snapshot id for test", e);
        }
        explicit.setIndexProfileJsonb(
                Map.of("materializationStrategy", "DOCUMENT_LEVEL", "embeddingModelId", "nomic-embed-text"));
        run.setIndexSnapshot(explicit);

        when(evaluationRunRepository.findCorpusIdByRunId(runId)).thenReturn(Optional.of(corpusId));
        when(evaluationRunRepository.findUserIdByRunId(runId)).thenReturn(Optional.of(userId));
        when(evaluationRunRepository.findEffectiveProjectIdByRunId(runId)).thenReturn(Optional.of(indexProjectId));
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any())).thenReturn(Optional.empty());
        when(knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId)).thenReturn(Optional.empty());
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(false);

        ExperimentalPresetCanonicalCatalog.IndexRequirements requirements =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(RagExperimentalPresetCode.P2);
        LabEvaluationSnapshotService.ResolvedSnapshot resolved =
                service.resolveCompatibleSnapshot(run, requirements);

        assertThat(resolved.snapshotId()).isEqualTo(snapshotId);
        assertThat(resolved.hasUsableSnapshot()).isFalse();
    }

    @Test
    void prepareSnapshotIfNeeded_doesNotReuseWhenSnapshotHasZeroVectorRows() {
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        run.setAggregatesJson(
                Map.of(
                        "autoReindexPolicy",
                        Map.of(
                                "enabled", true,
                                "allowActiveSnapshotMutation", true,
                                "reuseCompatibleActiveSnapshot", true,
                                "failOnReindexFailure", true)));

        when(evaluationRunRepository.findUserIdByRunId(runId)).thenReturn(Optional.of(userId));
        when(evaluationRunRepository.findCorpusIdByRunId(runId)).thenReturn(Optional.of(corpusId));
        when(evaluationRunRepository.findEffectiveProjectIdByRunId(runId)).thenReturn(Optional.of(UUID.randomUUID()));

        KnowledgeIndexSnapshotEntity emptySnap = new KnowledgeIndexSnapshotEntity();
        try {
            var f = KnowledgeIndexSnapshotEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(emptySnap, snapshotId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set snapshot id for test", e);
        }
        emptySnap.setIndexProfileJsonb(
                Map.of("materializationStrategy", "CHUNK_LEVEL", "embeddingModelId", "nomic-embed-text"));
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any()))
                .thenReturn(Optional.of(emptySnap));
        when(corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId)).thenReturn(false);

        ExperimentalPresetCanonicalCatalog.IndexRequirements requirements =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(RagExperimentalPresetCode.P3);
        when(evaluationCorpusIndexService.prepareForPresetRequirements(
                        eq(userId),
                        eq(corpusId),
                        eq(LabPresetRunGroupKey.CHUNK_LEVEL),
                        eq(requirements),
                        eq(null),
                        eq(true)))
                .thenReturn(
                        EvaluationCorpusIndexPrepareResult.built(
                                UUID.randomUUID(), UUID.randomUUID(), "cfg-hash", "profile-hash"));

        LabEvaluationSnapshotService.PrepareResult result =
                service.prepareSnapshotIfNeeded(
                        run,
                        LabPresetRunGroupKey.CHUNK_LEVEL,
                        requirements,
                        LabEvaluationSnapshotService.AutoReindexPolicy.fromRun(run),
                        null);

        assertThat(result.action()).isNotEqualTo("REUSE_COMPATIBLE_SNAPSHOT");
        assertThat(result.status()).isEqualTo("BUILT");
        verify(evaluationCorpusIndexService)
                .prepareForPresetRequirements(
                        eq(userId),
                        eq(corpusId),
                        eq(LabPresetRunGroupKey.CHUNK_LEVEL),
                        eq(requirements),
                        eq(null),
                        eq(true));
    }
}
