package com.uniovi.rag.domain.runtime.traceregressionsuite;

/**
 * Suite-level counters only (P30) — no rolled-up P24 batch aggregates beyond these definitions.
 */
public record RuntimeTraceRegressionSuiteSummary(
        int requestedEntryCount,
        int processedEntryCount,
        int batchReturnedCount,
        int executionFailedCount,
        int batchNotAttemptedSubcount) {}
