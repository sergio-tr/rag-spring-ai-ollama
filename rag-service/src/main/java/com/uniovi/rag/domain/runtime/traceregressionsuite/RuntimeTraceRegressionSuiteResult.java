package com.uniovi.rag.domain.runtime.traceregressionsuite;

import java.util.List;

/**
 * Transient P30 suite result (never persisted).
 */
public record RuntimeTraceRegressionSuiteResult(
        RuntimeTraceRegressionSuiteOutcome suiteOutcome,
        RuntimeTraceRegressionSuiteSummary summary,
        List<RuntimeTraceRegressionSuiteEntryResult> entryResults) {

    public RuntimeTraceRegressionSuiteResult {
        entryResults = List.copyOf(entryResults);
    }
}
