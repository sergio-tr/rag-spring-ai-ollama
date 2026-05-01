package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;
import java.util.UUID;

@JsonTypeName("BY_TRACE_IDS")
public final class RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto
        extends RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto {

    private static final String ENTRY_KIND = "BY_TRACE_IDS";

    private final List<UUID> traceIds;

    @JsonCreator
    public RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto(
            @JsonProperty(value = "entryKind", required = false) String entryKind,
            @JsonProperty("traceIds") List<UUID> traceIds) {
        this.traceIds = traceIds;
    }

    public String getEntryKind() {
        return ENTRY_KIND;
    }

    public List<UUID> getTraceIds() {
        return traceIds;
    }
}
