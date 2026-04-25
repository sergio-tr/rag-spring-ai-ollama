package com.uniovi.rag.domain.runtime.traceregressionsuite;

/**
 * One suite slot where request construction or P24 {@code execute} threw a captured {@link Exception} (P30).
 */
public record RuntimeTraceRegressionSuiteExecutionFailedEntryResult(
        int entryOrder,
        RuntimeTraceRegressionSuiteEntryKind entryKind,
        String selectorEcho,
        RuntimeTraceRegressionSuiteEntryFailureKind failureKind,
        String failureDetail)
        implements RuntimeTraceRegressionSuiteEntryResult {

    public RuntimeTraceRegressionSuiteExecutionFailedEntryResult {
        selectorEcho = selectorEcho == null ? "" : selectorEcho;
        failureDetail = failureDetail == null ? "" : failureDetail;
    }
}
