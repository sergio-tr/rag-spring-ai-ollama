package com.uniovi.rag.application.service.evaluation;

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
    private static final int MAX_EVENTS = 200;

    private final AsyncTaskRepository asyncTaskRepository;

    public LabJobEventService(AsyncTaskRepository asyncTaskRepository) {
        this.asyncTaskRepository = asyncTaskRepository;
    }

    @Transactional
    public LabJobEventDto recordEvent(UUID taskId, LabJobEventType type, String message) {
        AsyncTaskEntity task = asyncTaskRepository.findById(taskId).orElseThrow();
        return appendEvent(task, type, message, null);
    }

    @Transactional
    public LabJobEventDto recordFromStatus(UUID taskId, LabJobEventType type, AsyncTaskStatusDto status, String message) {
        AsyncTaskEntity task = asyncTaskRepository.findById(taskId).orElseThrow();
        Map<String, Object> payload = statusPayload(status);
        return appendEvent(task, type, message, payload);
    }

    @Transactional(readOnly = true)
    public List<LabJobEventDto> listEvents(UUID taskId, UUID userId, Long sinceEventId) {
        AsyncTaskEntity task = requireOwnedTask(taskId, userId);
        return eventsAfter(readLog(task), sinceEventId);
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

    private LabJobEventDto appendEvent(
            AsyncTaskEntity task, LabJobEventType type, String message, Map<String, Object> payload) {
        Map<String, Object> log = new LinkedHashMap<>(readLog(task));
        long nextId = readNextId(log);
        LabJobEventDto event = toEvent(task, nextId, type, message, payload);
        List<Map<String, Object>> events = readEvents(log);
        events.add(toStoredMap(event));
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
        log.put(NEXT_ID_KEY, nextId + 1);
        log.put(LOG_KEY, events);
        task.setEventLogJson(log);
        asyncTaskRepository.save(task);
        return event;
    }

    private static LabJobEventDto toEvent(
            AsyncTaskEntity task,
            long eventId,
            LabJobEventType type,
            String message,
            Map<String, Object> payload) {
        String progress = task.getProgressText();
        String status = task.getStatus() != null ? task.getStatus().name() : "UNKNOWN";
        return new LabJobEventDto(
                eventId,
                task.getId(),
                type.name(),
                status,
                progress,
                message,
                task.getUpdatedAt() != null ? task.getUpdatedAt() : Instant.now(),
                payload != null ? payload : Map.of());
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
                payload);
    }

    static LabJobEventType typeForStatus(AsyncTaskStatus status) {
        return switch (status) {
            case QUEUED -> LabJobEventType.ACCEPTED;
            case RUNNING -> LabJobEventType.STARTED;
            case CANCELLING -> LabJobEventType.PROGRESS;
            case SUCCEEDED -> LabJobEventType.COMPLETED;
            case FAILED -> LabJobEventType.FAILED;
            case CANCELLED -> LabJobEventType.CANCELLED;
        };
    }
}
