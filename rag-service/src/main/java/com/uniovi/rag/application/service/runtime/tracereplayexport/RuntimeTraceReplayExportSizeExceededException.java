package com.uniovi.rag.application.service.runtime.tracereplayexport;

/**
 * Thrown when the P23 replay export ZIP exceeds the synchronous size limit.
 */
public class RuntimeTraceReplayExportSizeExceededException extends RuntimeException {

    public RuntimeTraceReplayExportSizeExceededException(String message) {
        super(message);
    }
}
