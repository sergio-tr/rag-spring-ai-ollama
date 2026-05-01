package com.uniovi.rag.domain.runtime.tracecomparison;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal echo of the comparison selector (no secrets; transient).
 */
public record RuntimeTraceReplayComparisonReplayEcho(
        Optional<UUID> traceId, Optional<UUID> conversationId, Optional<UUID> messageId) {

    public RuntimeTraceReplayComparisonReplayEcho {
        traceId = Objects.requireNonNullElseGet(traceId, Optional::empty);
        conversationId = Objects.requireNonNullElseGet(conversationId, Optional::empty);
        messageId = Objects.requireNonNullElseGet(messageId, Optional::empty);
    }
}
