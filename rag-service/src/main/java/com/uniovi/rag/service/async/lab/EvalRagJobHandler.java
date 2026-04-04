package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
class EvalRagJobHandler implements LabJobHandler {

    private final EvaluationService evaluationService;
    private final EvaluationCanonicalPersistenceService canonicalPersistence;

    EvalRagJobHandler(EvaluationService evaluationService, EvaluationCanonicalPersistenceService canonicalPersistence) {
        this.evaluationService = evaluationService;
        this.canonicalPersistence = canonicalPersistence;
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
        try {
            mutation.appendProgressLine(taskId, "Starting RAG evaluation…");
            Map<String, Object> res = evaluationService.evaluate();
            if (evaluationRunId != null) {
                canonicalPersistence.persistLlmJudgeFromEvaluationMap(
                        evaluationRunId, res, BenchmarkKind.RAG_PRESET_END_TO_END);
            }
            mutation.markSucceeded(taskId, res);
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
