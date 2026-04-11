package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto extends RuntimeTraceRegressionSuiteDefinitionEntryDto {

    private static final String ENTRY_KIND = "BY_CONVERSATION";

    private final UUID conversationId;
    private final Instant createdAtFrom;
    private final Instant createdAtTo;
    private final String workflowName;

    @JsonCreator
    public RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto(
            @JsonProperty("conversationId") UUID conversationId,
            @JsonProperty("createdAtFrom") Instant createdAtFrom,
            @JsonProperty("createdAtTo") Instant createdAtTo,
            @JsonProperty("workflowName") String workflowName) {
        this.conversationId = conversationId;
        this.createdAtFrom = createdAtFrom;
        this.createdAtTo = createdAtTo;
        this.workflowName = workflowName;
    }

    public String getEntryKind() {
        return ENTRY_KIND;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public Instant getCreatedAtFrom() {
        return createdAtFrom;
    }

    public Instant getCreatedAtTo() {
        return createdAtTo;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto that =
                (RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto) o;
        return Objects.equals(conversationId, that.conversationId)
                && Objects.equals(createdAtFrom, that.createdAtFrom)
                && Objects.equals(createdAtTo, that.createdAtTo)
                && Objects.equals(workflowName, that.workflowName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, createdAtFrom, createdAtTo, workflowName);
    }
}
