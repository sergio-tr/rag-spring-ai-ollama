package com.uniovi.rag.domain.runtime.tracecomparisonbatch;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P24 batch request envelope. {@link #byTraceIds} rejects {@code null} trace id elements with
 * {@link IllegalArgumentException}. Raw list size {@code > 50} is allowed in the record and handled by the batch
 * service as {@link RuntimeTraceReplayComparisonBatchOutcome#NOT_ATTEMPTED} (no {@code compare} calls).
 */
public record RuntimeTraceReplayComparisonBatchRequest(
        UUID userId,
        RuntimeTraceReplayComparisonBatchMode mode,
        RuntimeTraceReplayComparisonBatchSelection selection) {

    public static RuntimeTraceReplayComparisonBatchRequest byTraceIds(UUID userId, List<UUID> traceIds) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(traceIds, "traceIds");
        for (UUID id : traceIds) {
            if (id == null) {
                throw new IllegalArgumentException("traceIds must not contain null");
            }
        }
        return new RuntimeTraceReplayComparisonBatchRequest(
                userId, RuntimeTraceReplayComparisonBatchMode.BY_TRACE_IDS, new RuntimeTraceReplayComparisonBatchSelection.ByTraceIds(traceIds));
    }

    public static RuntimeTraceReplayComparisonBatchRequest byConversation(
            UUID userId,
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(conversationId, "conversationId");
        return new RuntimeTraceReplayComparisonBatchRequest(
                userId,
                RuntimeTraceReplayComparisonBatchMode.BY_CONVERSATION,
                new RuntimeTraceReplayComparisonBatchSelection.ByConversation(
                        conversationId, createdAtFrom, createdAtTo, workflowName));
    }
}
