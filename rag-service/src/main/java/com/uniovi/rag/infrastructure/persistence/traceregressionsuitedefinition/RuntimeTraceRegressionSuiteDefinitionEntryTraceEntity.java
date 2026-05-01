package com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "runtime_trace_regression_suite_definition_entry_trace")
public class RuntimeTraceRegressionSuiteDefinitionEntryTraceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private RuntimeTraceRegressionSuiteDefinitionEntryEntity entry;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "trace_id", nullable = false)
    private UUID traceId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RuntimeTraceRegressionSuiteDefinitionEntryEntity getEntry() {
        return entry;
    }

    public void setEntry(RuntimeTraceRegressionSuiteDefinitionEntryEntity entry) {
        this.entry = entry;
    }

    public short getPosition() {
        return position;
    }

    public void setPosition(short position) {
        this.position = position;
    }

    public UUID getTraceId() {
        return traceId;
    }

    public void setTraceId(UUID traceId) {
        this.traceId = traceId;
    }
}
