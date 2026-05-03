package com.uniovi.rag.application.service.runtime.traceregressionsuiteexport;

/** Final ZIP exceeds the hard cap (P32). */
public class RuntimeTraceRegressionSuiteExportSizeExceededException extends RuntimeException {

    public RuntimeTraceRegressionSuiteExportSizeExceededException(String message) {
        super(message);
    }
}
