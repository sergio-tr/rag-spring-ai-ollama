package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.application.service.evaluation.BenchmarkDatasetResolutionException;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.application.service.evaluation.lab.LabClasspathCorpusBootstrapService;
import com.uniovi.rag.application.service.evaluation.lab.LabCorpusBootstrapErrors;
import com.uniovi.rag.application.service.evaluation.lab.LabCorpusBootstrapResult;
import com.uniovi.rag.application.service.knowledge.ProjectIndexOperationLockService;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.async.AsyncTaskCancellationService;
import com.uniovi.rag.application.service.async.LabJobCancelledException;
import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkCompletionService;
import com.uniovi.rag.application.service.evaluation.LabCampaignBenchmarkExecutor;
import com.uniovi.rag.application.service.evaluation.LabJobPhaseEmitter;
import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.result.evaluation.RagPresetBenchmarkRunPayload;
import com.uniovi.rag.application.service.evaluation.EvaluationPayloadMapper;
import com.uniovi.rag.application.service.evaluation.LabRagRunDiagnostics;
import com.uniovi.rag.application.service.evaluation.metrics.DatasetQuestionSubsetSupport;
import com.uniovi.rag.application.service.evaluation.preset.TypedRagPresetBenchmarkOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
class EvalRagJobHandler implements LabJobHandler {

    private static final Logger log = LoggerFactory.getLogger(EvalRagJobHandler.class);

    private final RagFeatureConfiguration featureConfiguration;
    private final RagImplementationProperties implementationProperties;
    private final EvaluationCanonicalPersistenceService canonicalPersistence;
    private final ExperimentalDatasetResolver experimentalDatasetResolver;
    private final TypedRagPresetBenchmarkOrchestrator typedRagPresetBenchmarkOrchestrator;
    private final AsyncTaskCancellationService cancellationService;
    private final ProjectIndexOperationLockService projectIndexOperationLockService;
    private final LabClasspathCorpusBootstrapService labClasspathCorpusBootstrapService;
    private final LabJobProgressTracker labJobProgressTracker;
    private final LabJobPhaseEmitter labJobPhaseEmitter;
    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final LabCampaignBenchmarkExecutor labCampaignBenchmarkExecutor;
    private final EvaluationRunRagJobContextLoader evaluationRunRagJobContextLoader;
    private final LabBenchmarkCompletionService labBenchmarkCompletionService;
    private final EvaluationResultRepository evaluationResultRepository;

    EvalRagJobHandler(
            RagFeatureConfiguration featureConfiguration,
            RagImplementationProperties implementationProperties,
            EvaluationCanonicalPersistenceService canonicalPersistence,
            ExperimentalDatasetResolver experimentalDatasetResolver,
            TypedRagPresetBenchmarkOrchestrator typedRagPresetBenchmarkOrchestrator,
            AsyncTaskCancellationService cancellationService,
            ProjectIndexOperationLockService projectIndexOperationLockService,
            LabClasspathCorpusBootstrapService labClasspathCorpusBootstrapService,
            LabJobProgressTracker labJobProgressTracker,
            LabJobPhaseEmitter labJobPhaseEmitter,
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            LabCampaignBenchmarkExecutor labCampaignBenchmarkExecutor,
            EvaluationRunRagJobContextLoader evaluationRunRagJobContextLoader,
            LabBenchmarkCompletionService labBenchmarkCompletionService,
            EvaluationResultRepository evaluationResultRepository) {
        this.featureConfiguration = featureConfiguration;
        this.implementationProperties = implementationProperties;
        this.canonicalPersistence = canonicalPersistence;
        this.experimentalDatasetResolver = experimentalDatasetResolver;
        this.typedRagPresetBenchmarkOrchestrator = typedRagPresetBenchmarkOrchestrator;
        this.cancellationService = cancellationService;
        this.projectIndexOperationLockService = projectIndexOperationLockService;
        this.labClasspathCorpusBootstrapService = labClasspathCorpusBootstrapService;
        this.labJobProgressTracker = labJobProgressTracker;
        this.labJobPhaseEmitter = labJobPhaseEmitter;
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.labCampaignBenchmarkExecutor = labCampaignBenchmarkExecutor;
        this.evaluationRunRagJobContextLoader = evaluationRunRagJobContextLoader;
        this.labBenchmarkCompletionService = labBenchmarkCompletionService;
        this.evaluationResultRepository = evaluationResultRepository;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_RAG;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID campaignId = LabJobPayloads.campaignId(task.getRequestPayload());
        if (campaignId != null) {
            LabEvalConcurrency.SERIAL_EVAL.lock();
            try {
                labCampaignBenchmarkExecutor.runCampaign(
                        task, mutation, campaignId, (t, m, runId) -> runSingleRagRun(t, m, runId));
            } finally {
                LabEvalConcurrency.SERIAL_EVAL.unlock();
            }
            return;
        }
        UUID taskId = task.getId();
        UUID evaluationRunId = LabJobPayloads.evaluationRunId(task.getRequestPayload());
        LabEvalConcurrency.SERIAL_EVAL.lock();
        try {
            if (evaluationRunId == null) {
                throw new IllegalStateException(
                        "This RAG evaluation job is missing its run reference — start a new RAG preset benchmark"
                                + " from the Lab evaluation page with a compatible workbook.");
            }
            Map<String, Object> payload = runSingleRagRun(task, mutation, evaluationRunId);
            labBenchmarkCompletionService.completeRun(mutation, taskId, evaluationRunId, payload);
        } finally {
            LabEvalConcurrency.SERIAL_EVAL.unlock();
        }
    }

    private Map<String, Object> runSingleRagRun(
            AsyncTaskEntity task, AsyncTaskMutationService mutation, UUID evaluationRunId) {
        UUID taskId = task.getId();
        UUID lockedProjectId = null;
        boolean lockAcquired = false;
        try {
            EvaluationRunRagJobContext ctx =
                    evaluationRunRagJobContextLoader
                            .loadContext(evaluationRunId)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "evaluation_run not found: " + evaluationRunId));

            LabRagRunDiagnostics.logHandlerStart(
                    log,
                    taskId,
                    evaluationRunId,
                    ctx.corpusId(),
                    ctx.projectId(),
                    ctx.requestedPresetCodes(),
                    ctx.aggregatesJson());
            LabRagRunDiagnostics.requireCorpusId(ctx.corpusId());
            LabRagRunDiagnostics.requireConfigPreflightPresent(ctx.aggregatesJson());

            if (ctx.corpusBootstrapEnabled()) {
                try {
                    LabCorpusBootstrapResult corpusResult =
                            labClasspathCorpusBootstrapService.bootstrap(
                                    ctx.userId(),
                                    ctx.runId(),
                                    ctx.projectId(),
                                    ctx.aggregatesJson());
                    evaluationRunRagJobContextLoader.mergeAggregatesJson(
                            evaluationRunId, Map.of("corpusBootstrap", corpusResult.toAggregatesMap()));
                    mutation.appendProgressLine(
                            taskId,
                            "Classpath corpus bootstrap: found="
                                    + corpusResult.discoveredCount()
                                    + " created="
                                    + corpusResult.createdCount()
                                    + " reused="
                                    + corpusResult.reusedCount()
                                    + " skipped="
                                    + corpusResult.skippedCount()
                                    + " failed="
                                    + corpusResult.failedCount()
                                    + " ready="
                                    + corpusResult.readyCount()
                                    + " scope="
                                    + corpusResult.corpusScope()
                                    + " pattern="
                                    + corpusResult.classpathDocsLocation());
                    LabRagRunDiagnostics.logStage(
                            log,
                            "corpus_bootstrap",
                            LabRagRunDiagnostics.fields(
                                    "runId", evaluationRunId,
                                    "taskId", taskId,
                                    "corpusId", ctx.corpusId(),
                                    "documentCount", corpusResult.discoveredCount(),
                                    "readyDocumentCount", corpusResult.readyCount(),
                                    "reasonCode",
                                    corpusResult.failedCount() > 0 ? "BOOTSTRAP_PARTIAL_FAILURE" : "OK"));
                } catch (IllegalStateException ex) {
                    evaluationRunRagJobContextLoader.mergeAggregatesJson(
                            evaluationRunId,
                            Map.of(
                                    "corpusBootstrap",
                                    Map.of(
                                            "success",
                                            false,
                                            "reasonCode",
                                            LabCorpusBootstrapErrors.reasonCodeFromMessage(ex.getMessage()),
                                            "message",
                                            ex.getMessage())));
                    throw ex;
                }
            }

            if (ctx.autoReindexEnabled()) {
                UUID projectId = ctx.projectId();
                if (projectId == null) {
                    throw new IllegalStateException("AUTO_REINDEX_REQUIRES_CORPUS_INDEX_CONTEXT");
                }
                lockedProjectId = projectId;
                var attempt =
                        projectIndexOperationLockService.tryAcquire(
                                projectId,
                                "lab:auto-reindex",
                                evaluationRunId,
                                "RAG_PRESET_END_TO_END autoReindex");
                if (!attempt.acquired()) {
                    throw new IllegalStateException("REINDEX_IN_PROGRESS");
                }
                lockAcquired = true;
                evaluationRunRagJobContextLoader.markAutoReindexLockAcquired(evaluationRunId);
                labJobPhaseEmitter.emitAutoReindexLock(taskId, evaluationRunId, projectId);
            }

            TypedBenchmarkDataset typed = experimentalDatasetResolver.resolve(evaluationRunId);
            if (!(typed instanceof TypedBenchmarkDataset.RagPresetQuestions rag)) {
                throw new IllegalStateException("Resolver returned unexpected payload for RAG_PRESET_END_TO_END");
            }

            Set<RagExperimentalPresetCode> requestedPresets = requestedPresets(ctx);
            int questionCount = rag.questions() != null ? rag.questions().size() : 0;
            int presetCount = rag.presetCatalog() != null ? rag.presetCatalog().size() : 0;
            LabRagRunDiagnostics.requireDatasetItems(questionCount);
            LabRagRunDiagnostics.requirePresetsResolvable(
                    requestedPresets.size(), presetCount, ctx.requestedPresetCodes());
            int selectedPresetCount =
                    requestedPresets.isEmpty() ? presetCount : requestedPresets.size();
            int runTotalItems =
                    DatasetQuestionSubsetSupport.resolvedExpectedItemCount(
                            ctx.aggregatesJson(), questionCount, selectedPresetCount);
            String presetCode =
                    requestedPresets.isEmpty() ? null : requestedPresets.iterator().next().name();

            UUID campaignId = LabJobPayloads.campaignId(task.getRequestPayload());
            Map<String, Object> corpusReadinessPayload =
                    LabRagRunDiagnostics.copyCorpusReadinessFromAggregates(
                            log, evaluationRunId, taskId, ctx.aggregatesJson());
            LabRagRunDiagnostics.logStage(
                    log,
                    "corpus_readiness_loaded",
                    LabRagRunDiagnostics.fields(
                            "runId", evaluationRunId,
                            "taskId", taskId,
                            "corpusId", ctx.corpusId(),
                            "documentCount", corpusReadinessPayload.get("documentCount"),
                            "readyDocumentCount", corpusReadinessPayload.get("readyCount"),
                            "reasonCode",
                            corpusReadinessPayload.getOrDefault("primaryBlocker", "OK")));
            labJobProgressTracker.emitRagEvaluationAccepted(
                    taskId,
                    evaluationRunId,
                    ctx.corpusId(),
                    ctx.datasetId(),
                    campaignId,
                    corpusReadinessPayload);
            labJobPhaseEmitter.emitDatasetResolved(
                    taskId,
                    evaluationRunId,
                    ctx.datasetId(),
                    "RAG_PRESET_END_TO_END",
                    questionCount,
                    selectedPresetCount);
            if (ctx.corpusId() != null) {
                var corpusSummary =
                        evaluationCorpusApplicationService.getSummary(ctx.userId(), ctx.corpusId());
                labJobPhaseEmitter.emitKnowledgeBaseChecked(
                        taskId,
                        evaluationRunId,
                        ctx.corpusId(),
                        corpusSummary.readyCount(),
                        corpusSummary.documentCount());
            }
            labJobProgressTracker.emitRunStarted(
                    taskId, evaluationRunId, runTotalItems, null, null, presetCode);
            evaluationRunRagJobContextLoader.mergeAggregatesJson(
                    evaluationRunId,
                    Map.of(
                            "expectedItemCount",
                            runTotalItems,
                            "plannedQuestionCount",
                            questionCount,
                            "plannedPresetCount",
                            selectedPresetCount));

            boolean hasDemoId =
                    rag.questions() != null
                            && rag.questions().stream().anyMatch(q -> q != null && "RAG_Q1".equalsIgnoreCase(q.id()));
            if (hasDemoId) {
                throw new IllegalStateException("Demo dataset_question_id RAG_Q1 detected; aborting benchmark.");
            }
            LabRagRunDiagnostics.logStage(
                    log,
                    "benchmark_execution_start",
                    LabRagRunDiagnostics.fields(
                            "runId", evaluationRunId,
                            "taskId", taskId,
                            "corpusId", ctx.corpusId(),
                            "presetKey", presetCode,
                            "plannedPresetCount", selectedPresetCount,
                            "questionCount", questionCount));
            RagPresetBenchmarkRunPayload res =
                    typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                            evaluationRunId,
                            rag,
                            featureConfiguration,
                            implementationProperties,
                            requestedPresets,
                            labJobProgressTracker.itemProgressCallback(
                                    taskId,
                                    evaluationRunId,
                                    runTotalItems,
                                    null,
                                    null,
                                    presetCode,
                                    () -> cancellationService.throwIfCancellationRequested(taskId)),
                            () -> cancellationService.throwIfCancellationRequested(taskId));
            canonicalPersistence.persistLlmJudgeBatch(
                    evaluationRunId,
                    new LlmJudgeEvaluationBatchResult(
                            res.configuration(), res.results(), res.evaluationSummary()),
                    BenchmarkKind.RAG_PRESET_END_TO_END);
            int expectedItems = res.results() != null ? res.results().size() : 0;
            int persistedItems =
                    evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(evaluationRunId).size();
            if (expectedItems > 0 && persistedItems < expectedItems) {
                log.warn(
                        "rag_preset_persistence_mismatch runId={} presetCode={} itemCount={} persistedCount={} errorCode=PERSISTENCE_MISMATCH",
                        evaluationRunId,
                        presetCode,
                        expectedItems,
                        persistedItems);
                canonicalPersistence.markRunFailed(evaluationRunId, "RAG preset persistence mismatch");
                throw new IllegalStateException("RAG preset persistence mismatch for run " + evaluationRunId);
            }
            if (res.evaluationSummary() != null && Boolean.TRUE.equals(res.evaluationSummary().cancelled())) {
                mutation.appendProgressLine(taskId, "Cancellation requested by user");
                throw new LabJobCancelledException("Cancellation requested by user");
            }
            return EvaluationPayloadMapper.toAsyncPayload(res);
        } catch (BenchmarkDatasetResolutionException e) {
            canonicalPersistence.markRunFailed(evaluationRunId, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            canonicalPersistence.markRunFailed(evaluationRunId, e.getMessage());
            throw e;
        } finally {
            if (lockAcquired && lockedProjectId != null) {
                projectIndexOperationLockService.release(lockedProjectId, "lab:auto-reindex", evaluationRunId);
            }
        }
    }

    private static Set<RagExperimentalPresetCode> requestedPresets(EvaluationRunRagJobContext ctx) {
        Set<RagExperimentalPresetCode> out = new LinkedHashSet<>();
        for (String row : ctx.requestedPresetCodes()) {
            RagExperimentalPresetCode.tryParse(row).ifPresent(out::add);
        }
        return out;
    }
}
