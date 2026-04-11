package com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport;

/**
 * Thrown when the P26 batch export ZIP exceeds the synchronous size limit ({@code 413}).
 */
public class RuntimeTraceReplayComparisonBatchExportSizeExceededException extends RuntimeException {

    public RuntimeTraceReplayComparisonBatchExportSizeExceededException(String message) {
        super(message);
    }
}
