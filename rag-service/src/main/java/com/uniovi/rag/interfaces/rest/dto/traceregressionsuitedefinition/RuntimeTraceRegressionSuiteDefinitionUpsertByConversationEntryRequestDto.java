package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.time.Instant;
import java.util.UUID;

@JsonTypeName("BY_CONVERSATION")
public final class RuntimeTraceRegressionSuiteDefinitionUpsertByConversationEntryRequestDto
        extends RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto {

    private static final String ENTRY_KIND = "BY_CONVERSATION";

    private final UUID conversationId;
    private final Instant createdAtFrom;
    private final Instant createdAtTo;
    private final String workflowName;

    @JsonCreator
    public RuntimeTraceRegressionSuiteDefinitionUpsertByConversationEntryRequestDto(
            @JsonProperty(value = "entryKind", required = false) String entryKind,
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
}
