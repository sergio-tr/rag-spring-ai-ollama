package com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** P42 detail JSON shape: flat summary scalars plus {@code entries} (no nested summary object). */
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
}
