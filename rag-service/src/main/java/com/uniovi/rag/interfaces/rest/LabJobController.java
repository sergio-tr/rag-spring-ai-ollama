package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.application.service.evaluation.LabJobLifecycleService;
import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.interfaces.rest.dto.ActiveLabJobDto;
import com.uniovi.rag.interfaces.rest.dto.LabJobEventDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Poll or subscribe (SSE) to background lab/admin task status without blocking the browser.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/lab/jobs")
public class LabJobController {

    private static final long SSE_TIMEOUT_MS = 60L * 60 * 1000;
    private static final long SSE_TICK_MS = 750L;
    private static final int HEARTBEAT_EVERY_TICKS = 14;

    private final AsyncTaskService asyncTaskService;
    private final ChatMessageApplicationService chatMessageApplicationService;
    private final LabJobLifecycleService labJobLifecycleService;
    private final LabJobEventService labJobEventService;
    private final ScheduledExecutorService labJobSseExecutor;

    public LabJobController(
            AsyncTaskService asyncTaskService,
            ChatMessageApplicationService chatMessageApplicationService,
            LabJobLifecycleService labJobLifecycleService,
            LabJobEventService labJobEventService,
            @Qualifier("labJobSseExecutor") ScheduledExecutorService labJobSseExecutor) {
        this.asyncTaskService = asyncTaskService;
        this.chatMessageApplicationService = chatMessageApplicationService;
        this.labJobLifecycleService = labJobLifecycleService;
        this.labJobEventService = labJobEventService;
        this.labJobSseExecutor = labJobSseExecutor;
    }

    @GetMapping("/active")
    public List<ActiveLabJobDto> active(@AuthenticationPrincipal RagPrincipal principal) {
        return labJobLifecycleService.listActiveJobs(principal.userId());
    }

    @GetMapping("/{taskId}")
    public AsyncTaskStatusDto get(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID taskId) {
        return asyncTaskService.getStatus(taskId, principal.userId());
    }

    @GetMapping("/{taskId}/events")
    public ResponseEntity<?> events(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID taskId,
            @RequestParam(required = false) Long since,
            @RequestParam(name = "stream", defaultValue = "true") boolean stream) {
        asyncTaskService.getStatus(taskId, principal.userId());
        if (!stream) {
            List<LabJobEventDto> events = labJobEventService.listEvents(taskId, principal.userId(), since);
            return ResponseEntity.ok(events);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(openEventStream(principal.userId(), taskId, since));
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID taskId) {
        try {
            labJobLifecycleService.cancelEvaluationJob(principal.userId(), taskId);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
                throw ex;
            }
            // Fallback: chat job cancellation (Lab UI also uses /lab/jobs/* for chat tasks).
            chatMessageApplicationService.cancelChatTask(principal.userId(), taskId);
        }
        return ResponseEntity.noContent().build();
    }

    private SseEmitter openEventStream(UUID userId, UUID taskId, Long sinceEventId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
        AtomicLong lastSentEventId = new AtomicLong(sinceEventId != null ? sinceEventId : 0L);
        AtomicInteger tickCount = new AtomicInteger(0);

        Runnable replayMissed = () -> {
            try {
                List<LabJobEventDto> missed = labJobEventService.listEvents(taskId, userId, lastSentEventId.get());
                for (LabJobEventDto event : missed) {
                    sendJobEvent(emitter, event);
                    lastSentEventId.set(Math.max(lastSentEventId.get(), event.eventId()));
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        };

        Runnable tick = () -> {
            try {
                if (tickCount.getAndIncrement() % HEARTBEAT_EVERY_TICKS == 0) {
                    sendHeartbeat(emitter, taskId);
                }
                AsyncTaskStatusDto dto = asyncTaskService.getStatus(taskId, userId);
                emitter.send(SseEmitter.event().name("task").data(dto, MediaType.APPLICATION_JSON));
                List<LabJobEventDto> fresh = labJobEventService.listEvents(taskId, userId, lastSentEventId.get());
                for (LabJobEventDto event : fresh) {
                    sendJobEvent(emitter, event);
                    lastSentEventId.set(Math.max(lastSentEventId.get(), event.eventId()));
                }
                if (dto.terminal()) {
                    if (holder[0] != null) {
                        holder[0].cancel(false);
                    }
                    emitter.complete();
                }
            } catch (IOException ex) {
                if (holder[0] != null) {
                    holder[0].cancel(false);
                }
                emitter.completeWithError(ex);
            } catch (RuntimeException ex) {
                if (ex.getCause() instanceof IOException io) {
                    if (holder[0] != null) {
                        holder[0].cancel(false);
                    }
                    emitter.completeWithError(io);
                } else {
                    throw ex;
                }
            }
        };

        try {
            replayMissed.run();
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) {
                emitter.completeWithError(io);
                return emitter;
            }
            throw ex;
        }

        holder[0] = labJobSseExecutor.scheduleAtFixedRate(tick, 0, SSE_TICK_MS, TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> cancel(holder));
        emitter.onTimeout(() -> cancel(holder));
        emitter.onError(e -> cancel(holder));
        return emitter;
    }

    private static void sendJobEvent(SseEmitter emitter, LabJobEventDto event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(event.eventId()))
                .name("job-event")
                .data(event, MediaType.APPLICATION_JSON));
    }

    private static void sendHeartbeat(SseEmitter emitter, UUID taskId) throws IOException {
        LabJobEventDto heartbeat = new LabJobEventDto(
                0L,
                taskId,
                "HEARTBEAT",
                null,
                null,
                null,
                Instant.now(),
                Map.of());
        emitter.send(SseEmitter.event().name("heartbeat").data(heartbeat, MediaType.APPLICATION_JSON));
    }

    private static void cancel(ScheduledFuture<?>[] holder) {
        if (holder[0] != null) {
            holder[0].cancel(false);
        }
    }
}
