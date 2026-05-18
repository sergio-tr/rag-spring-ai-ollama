package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.lab.LabClasspathCorpusBootstrapService;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.application.service.knowledge.ProjectIndexOperationLockService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.evaluation.lab.LabCorpusBootstrapResult;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.service.async.AsyncTaskCancellationService;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.preset.TypedRagPresetBenchmarkOrchestrator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private EvaluationRunRepository evaluationRunRepository;

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

    private EvalRagJobHandler handler() {
        return new EvalRagJobHandler(
                featureConfiguration,
                implementationProperties,
                canonicalPersistence,
                evaluationRunRepository,
                experimentalDatasetResolver,
                typedRagPresetBenchmarkOrchestrator,
                cancellationService,
                projectIndexOperationLockService,
                labClasspathCorpusBootstrapService);
    }

    @Test
    void taskType_isEvalRag() {
        assertThat(handler().taskType()).isEqualTo(AsyncTaskType.EVAL_RAG);
    }

    @Test
    void run_withRunId_usesTypedRag_neverLegacyEvaluate() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        RagPresetQuestion q =
                new RagPresetQuestion(
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
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()));
        Map<String, Object> eval = Map.of("k", "v");
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
        verify(canonicalPersistence).persistLlmJudgeFromEvaluationMap(runId, eval, BenchmarkKind.RAG_PRESET_END_TO_END);
        verify(mutation).markSucceeded(taskId, eval);
        verifyNoInteractions(labClasspathCorpusBootstrapService);
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
        RagPresetQuestion q =
                new RagPresetQuestion(
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

        // Enable autoReindex via aggregates_json payload.
        EvaluationRunEntity run = new EvaluationRunEntity();
        var project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        run.setProject(project);
        run.setAggregatesJson(
                Map.of(
                        "autoReindexPolicy",
                        Map.of(
                                "enabled", true,
                                "allowActiveSnapshotMutation", true,
                                "reuseCompatibleActiveSnapshot", true,
                                "failOnReindexFailure", true)));
        when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));

        when(projectIndexOperationLockService.tryAcquire(eq(projectId), Mockito.any(), eq(runId), Mockito.any()))
                .thenReturn(ProjectIndexOperationLockService.LockAttempt.acquired(null));

        RagPresetQuestion q =
                new RagPresetQuestion(
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

        EvaluationRunEntity run = new EvaluationRunEntity();
        var project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        run.setProject(project);
        run.setAggregatesJson(Map.of("autoReindexPolicy", Map.of("enabled", true)));
        when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));

        when(projectIndexOperationLockService.tryAcquire(eq(projectId), Mockito.any(), eq(runId), Mockito.any()))
                .thenReturn(ProjectIndexOperationLockService.LockAttempt.acquired(null));

        RagPresetQuestion q =
                new RagPresetQuestion(
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
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()));
        Map<String, Object> eval = Map.of("k", "v", "evaluation_summary", Map.of());
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

        verify(projectIndexOperationLockService).release(projectId, "lab:auto-reindex", runId);
    }

    @Test
    void run_autoReindex_failsWhenProjectLockAlreadyHeld() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        EvaluationRunEntity run = new EvaluationRunEntity();
        var project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        run.setProject(project);
        run.setAggregatesJson(Map.of("autoReindexPolicy", Map.of("enabled", true)));
        when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));

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

        EvaluationRunEntity run = new EvaluationRunEntity();
        var project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        run.setProject(project);
        UserEntity user = Mockito.mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        run.setUser(user);
        run.setAggregatesJson(
                Map.of(
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
                                true),
                        "autoReindexPolicy",
                        Map.of("enabled", true)));
        when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));
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
        when(labClasspathCorpusBootstrapService.bootstrap(eq(userId), Mockito.any())).thenReturn(corpus);

        RagPresetQuestion q =
                new RagPresetQuestion(
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
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()));
        Map<String, Object> eval = Map.of("k", "v", "evaluation_summary", Map.of());
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenReturn(eval);

        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler().run(task, mutation);

        var order = inOrder(labClasspathCorpusBootstrapService, projectIndexOperationLockService);
        order.verify(labClasspathCorpusBootstrapService).bootstrap(eq(userId), Mockito.any());
        order.verify(projectIndexOperationLockService)
                .tryAcquire(eq(projectId), Mockito.any(), eq(runId), Mockito.any());
        verify(projectIndexOperationLockService).release(projectId, "lab:auto-reindex", runId);
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = Mockito.mock(AsyncTaskEntity.class);
        when(t.getId()).thenReturn(id);
        when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }
}
