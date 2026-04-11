package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import java.time.Instant;
import java.util.UUID;

public final class RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto extends RuntimeTraceRegressionSuiteDefinitionEntryDto {

    private static final String ENTRY_KIND = "BY_CONVERSATION";

    private final UUID conversationId;
    private final Instant createdAtFrom;
    private final Instant createdAtTo;
    private final String workflowName;

    public RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto(
            UUID conversationId, Instant createdAtFrom, Instant createdAtTo, String workflowName) {
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
