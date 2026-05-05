package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LlmCampaignOrchestratorTest {

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

    @Test
    void startJsonBenchmark_llmCampaign_createsCampaignAndChildRuns_andReturnsCampaignId() {
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
                        new EvaluationWorkbookParser());

        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UUID datasetId = UUID.randomUUID();
        EvaluationDatasetEntity ds = mock(EvaluationDatasetEntity.class);
        when(ds.getId()).thenReturn(datasetId);
        when(ds.getOwner()).thenReturn(user);
        when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        when(ds.getExperimentalKind()).thenReturn("LLM_MODEL_BASELINE");
        when(evaluationDatasetRepository.findById(datasetId)).thenReturn(Optional.of(ds));

        // Persist campaign: return an entity with an id.
        when(evaluationCampaignRepository.save(any())).thenAnswer(inv -> {
            EvaluationCampaignEntity c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            return c;
        });

        // Persist runs: assign ids.
        when(evaluationRunRepository.save(any())).thenAnswer(inv -> {
            EvaluationRunEntity r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        UUID taskId = UUID.randomUUID();
        when(asyncTaskService.submitEvalLlm(any(), any(), any())).thenReturn(taskId);
        AsyncTaskEntity task = mock(AsyncTaskEntity.class);
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        datasetId,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "My run",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of("m1", "m2"),
                        List.of(),
                        false,
                        "My campaign");

        BenchmarkJobAccepted accepted = orch.startJsonBenchmark(userId, "USER", BenchmarkKind.LLM_JUDGE_QA, req);
        assertThat(accepted.campaignId()).isPresent();
        verify(evaluationCampaignRepository).save(any());
        verify(asyncTaskService, times(2)).submitEvalLlm(any(), any(), any());

        ArgumentCaptor<EvaluationRunEntity> runCaptor = ArgumentCaptor.forClass(EvaluationRunEntity.class);
        verify(evaluationRunRepository, atLeast(2)).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().stream().anyMatch(r -> r.getCampaign() != null)).isTrue();
    }
}

