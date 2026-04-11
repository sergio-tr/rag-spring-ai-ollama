package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ordered suite entry in a create/update command — mirrors P30 {@code RuntimeTraceRegressionSuiteEntry} shapes.
 */
public sealed interface RuntimeTraceRegressionSuiteDefinitionEntrySpec
        permits RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds,
                RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation {

    record ByTraceIds(List<UUID> traceIds) implements RuntimeTraceRegressionSuiteDefinitionEntrySpec {

        public ByTraceIds {
            traceIds = traceIds == null ? List.of() : List.copyOf(traceIds);
        }
    }

    record ByConversation(
            UUID conversationId,
            Optional<Instant> createdAtFrom,
            Optional<Instant> createdAtTo,
            Optional<String> workflowName)
            implements RuntimeTraceRegressionSuiteDefinitionEntrySpec {

        public ByConversation {
            createdAtFrom = createdAtFrom == null ? Optional.empty() : createdAtFrom;
            createdAtTo = createdAtTo == null ? Optional.empty() : createdAtTo;
            workflowName = workflowName == null ? Optional.empty() : workflowName;
        }
    }
}
