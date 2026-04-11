package com.uniovi.rag.domain.runtime.tracereplaybatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Discriminated selection for P27 batch (no repository types). Aligns with P24 batch selection patterns.
 */
public sealed interface RuntimeTraceReplayBatchSelection
        permits RuntimeTraceReplayBatchSelection.ByTraceIds, RuntimeTraceReplayBatchSelection.ByConversation {

    record ByTraceIds(List<UUID> traceIds) implements RuntimeTraceReplayBatchSelection {

        public ByTraceIds {
            traceIds = new ArrayList<>(traceIds);
        }
    }

    record ByConversation(
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName)
            implements RuntimeTraceReplayBatchSelection {

        public ByConversation {
            createdAtFrom = createdAtFrom == null ? Optional.empty() : createdAtFrom;
            createdAtTo = createdAtTo == null ? Optional.empty() : createdAtTo;
            workflowName = workflowName == null ? Optional.empty() : workflowName;
        }
    }
}
