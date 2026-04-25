package com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "runtime_trace_regression_suite_run")
public class RuntimeTraceRegressionSuiteRunEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private RuntimeTraceRegressionSuiteRunSourceType sourceType;

    @Column(name = "definition_id")
    private UUID definitionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "suite_outcome", nullable = false, length = 64)
    private RuntimeTraceRegressionSuiteOutcome suiteOutcome;

    @Column(name = "requested_entry_count", nullable = false)
    private int requestedEntryCount;

    @Column(name = "processed_entry_count", nullable = false)
    private int processedEntryCount;

    @Column(name = "batch_returned_count", nullable = false)
    private int batchReturnedCount;

    @Column(name = "execution_failed_count", nullable = false)
    private int executionFailedCount;

    @Column(name = "batch_not_attempted_subcount", nullable = false)
    private int batchNotAttemptedSubcount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public RuntimeTraceRegressionSuiteRunSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(RuntimeTraceRegressionSuiteRunSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public UUID getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(UUID definitionId) {
        this.definitionId = definitionId;
    }

    public RuntimeTraceRegressionSuiteOutcome getSuiteOutcome() {
        return suiteOutcome;
    }

    public void setSuiteOutcome(RuntimeTraceRegressionSuiteOutcome suiteOutcome) {
        this.suiteOutcome = suiteOutcome;
    }

    public int getRequestedEntryCount() {
        return requestedEntryCount;
    }

    public void setRequestedEntryCount(int requestedEntryCount) {
        this.requestedEntryCount = requestedEntryCount;
    }

    public int getProcessedEntryCount() {
        return processedEntryCount;
    }

    public void setProcessedEntryCount(int processedEntryCount) {
        this.processedEntryCount = processedEntryCount;
    }

    public int getBatchReturnedCount() {
        return batchReturnedCount;
    }

    public void setBatchReturnedCount(int batchReturnedCount) {
        this.batchReturnedCount = batchReturnedCount;
    }

    public int getExecutionFailedCount() {
        return executionFailedCount;
    }

    public void setExecutionFailedCount(int executionFailedCount) {
        this.executionFailedCount = executionFailedCount;
    }

    public int getBatchNotAttemptedSubcount() {
        return batchNotAttemptedSubcount;
    }

    public void setBatchNotAttemptedSubcount(int batchNotAttemptedSubcount) {
        this.batchNotAttemptedSubcount = batchNotAttemptedSubcount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
