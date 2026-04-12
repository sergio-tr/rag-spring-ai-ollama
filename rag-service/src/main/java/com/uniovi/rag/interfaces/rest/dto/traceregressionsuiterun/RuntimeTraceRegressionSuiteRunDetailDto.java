package com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryFailureKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteExecutionFailedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistedExecutionStatus;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * P42 detail JSON shape: flat summary scalars plus {@code entries} (no nested summary object).
 *
 * <p>P44: {@link #toRuntimeTraceRegressionSuiteResultForImport()} rebuilds a {@link RuntimeTraceRegressionSuiteResult} from
 * imported {@code run.json}. The {@link #id()} and {@link #createdAt()} values from that JSON are not used as the persisted
 * primary key or {@code createdAt} column — P41 assigns new identifiers and timestamps via {@code createRun}.
 */
public record RuntimeTraceRegressionSuiteRunDetailDto(
        UUID id,
        String sourceType,
        UUID definitionId,
        String suiteOutcome,
        Instant createdAt,
        int requestedEntryCount,
        int processedEntryCount,
        int batchReturnedCount,
        int executionFailedCount,
        int batchNotAttemptedSubcount,
        List<RuntimeTraceRegressionSuiteRunEntryDto> entries) {

    public RuntimeTraceRegressionSuiteRunDetailDto {
        entries = List.copyOf(entries);
    }

    public static RuntimeTraceRegressionSuiteRunDetailDto fromSnapshot(RuntimeTraceRegressionSuiteRunSnapshot snap) {
        List<RuntimeTraceRegressionSuiteRunEntrySnapshot> ordered = new ArrayList<>(snap.entries());
        ordered.sort(Comparator.comparingInt(RuntimeTraceRegressionSuiteRunEntrySnapshot::entryOrder));
        List<RuntimeTraceRegressionSuiteRunEntryDto> entryDtos =
                ordered.stream().map(RuntimeTraceRegressionSuiteRunEntryDto::fromSnapshot).toList();
        var sum = snap.summary();
        return new RuntimeTraceRegressionSuiteRunDetailDto(
                snap.id().value(),
                snap.sourceType().name(),
                snap.definitionId().orElse(null),
                snap.suiteOutcome().name(),
                snap.createdAt(),
                sum.requestedEntryCount(),
                sum.processedEntryCount(),
                sum.batchReturnedCount(),
                sum.executionFailedCount(),
                sum.batchNotAttemptedSubcount(),
                entryDtos);
    }

    /**
     * P44: maps this DTO (from imported {@code run.json}) to a {@link RuntimeTraceRegressionSuiteResult} suitable for
     * {@link com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService#createRun}.
     */
    public RuntimeTraceRegressionSuiteResult toRuntimeTraceRegressionSuiteResultForImport() {
        RuntimeTraceRegressionSuiteOutcome outcome = parseEnum(suiteOutcome(), RuntimeTraceRegressionSuiteOutcome.class);
        RuntimeTraceRegressionSuiteSummary summary =
                new RuntimeTraceRegressionSuiteSummary(
                        requestedEntryCount(),
                        processedEntryCount(),
                        batchReturnedCount(),
                        executionFailedCount(),
                        batchNotAttemptedSubcount());
        List<RuntimeTraceRegressionSuiteRunEntryDto> sorted =
                entries.stream()
                        .sorted(Comparator.comparingInt(RuntimeTraceRegressionSuiteRunEntryDto::entryOrder))
                        .toList();
        if (sorted.size() != requestedEntryCount()) {
            throw new IllegalArgumentException("P44 import: entries size mismatch requestedEntryCount");
        }
        List<RuntimeTraceRegressionSuiteEntryResult> entryResults = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            entryResults.add(mapEntryToResult(sorted.get(i), i));
        }
        return new RuntimeTraceRegressionSuiteResult(outcome, summary, entryResults);
    }

    private static RuntimeTraceRegressionSuiteEntryResult mapEntryToResult(
            RuntimeTraceRegressionSuiteRunEntryDto e, int expectedOrder) {
        if (e.entryOrder() != expectedOrder) {
            throw new IllegalArgumentException("P44 import: entryOrder mismatch");
        }
        RuntimeTraceRegressionSuiteEntryKind kind = parseEnum(e.entryKind(), RuntimeTraceRegressionSuiteEntryKind.class);
        RuntimeTraceRegressionSuiteRunPersistedExecutionStatus exec =
                parseEnum(e.executionStatus(), RuntimeTraceRegressionSuiteRunPersistedExecutionStatus.class);
        String echo = e.selectorEcho() != null ? e.selectorEcho() : "";
        return switch (exec) {
            case BATCH_RETURNED -> {
                if (e.batchOutcome() == null
                        || e.requestedCount() == null
                        || e.selectedCount() == null
                        || e.processedCount() == null) {
                    throw new IllegalArgumentException("P44 import: BATCH_RETURNED entry missing required fields");
                }
                yield new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        expectedOrder,
                        kind,
                        echo,
                        parseEnum(e.batchOutcome(), RuntimeTraceReplayComparisonBatchOutcome.class),
                        e.requestedCount(),
                        e.selectedCount(),
                        e.processedCount());
            }
            case EXECUTION_FAILED -> {
                if (e.failureKind() == null) {
                    throw new IllegalArgumentException("P44 import: EXECUTION_FAILED entry missing failureKind");
                }
                yield new RuntimeTraceRegressionSuiteExecutionFailedEntryResult(
                        expectedOrder,
                        kind,
                        echo,
                        parseEnum(e.failureKind(), RuntimeTraceRegressionSuiteEntryFailureKind.class),
                        e.failureDetail() != null ? e.failureDetail() : "");
            }
        };
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("P44 import: missing " + type.getSimpleName());
        }
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("P44 import: invalid " + type.getSimpleName() + ": " + raw, ex);
        }
    }
}
