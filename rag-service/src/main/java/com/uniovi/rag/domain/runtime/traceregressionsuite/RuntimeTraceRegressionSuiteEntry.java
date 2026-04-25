package com.uniovi.rag.domain.runtime.traceregressionsuite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sealed suite entry — exactly two variants (P30). Maps to P24 batch selection.
 */
public sealed interface RuntimeTraceRegressionSuiteEntry
        permits RuntimeTraceRegressionSuiteEntry.ByTraceIds, RuntimeTraceRegressionSuiteEntry.ByConversation {

    record ByTraceIds(List<UUID> traceIds) implements RuntimeTraceRegressionSuiteEntry {

        public ByTraceIds {
            traceIds = List.copyOf(traceIds);
        }
    }

    record ByConversation(
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName)
            implements RuntimeTraceRegressionSuiteEntry {

        public ByConversation {
            createdAtFrom = createdAtFrom == null ? Optional.empty() : createdAtFrom;
            createdAtTo = createdAtTo == null ? Optional.empty() : createdAtTo;
            workflowName = workflowName == null ? Optional.empty() : workflowName;
        }
    }
}
