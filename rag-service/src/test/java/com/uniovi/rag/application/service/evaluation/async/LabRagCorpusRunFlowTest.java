package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.application.result.evaluation.RagPresetBenchmarkRunPayload;
import com.uniovi.rag.application.service.async.AsyncTaskCancellationService;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.application.service.evaluation.EvaluationTestFixtures;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkCompletionService;
import com.uniovi.rag.application.service.evaluation.LabCampaignBenchmarkExecutor;
import com.uniovi.rag.application.service.evaluation.LabCorpusReadinessAggregates;
import com.uniovi.rag.application.service.evaluation.LabJobPhaseEmitter;
import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.application.service.evaluation.LabRagReasonCodes;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.lab.LabClasspathCorpusBootstrapService;
import com.uniovi.rag.application.service.evaluation.preset.TypedRagPresetBenchmarkOrchestrator;
import com.uniovi.rag.application.service.knowledge.ProjectIndexOperationLockService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusSummaryDto;
import java.time.Instant;
import java.util.LinkedHashMap;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Corpus-driven RAG run flow: documents ready on evaluation corpus, no user project selection, no NPE on progress
 * emission.
 */
@ExtendWith(MockitoExtension.class)
class LabRagCorpusRunFlowTest {

    @Mock private RagFeatureConfiguration featureConfiguration;
    @Mock private RagImplementationProperties implementationProperties;
    @Mock private EvaluationCanonicalPersistenceService canonicalPersistence;
    @Mock private ExperimentalDatasetResolver experimentalDatasetResolver;
    @Mock private TypedRagPresetBenchmarkOrchestrator typedRagPresetBenchmarkOrchestrator;
    @Mock private AsyncTaskCancellationService cancellationService;
    @Mock private ProjectIndexOperationLockService projectIndexOperationLockService;
    @Mock private LabClasspathCorpusBootstrapService labClasspathCorpusBootstrapService;
    @Mock private LabJobProgressTracker labJobProgressTracker;
    @Mock private LabJobPhaseEmitter labJobPhaseEmitter;
    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private LabCampaignBenchmarkExecutor labCampaignBenchmarkExecutor;
    @Mock private EvaluationRunRagJobContextLoader evaluationRunRagJobContextLoader;
    @Mock private LabBenchmarkCompletionService labBenchmarkCompletionService;
    @Mock private AsyncTaskMutationService mutation;

    @Test
    void ragRun_afterDocumentsReady_doesNotThrowNpe_andDoesNotRequireUserProject() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID corpusId = UUID.fromString("33805fe2-ac11-4d30-b8c5-6539d1ac3268");
        UUID hiddenIndexProjectId = UUID.randomUUID();

        EvaluationCorpusReadinessDto readiness =
                new EvaluationCorpusReadinessDto(
                        corpusId,
                        hiddenIndexProjectId,
                        5,
                        5,
                        0,
                        0,
                        null,
                        null,
                        UUID.randomUUID(),
                        false,
                        null,
                        null,
                        List.of(UUID.randomUUID()),
                        true);
        Map<String, Object> aggregates = new LinkedHashMap<>();
        aggregates.put(
                LabCorpusReadinessAggregates.AGG_KEY,
                LabCorpusReadinessAggregates.toSnapshot(corpusId, readiness));
        aggregates.put("requested_preset_codes", List.of("P0"));

        EvaluationRunRagJobContext ctx =
                new EvaluationRunRagJobContext(
                        runId,
                        UUID.randomUUID(),
                        UUID.fromString("00000000-0000-7000-8000-000000000001"),
                        "RAG_PRESET_BENCHMARK",
                        corpusId,
                        "Lab knowledge base",
                        hiddenIndexProjectId,
                        false,
                        true,
                        aggregates,
                        List.of("P0"));

        when(evaluationRunRagJobContextLoader.loadContext(runId)).thenReturn(Optional.of(ctx));
        when(evaluationCorpusApplicationService.getSummary(ArgumentMatchers.any(), eq(corpusId)))
                .thenReturn(
                        new EvaluationCorpusSummaryDto(
                                corpusId,
                                "Lab knowledge base",
                                "UPLOADED",
                                5,
                                5,
                                0,
                                List.of(),
                                Instant.now(),
                                Instant.now()));
        when(projectIndexOperationLockService.tryAcquire(
                        eq(hiddenIndexProjectId), ArgumentMatchers.any(), eq(runId), ArgumentMatchers.any()))
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
        RagPresetBenchmarkRunPayload eval = EvaluationTestFixtures.emptyRagRunPayload();
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenReturn(eval);

        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getRequestPayload()).thenReturn(Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        EvalRagJobHandler handler =
                new EvalRagJobHandler(
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

        assertThatCode(() -> handler.run(task, mutation)).doesNotThrowAnyException();
        assertThat(handler.taskType()).isEqualTo(AsyncTaskType.EVAL_RAG);
        verify(labJobProgressTracker)
                .emitRagEvaluationAccepted(
                        eq(taskId),
                        eq(runId),
                        eq(corpusId),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.isNull(),
                        ArgumentMatchers.argThat(
                                payload ->
                                        payload != null
                                                && !payload.containsValue(null)
                                                && Map.copyOf(payload).containsKey("readyCount")));
        verify(typedRagPresetBenchmarkOrchestrator)
                .runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
    }

    @Test
    void ragRun_missingCorpus_producesControlledError_notNpe() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        EvaluationRunRagJobContext ctx =
                new EvaluationRunRagJobContext(
                        runId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "RAG_PRESET_BENCHMARK",
                        null,
                        "",
                        null,
                        false,
                        false,
                        Map.of(),
                        List.of("P0"));
        when(evaluationRunRagJobContextLoader.loadContext(runId)).thenReturn(Optional.of(ctx));
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getRequestPayload()).thenReturn(Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        EvalRagJobHandler handler =
                new EvalRagJobHandler(
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

        assertThatThrownBy(() -> handler.run(task, mutation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(LabRagReasonCodes.LAB_RAG_CORPUS_MISSING);
    }
}
