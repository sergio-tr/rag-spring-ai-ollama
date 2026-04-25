package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import java.time.Instant;
import java.util.UUID;

public record RuntimeTraceRegressionSuiteDefinitionSummaryDto(
        UUID id, String name, String description, int entryCount, Instant createdAt, Instant updatedAt) {}
