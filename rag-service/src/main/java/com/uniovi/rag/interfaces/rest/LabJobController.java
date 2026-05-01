package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.async.AsyncTaskService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Poll or subscribe (SSE) to background lab/admin task status without blocking the browser.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/lab/jobs")
public class LabJobController {

    private static final long SSE_TIMEOUT_MS = 60L * 60 * 1000;

    private final AsyncTaskService asyncTaskService;
    private final ChatMessageApplicationService chatMessageApplicationService;
    private final ScheduledExecutorService labJobSseExecutor;

    public LabJobController(
            AsyncTaskService asyncTaskService,
            ChatMessageApplicationService chatMessageApplicationService,
            @Qualifier("labJobSseExecutor") ScheduledExecutorService labJobSseExecutor) {
        this.asyncTaskService = asyncTaskService;
        this.chatMessageApplicationService = chatMessageApplicationService;
        this.labJobSseExecutor = labJobSseExecutor;
    }

    @GetMapping("/{taskId}")
    public AsyncTaskStatusDto get(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID taskId) {
        return asyncTaskService.getStatus(taskId, principal.userId());
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID taskId) {
        chatMessageApplicationService.cancelChatTask(principal.userId(), taskId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID taskId) {
        asyncTaskService.getStatus(taskId, principal.userId());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
        Runnable tick = () -> {
            try {
                AsyncTaskStatusDto dto = asyncTaskService.getStatus(taskId, principal.userId());
                emitter.send(SseEmitter.event().name("task").data(dto, MediaType.APPLICATION_JSON));
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
            }
        };
        holder[0] = labJobSseExecutor.scheduleAtFixedRate(tick, 0, 750, TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> cancel(holder));
        emitter.onTimeout(() -> cancel(holder));
        emitter.onError(e -> cancel(holder));
        return emitter;
    }

    private static void cancel(ScheduledFuture<?>[] holder) {
        if (holder[0] != null) {
            holder[0].cancel(false);
        }
    }
}
