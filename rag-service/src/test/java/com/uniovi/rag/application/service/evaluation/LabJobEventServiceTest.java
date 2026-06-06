package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.application.port.AfterCommitTaskScheduler;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabJobEventServiceTest {

    @Mock
    private AsyncTaskRepository asyncTaskRepository;

    @Mock
    private LabJobSseHub labJobSseHub;

    @Mock
    private AfterCommitTaskScheduler afterCommitTaskScheduler;

    private LabJobEventService service;
    private UUID taskId;
    private AsyncTaskEntity task;

    @BeforeEach
    void setUp() {
        service = new LabJobEventService(asyncTaskRepository, labJobSseHub, afterCommitTaskScheduler);
        lenient()
                .doAnswer(inv -> {
                    inv.getArgument(0, Runnable.class).run();
                    return null;
                })
                .when(afterCommitTaskScheduler)
                .scheduleAfterCommit(any(Runnable.class));
        taskId = UUID.randomUUID();
        task = AsyncTaskEntity.queued(mock(UserEntity.class), AsyncTaskType.EVAL_LLM, Map.of(), Instant.parse("2026-01-01T00:00:00Z"));
        try {
            var idField = AsyncTaskEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(task, taskId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set async task id for test", e);
        }
        task.setStatus(AsyncTaskStatus.RUNNING);
        task.setUpdatedAt(Instant.parse("2026-01-01T00:00:01Z"));
    }

    @Test
    void recordEvent_persistsBoundedLog() {
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(asyncTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var event = service.recordEvent(taskId, LabJobEventType.STARTED, "step 1");

        assertThat(event.eventId()).isEqualTo(1L);
        assertThat(event.type()).isEqualTo("STARTED");
        ArgumentCaptor<AsyncTaskEntity> captor = ArgumentCaptor.forClass(AsyncTaskEntity.class);
        verify(asyncTaskRepository).save(captor.capture());
        Map<String, Object> log = captor.getValue().getEventLogJson();
        assertThat(log).isNotNull();
        assertThat(log.get("nextId")).isEqualTo(2L);
    }

    @Test
    void listEvents_returnsEventsAfterCursor() {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("nextId", 3L);
        log.put(
                "events",
                List.of(
                        Map.of(
                                "eventId",
                                1L,
                                "jobId",
                                taskId.toString(),
                                "type",
                                "ACCEPTED",
                                "status",
                                "QUEUED",
                                "progress",
                                "",
                                "message",
                                "accepted",
                                "timestamp",
                                "2026-01-01T00:00:00Z"),
                        Map.of(
                                "eventId",
                                2L,
                                "jobId",
                                taskId.toString(),
                                "type",
                                "STARTED",
                                "status",
                                "RUNNING",
                                "progress",
                                "Running",
                                "message",
                                "started",
                                "timestamp",
                                "2026-01-01T00:00:01Z")));
        task.setEventLogJson(log);
        when(asyncTaskRepository.findByIdAndUser_Id(taskId, UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .thenReturn(Optional.of(task));

        var events = service.listEvents(taskId, UUID.fromString("11111111-1111-1111-1111-111111111111"), 1L);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().eventId()).isEqualTo(2L);
        assertThat(events.getFirst().type()).isEqualTo("STARTED");
    }

    @Test
    void buildSnapshot_includesLatestCounters() {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("nextId", 2L);
        log.put(
                "events",
                List.of(
                        Map.of(
                                "eventId",
                                1L,
                                "jobId",
                                taskId.toString(),
                                "type",
                                "ITEM_COMPLETED",
                                "status",
                                "RUNNING",
                                "progress",
                                "1/3",
                                "message",
                                "done",
                                "timestamp",
                                "2026-01-01T00:00:02Z",
                                "globalCompletedItems",
                                1,
                                "globalTotalItems",
                                108)));
        task.setEventLogJson(log);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(asyncTaskRepository.findByIdAndUser_Id(taskId, userId)).thenReturn(Optional.of(task));

        var snapshot = service.buildSnapshot(taskId, userId);

        assertThat(snapshot.type()).isEqualTo("SNAPSHOT");
        assertThat(snapshot.globalTotalItems()).isEqualTo(108);
        assertThat(snapshot.globalCompletedItems()).isEqualTo(1);
    }
}
