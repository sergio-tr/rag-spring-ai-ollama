package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

/**
 * Thrown when the P21 replay-comparison ZIP exceeds the synchronous size limit.
 */
public class RuntimeTraceReplayComparisonExportSizeExceededException extends RuntimeException {

    public RuntimeTraceReplayComparisonExportSizeExceededException(String message) {
        super(message);
    }
}
