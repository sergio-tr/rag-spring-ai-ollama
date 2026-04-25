package com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport;

/** Thrown when the assembled run export ZIP exceeds {@link RuntimeTraceRegressionSuiteRunExportService#MAX_ZIP_SIZE_BYTES}. */
public class RuntimeTraceRegressionSuiteRunExportSizeExceededException extends RuntimeException {

    public RuntimeTraceRegressionSuiteRunExportSizeExceededException(String message) {
        super(message);
    }
}
