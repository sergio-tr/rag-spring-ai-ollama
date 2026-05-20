package com.uniovi.rag.application.service.chat.async;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation for in-flight {@link com.uniovi.rag.domain.AsyncTaskType#CHAT_MESSAGE} jobs.
 */
@Component
public class ChatJobCancellationRegistry {

    private final ConcurrentHashMap<UUID, AtomicBoolean> flags = new ConcurrentHashMap<>();

    public void signalCancel(UUID taskId) {
        flags.computeIfAbsent(taskId, k -> new AtomicBoolean()).set(true);
    }

    public boolean isCancelled(UUID taskId) {
        AtomicBoolean b = flags.get(taskId);
        return b != null && b.get();
    }

    public void clear(UUID taskId) {
        flags.remove(taskId);
    }
}
