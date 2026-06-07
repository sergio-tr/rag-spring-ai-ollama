package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.interfaces.rest.dto.LabJobEventDto;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory registry of open SSE connections per job. Events are pushed when persisted (no polling).
 */
@Component
public class LabJobSseHub {

    private static final Logger log = LoggerFactory.getLogger(LabJobSseHub.class);

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Subscription>> subscribers = new ConcurrentHashMap<>();

    public Registration register(UUID taskId, UUID userId, SseEmitter emitter, Consumer<LabJobEventDto> onTerminal) {
        Subscription sub = new Subscription(userId, emitter, onTerminal);
        subscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(sub);
        Runnable cleanup = () -> remove(taskId, sub);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return new Registration(cleanup);
    }

    public void publish(UUID taskId, LabJobEventDto event) {
        List<Subscription> subs = subscribers.get(taskId);
        if (subs == null || subs.isEmpty()) {
            return;
        }
        for (Subscription sub : subs) {
            try {
                sendJobEvent(sub.emitter(), event);
                if (event.terminal()) {
                    sub.onTerminal().accept(event);
                }
            } catch (IOException ex) {
                log.debug("lab_job_sse_send_failed taskId={} eventId={}", taskId, event.eventId(), ex);
                remove(taskId, sub);
                sub.emitter().completeWithError(ex);
            }
        }
    }

    public void complete(UUID taskId) {
        List<Subscription> subs = subscribers.remove(taskId);
        if (subs == null) {
            return;
        }
        for (Subscription sub : subs) {
            try {
                sub.emitter().complete();
            } catch (Exception ignored) {
                // emitter may already be closed
            }
        }
    }

    private void remove(UUID taskId, Subscription sub) {
        CopyOnWriteArrayList<Subscription> subs = subscribers.get(taskId);
        if (subs != null) {
            subs.remove(sub);
            if (subs.isEmpty()) {
                subscribers.remove(taskId, subs);
            }
        }
    }

    public static void sendJobEvent(SseEmitter emitter, LabJobEventDto event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(event.eventId()))
                .name("job-event")
                .data(event, MediaType.APPLICATION_JSON));
    }

    public record Registration(Runnable unregister) {}

    private record Subscription(UUID userId, SseEmitter emitter, Consumer<LabJobEventDto> onTerminal) {}
}
