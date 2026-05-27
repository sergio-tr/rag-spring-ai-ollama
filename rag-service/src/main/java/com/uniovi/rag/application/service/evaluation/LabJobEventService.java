package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.port.AfterCommitTaskScheduler;
import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.interfaces.rest.dto.LabJobEventDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LabJobEventService {

    private static final String LOG_KEY = "events";
    private static final String NEXT_ID_KEY = "nextId";
    private static final int MAX_EVENTS = 500;

    private final AsyncTaskRepository asyncTaskRepository;
    private final LabJobSseHub sseHub;
    private final AfterCommitTaskScheduler afterCommitTaskScheduler;

    public LabJobEventService(
            AsyncTaskRepository asyncTaskRepository,
            LabJobSseHub sseHub,
            AfterCommitTaskScheduler afterCommitTaskScheduler) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.sseHub = sseHub;
        this.afterCommitTaskScheduler = afterCommitTaskScheduler;
    }

    @Transactional
    public LabJobEventDto recordEvent(UUID taskId, LabJobEventType type, String message) {
        return record(LabJobEventRequest.of(taskId, type, message));
    }

    @Transactional
    public LabJobEventDto record(LabJobEventRequest request) {
        if (request.type() == LabJobEventType.HEARTBEAT) {
            throw new IllegalArgumentException("HEARTBEAT is not persisted");
        }
        AsyncTaskEntity task = asyncTaskRepository.findById(request.taskId()).orElseThrow();
        return appendEvent(task, request);
    }

    @Transactional
    public LabJobEventDto recordFromStatus(UUID taskId, LabJobEventType type, AsyncTaskStatusDto status, String message) {
        Map<String, Object> payload = statusPayload(status);
        return record(LabJobEventRequest.of(taskId, type, message).withPayload(payload));
    }

    @Transactional(readOnly = true)
    public List<LabJobEventDto> listEvents(UUID taskId, UUID userId, Long sinceEventId) {
        AsyncTaskEntity task = requireOwnedTask(taskId, userId);
        return eventsAfter(readLog(task), sinceEventId);
    }

    @Transactional(readOnly = true)
    public LabJobEventDto buildSnapshot(UUID taskId, UUID userId) {
        AsyncTaskEntity task = requireOwnedTask(taskId, userId);
        Map<String, Object> log = readLog(task);
        List<LabJobEventDto> events = eventsAfter(log, null);
        LabJobEventDto latest = events.isEmpty() ? null : events.getLast();
        long nextId = readNextId(log);
        String status = task.getStatus() != null ? task.getStatus().name() : "UNKNOWN";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snapshot", true);
        payload.put("eventCount", events.size());
        if (latest != null) {
            payload.put("latestEventId", latest.eventId());
            payload.put("latestType", latest.type());
        }
        return new LabJobEventDto(
                Math.max(0L, nextId - 1L),
                task.getId(),
                "SNAPSHOT",
                status,
                task.getProgressText(),
                "Live updates connected.",
                task.getUpdatedAt() != null ? task.getUpdatedAt() : Instant.now(),
                Map.copyOf(payload),
                latest != null ? latest.campaignId() : null,
                latest != null ? latest.runId() : null,
                null,
                latest != null ? latest.globalCompletedItems() : null,
                latest != null ? latest.globalTotalItems() : null,
                latest != null ? latest.runCompletedItems() : null,
                latest != null ? latest.runTotalItems() : null,
                latest != null ? latest.currentModelId() : null,
                latest != null ? latest.currentPresetCode() : null);
    }

    @Transactional(readOnly = true)
    public long latestEventId(UUID taskId, UUID userId) {
        AsyncTaskEntity task = requireOwnedTask(taskId, userId);
        Map<String, Object> log = readLog(task);
        Object next = log.get(NEXT_ID_KEY);
        if (next instanceof Number n) {
            return Math.max(0L, n.longValue() - 1L);
        }
        return 0L;
    }

    private AsyncTaskEntity requireOwnedTask(UUID taskId, UUID userId) {
        return asyncTaskRepository
                .findByIdAndUser_Id(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private LabJobEventDto appendEvent(AsyncTaskEntity task, LabJobEventRequest request) {
        Map<String, Object> log = new LinkedHashMap<>(readLog(task));
        long nextId = readNextId(log);
        LabJobEventDto event = toEvent(task, nextId, request);
        List<Map<String, Object>> events = readEvents(log);
        events.add(toStoredMap(event));
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
        log.put(NEXT_ID_KEY, nextId + 1);
        log.put(LOG_KEY, events);
        task.setEventLogJson(log);
        asyncTaskRepository.save(task);
        UUID taskId = task.getId();
        afterCommitTaskScheduler.scheduleAfterCommit(() -> sseHub.publish(taskId, event));
        return event;
    }

    private static LabJobEventDto toEvent(AsyncTaskEntity task, long eventId, LabJobEventRequest request) {
        String status = task.getStatus() != null ? task.getStatus().name() : "UNKNOWN";
        return new LabJobEventDto(
                eventId,
                task.getId(),
                request.type().name(),
                status,
                task.getProgressText(),
                request.message(),
                task.getUpdatedAt() != null ? task.getUpdatedAt() : Instant.now(),
                request.payload() != null ? request.payload() : Map.of(),
                request.campaignId(),
                request.runId(),
                request.itemId(),
                request.globalCompletedItems(),
                request.globalTotalItems(),
                request.runCompletedItems(),
                request.runTotalItems(),
                request.currentModelId(),
                request.currentPresetCode());
    }

    private static Map<String, Object> statusPayload(AsyncTaskStatusDto status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("terminal", status.terminal());
        if (status.progressText() != null) {
            payload.put("progressText", status.progressText());
        }
        if (status.errorMessage() != null) {
            payload.put("errorMessage", status.errorMessage());
        }
        if (status.result() != null) {
            payload.put("result", status.result());
        }
        if (status.failureCode() != null) {
            payload.put("failureCode", status.failureCode());
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readLog(AsyncTaskEntity task) {
        Map<String, Object> raw = task.getEventLogJson();
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(raw);
    }

    private static long readNextId(Map<String, Object> log) {
        Object next = log.get(NEXT_ID_KEY);
        if (next instanceof Number n) {
            return n.longValue();
        }
        return 1L;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readEvents(Map<String, Object> log) {
        Object raw = log.get(LOG_KEY);
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    private static List<LabJobEventDto> eventsAfter(Map<String, Object> log, Long sinceEventId) {
        long since = sinceEventId != null ? sinceEventId : 0L;
        List<LabJobEventDto> out = new ArrayList<>();
        for (Map<String, Object> stored : readEvents(log)) {
            LabJobEventDto dto = fromStoredMap(stored);
            if (dto != null && dto.eventId() > since) {
                out.add(dto);
            }
        }
        return out;
    }

    private static Map<String, Object> toStoredMap(LabJobEventDto event) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", event.eventId());
        m.put("jobId", event.jobId().toString());
        m.put("type", event.type());
        m.put("status", event.status());
        m.put("progress", event.progress());
        m.put("message", event.message());
        m.put("timestamp", event.timestamp().toString());
        if (event.campaignId() != null) {
            m.put("campaignId", event.campaignId().toString());
        }
        if (event.runId() != null) {
            m.put("runId", event.runId().toString());
        }
        if (event.itemId() != null) {
            m.put("itemId", event.itemId());
        }
        if (event.globalCompletedItems() != null) {
            m.put("globalCompletedItems", event.globalCompletedItems());
        }
        if (event.globalTotalItems() != null) {
            m.put("globalTotalItems", event.globalTotalItems());
        }
        if (event.runCompletedItems() != null) {
            m.put("runCompletedItems", event.runCompletedItems());
        }
        if (event.runTotalItems() != null) {
            m.put("runTotalItems", event.runTotalItems());
        }
        if (event.currentModelId() != null) {
            m.put("currentModelId", event.currentModelId());
        }
        if (event.currentPresetCode() != null) {
            m.put("currentPresetCode", event.currentPresetCode());
        }
        if (event.payload() != null && !event.payload().isEmpty()) {
            m.put("payload", event.payload());
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static LabJobEventDto fromStoredMap(Map<String, Object> stored) {
        Object eventId = stored.get("eventId");
        Object jobId = stored.get("jobId");
        if (!(eventId instanceof Number) || jobId == null) {
            return null;
        }
        Instant ts = Instant.parse(String.valueOf(stored.get("timestamp")));
        Map<String, Object> payload = stored.get("payload") instanceof Map<?, ?> p
                ? new LinkedHashMap<>((Map<String, Object>) p)
                : Map.of();
        return new LabJobEventDto(
                ((Number) eventId).longValue(),
                UUID.fromString(jobId.toString()),
                String.valueOf(stored.get("type")),
                stored.get("status") != null ? String.valueOf(stored.get("status")) : null,
                stored.get("progress") != null ? String.valueOf(stored.get("progress")) : null,
                stored.get("message") != null ? String.valueOf(stored.get("message")) : null,
                ts,
                payload,
                parseUuid(stored.get("campaignId")),
                parseUuid(stored.get("runId")),
                stored.get("itemId") != null ? String.valueOf(stored.get("itemId")) : null,
                intOrNull(stored.get("globalCompletedItems")),
                intOrNull(stored.get("globalTotalItems")),
                intOrNull(stored.get("runCompletedItems")),
                intOrNull(stored.get("runTotalItems")),
                stored.get("currentModelId") != null ? String.valueOf(stored.get("currentModelId")) : null,
                stored.get("currentPresetCode") != null ? String.valueOf(stored.get("currentPresetCode")) : null);
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        return UUID.fromString(raw.toString());
    }

    private static Integer intOrNull(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    static LabJobEventType typeForStatus(AsyncTaskStatus status) {
        return switch (status) {
            case QUEUED -> LabJobEventType.ACCEPTED;
            case RUNNING -> LabJobEventType.STARTED;
            case CANCELLING -> LabJobEventType.CANCELLING;
            case SUCCEEDED -> LabJobEventType.RUN_COMPLETED;
            case FAILED -> LabJobEventType.FAILED;
            case CANCELLED -> LabJobEventType.CANCELLED;
        };
    }
}
