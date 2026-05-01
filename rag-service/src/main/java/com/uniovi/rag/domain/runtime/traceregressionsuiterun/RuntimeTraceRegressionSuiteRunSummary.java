package com.uniovi.rag.domain.runtime.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** List-row projection for a persisted suite run header (P41). */
public record RuntimeTraceRegressionSuiteRunSummary(
        RuntimeTraceRegressionSuiteRunId id,
        RuntimeTraceRegressionSuiteRunSourceType sourceType,
        Optional<UUID> definitionId,
        RuntimeTraceRegressionSuiteOutcome suiteOutcome,
        Instant createdAt,
        int requestedEntryCount,
        int processedEntryCount,
        int batchReturnedCount,
        int executionFailedCount,
        int batchNotAttemptedSubcount) {}
