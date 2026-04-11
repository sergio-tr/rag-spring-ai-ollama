package com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "runtime_trace_regression_suite_definition_entry")
public class RuntimeTraceRegressionSuiteDefinitionEntryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private RuntimeTraceRegressionSuiteDefinitionEntity definition;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "entry_kind", nullable = false, length = 32)
    private String entryKind;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "created_at_from")
    private Instant createdAtFrom;

    @Column(name = "created_at_to")
    private Instant createdAtTo;

    @Column(name = "workflow_name", length = 256)
    private String workflowName;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RuntimeTraceRegressionSuiteDefinitionEntity getDefinition() {
        return definition;
    }

    public void setDefinition(RuntimeTraceRegressionSuiteDefinitionEntity definition) {
        this.definition = definition;
    }

    public short getPosition() {
        return position;
    }

    public void setPosition(short position) {
        this.position = position;
    }

    public String getEntryKind() {
        return entryKind;
    }

    public void setEntryKind(String entryKind) {
        this.entryKind = entryKind;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public Instant getCreatedAtFrom() {
        return createdAtFrom;
    }

    public void setCreatedAtFrom(Instant createdAtFrom) {
        this.createdAtFrom = createdAtFrom;
    }

    public Instant getCreatedAtTo() {
        return createdAtTo;
    }

    public void setCreatedAtTo(Instant createdAtTo) {
        this.createdAtTo = createdAtTo;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }
}
