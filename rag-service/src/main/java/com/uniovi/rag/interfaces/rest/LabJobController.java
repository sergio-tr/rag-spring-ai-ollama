package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.application.service.evaluation.LabJobLifecycleService;
import com.uniovi.rag.application.service.evaluation.LabJobSseHub;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Poll or subscribe (SSE) to background lab/admin task status without blocking the browser.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/lab/jobs")
public class LabJobController {

    private static final long SSE_TIMEOUT_MS = 60L * 60 * 1000;
    private static final long SSE_HEARTBEAT_MS = 30_000L;

    private final AsyncTaskService asyncTaskService;
    private final ChatMessageApplicationService chatMessageApplicationService;
    private final LabJobLifecycleService labJobLifecycleService;
    private final LabJobEventService labJobEventService;
    private final LabJobSseHub labJobSseHub;
    private final ScheduledExecutorService labJobSseExecutor;

    public LabJobController(
            AsyncTaskService asyncTaskService,
            ChatMessageApplicationService chatMessageApplicationService,
            LabJobLifecycleService labJobLifecycleService,
            LabJobEventService labJobEventService,
            LabJobSseHub labJobSseHub,
            @Qualifier("labJobSseExecutor") ScheduledExecutorService labJobSseExecutor) {
        this.asyncTaskService = asyncTaskService;
        this.chatMessageApplicationService = chatMessageApplicationService;
        this.labJobLifecycleService = labJobLifecycleService;
        this.labJobEventService = labJobEventService;
        this.labJobSseHub = labJobSseHub;
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
                .header("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
                .header("Pragma", "no-cache")
                .header("X-Accel-Buffering", "no")
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
            chatMessageApplicationService.cancelChatTask(principal.userId(), taskId);
        }
        return ResponseEntity.noContent().build();
    }

    private SseEmitter openEventStream(UUID userId, UUID taskId, Long sinceEventId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        ScheduledFuture<?>[] heartbeatHolder = new ScheduledFuture<?>[1];
        AtomicBoolean closed = new AtomicBoolean(false);

        Runnable stopHeartbeat = () -> {
            if (heartbeatHolder[0] != null) {
                heartbeatHolder[0].cancel(false);
                heartbeatHolder[0] = null;
            }
        };

        Runnable closeThisConnection = () -> {
            if (closed.compareAndSet(false, true)) {
                stopHeartbeat.run();
            }
        };

        try {
            LabJobEventDto snapshot = labJobEventService.buildSnapshot(taskId, userId);
            LabJobSseHub.sendJobEvent(emitter, snapshot);
            List<LabJobEventDto> replay = labJobEventService.listEvents(taskId, userId, sinceEventId);
            for (LabJobEventDto event : replay) {
                LabJobSseHub.sendJobEvent(emitter, event);
                if (event.terminal()) {
                    LabJobSseHub.completeEmitterQuietly(emitter);
                    return emitter;
                }
            }
        } catch (Exception ex) {
            if (LabJobSseHub.isClientDisconnected(ex)) {
                closeThisConnection.run();
                LabJobSseHub.completeEmitterQuietly(emitter);
            } else {
                emitter.completeWithError(ex);
            }
            return emitter;
        }

        LabJobSseHub.Registration registration = labJobSseHub.register(
                taskId,
                userId,
                emitter,
                terminal -> {
                    LabJobSseHub.completeEmitterQuietly(emitter);
                    closeThisConnection.run();
                    labJobSseHub.complete(taskId);
                });
        emitter.onCompletion(() -> {
            registration.unregister().run();
            closeThisConnection.run();
        });

        heartbeatHolder[0] =
                labJobSseExecutor.scheduleAtFixedRate(
                        () -> {
                            if (closed.get()) {
                                return;
                            }
                            try {
                                sendHeartbeat(emitter, taskId);
                            } catch (Exception ex) {
                                registration.unregister().run();
                                if (LabJobSseHub.isClientDisconnected(ex)) {
                                    LabJobSseHub.completeEmitterQuietly(emitter);
                                } else {
                                    emitter.completeWithError(ex);
                                }
                                closeThisConnection.run();
                            }
                        },
                        SSE_HEARTBEAT_MS,
                        SSE_HEARTBEAT_MS,
                        TimeUnit.MILLISECONDS);

        return emitter;
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
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        emitter.send(SseEmitter.event().name("heartbeat").data(heartbeat, MediaType.APPLICATION_JSON));
    }
}
