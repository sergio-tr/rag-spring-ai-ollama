package com.uniovi.rag.infrastructure.observability;

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
        return m;
    }
}
