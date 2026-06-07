package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.application.service.evaluation.BenchmarkDatasetResolutionException;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.async.AsyncTaskCancellationService;
import com.uniovi.rag.application.service.async.LabJobCancelledException;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.application.service.evaluation.EvaluationPayloadMapper;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkCompletionService;
import com.uniovi.rag.application.service.evaluation.LabCampaignBenchmarkExecutor;
import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.application.service.evaluation.baseline.ModelBaselineEvaluationOrchestrator;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
class EvalLlmJobHandler implements LabJobHandler {

    private final EvaluationCanonicalPersistenceService canonicalPersistence;
    private final ExperimentalDatasetResolver experimentalDatasetResolver;
    private final ModelBaselineEvaluationOrchestrator modelBaselineEvaluationOrchestrator;
    private final AsyncTaskCancellationService cancellationService;
    private final LabJobProgressTracker labJobProgressTracker;
    private final EvaluationRunRepository evaluationRunRepository;
    private final LabCampaignBenchmarkExecutor labCampaignBenchmarkExecutor;
    private final LabBenchmarkCompletionService labBenchmarkCompletionService;

    EvalLlmJobHandler(
            EvaluationCanonicalPersistenceService canonicalPersistence,
            ExperimentalDatasetResolver experimentalDatasetResolver,
            ModelBaselineEvaluationOrchestrator modelBaselineEvaluationOrchestrator,
            AsyncTaskCancellationService cancellationService,
            LabJobProgressTracker labJobProgressTracker,
            EvaluationRunRepository evaluationRunRepository,
            LabCampaignBenchmarkExecutor labCampaignBenchmarkExecutor,
            LabBenchmarkCompletionService labBenchmarkCompletionService) {
        this.canonicalPersistence = canonicalPersistence;
        this.experimentalDatasetResolver = experimentalDatasetResolver;
        this.modelBaselineEvaluationOrchestrator = modelBaselineEvaluationOrchestrator;
        this.cancellationService = cancellationService;
        this.labJobProgressTracker = labJobProgressTracker;
        this.evaluationRunRepository = evaluationRunRepository;
        this.labCampaignBenchmarkExecutor = labCampaignBenchmarkExecutor;
        this.labBenchmarkCompletionService = labBenchmarkCompletionService;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_LLM;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID campaignId = LabJobPayloads.campaignId(task.getRequestPayload());
        LabEvalConcurrency.SERIAL_EVAL.lock();
        try {
            if (campaignId != null) {
                labCampaignBenchmarkExecutor.runCampaign(
                        task, mutation, campaignId, this::runSingleRun);
                return;
            }
            UUID evaluationRunId = LabJobPayloads.evaluationRunId(task.getRequestPayload());
            if (evaluationRunId == null) {
                throw new IllegalStateException(
                        "This LLM evaluation job is missing its run reference — start a new LLM benchmark from the"
                                + " Lab evaluation page with a compatible workbook.");
            }
            Map<String, Object> payload = runSingleRun(task, mutation, evaluationRunId);
            labBenchmarkCompletionService.completeRun(mutation, task.getId(), evaluationRunId, payload);
        } finally {
            LabEvalConcurrency.SERIAL_EVAL.unlock();
        }
    }

    Map<String, Object> runSingleRun(AsyncTaskEntity task, AsyncTaskMutationService mutation, UUID evaluationRunId) {
        UUID taskId = task.getId();
        try {
            mutation.appendProgressLine(taskId, "Resolving typed dataset for LLM_JUDGE_QA…");
            TypedBenchmarkDataset typed = experimentalDatasetResolver.resolve(evaluationRunId);
            if (!(typed instanceof TypedBenchmarkDataset.LlmQuestions llm)) {
                throw new IllegalStateException("Resolver returned unexpected payload for LLM_JUDGE_QA");
            }
            int runTotal = llm.questions().size();
            String modelId =
                    evaluationRunRepository
                            .findById(evaluationRunId)
                            .map(EvaluationRunEntity::getLlmModelId)
                            .orElse(null);
            labJobProgressTracker.emitRunStarted(taskId, evaluationRunId, runTotal, null, modelId, null);
            mutation.appendProgressLine(
                    taskId,
                    "Parsed dataset LLM_JUDGE_QA: " + runTotal + " questions");
            LlmJudgeEvaluationBatchResult res =
                    modelBaselineEvaluationOrchestrator.runLlmJudgeBaseline(
                            evaluationRunId,
                            llm,
                            labJobProgressTracker.itemProgressCallback(
                                    taskId,
                                    evaluationRunId,
                                    runTotal,
                                    null,
                                    modelId,
                                    null,
                                    () -> cancellationService.throwIfCancellationRequested(taskId)),
                            () -> cancellationService.throwIfCancellationRequested(taskId));
            canonicalPersistence.persistLlmJudgeBatch(evaluationRunId, res, BenchmarkKind.LLM_JUDGE_QA);
            if (res.evaluationSummary() != null && Boolean.TRUE.equals(res.evaluationSummary().cancelled())) {
                mutation.appendProgressLine(taskId, "Cancellation requested by user");
                throw new LabJobCancelledException("Cancellation requested by user");
            }
            return EvaluationPayloadMapper.toAsyncPayload(res);
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
        }
    }
}
