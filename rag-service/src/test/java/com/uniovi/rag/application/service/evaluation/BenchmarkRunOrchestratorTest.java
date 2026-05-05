package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkRunOrchestratorTest {

    @Mock private UserRepository userRepository;
    @Mock private EvaluationDatasetRepository evaluationDatasetRepository;
    @Mock private EvaluationCampaignRepository evaluationCampaignRepository;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    @Mock private RagPresetRepository ragPresetRepository;
    @Mock private AsyncTaskRepository asyncTaskRepository;
    @Mock private AsyncTaskService asyncTaskService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private RagRuntimeProperties ragRuntimeProperties;
    @Mock private EvaluationDatasetStorePort evaluationDatasetStorePort;
    private final EvaluationWorkbookParser evaluationWorkbookParser = new EvaluationWorkbookParser();

    @Test
    void startJsonBenchmark_forbidsAdminBaselineForNonAdmin() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
                        userRepository,
                        evaluationDatasetRepository,
                        evaluationCampaignRepository,
                        evaluationRunRepository,
                        resolvedConfigSnapshotRepository,
                        knowledgeIndexSnapshotRepository,
                        ragPresetRepository,
                        asyncTaskRepository,
                        asyncTaskService,
                        projectAccessService,
                        ragRuntimeProperties,
                        evaluationDatasetStorePort,
                        evaluationWorkbookParser);

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        EvaluationRunKind.ADMIN_BASELINE,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null);

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.LLM_JUDGE_QA, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void startJsonBenchmark_returnsNotFoundWhenDatasetMissing() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
                        userRepository,
                        evaluationDatasetRepository,
                        evaluationCampaignRepository,
                        evaluationRunRepository,
                        resolvedConfigSnapshotRepository,
                        knowledgeIndexSnapshotRepository,
                        ragPresetRepository,
                        asyncTaskRepository,
                        asyncTaskService,
                        projectAccessService,
                        ragRuntimeProperties,
                        evaluationDatasetStorePort,
                        evaluationWorkbookParser);

        UUID dsId = UUID.randomUUID();
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.empty());
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        UUID.randomUUID(),
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null);

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "ADMIN", BenchmarkKind.LLM_JUDGE_QA, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void startJsonBenchmark_rejectsIncompatibleExperimentalKind() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
                        userRepository,
                        evaluationDatasetRepository,
                        evaluationCampaignRepository,
                        evaluationRunRepository,
                        resolvedConfigSnapshotRepository,
                        knowledgeIndexSnapshotRepository,
                        ragPresetRepository,
                        asyncTaskRepository,
                        asyncTaskService,
                        projectAccessService,
                        ragRuntimeProperties,
                        evaluationDatasetStorePort,
                        evaluationWorkbookParser);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getExperimentalKind()).thenReturn("LLM_MODEL_BASELINE");
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null);

        assertThatThrownBy(() -> orch.startJsonBenchmark(userId, "USER", BenchmarkKind.EMBEDDING_RETRIEVAL, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}

