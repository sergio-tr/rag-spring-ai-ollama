package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.application.service.evaluation.lab.LabClasspathCorpusBootstrapService;
import com.uniovi.rag.application.service.evaluation.lab.LabCorpusBootstrapResult;
import com.uniovi.rag.application.service.knowledge.ProjectIndexOperationLockService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskCancellationService;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkCompletionService;
import com.uniovi.rag.application.service.evaluation.LabCampaignBenchmarkExecutor;
import com.uniovi.rag.application.service.evaluation.LabJobPhaseEmitter;
import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusSummaryDto;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.result.evaluation.RagPresetBenchmarkRunPayload;
import com.uniovi.rag.application.service.evaluation.EvaluationPayloadMapper;
import com.uniovi.rag.application.service.evaluation.EvaluationTestFixtures;
import com.uniovi.rag.application.service.evaluation.preset.TypedRagPresetBenchmarkOrchestrator;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalRagJobHandlerTest {

    @Mock
    private RagFeatureConfiguration featureConfiguration;

    @Mock
    private RagImplementationProperties implementationProperties;

    @Mock
    private EvaluationCanonicalPersistenceService canonicalPersistence;

    @Mock
    private ExperimentalDatasetResolver experimentalDatasetResolver;

    @Mock
    private TypedRagPresetBenchmarkOrchestrator typedRagPresetBenchmarkOrchestrator;

    @Mock
    private AsyncTaskMutationService mutation;

    @Mock
    private AsyncTaskCancellationService cancellationService;

    @Mock
    private ProjectIndexOperationLockService projectIndexOperationLockService;

    @Mock
    private LabClasspathCorpusBootstrapService labClasspathCorpusBootstrapService;

    @Mock
    private LabJobProgressTracker labJobProgressTracker;

    @Mock
    private LabJobPhaseEmitter labJobPhaseEmitter;

    @Mock
    private EvaluationCorpusApplicationService evaluationCorpusApplicationService;

    @Mock
    private LabCampaignBenchmarkExecutor labCampaignBenchmarkExecutor;

    @Mock
    private EvaluationRunRagJobContextLoader evaluationRunRagJobContextLoader;

    @Mock
    private LabBenchmarkCompletionService labBenchmarkCompletionService;

    @BeforeEach
    void stubCorpusSummary() {
        Mockito.lenient()
                .when(evaluationCorpusApplicationService.getSummary(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(
                        new EvaluationCorpusSummaryDto(
                                UUID.randomUUID(),
                                "Lab KB",
                                "UPLOADED",
                                1,
                                1,
                                0,
                                List.of(),
                                Instant.now(),
                                Instant.now()));
    }

    private EvalRagJobHandler handler() {
        return new EvalRagJobHandler(
                featureConfiguration,
                implementationProperties,
                canonicalPersistence,
                experimentalDatasetResolver,
                typedRagPresetBenchmarkOrchestrator,
                cancellationService,
                projectIndexOperationLockService,
                labClasspathCorpusBootstrapService,
                labJobProgressTracker,
                labJobPhaseEmitter,
                evaluationCorpusApplicationService,
                labCampaignBenchmarkExecutor,
                evaluationRunRagJobContextLoader,
                labBenchmarkCompletionService);
    }

    @Test
    void taskType_isEvalRag() {
        assertThat(handler().taskType()).isEqualTo(AsyncTaskType.EVAL_RAG);
    }

    @Test
    void run_withRunId_usesTypedRag_neverRemovedEvaluateApi() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(evaluationRunRagJobContextLoader.loadContext(runId))
                .thenReturn(Optional.of(baseContext(runId, null, null, false, false)));
        RagPresetQuestion q = sampleQuestion();
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()));
        RagPresetBenchmarkRunPayload eval = EvaluationTestFixtures.emptyRagRunPayload();
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(TypedBenchmarkDataset.RagPresetQuestions.class),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenReturn(eval);
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler().run(task, mutation);

        Mockito.verify(typedRagPresetBenchmarkOrchestrator, Mockito.times(1))
                .runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(TypedBenchmarkDataset.RagPresetQuestions.class),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
        verify(canonicalPersistence)
                .persistLlmJudgeBatch(
                        runId,
                        new LlmJudgeEvaluationBatchResult(
                                eval.configuration(), eval.results(), eval.evaluationSummary()),
                        BenchmarkKind.RAG_PRESET_END_TO_END);
        verify(labBenchmarkCompletionService)
                .completeRun(mutation, taskId, runId, EvaluationPayloadMapper.toAsyncPayload(eval));
        verifyNoInteractions(labClasspathCorpusBootstrapService);
    }

    @Test
    void run_projectlessWithKnowledgeBase_usesContextIdsNotJpaEntities() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(evaluationRunRagJobContextLoader.loadContext(runId))
                .thenReturn(Optional.of(baseContext(runId, corpusId, projectId, true, false)));
        when(projectIndexOperationLockService.tryAcquire(eq(projectId), Mockito.any(), eq(runId), Mockito.any()))
                .thenReturn(ProjectIndexOperationLockService.LockAttempt.acquired(null));
        RagPresetQuestion q = sampleQuestion();
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()));
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.emptyRagRunPayload());
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler().run(task, mutation);

        verify(evaluationRunRagJobContextLoader).markAutoReindexLockAcquired(runId);
        verify(labJobProgressTracker)
                .emitRagEvaluationAccepted(
                        eq(taskId), eq(runId), eq(corpusId), ArgumentMatchers.any(), ArgumentMatchers.isNull());
    }

    @Test
    void run_withoutRunId_throws() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, Map.of());

        assertThatThrownBy(() -> handler().run(task, mutation)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(canonicalPersistence);
        verifyNoInteractions(experimentalDatasetResolver);
        verifyNoInteractions(labClasspathCorpusBootstrapService);
    }

    @Test
    void run_marksRunFailed_thenRethrows_whenEvaluationThrows() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(evaluationRunRagJobContextLoader.loadContext(runId))
                .thenReturn(Optional.of(baseContext(runId, null, null, false, false)));
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(sampleQuestion()), List.of()));
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("boom"));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(() -> handler().run(task, mutation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(canonicalPersistence).markRunFailed(runId, "boom");
    }

    @Test
    void run_autoReindex_releasesProjectLock_onException() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(evaluationRunRagJobContextLoader.loadContext(runId))
                .thenReturn(Optional.of(baseContext(runId, UUID.randomUUID(), projectId, true, false)));
        when(projectIndexOperationLockService.tryAcquire(eq(projectId), Mockito.any(), eq(runId), Mockito.any()))
                .thenReturn(ProjectIndexOperationLockService.LockAttempt.acquired(null));
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(sampleQuestion()), List.of()));
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("boom"));

        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(() -> handler().run(task, mutation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(projectIndexOperationLockService).release(projectId, "lab:auto-reindex", runId);
    }

    @Test
    void run_autoReindex_releasesProjectLock_onSuccess() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(evaluationRunRagJobContextLoader.loadContext(runId))
                .thenReturn(Optional.of(baseContext(runId, null, projectId, true, false)));
        when(projectIndexOperationLockService.tryAcquire(eq(projectId), Mockito.any(), eq(runId), Mockito.any()))
                .thenReturn(ProjectIndexOperationLockService.LockAttempt.acquired(null));
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(sampleQuestion()), List.of()));
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(TypedBenchmarkDataset.RagPresetQuestions.class),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.emptyRagRunPayload());
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler().run(task, mutation);

        verify(projectIndexOperationLockService).release(projectId, "lab:auto-reindex", runId);
    }

    @Test
    void run_autoReindex_failsWhenProjectLockAlreadyHeld() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(evaluationRunRagJobContextLoader.loadContext(runId))
                .thenReturn(Optional.of(baseContext(runId, null, projectId, true, false)));
        when(projectIndexOperationLockService.tryAcquire(eq(projectId), Mockito.any(), eq(runId), Mockito.any()))
                .thenReturn(ProjectIndexOperationLockService.LockAttempt.rejected("ALREADY_LOCKED", null));

        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(() -> handler().run(task, mutation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("REINDEX_IN_PROGRESS");

        verify(canonicalPersistence).markRunFailed(runId, "REINDEX_IN_PROGRESS");
    }

    @Test
    void run_classpathBootstrap_executesBeforeAutoReindexLock() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();

        when(evaluationRunRagJobContextLoader.loadContext(runId))
                .thenReturn(Optional.of(baseContext(runId, corpusId, projectId, true, true, userId)));
        when(projectIndexOperationLockService.tryAcquire(eq(projectId), Mockito.any(), eq(runId), Mockito.any()))
                .thenReturn(ProjectIndexOperationLockService.LockAttempt.acquired(null));

        LabCorpusBootstrapResult corpus =
                new LabCorpusBootstrapResult(
                        true,
                        "classpath*:docs/**/*",
                        "PROJECT_SHARED",
                        1,
                        1,
                        0,
                        1,
                        0,
                        0,
                        List.of(UUID.randomUUID()),
                        List.of(),
                        Instant.now(),
                        Instant.now());
        when(labClasspathCorpusBootstrapService.bootstrap(
                        eq(userId), eq(runId), eq(projectId), ArgumentMatchers.any()))
                .thenReturn(corpus);
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(sampleQuestion()), List.of()));
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.emptyRagRunPayload());

        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler().run(task, mutation);

        var order = inOrder(labClasspathCorpusBootstrapService, projectIndexOperationLockService);
        order.verify(labClasspathCorpusBootstrapService)
                .bootstrap(eq(userId), eq(runId), eq(projectId), ArgumentMatchers.any());
        order.verify(projectIndexOperationLockService)
                .tryAcquire(eq(projectId), Mockito.any(), eq(runId), Mockito.any());
        verify(projectIndexOperationLockService).release(projectId, "lab:auto-reindex", runId);
    }

    private static EvaluationRunRagJobContext baseContext(
            UUID runId, UUID corpusId, UUID projectId, boolean autoReindex, boolean bootstrap) {
        return baseContext(runId, corpusId, projectId, autoReindex, bootstrap, UUID.randomUUID());
    }

    private static EvaluationRunRagJobContext baseContext(
            UUID runId,
            UUID corpusId,
            UUID projectId,
            boolean autoReindex,
            boolean bootstrap,
            UUID userId) {
        Map<String, Object> aggregates = new LinkedHashMap<>();
        if (autoReindex) {
            aggregates.put("autoReindexPolicy", Map.of("enabled", true));
        }
        if (bootstrap) {
            aggregates.put(
                    "corpusBootstrapPolicy",
                    Map.of(
                            "enabled",
                            true,
                            "classpathDocsLocation",
                            "classpath*:docs/**/*",
                            "corpusScope",
                            "PROJECT_SHARED",
                            "skipExisting",
                            true,
                            "failOnDocumentError",
                            true));
        }
        return new EvaluationRunRagJobContext(
                runId,
                userId,
                UUID.randomUUID(),
                "RAG_PRESET_END_TO_END",
                corpusId,
                corpusId != null ? "Lab KB" : "",
                projectId,
                bootstrap,
                autoReindex,
                aggregates,
                List.of());
    }

    private static RagPresetQuestion sampleQuestion() {
        return new RagPresetQuestion(
                "rp1",
                "Q?",
                "A",
                Optional.empty(),
                Optional.empty(),
                "",
                List.of(),
                List.of(),
                "",
                false,
                false,
                false,
                false,
                false,
                "");
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = Mockito.mock(AsyncTaskEntity.class);
        when(t.getId()).thenReturn(id);
        when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }
}
