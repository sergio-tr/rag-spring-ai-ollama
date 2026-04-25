package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview;

/**
 * Raised when a run import-preview ZIP fails structural, manifest, coherence, or source validation (P45).
 */
public class RuntimeTraceRegressionSuiteRunImportPreviewRejectedException extends RuntimeException {

    public RuntimeTraceRegressionSuiteRunImportPreviewRejectedException(String message) {
        super(message);
    }

    public RuntimeTraceRegressionSuiteRunImportPreviewRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
