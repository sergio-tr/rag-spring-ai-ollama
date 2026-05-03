package com.uniovi.rag.application.service.runtime.tracereplaybatchexport;

/**
 * Thrown when {@link com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchOutcome} is {@code
 * NOT_ATTEMPTED}; maps to HTTP {@code 400} with no ZIP body.
 */
public class RuntimeTraceReplayBatchExportNotAttemptedException extends RuntimeException {

    public RuntimeTraceReplayBatchExportNotAttemptedException(String message) {
        super(message);
    }
}
