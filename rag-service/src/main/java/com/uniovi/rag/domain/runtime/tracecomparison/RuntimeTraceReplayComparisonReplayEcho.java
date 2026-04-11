package com.uniovi.rag.domain.runtime.tracecomparison;

import java.util.Optional;
import java.util.UUID;

/**
 * Minimal echo of the comparison selector (no secrets; transient).
 */
public record RuntimeTraceReplayComparisonReplayEcho(
        Optional<UUID> traceId, Optional<UUID> conversationId, Optional<UUID> messageId) {

    public RuntimeTraceReplayComparisonReplayEcho {
        traceId = traceId == null ? Optional.empty() : traceId;
        conversationId = conversationId == null ? Optional.empty() : conversationId;
        messageId = messageId == null ? Optional.empty() : messageId;
    }
}
