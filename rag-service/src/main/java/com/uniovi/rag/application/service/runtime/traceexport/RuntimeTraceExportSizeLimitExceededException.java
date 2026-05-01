package com.uniovi.rag.application.service.runtime.traceexport;

/**
 * Thrown when a trace export exceeds the synchronous P17 ZIP size limit.
 * This is an application-layer exception; HTTP mapping is done in the REST adapter.
 */
public class RuntimeTraceExportSizeLimitExceededException extends RuntimeException {

    public RuntimeTraceExportSizeLimitExceededException(String message) {
        super(message);
    }
}

