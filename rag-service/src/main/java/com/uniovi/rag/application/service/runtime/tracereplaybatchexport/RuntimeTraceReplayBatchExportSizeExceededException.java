package com.uniovi.rag.application.service.runtime.tracereplaybatchexport;

/**
 * Thrown when the P29 batch export ZIP exceeds the synchronous size limit ({@code 413}).
 */
public class RuntimeTraceReplayBatchExportSizeExceededException extends RuntimeException {

    public RuntimeTraceReplayBatchExportSizeExceededException(String message) {
        super(message);
    }
}
