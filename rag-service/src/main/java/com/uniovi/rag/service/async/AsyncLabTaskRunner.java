package com.uniovi.rag.service.async;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.service.async.lab.LabJobHandler;
import com.uniovi.rag.service.async.lab.LabJobPayloadKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatches {@link AsyncTaskEntity} work off the servlet thread to type-specific {@link LabJobHandler}s.
 *
 * <p>Lab jobs are report-only for production state: they do not write {@code rag_configuration},
 * presets, or classifier routing unless a future explicit promotion flow is added (product decision).
 *
 * <p>Payload key constants are shared with {@link AsyncTaskService} for serialized job requests.
 */
@Service
public class AsyncLabTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(AsyncLabTaskRunner.class);

    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_TRAIN_PATH = LabJobPayloadKeys.TRAIN_PATH;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_LABELS_PATH = LabJobPayloadKeys.LABELS_PATH;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_MODEL_NAME = LabJobPayloadKeys.MODEL_NAME;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_LABELS_JSON = LabJobPayloadKeys.LABELS_JSON;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_EPOCHS = LabJobPayloadKeys.EPOCHS;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_BATCH_SIZE = LabJobPayloadKeys.BATCH_SIZE;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_MODEL_ID = LabJobPayloadKeys.MODEL_ID;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_INCLUDE_IMAGES = LabJobPayloadKeys.INCLUDE_IMAGES;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_EVAL_PATH = LabJobPayloadKeys.EVAL_PATH;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_EVAL_FILENAME = LabJobPayloadKeys.EVAL_FILENAME;
    /** @deprecated Use {@link LabJobPayloadKeys} */
    @Deprecated
    public static final String P_OLLAMA_MODEL = LabJobPayloadKeys.OLLAMA_MODEL;

    private final AsyncTaskRepository asyncTaskRepository;
    private final AsyncTaskMutationService mutation;
    private final Map<AsyncTaskType, LabJobHandler> handlersByType;

    public AsyncLabTaskRunner(
            AsyncTaskRepository asyncTaskRepository,
            AsyncTaskMutationService mutation,
            List<LabJobHandler> handlers) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.mutation = mutation;
        this.handlersByType = new EnumMap<>(AsyncTaskType.class);
        for (LabJobHandler h : handlers) {
            if (handlersByType.put(h.taskType(), h) != null) {
                throw new IllegalStateException("Duplicate LabJobHandler for " + h.taskType());
            }
        }
    }

    @Async("labExecutor")
    public void execute(UUID taskId) {
        try {
            AsyncTaskEntity pre = asyncTaskRepository.findById(taskId).orElse(null);
            if (pre == null) {
                return;
            }
            if (pre.getStatus() != AsyncTaskStatus.QUEUED) {
                return;
            }
            mutation.markRunning(taskId);
            AsyncTaskEntity task = asyncTaskRepository.findById(taskId).orElseThrow();
            LabJobHandler handler = handlersByType.get(task.getTaskType());
            if (handler == null) {
                mutation.markFailed(taskId, "No handler for task type: " + task.getTaskType());
                return;
            }
            handler.run(task, mutation);
        } catch (Exception e) {
            log.warn("Async task {} failed: {}", taskId, e.getMessage());
            mutation.markFailed(taskId, shortMessage(e));
        }
    }

    private static String shortMessage(Throwable e) {
        if (e instanceof ResponseStatusException rse) {
            String r = rse.getReason();
            return r != null ? r : (e.getMessage() != null ? e.getMessage() : rse.getStatusCode().toString());
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
