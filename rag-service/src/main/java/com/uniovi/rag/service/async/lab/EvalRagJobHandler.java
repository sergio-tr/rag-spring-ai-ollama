package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
class EvalRagJobHandler implements LabJobHandler {

    private final EvaluationService evaluationService;

    EvalRagJobHandler(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_RAG;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        LabEvalConcurrency.SERIAL_EVAL.lock();
        try {
            mutation.appendProgressLine(taskId, "Starting RAG evaluation…");
            Map<String, Object> res = evaluationService.evaluate();
            mutation.markSucceeded(taskId, res);
        } finally {
            LabEvalConcurrency.SERIAL_EVAL.unlock();
        }
    }
}
