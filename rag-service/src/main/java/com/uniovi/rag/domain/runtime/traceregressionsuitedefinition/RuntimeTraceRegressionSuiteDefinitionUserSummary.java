package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import java.time.Instant;
import java.util.UUID;

/**
 * List row for a user's saved regression suite definitions (P34).
 */
public record RuntimeTraceRegressionSuiteDefinitionUserSummary(
        UUID definitionId,
        String name,
        String description,
        int entryCount,
        Instant createdAt,
        Instant updatedAt) {}
