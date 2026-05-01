package com.uniovi.rag.domain.runtime.traceregressionsuite;

/**
 * Terminal suite outcome (P30 closed set).
 */
public enum RuntimeTraceRegressionSuiteOutcome {
    NOT_ATTEMPTED,
    EMPTY_SUITE,
    COMPLETED_ALL_BATCH_RETURNS,
    COMPLETED_WITH_ENTRY_EXECUTION_FAILURES
}
