package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport;

/**
 * Raised when the built definition export ZIP exceeds the configured maximum size (P38 FD17).
 */
public class RuntimeTraceRegressionSuiteDefinitionExportSizeExceededException extends RuntimeException {

    public RuntimeTraceRegressionSuiteDefinitionExportSizeExceededException(String message) {
        super(message);
    }
}
