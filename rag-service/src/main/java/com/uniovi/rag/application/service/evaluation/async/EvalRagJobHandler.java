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
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.async.AsyncTaskCancellationService;
import com.uniovi.rag.application.service.async.LabJobCancelledException;
import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.result.evaluation.RagPresetBenchmarkRunPayload;
import com.uniovi.rag.application.service.evaluation.EvaluationPayloadMapper;
import com.uniovi.rag.application.service.evaluation.preset.TypedRagPresetBenchmarkOrchestrator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

@Component
class EvalRagJobHandler implements LabJobHandler {

    private final RagFeatureConfiguration featureConfiguration;
    private final RagImplementationProperties implementationProperties;
    private final EvaluationCanonicalPersistenceService canonicalPersistence;
    private final EvaluationRunRepository evaluationRunRepository;
    private final ExperimentalDatasetResolver experimentalDatasetResolver;
    private final TypedRagPresetBenchmarkOrchestrator typedRagPresetBenchmarkOrchestrator;
    private final AsyncTaskCancellationService cancellationService;
    private final ProjectIndexOperationLockService projectIndexOperationLockService;
    private final LabClasspathCorpusBootstrapService labClasspathCorpusBootstrapService;

    EvalRagJobHandler(
            RagFeatureConfiguration featureConfiguration,
            RagImplementationProperties implementationProperties,
            EvaluationCanonicalPersistenceService canonicalPersistence,
            EvaluationRunRepository evaluationRunRepository,
            ExperimentalDatasetResolver experimentalDatasetResolver,
            TypedRagPresetBenchmarkOrchestrator typedRagPresetBenchmarkOrchestrator,
            AsyncTaskCancellationService cancellationService,
            ProjectIndexOperationLockService projectIndexOperationLockService,
            LabClasspathCorpusBootstrapService labClasspathCorpusBootstrapService) {
        this.featureConfiguration = featureConfiguration;
        this.implementationProperties = implementationProperties;
        this.canonicalPersistence = canonicalPersistence;
        this.evaluationRunRepository = evaluationRunRepository;
        this.experimentalDatasetResolver = experimentalDatasetResolver;
        this.typedRagPresetBenchmarkOrchestrator = typedRagPresetBenchmarkOrchestrator;
        this.cancellationService = cancellationService;
        this.projectIndexOperationLockService = projectIndexOperationLockService;
        this.labClasspathCorpusBootstrapService = labClasspathCorpusBootstrapService;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_RAG;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        UUID evaluationRunId = LabJobPayloads.evaluationRunId(task.getRequestPayload());
        LabEvalConcurrency.SERIAL_EVAL.lock();
        UUID lockedProjectId = null;
        boolean lockAcquired = false;
        try {
            if (evaluationRunId == null) {
                throw new IllegalStateException(
                        "This RAG evaluation job is missing its run reference — start a new RAG preset benchmark"
                                + " from the Lab evaluation page with a compatible workbook.");
            }
            mutation.appendProgressLine(taskId, "Resolving typed dataset for RAG_PRESET_END_TO_END…");
            var runWithDataset = evaluationRunRepository.findByIdFetchDataset(evaluationRunId).orElse(null);
            if (runWithDataset != null && corpusBootstrapPolicyEnabled(runWithDataset)) {
                UUID uid =
                        runWithDataset.getUser() != null && runWithDataset.getUser().getId() != null
                                ? runWithDataset.getUser().getId()
                                : null;
                if (uid == null) {
                    throw new IllegalStateException("EVAL_RAG_CORPUS_BOOTSTRAP_REQUIRES_USER");
                }
                try {
                    LabCorpusBootstrapResult corpusResult =
                            labClasspathCorpusBootstrapService.bootstrap(uid, runWithDataset);
                    Map<String, Object> merged =
                            runWithDataset.getAggregatesJson() != null
                                    ? new LinkedHashMap<>(runWithDataset.getAggregatesJson())
                                    : new LinkedHashMap<>();
                    merged.put("corpusBootstrap", corpusResult.toAggregatesMap());
                    runWithDataset.setAggregatesJson(Map.copyOf(merged));
                    evaluationRunRepository.save(runWithDataset);
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
                } catch (IllegalStateException ex) {
                    persistCorpusBootstrapFailure(runWithDataset, ex);
                    evaluationRunRepository.save(runWithDataset);
                    throw ex;
                }
            }
            if (runWithDataset != null && Boolean.TRUE.equals(autoReindexEnabled(runWithDataset))) {
                UUID projectId =
                        runWithDataset.getProject() != null ? runWithDataset.getProject().getId() : null;
                if (projectId == null) {
                    EvaluationRunEntity linked = runWithDataset;
                    if (linked != null
                            && linked.getEvaluationCorpus() != null
                            && linked.getEvaluationCorpus().getIndexProject() != null) {
                        projectId = linked.getEvaluationCorpus().getIndexProject().getId();
                        linked.setProject(linked.getEvaluationCorpus().getIndexProject());
                        evaluationRunRepository.save(linked);
                    }
                }
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
                markLockAcquiredBestEffort(runWithDataset);
                mutation.appendProgressLine(taskId, "Auto-reindex lock acquired for projectId=" + projectId);
            }
            TypedBenchmarkDataset typed = experimentalDatasetResolver.resolve(evaluationRunId);
            if (!(typed instanceof TypedBenchmarkDataset.RagPresetQuestions rag)) {
                throw new IllegalStateException("Resolver returned unexpected payload for RAG_PRESET_END_TO_END");
            }
            String dsId = runWithDataset != null && runWithDataset.getDataset() != null ? String.valueOf(runWithDataset.getDataset().getId()) : null;
            String dsKind = runWithDataset != null && runWithDataset.getDataset() != null ? runWithDataset.getDataset().getExperimentalKind() : null;
            int questionCount = rag.questions() != null ? rag.questions().size() : 0;
            int presetCount = rag.presetCatalog() != null ? rag.presetCatalog().size() : 0;
            Set<RagExperimentalPresetCode> requestedPresets = requestedPresets(evaluationRunId);
            int selectedPresetCount = requestedPresets != null && !requestedPresets.isEmpty() ? requestedPresets.size() : presetCount;
            long expectedItemCount = (long) questionCount * (long) Math.max(1, selectedPresetCount);
            mutation.appendProgressLine(
                    taskId,
                    "RAG dataset resolved: datasetId="
                            + (dsId != null ? dsId : "unknown")
                            + " experimentalKind="
                            + (dsKind != null ? dsKind : "unknown")
                            + " questions="
                            + questionCount
                            + " presets="
                            + presetCount
                            + " selectedPresets="
                            + selectedPresetCount
                            + " expectedItems="
                            + expectedItemCount);

            // Defensive check: never allow demo question ids in RAG preset benchmark payload.
            boolean hasDemoId =
                    rag.questions() != null
                            && rag.questions().stream().anyMatch(q -> q != null && "RAG_Q1".equalsIgnoreCase(q.id()));
            if (hasDemoId) {
                throw new IllegalStateException("Demo dataset_question_id RAG_Q1 detected; aborting benchmark.");
            }
            mutation.appendProgressLine(
                    taskId,
                    "Parsed dataset RAG_PRESET_END_TO_END: " + rag.questions().size() + " questions");
            RagPresetBenchmarkRunPayload res =
                    typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                            evaluationRunId,
                            rag,
                            featureConfiguration,
                            implementationProperties,
                            requestedPresets,
                            (i, n) -> {
                                cancellationService.throwIfCancellationRequested(taskId);
                                mutation.appendProgressLine(taskId, "Running item " + i + "/" + n);
                            },
                            () -> cancellationService.throwIfCancellationRequested(taskId));
            canonicalPersistence.persistLlmJudgeBatch(
                    evaluationRunId,
                    new LlmJudgeEvaluationBatchResult(
                            res.configuration(), res.results(), res.evaluationSummary()),
                    BenchmarkKind.RAG_PRESET_END_TO_END);
            if (res.evaluationSummary() != null && Boolean.TRUE.equals(res.evaluationSummary().cancelled())) {
                mutation.appendProgressLine(taskId, "Cancellation requested by user");
                throw new LabJobCancelledException("Cancellation requested by user");
            }
            mutation.markSucceeded(taskId, EvaluationPayloadMapper.toAsyncPayload(res));
        } catch (BenchmarkDatasetResolutionException e) {
            if (evaluationRunId != null) {
                canonicalPersistence.markRunFailed(evaluationRunId, e.getMessage());
            }
            throw e;
        } catch (RuntimeException e) {
            if (evaluationRunId != null) {
                canonicalPersistence.markRunFailed(evaluationRunId, e.getMessage());
            }
            throw e;
        } finally {
            if (lockAcquired && lockedProjectId != null) {
                projectIndexOperationLockService.release(lockedProjectId, "lab:auto-reindex", evaluationRunId);
            }
            LabEvalConcurrency.SERIAL_EVAL.unlock();
        }
    }

    private static boolean corpusBootstrapPolicyEnabled(EvaluationRunEntity run) {
        if (run == null || run.getAggregatesJson() == null) {
            return false;
        }
        Object policyObj = run.getAggregatesJson().get("corpusBootstrapPolicy");
        if (!(policyObj instanceof Map<?, ?> m)) {
            return false;
        }
        return Boolean.TRUE.equals(m.get("enabled"));
    }

    private static boolean autoReindexEnabled(EvaluationRunEntity run) {
        if (run == null || run.getAggregatesJson() == null) {
            return false;
        }
        Object policyObj = run.getAggregatesJson().get("autoReindexPolicy");
        if (!(policyObj instanceof Map<?, ?> m)) {
            return false;
        }
        Object enabled = m.get("enabled");
        return Boolean.TRUE.equals(enabled);
    }

    /** Persists machine-readable bootstrap failure evidence before the job aborts (async handler catch also marks run failed). */
    private static void persistCorpusBootstrapFailure(EvaluationRunEntity run, IllegalStateException ex) {
        Map<String, Object> merged =
                run.getAggregatesJson() != null ? new LinkedHashMap<>(run.getAggregatesJson()) : new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("reasonCode", LabCorpusBootstrapErrors.reasonCodeFromMessage(ex.getMessage()));
        payload.put("message", ex.getMessage());
        merged.put("corpusBootstrap", payload);
        run.setAggregatesJson(Map.copyOf(merged));
    }

    private void markLockAcquiredBestEffort(EvaluationRunEntity run) {
        if (run == null) {
            return;
        }
        Map<String, Object> agg = run.getAggregatesJson() != null ? new LinkedHashMap<>(run.getAggregatesJson()) : new LinkedHashMap<>();
        agg.put("autoReindexLockAcquired", Boolean.TRUE);
        run.setAggregatesJson(Map.copyOf(agg));
        evaluationRunRepository.save(run);
    }

    @SuppressWarnings("unchecked")
    private Set<RagExperimentalPresetCode> requestedPresets(UUID evaluationRunId) {
        List<?> raw = evaluationRunRepository.findById(evaluationRunId)
                .map(run -> run.getAggregatesJson() != null ? run.getAggregatesJson().get("requested_preset_codes") : null)
                .filter(List.class::isInstance)
                .map(List.class::cast)
                .orElse(List.of());
        Set<RagExperimentalPresetCode> out = new LinkedHashSet<>();
        for (Object row : raw) {
            RagExperimentalPresetCode.tryParse(String.valueOf(row)).ifPresent(out::add);
        }
        return out;
    }
}
