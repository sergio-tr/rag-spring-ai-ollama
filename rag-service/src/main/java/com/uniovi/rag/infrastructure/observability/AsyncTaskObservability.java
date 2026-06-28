package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.service.evaluation.async.LabJobPayloadKeys;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps {@link AsyncTaskType} to observability subsystem labels and span attributes for async jobs.
 */
public final class AsyncTaskObservability {

    private AsyncTaskObservability() {}

    /**
     * Subsystem label for metrics and {@code rag.subsystem} span tag (plan: product, lab, classifier, admin, account).
     */
    public static String subsystem(AsyncTaskType type) {
        return switch (type) {
            case CHAT_MESSAGE -> "product";
            case EVAL_LLM, EVAL_RAG, EVAL_EMBEDDING_RETRIEVAL -> "lab";
            case CLASSIFIER_TRAIN, CLASSIFIER_EVAL -> "classifier";
            case OLLAMA_PULL -> "admin";
            case ACCOUNT_EXPORT, ACCOUNT_DELETION -> "account";
        };
    }

    /**
     * Input attributes for {@link ObservabilitySupport#runWithSpan} around async task execution.
     */
    public static Map<String, String> spanAttributes(AsyncTaskEntity task) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rag.task_id", task.getId().toString());
        m.put("rag.task_type", task.getTaskType().name());
        m.put("rag.subsystem", subsystem(task.getTaskType()));
        UUID uid = task.getUser() != null ? task.getUser().getId() : null;
        if (uid != null) {
            m.put("rag.user_id", uid.toString());
        }
        if (task.getProject() != null) {
            m.put("rag.project_id", task.getProject().getId().toString());
        }
        UUID evaluationRunId = parseEvaluationRunId(task.getRequestPayload());
        if (evaluationRunId != null) {
            m.put("rag.evaluation_run_id", evaluationRunId.toString());
            m.put("runId", evaluationRunId.toString());
        }
        return m;
    }

    private static UUID parseEvaluationRunId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object raw = payload.get(LabJobPayloadKeys.EVALUATION_RUN_ID);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
