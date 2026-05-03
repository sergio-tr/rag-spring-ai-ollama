package com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteExecutionFailedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunId;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistedExecutionStatus;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Maps P41 run entities to domain read models and builds insert entities from P30 results. */
@Component
public class RuntimeTraceRegressionSuiteRunPersistenceMapper {

    public RuntimeTraceRegressionSuiteRunSummary toSummary(RuntimeTraceRegressionSuiteRunEntity row) {
        return new RuntimeTraceRegressionSuiteRunSummary(
                new RuntimeTraceRegressionSuiteRunId(row.getId()),
                row.getSourceType(),
                Optional.ofNullable(row.getDefinitionId()),
                row.getSuiteOutcome(),
                row.getCreatedAt(),
                row.getRequestedEntryCount(),
                row.getProcessedEntryCount(),
                row.getBatchReturnedCount(),
                row.getExecutionFailedCount(),
                row.getBatchNotAttemptedSubcount());
    }

    public RuntimeTraceRegressionSuiteRunSnapshot toSnapshot(
            RuntimeTraceRegressionSuiteRunEntity run, List<RuntimeTraceRegressionSuiteRunEntryEntity> entriesOrdered) {
        List<RuntimeTraceRegressionSuiteRunEntrySnapshot> snaps = new ArrayList<>(entriesOrdered.size());
        for (RuntimeTraceRegressionSuiteRunEntryEntity e : entriesOrdered) {
            snaps.add(toEntrySnapshot(e));
        }
        RuntimeTraceRegressionSuiteSummary summary =
                new RuntimeTraceRegressionSuiteSummary(
                        run.getRequestedEntryCount(),
                        run.getProcessedEntryCount(),
                        run.getBatchReturnedCount(),
                        run.getExecutionFailedCount(),
                        run.getBatchNotAttemptedSubcount());
        return new RuntimeTraceRegressionSuiteRunSnapshot(
                new RuntimeTraceRegressionSuiteRunId(run.getId()),
                run.getUserId(),
                run.getSourceType(),
                Optional.ofNullable(run.getDefinitionId()),
                run.getSuiteOutcome(),
                summary,
                run.getCreatedAt(),
                snaps);
    }

    private static RuntimeTraceRegressionSuiteRunEntrySnapshot toEntrySnapshot(RuntimeTraceRegressionSuiteRunEntryEntity e) {
        if (e.getExecutionStatus() == RuntimeTraceRegressionSuiteRunPersistedExecutionStatus.BATCH_RETURNED) {
            return new RuntimeTraceRegressionSuiteRunEntrySnapshot(
                    e.getEntryOrder(),
                    e.getEntryKind(),
                    e.getSelectorEcho(),
                    e.getExecutionStatus(),
                    Optional.ofNullable(e.getBatchOutcome()),
                    Optional.ofNullable(e.getRequestedCount()),
                    Optional.ofNullable(e.getSelectedCount()),
                    Optional.ofNullable(e.getProcessedCount()),
                    Optional.empty(),
                    Optional.empty());
        }
        return new RuntimeTraceRegressionSuiteRunEntrySnapshot(
                e.getEntryOrder(),
                e.getEntryKind(),
                e.getSelectorEcho(),
                e.getExecutionStatus(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(e.getFailureKind()),
                Optional.ofNullable(e.getFailureDetail()));
    }

    public RuntimeTraceRegressionSuiteRunEntryEntity newEntryEntity(
            UUID id, UUID runId, RuntimeTraceRegressionSuiteEntryResult row, String selectorEchoCapped) {
        RuntimeTraceRegressionSuiteRunEntryEntity e = new RuntimeTraceRegressionSuiteRunEntryEntity();
        e.setId(id);
        e.setRunId(runId);
        if (row instanceof RuntimeTraceRegressionSuiteBatchReturnedEntryResult br) {
            e.setEntryOrder((short) br.entryOrder());
            e.setEntryKind(br.entryKind());
            e.setSelectorEcho(selectorEchoCapped);
            e.setExecutionStatus(RuntimeTraceRegressionSuiteRunPersistedExecutionStatus.BATCH_RETURNED);
            e.setBatchOutcome(br.batchOutcome());
            e.setRequestedCount(br.requestedCount());
            e.setSelectedCount(br.selectedCount());
            e.setProcessedCount(br.processedCount());
            e.setFailureKind(null);
            e.setFailureDetail(null);
        } else if (row instanceof RuntimeTraceRegressionSuiteExecutionFailedEntryResult fr) {
            e.setEntryOrder((short) fr.entryOrder());
            e.setEntryKind(fr.entryKind());
            e.setSelectorEcho(selectorEchoCapped);
            e.setExecutionStatus(RuntimeTraceRegressionSuiteRunPersistedExecutionStatus.EXECUTION_FAILED);
            e.setBatchOutcome(null);
            e.setRequestedCount(null);
            e.setSelectedCount(null);
            e.setProcessedCount(null);
            e.setFailureKind(fr.failureKind());
            e.setFailureDetail(fr.failureDetail());
        } else {
            throw new IllegalStateException("unknown entry result type");
        }
        return e;
    }
}
