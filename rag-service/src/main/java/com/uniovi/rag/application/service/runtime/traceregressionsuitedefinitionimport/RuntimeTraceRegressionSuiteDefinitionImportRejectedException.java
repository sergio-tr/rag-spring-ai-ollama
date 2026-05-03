package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport;

/**
 * Raised when an import ZIP fails structural, manifest, or definition validation (P39).
 */
public class RuntimeTraceRegressionSuiteDefinitionImportRejectedException extends RuntimeException {

    public RuntimeTraceRegressionSuiteDefinitionImportRejectedException(String message) {
        super(message);
    }

    public RuntimeTraceRegressionSuiteDefinitionImportRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
