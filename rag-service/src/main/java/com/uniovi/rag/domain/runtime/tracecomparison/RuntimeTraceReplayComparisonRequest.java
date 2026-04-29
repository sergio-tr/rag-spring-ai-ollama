package com.uniovi.rag.domain.runtime.tracecomparison;

import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayMode;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * User-scoped P19 comparison selector (same resolution rules as P18 replay modes).
 */
public record RuntimeTraceReplayComparisonRequest(
        UUID userId,
        RuntimeTraceReplayMode mode,
        Optional<UUID> traceId,
        Optional<UUID> conversationId,
        Optional<UUID> messageId) {

    public RuntimeTraceReplayComparisonRequest {
        traceId = Objects.requireNonNullElseGet(traceId, Optional::empty);
        conversationId = Objects.requireNonNullElseGet(conversationId, Optional::empty);
        messageId = Objects.requireNonNullElseGet(messageId, Optional::empty);
    }

    public static RuntimeTraceReplayComparisonRequest byTraceId(UUID userId, UUID traceId) {
        return new RuntimeTraceReplayComparisonRequest(
                userId, RuntimeTraceReplayMode.BY_TRACE_ID, Optional.of(traceId), Optional.empty(), Optional.empty());
    }

    public static RuntimeTraceReplayComparisonRequest byMessageId(
            UUID userId, UUID conversationId, UUID messageId) {
        return new RuntimeTraceReplayComparisonRequest(
                userId, RuntimeTraceReplayMode.BY_MESSAGE_ID, Optional.empty(), Optional.of(conversationId), Optional.of(messageId));
    }

    /**
     * P18 replay uses the same linkage; keep replay construction in one place.
     */
    public RuntimeTraceReplayRequest toReplayRequest() {
        return new RuntimeTraceReplayRequest(userId, mode, traceId, conversationId, messageId);
    }
}
