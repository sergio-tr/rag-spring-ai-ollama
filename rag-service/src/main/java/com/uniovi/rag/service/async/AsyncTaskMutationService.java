package com.uniovi.rag.service.async;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncTaskMutationService {

    private final AsyncTaskRepository asyncTaskRepository;

    public AsyncTaskMutationService(AsyncTaskRepository asyncTaskRepository) {
        this.asyncTaskRepository = asyncTaskRepository;
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
    }

    @Transactional
    public void appendProgressLine(UUID taskId, String line) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        appendProgress(e, line);
        e.setUpdatedAt(Instant.now());
        asyncTaskRepository.save(e);
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
    }

    @Transactional
    public void markFailed(UUID taskId, String message) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        Instant now = Instant.now();
        e.setStatus(AsyncTaskStatus.FAILED);
        e.setErrorMessage(message);
        e.setCompletedAt(now);
        e.setUpdatedAt(now);
        appendProgress(e, "Failed: " + (message != null ? message : "unknown error"));
        asyncTaskRepository.save(e);
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

    private static void appendProgress(AsyncTaskEntity e, String line) {
        String prev = e.getProgressText();
        String next = (prev == null || prev.isBlank()) ? line : prev + "\n" + line;
        if (next.length() > 12000) {
            next = next.substring(next.length() - 12000);
        }
        e.setProgressText(next);
    }
}
