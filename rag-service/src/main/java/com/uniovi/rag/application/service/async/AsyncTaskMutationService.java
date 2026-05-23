package com.uniovi.rag.application.service.async;

import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.interfaces.rest.support.UserFacingErrorSanitizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncTaskMutationService {

    private static final int USER_ERROR_MESSAGE_MAX_LEN = 600;

    private final AsyncTaskRepository asyncTaskRepository;
    private final LabJobEventService labJobEventService;

    public AsyncTaskMutationService(AsyncTaskRepository asyncTaskRepository, LabJobEventService labJobEventService) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.labJobEventService = labJobEventService;
    }

    @Transactional
    public void markRunning(UUID taskId) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        Instant now = Instant.now();
        e.setStatus(AsyncTaskStatus.RUNNING);
        e.setStartedAt(now);
        e.setUpdatedAt(now);
        appendProgress(e, "Running…");
        asyncTaskRepository.save(e);
        recordEvent(e, LabJobEventType.STARTED, "Job started");
    }

    @Transactional
    public void appendProgressLine(UUID taskId, String line) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        appendProgress(e, line);
        e.setUpdatedAt(Instant.now());
        asyncTaskRepository.save(e);
        recordEvent(e, LabJobEventType.PROGRESS, line);
    }

    @Transactional
    public void markSucceeded(UUID taskId, Map<String, Object> result) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        Instant now = Instant.now();
        e.setStatus(AsyncTaskStatus.SUCCEEDED);
        e.setResultJson(result);
        e.setCompletedAt(now);
        e.setUpdatedAt(now);
        appendProgress(e, "Finished successfully.");
        asyncTaskRepository.save(e);
        recordEvent(e, LabJobEventType.COMPLETED, "Job completed successfully");
    }

    @Transactional
    public void markFailed(UUID taskId, String message) {
        markFailed(taskId, message, null);
    }

    /**
     * @param failureCode stable ErrorCode enum name (or similar) for API consumers; optional.
     */
    @Transactional
    public void markFailed(UUID taskId, String message, String failureCode) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        Instant now = Instant.now();
        e.setStatus(AsyncTaskStatus.FAILED);
        String safeMsg =
                UserFacingErrorSanitizer.sanitizeOrDefault(message, USER_ERROR_MESSAGE_MAX_LEN, "Job failed");
        e.setErrorMessage(safeMsg);
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        if (failureCode != null && !failureCode.isBlank()) {
            meta.put("failureCode", failureCode.trim());
        }
        meta.put("phase", "failed");
        e.setResultJson(meta);
        e.setCompletedAt(now);
        e.setUpdatedAt(now);
        appendProgress(e, "Failed: " + safeMsg);
        asyncTaskRepository.save(e);
        recordEvent(e, LabJobEventType.FAILED, safeMsg);
    }

    @Transactional
    public void markCancelled(UUID taskId, String reason) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        if (e.getStatus() == AsyncTaskStatus.SUCCEEDED
                || e.getStatus() == AsyncTaskStatus.FAILED
                || e.getStatus() == AsyncTaskStatus.CANCELLED) {
            return;
        }
        Instant now = Instant.now();
        e.setStatus(AsyncTaskStatus.CANCELLED);
        e.setErrorMessage(reason);
        e.setCompletedAt(now);
        e.setUpdatedAt(now);
        appendProgress(e, "Cancelled: " + (reason != null ? reason : ""));
        asyncTaskRepository.save(e);
        recordEvent(e, LabJobEventType.CANCELLED, reason != null ? reason : "Cancelled");
    }

    @Transactional
    public void requestCancellation(UUID taskId, String reason) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        if (e.getStatus() == AsyncTaskStatus.SUCCEEDED
                || e.getStatus() == AsyncTaskStatus.FAILED
                || e.getStatus() == AsyncTaskStatus.CANCELLED) {
            return;
        }
        if (e.getStatus() == AsyncTaskStatus.CANCELLING) {
            return;
        }
        Instant now = Instant.now();
        e.setStatus(AsyncTaskStatus.CANCELLING);
        e.setUpdatedAt(now);
        String msg = (reason != null && !reason.isBlank()) ? reason : "Cancellation requested by user";
        e.setErrorMessage(msg);
        appendProgress(e, msg);
        asyncTaskRepository.save(e);
        recordEvent(e, LabJobEventType.PROGRESS, msg);
    }

    /**
     * Live partial answer for {@link com.uniovi.rag.domain.AsyncTaskType#CHAT_MESSAGE} (polled via GET /lab/jobs/{id}).
     */
    @Transactional
    public void updateStreamingChatResult(UUID taskId, String partialAnswer) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        Map<String, Object> r =
                e.getResultJson() != null ? new LinkedHashMap<>(e.getResultJson()) : new LinkedHashMap<>();
        r.put("streamedAnswer", partialAnswer);
        r.put("phase", "streaming");
        e.setResultJson(r);
        e.setUpdatedAt(Instant.now());
        asyncTaskRepository.save(e);
    }

    private void recordEvent(AsyncTaskEntity e, LabJobEventType type, String message) {
        labJobEventService.recordEvent(e.getId(), type, message);
    }

    private static void appendProgress(AsyncTaskEntity e, String line) {
        String prev = e.getProgressText();
        String next = (prev == null || prev.isBlank()) ? line : prev + "\n" + line;
        if (next.length() > 12000) {
            next = next.substring(next.length() - 12000);
        }
        e.setProgressText(next);
    }
}
