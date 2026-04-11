package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import java.util.List;
import java.util.UUID;

public final class RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto extends RuntimeTraceRegressionSuiteDefinitionEntryDto {

    private static final String ENTRY_KIND = "BY_TRACE_IDS";

    private final List<UUID> traceIds;

    public RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto(List<UUID> traceIds) {
        this.traceIds = traceIds == null ? List.of() : List.copyOf(traceIds);
    }

    public String getEntryKind() {
        return ENTRY_KIND;
    }

    public List<UUID> getTraceIds() {
        return traceIds;
    }
}
