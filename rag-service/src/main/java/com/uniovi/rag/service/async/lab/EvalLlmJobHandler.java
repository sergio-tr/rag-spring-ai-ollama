package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.application.service.evaluation.BenchmarkDatasetResolutionException;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.async.AsyncTaskCancellationService;
import com.uniovi.rag.service.async.LabJobCancelledException;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.baseline.ModelBaselineEvaluationOrchestrator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
class EvalLlmJobHandler implements LabJobHandler {

    private final EvaluationCanonicalPersistenceService canonicalPersistence;
    private final ExperimentalDatasetResolver experimentalDatasetResolver;
    private final ModelBaselineEvaluationOrchestrator modelBaselineEvaluationOrchestrator;
    private final AsyncTaskCancellationService cancellationService;

    EvalLlmJobHandler(
            EvaluationCanonicalPersistenceService canonicalPersistence,
            ExperimentalDatasetResolver experimentalDatasetResolver,
            ModelBaselineEvaluationOrchestrator modelBaselineEvaluationOrchestrator,
            AsyncTaskCancellationService cancellationService) {
        this.canonicalPersistence = canonicalPersistence;
        this.experimentalDatasetResolver = experimentalDatasetResolver;
        this.modelBaselineEvaluationOrchestrator = modelBaselineEvaluationOrchestrator;
        this.cancellationService = cancellationService;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_LLM;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        UUID evaluationRunId = LabJobPayloads.evaluationRunId(task.getRequestPayload());
        LabEvalConcurrency.SERIAL_EVAL.lock();
        try {
            if (evaluationRunId == null) {
                throw new IllegalStateException(
                        "EVAL_LLM jobs require evaluation_run_id on the async payload; "
                                + "enqueue via POST /lab/benchmarks/LLM_JUDGE_QA/runs with a typed evaluation_dataset.");
            }
            mutation.appendProgressLine(taskId, "Resolving typed dataset for LLM_JUDGE_QA…");
            TypedBenchmarkDataset typed = experimentalDatasetResolver.resolve(evaluationRunId);
            if (!(typed instanceof TypedBenchmarkDataset.LlmQuestions llm)) {
                throw new IllegalStateException("Resolver returned unexpected payload for LLM_JUDGE_QA");
            }
            mutation.appendProgressLine(
                    taskId,
                    "Parsed dataset LLM_JUDGE_QA: " + llm.questions().size() + " questions");
            Map<String, Object> res =
                    modelBaselineEvaluationOrchestrator.runLlmJudgeBaseline(
                            evaluationRunId,
                            llm,
                            (i, n) -> {
                                cancellationService.throwIfCancellationRequested(taskId);
                                mutation.appendProgressLine(taskId, "Running item " + i + "/" + n);
                            },
                            () -> cancellationService.throwIfCancellationRequested(taskId));
            canonicalPersistence.persistLlmJudgeFromEvaluationMap(
                    evaluationRunId, res, BenchmarkKind.LLM_JUDGE_QA);
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) res.get("evaluation_summary");
            if (summary != null && Boolean.TRUE.equals(summary.get("cancelled"))) {
                mutation.appendProgressLine(taskId, "Cancellation requested by user");
                throw new LabJobCancelledException("Cancellation requested by user");
            }
            mutation.markSucceeded(taskId, res);
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
            LabEvalConcurrency.SERIAL_EVAL.unlock();
        }
    }
}
