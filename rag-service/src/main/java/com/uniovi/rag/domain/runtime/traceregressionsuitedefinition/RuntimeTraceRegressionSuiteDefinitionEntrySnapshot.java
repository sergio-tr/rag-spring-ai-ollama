package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable entry row in {@link RuntimeTraceRegressionSuiteDefinitionSnapshot}.
 */
public sealed interface RuntimeTraceRegressionSuiteDefinitionEntrySnapshot
        permits RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds,
                RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByConversation {

    record ByTraceIds(List<UUID> traceIds) implements RuntimeTraceRegressionSuiteDefinitionEntrySnapshot {

        public ByTraceIds {
            traceIds = List.copyOf(traceIds);
        }
    }

    /**
     * SQL null columns are exposed as Java {@code null} (P34 service API).
     */
    record ByConversation(UUID conversationId, Instant createdAtFrom, Instant createdAtTo, String workflowName)
            implements RuntimeTraceRegressionSuiteDefinitionEntrySnapshot {}
}
