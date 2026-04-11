package com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryFailureKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistedExecutionStatus;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "runtime_trace_regression_suite_run_entry")
public class RuntimeTraceRegressionSuiteRunEntryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "entry_order", nullable = false)
    private short entryOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_kind", nullable = false, length = 32)
    private RuntimeTraceRegressionSuiteEntryKind entryKind;

    @Column(name = "selector_echo", nullable = false, length = 256)
    private String selectorEcho;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 32)
    private RuntimeTraceRegressionSuiteRunPersistedExecutionStatus executionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_outcome", length = 64)
    private RuntimeTraceReplayComparisonBatchOutcome batchOutcome;

    @Column(name = "requested_count")
    private Integer requestedCount;

    @Column(name = "selected_count")
    private Integer selectedCount;

    @Column(name = "processed_count")
    private Integer processedCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_kind", length = 32)
    private RuntimeTraceRegressionSuiteEntryFailureKind failureKind;

    @Column(name = "failure_detail", length = 1024)
    private String failureDetail;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public short getEntryOrder() {
        return entryOrder;
    }

    public void setEntryOrder(short entryOrder) {
        this.entryOrder = entryOrder;
    }

    public RuntimeTraceRegressionSuiteEntryKind getEntryKind() {
        return entryKind;
    }

    public void setEntryKind(RuntimeTraceRegressionSuiteEntryKind entryKind) {
        this.entryKind = entryKind;
    }

    public String getSelectorEcho() {
        return selectorEcho;
    }

    public void setSelectorEcho(String selectorEcho) {
        this.selectorEcho = selectorEcho;
    }

    public RuntimeTraceRegressionSuiteRunPersistedExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(RuntimeTraceRegressionSuiteRunPersistedExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public RuntimeTraceReplayComparisonBatchOutcome getBatchOutcome() {
        return batchOutcome;
    }

    public void setBatchOutcome(RuntimeTraceReplayComparisonBatchOutcome batchOutcome) {
        this.batchOutcome = batchOutcome;
    }

    public Integer getRequestedCount() {
        return requestedCount;
    }

    public void setRequestedCount(Integer requestedCount) {
        this.requestedCount = requestedCount;
    }

    public Integer getSelectedCount() {
        return selectedCount;
    }

    public void setSelectedCount(Integer selectedCount) {
        this.selectedCount = selectedCount;
    }

    public Integer getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(Integer processedCount) {
        this.processedCount = processedCount;
    }

    public RuntimeTraceRegressionSuiteEntryFailureKind getFailureKind() {
        return failureKind;
    }

    public void setFailureKind(RuntimeTraceRegressionSuiteEntryFailureKind failureKind) {
        this.failureKind = failureKind;
    }

    public String getFailureDetail() {
        return failureDetail;
    }

    public void setFailureDetail(String failureDetail) {
        this.failureDetail = failureDetail;
    }
}
