package com.uniovi.rag.domain.runtime.tracereplaybatch;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P27 batch request envelope. Null elements in {@code traceIds} are detected by the batch service (returns
 * {@link RuntimeTraceReplayBatchOutcome#NOT_ATTEMPTED}); this factory does not throw for them.
 */
public record RuntimeTraceReplayBatchRequest(
        UUID userId, RuntimeTraceReplayBatchMode mode, RuntimeTraceReplayBatchSelection selection) {

    public static RuntimeTraceReplayBatchRequest byTraceIds(UUID userId, List<UUID> traceIds) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(traceIds, "traceIds");
        return new RuntimeTraceReplayBatchRequest(
                userId, RuntimeTraceReplayBatchMode.BY_TRACE_IDS, new RuntimeTraceReplayBatchSelection.ByTraceIds(traceIds));
    }

    public static RuntimeTraceReplayBatchRequest byConversation(
            UUID userId,
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(conversationId, "conversationId");
        return new RuntimeTraceReplayBatchRequest(
                userId,
                RuntimeTraceReplayBatchMode.BY_CONVERSATION,
                new RuntimeTraceReplayBatchSelection.ByConversation(
                        conversationId, createdAtFrom, createdAtTo, workflowName));
    }
}
