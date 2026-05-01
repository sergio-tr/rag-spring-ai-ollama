package com.uniovi.rag.domain.runtime.traceregressionsuite;

/**
 * Sealed per-entry suite result — batch returned vs execution failed (P30).
 */
public sealed interface RuntimeTraceRegressionSuiteEntryResult
        permits RuntimeTraceRegressionSuiteBatchReturnedEntryResult,
                RuntimeTraceRegressionSuiteExecutionFailedEntryResult {}
