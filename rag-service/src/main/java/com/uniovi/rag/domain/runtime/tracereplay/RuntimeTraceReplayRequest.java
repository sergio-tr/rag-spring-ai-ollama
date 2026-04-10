package com.uniovi.rag.domain.runtime.tracereplay;

import java.util.Optional;
import java.util.UUID;

/**
 * User-scoped replay request envelope (P18). Internal application use only.
 */
public record RuntimeTraceReplayRequest(
        UUID userId,
        RuntimeTraceReplayMode mode,
        Optional<UUID> traceId,
        Optional<UUID> conversationId,
        Optional<UUID> messageId) {

    public RuntimeTraceReplayRequest {
        traceId = traceId == null ? Optional.empty() : traceId;
        conversationId = conversationId == null ? Optional.empty() : conversationId;
        messageId = messageId == null ? Optional.empty() : messageId;
    }

    public static RuntimeTraceReplayRequest byTraceId(UUID userId, UUID traceId) {
        return new RuntimeTraceReplayRequest(userId, RuntimeTraceReplayMode.BY_TRACE_ID, Optional.of(traceId), Optional.empty(), Optional.empty());
    }

    public static RuntimeTraceReplayRequest byMessageId(UUID userId, UUID conversationId, UUID messageId) {
        return new RuntimeTraceReplayRequest(
                userId, RuntimeTraceReplayMode.BY_MESSAGE_ID, Optional.empty(), Optional.of(conversationId), Optional.of(messageId));
    }
}
