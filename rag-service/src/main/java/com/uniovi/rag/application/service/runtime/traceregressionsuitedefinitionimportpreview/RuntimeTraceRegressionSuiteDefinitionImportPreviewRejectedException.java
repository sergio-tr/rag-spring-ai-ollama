package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview;

/**
 * Raised when an import-preview ZIP fails structural, manifest, or definition validation (P40).
 */
public class RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException extends RuntimeException {

    public RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException(String message) {
        super(message);
    }

    public RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
