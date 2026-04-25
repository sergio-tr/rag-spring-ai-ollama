package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model returned from {@code loadByIdForUser} (P33/P34).
 */
public record RuntimeTraceRegressionSuiteDefinitionSnapshot(
        UUID id,
        String name,
        String description,
        int schemaVersion,
        Instant createdAt,
        Instant updatedAt,
        List<RuntimeTraceRegressionSuiteDefinitionEntrySnapshot> entries) {

    public RuntimeTraceRegressionSuiteDefinitionSnapshot {
        entries = List.copyOf(entries);
    }
}
