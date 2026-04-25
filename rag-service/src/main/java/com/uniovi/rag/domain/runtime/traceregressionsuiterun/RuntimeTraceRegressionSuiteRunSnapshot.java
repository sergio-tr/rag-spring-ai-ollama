package com.uniovi.rag.domain.runtime.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Full persisted run with ordered entries (P41). */
public record RuntimeTraceRegressionSuiteRunSnapshot(
        RuntimeTraceRegressionSuiteRunId id,
        UUID userId,
        RuntimeTraceRegressionSuiteRunSourceType sourceType,
        Optional<UUID> definitionId,
        RuntimeTraceRegressionSuiteOutcome suiteOutcome,
        RuntimeTraceRegressionSuiteSummary summary,
        Instant createdAt,
        List<RuntimeTraceRegressionSuiteRunEntrySnapshot> entries) {

    public RuntimeTraceRegressionSuiteRunSnapshot {
        entries = List.copyOf(entries);
    }
}
