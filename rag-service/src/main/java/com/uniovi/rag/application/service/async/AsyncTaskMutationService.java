package com.uniovi.rag.application.service.async;

import com.uniovi.rag.application.service.evaluation.LabJobEventRequest;
import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.application.service.evaluation.async.LabJobPayloadKeys;
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
    private final LabJobProgressTracker labJobProgressTracker;

    public AsyncTaskMutationService(
            AsyncTaskRepository asyncTaskRepository,
            LabJobEventService labJobEventService,
            LabJobProgressTracker labJobProgressTracker) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.labJobEventService = labJobEventService;
        this.labJobProgressTracker = labJobProgressTracker;
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
    }

    @Transactional
    public void markSucceeded(UUID taskId, Map<String, Object> result) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        if (e.getStatus() == AsyncTaskStatus.CANCELLED || e.getStatus() == AsyncTaskStatus.CANCELLING) {
            return;
        }
        Instant now = Instant.now();
        e.setStatus(AsyncTaskStatus.SUCCEEDED);
        e.setResultJson(result);
        e.setCompletedAt(now);
        e.setUpdatedAt(now);
        appendProgress(e, finishProgressLine(result));
        asyncTaskRepository.save(e);
        UUID runId = evaluationRunId(e);
        if (runId != null) {
            labJobProgressTracker.emitRunCompleted(taskId, runId, "Run completed successfully");
        } else {
            recordEvent(
                    e,
                    LabJobEventType.RUN_COMPLETED,
                    "Job completed successfully",
                    terminalPayload(result, null));
        }
    }

    @Transactional
    public void markFailed(UUID taskId, String message) {
        markFailed(taskId, message, null);
    }

    @Transactional
    public void markFailed(UUID taskId, String message, String failureCode) {
        AsyncTaskEntity e = asyncTaskRepository.findById(taskId).orElseThrow();
        if (e.getStatus() == AsyncTaskStatus.CANCELLED) {
            return;
        }
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
        recordEvent(e, LabJobEventType.FAILED, safeMsg, terminalPayload(null, failureCode));
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
        recordEvent(e, LabJobEventType.CANCELLED, reason != null ? reason : "Cancelled", Map.of("terminal", true));
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
        recordEvent(e, LabJobEventType.CANCELLING, msg, Map.of());
    }

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
        recordEvent(e, type, message, Map.of());
    }

    private void recordEvent(AsyncTaskEntity e, LabJobEventType type, String message, Map<String, Object> payload) {
        labJobEventService.record(LabJobEventRequest.of(e.getId(), type, message).withPayload(payload));
    }

    private static Map<String, Object> terminalPayload(Map<String, Object> result, String failureCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("terminal", true);
        if (result != null) {
            payload.put("result", result);
        }
        if (failureCode != null) {
            payload.put("failureCode", failureCode);
        }
        return payload;
    }

    private static UUID evaluationRunId(AsyncTaskEntity e) {
        if (e.getRequestPayload() == null) {
            return null;
        }
        Object raw = e.getRequestPayload().get(LabJobPayloadKeys.EVALUATION_RUN_ID);
        if (raw == null) {
            return null;
        }
        return UUID.fromString(raw.toString());
    }

    @SuppressWarnings("unchecked")
    private static String finishProgressLine(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "Finished successfully.";
        }
        Object closure = result.get("benchmarkClosure");
        if (!(closure instanceof Map<?, ?> c)) {
            return "Finished successfully.";
        }
        Map<String, Object> closureMap = (Map<String, Object>) c;
        long executed = longValue(closureMap.get("executedItems"));
        long failed = longValue(closureMap.get("failedItems"));
        long skipped = longValue(closureMap.get("skippedItems"));
        long notSupported = longValue(closureMap.get("notSupportedItems"));
        String classification = strValue(closureMap.get("classification"));
        if ("COMPLETED_WITH_FAILURES".equals(classification)
                || "COMPLETED_WITH_UNSUPPORTED".equals(classification)) {
            return "Finished with warnings — executed="
                    + executed
                    + ", failed="
                    + failed
                    + ", skipped="
                    + skipped
                    + ", notSupported="
                    + notSupported
                    + ".";
        }
        if ("COMPLETED_WITH_NO_EXECUTED_ITEMS".equals(classification) || executed <= 0) {
            return "Finished without executed items — skipped="
                    + skipped
                    + ", notSupported="
                    + notSupported
                    + ".";
        }
        if (executed > 0) {
            return "Finished — executed="
                    + executed
                    + " item(s)"
                    + (failed > 0 || skipped > 0 || notSupported > 0
                            ? " (failed="
                                    + failed
                                    + ", skipped="
                                    + skipped
                                    + ", notSupported="
                                    + notSupported
                                    + ")"
                            : "")
                    + ".";
        }
        return "Finished without executed items.";
    }

    private static long longValue(Object raw) {
        if (raw instanceof Number n) {
            return Math.max(0, n.longValue());
        }
        return 0;
    }

    private static String strValue(Object raw) {
        return raw != null ? raw.toString().trim() : "";
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
