package com.uniovi.rag.application.service.runtime.traceregressionsuiteexport;

/** Suite outcome is {@code NOT_ATTEMPTED} — not exportable as ZIP (P32). */
public class RuntimeTraceRegressionSuiteExportNotAttemptedException extends RuntimeException {

    public RuntimeTraceRegressionSuiteExportNotAttemptedException(String message) {
        super(message);
    }
}
