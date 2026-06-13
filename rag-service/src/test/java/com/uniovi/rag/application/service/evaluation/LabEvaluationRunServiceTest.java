package com.uniovi.rag.application.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.interfaces.rest.dto.CompareRunsResponseDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationResultItemDto;
import com.uniovi.rag.interfaces.rest.dto.LatestLabRunRecoveryDto;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabEvaluationRunServiceTest {

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private EvaluationResultRepository evaluationResultRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RagApiPathProperties apiPathProperties;

    @Mock
    private LabPresetAxisSupport labPresetAxisSupport;

    @InjectMocks
    private LabEvaluationRunService service;

    @Test
    void compare_matchingRagPresetRuns_returnsComparable() {
        UUID userId = UUID.randomUUID();
        UUID runA = UUID.randomUUID();
        UUID runB = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);

        KnowledgeIndexSnapshotEntity indexSnap = mock(KnowledgeIndexSnapshotEntity.class);
        when(indexSnap.getId()).thenReturn(UUID.randomUUID());

        EvaluationRunEntity a = mock(EvaluationRunEntity.class);
        EvaluationRunEntity b = mock(EvaluationRunEntity.class);
        stubComparableBaseline(a, b, BenchmarkKind.RAG_PRESET_END_TO_END.name());
        when(a.getPreset()).thenReturn(preset);
        when(b.getPreset()).thenReturn(preset);
        when(a.getIndexSnapshot()).thenReturn(indexSnap);
        when(b.getIndexSnapshot()).thenReturn(indexSnap);
        when(a.getIndexSignatureHash()).thenReturn("hash-a");
        when(b.getIndexSignatureHash()).thenReturn("hash-a");

        when(evaluationRunRepository.findByIdAndUser_Id(runA, userId)).thenReturn(Optional.of(a));
        when(evaluationRunRepository.findByIdAndUser_Id(runB, userId)).thenReturn(Optional.of(b));

        CompareRunsResponseDto dto = service.compare(userId, runA, runB);
        assertThat(dto.comparable()).isTrue();
        assertThat(dto.incompatibilityReasons()).isEmpty();
    }

    @Test
    void compare_benchmarkKindMismatch_listsReason() {
        UUID userId = UUID.randomUUID();
        UUID runA = UUID.randomUUID();
        UUID runB = UUID.randomUUID();

        EvaluationRunEntity a = mock(EvaluationRunEntity.class);
        EvaluationRunEntity b = mock(EvaluationRunEntity.class);
        stubComparableBaseline(a, b, BenchmarkKind.RAG_PRESET_END_TO_END.name());
        when(a.getBenchmarkKind()).thenReturn(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        when(b.getBenchmarkKind()).thenReturn(BenchmarkKind.EMBEDDING_RETRIEVAL.name());

        when(evaluationRunRepository.findByIdAndUser_Id(runA, userId)).thenReturn(Optional.of(a));
        when(evaluationRunRepository.findByIdAndUser_Id(runB, userId)).thenReturn(Optional.of(b));

        CompareRunsResponseDto dto = service.compare(userId, runA, runB);
        assertThat(dto.comparable()).isFalse();
        assertThat(dto.incompatibilityReasons()).contains("benchmark_kind mismatch");
    }

    @Test
    void compare_embeddingRetrieval_indexSnapshotMismatch_listsReason() {
        UUID userId = UUID.randomUUID();
        UUID runA = UUID.randomUUID();
        UUID runB = UUID.randomUUID();

        EvaluationRunEntity a = mock(EvaluationRunEntity.class);
        EvaluationRunEntity b = mock(EvaluationRunEntity.class);
        String kind = BenchmarkKind.EMBEDDING_RETRIEVAL.name();
        stubComparableBaseline(a, b, kind);
        when(a.getBenchmarkKind()).thenReturn(kind);
        when(b.getBenchmarkKind()).thenReturn(kind);

        KnowledgeIndexSnapshotEntity ia = mock(KnowledgeIndexSnapshotEntity.class);
        KnowledgeIndexSnapshotEntity ib = mock(KnowledgeIndexSnapshotEntity.class);
        when(ia.getId()).thenReturn(UUID.randomUUID());
        when(ib.getId()).thenReturn(UUID.randomUUID());
        when(a.getIndexSnapshot()).thenReturn(ia);
        when(b.getIndexSnapshot()).thenReturn(ib);
        when(a.getIndexSignatureHash()).thenReturn("same");
        when(b.getIndexSignatureHash()).thenReturn("same");

        when(evaluationRunRepository.findByIdAndUser_Id(runA, userId)).thenReturn(Optional.of(a));
        when(evaluationRunRepository.findByIdAndUser_Id(runB, userId)).thenReturn(Optional.of(b));

        CompareRunsResponseDto dto = service.compare(userId, runA, runB);
        assertThat(dto.comparable()).isFalse();
        assertThat(dto.incompatibilityReasons()).contains("index_snapshot_id mismatch");
    }

    @Test
    void mvpCsvColumns_includeRequiredLabMetricsAndEvidenceFields() {
        assertThat(LabEvaluationRunService.MVP_ITEMS_CSV_COLUMNS_FOR_TESTS())
                .contains(
                        "evaluationRunId",
                        "evaluationDatasetId",
                        "evaluationDatasetSha256",
                        "projectId",
                        "corpusDocumentSet",
                        "presetCode",
                        "modelId",
                        "embeddingModelId",
                        "embeddingDimensions",
                        "embeddingCompatibilityStatus",
                        "embeddingCompatibilityErrorCode",
                        "embeddingCompatibilityReason",
                        "classifierModelId",
                        "selectedSnapshotIds",
                        "correctness",
                        "llmJudgeScore",
                        "hallucinationRate",
                        "faithfulness",
                        "sourceSupport",
                        "dateCorrectness",
                        "skipReason",
                        "failureCode",
                        "latencyMs",
                        "timestamp");
    }

    private static void stubComparableBaseline(EvaluationRunEntity a, EvaluationRunEntity b, String benchmarkKind) {
        when(a.getDatasetSha256()).thenReturn("sha");
        when(b.getDatasetSha256()).thenReturn("sha");
        when(a.getRunKind()).thenReturn("rk");
        when(b.getRunKind()).thenReturn("rk");
        when(a.getWorkflowSchemaVersion()).thenReturn("1");
        when(b.getWorkflowSchemaVersion()).thenReturn("1");
        when(a.getBenchmarkKind()).thenReturn(benchmarkKind);
        when(b.getBenchmarkKind()).thenReturn(benchmarkKind);
    }

    @Test
    void findLatestRunForRecovery_returnsMostRecentMatchingRun() {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        AsyncTaskEntity task = spy(
                AsyncTaskEntity.queued(user, AsyncTaskType.EVAL_LLM, Map.of(), Instant.now()));
        lenient().when(task.getId()).thenReturn(taskId);
        task.setStatus(AsyncTaskStatus.SUCCEEDED);
        task.setResultJson(Map.of("ok", true));
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        run.setUser(user);
        run.setAsyncTask(task);
        run.setBenchmarkKind(BenchmarkKind.LLM_JUDGE_QA.name());

        when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
        when(evaluationRunRepository.findRecentByUserAndBenchmarkKind(
                        eq(userId),
                        eq(BenchmarkKind.LLM_JUDGE_QA.name()),
                        any(Pageable.class)))
                .thenReturn(List.of(run));

        LatestLabRunRecoveryDto dto =
                service.findLatestRunForRecovery(userId, BenchmarkKind.LLM_JUDGE_QA, null);

        assertThat(dto).isNotNull();
        assertThat(dto.evaluationRunId()).isEqualTo(runId);
        assertThat(dto.jobId()).isEqualTo(taskId);
        assertThat(dto.terminal()).isTrue();
        assertThat(dto.hasResults()).isTrue();
        assertThat(dto.pollPath()).isEqualTo("/api/v5/lab/jobs/" + taskId);
        assertThat(dto.streamPath()).isEqualTo("/api/v5/lab/jobs/" + taskId + "/events");
    }

    @Test
    void listItems_campaignRun_returnsAllChildPresetRows() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID coordinatorRunId = UUID.randomUUID();
        UUID p0RunId = UUID.randomUUID();
        UUID p1RunId = UUID.randomUUID();

        EvaluationCampaignEntity campaign = mock(EvaluationCampaignEntity.class);
        when(campaign.getId()).thenReturn(campaignId);

        EvaluationRunEntity coordinator = mock(EvaluationRunEntity.class);
        when(coordinator.getId()).thenReturn(coordinatorRunId);
        when(coordinator.getCampaign()).thenReturn(campaign);

        EvaluationRunEntity p0 = mock(EvaluationRunEntity.class);
        when(p0.getId()).thenReturn(p0RunId);
        EvaluationRunEntity p1 = mock(EvaluationRunEntity.class);
        when(p1.getId()).thenReturn(p1RunId);

        EvaluationResultEntity item0 = mock(EvaluationResultEntity.class);
        EvaluationResultEntity item1 = mock(EvaluationResultEntity.class);
        when(item0.getId()).thenReturn(UUID.randomUUID());
        when(item1.getId()).thenReturn(UUID.randomUUID());
        when(item0.getBenchmarkKind()).thenReturn(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        when(item1.getBenchmarkKind()).thenReturn(BenchmarkKind.RAG_PRESET_END_TO_END.name());

        when(evaluationRunRepository.findByIdAndUser_Id(coordinatorRunId, userId)).thenReturn(Optional.of(coordinator));
        when(evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(p0, p1));
        when(evaluationResultRepository.findByRun_IdInOrderByEvaluatedAtAsc(List.of(p0RunId, p1RunId)))
                .thenReturn(List.of(item0, item1));

        List<EvaluationResultItemDto> items = service.listItems(userId, coordinatorRunId);
        assertThat(items).hasSize(2);
    }

    @Test
    void findLatestRunForRecovery_campaignRun_includesCampaignMetadata() {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID childRunId = UUID.randomUUID();

        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);

        AsyncTaskEntity task = spy(
                AsyncTaskEntity.queued(user, AsyncTaskType.EVAL_RAG, Map.of(), Instant.now()));
        lenient().when(task.getId()).thenReturn(taskId);
        task.setStatus(AsyncTaskStatus.SUCCEEDED);

        EvaluationCampaignEntity campaign = mock(EvaluationCampaignEntity.class);
        when(campaign.getId()).thenReturn(campaignId);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        run.setUser(user);
        run.setAsyncTask(task);
        run.setCampaign(campaign);
        run.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());

        EvaluationRunEntity child = mock(EvaluationRunEntity.class);
        when(child.getId()).thenReturn(childRunId);

        when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
        when(evaluationRunRepository.findRecentByUserAndBenchmarkKind(
                        eq(userId),
                        eq(BenchmarkKind.RAG_PRESET_END_TO_END.name()),
                        any(Pageable.class)))
                .thenReturn(List.of(run));
        when(evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(run, child));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId)).thenReturn(List.of());
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(childRunId)).thenReturn(List.of());

        LatestLabRunRecoveryDto dto =
                service.findLatestRunForRecovery(userId, BenchmarkKind.RAG_PRESET_END_TO_END, null);

        assertThat(dto).isNotNull();
        assertThat(dto.campaignId()).isEqualTo(campaignId);
        assertThat(dto.campaignChildRunIds()).containsExactly(runId, childRunId);
        assertThat(dto.persistedItemCount()).isZero();
    }
}
