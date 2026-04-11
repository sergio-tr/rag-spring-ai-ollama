package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RuntimeTraceRegressionSuiteDefinitionDetailDto(
        UUID id,
        String name,
        String description,
        int schemaVersion,
        Instant createdAt,
        Instant updatedAt,
        List<RuntimeTraceRegressionSuiteDefinitionEntryDto> entries) {

    public RuntimeTraceRegressionSuiteDefinitionDetailDto {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
