package com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSummary;

import java.time.Instant;
import java.util.UUID;

/** P42 list/detail summary row JSON shape. */
public record RuntimeTraceRegressionSuiteRunSummaryDto(
        UUID id,
        String sourceType,
        UUID definitionId,
        String suiteOutcome,
        Instant createdAt,
        int requestedEntryCount,
        int processedEntryCount,
        int batchReturnedCount,
        int executionFailedCount,
        int batchNotAttemptedSubcount) {

    public static RuntimeTraceRegressionSuiteRunSummaryDto fromSummary(RuntimeTraceRegressionSuiteRunSummary s) {
        return new RuntimeTraceRegressionSuiteRunSummaryDto(
                s.id().value(),
                s.sourceType().name(),
                s.definitionId().orElse(null),
                s.suiteOutcome().name(),
                s.createdAt(),
                s.requestedEntryCount(),
                s.processedEntryCount(),
                s.batchReturnedCount(),
                s.executionFailedCount(),
                s.batchNotAttemptedSubcount());
    }
}
