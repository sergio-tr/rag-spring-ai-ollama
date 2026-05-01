package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport;

/** Thrown when a run import ZIP fails structural, manifest, or run.json validation. */
public class RuntimeTraceRegressionSuiteRunImportRejectedException extends RuntimeException {

    public RuntimeTraceRegressionSuiteRunImportRejectedException(String message) {
        super(message);
    }

    public RuntimeTraceRegressionSuiteRunImportRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
