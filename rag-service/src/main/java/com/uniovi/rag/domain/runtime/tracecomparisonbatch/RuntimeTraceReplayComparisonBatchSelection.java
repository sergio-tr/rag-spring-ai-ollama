package com.uniovi.rag.domain.runtime.tracecomparisonbatch;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Discriminated selection for P24 batch (no repository types).
 */
public sealed interface RuntimeTraceReplayComparisonBatchSelection
        permits RuntimeTraceReplayComparisonBatchSelection.ByTraceIds,
                RuntimeTraceReplayComparisonBatchSelection.ByConversation {

    record ByTraceIds(List<UUID> traceIds) implements RuntimeTraceReplayComparisonBatchSelection {

        public ByTraceIds {
            traceIds = List.copyOf(traceIds);
        }
    }

    record ByConversation(
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName)
            implements RuntimeTraceReplayComparisonBatchSelection {

        public ByConversation {
            createdAtFrom = createdAtFrom == null ? Optional.empty() : createdAtFrom;
            createdAtTo = createdAtTo == null ? Optional.empty() : createdAtTo;
            workflowName = workflowName == null ? Optional.empty() : workflowName;
        }
    }
}
