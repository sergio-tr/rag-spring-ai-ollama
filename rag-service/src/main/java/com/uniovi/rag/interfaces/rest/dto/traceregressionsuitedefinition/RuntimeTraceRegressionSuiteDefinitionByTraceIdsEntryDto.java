package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto extends RuntimeTraceRegressionSuiteDefinitionEntryDto {

    private static final String ENTRY_KIND = "BY_TRACE_IDS";

    private final List<UUID> traceIds;

    @JsonCreator
    public RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto(@JsonProperty("traceIds") List<UUID> traceIds) {
        this.traceIds = traceIds == null ? List.of() : List.copyOf(traceIds);
    }

    public String getEntryKind() {
        return ENTRY_KIND;
    }

    public List<UUID> getTraceIds() {
        return traceIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto that =
                (RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto) o;
        return traceIds.equals(that.traceIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceIds);
    }
}
