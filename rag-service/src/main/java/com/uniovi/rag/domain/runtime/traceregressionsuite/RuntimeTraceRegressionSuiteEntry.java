package com.uniovi.rag.domain.runtime.traceregressionsuite;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
            createdAtFrom = Objects.requireNonNullElseGet(createdAtFrom, Optional::empty);
            createdAtTo = Objects.requireNonNullElseGet(createdAtTo, Optional::empty);
            workflowName = Objects.requireNonNullElseGet(workflowName, Optional::empty);
        }
    }
}
