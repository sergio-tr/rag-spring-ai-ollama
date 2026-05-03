package com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport;

/**
 * Thrown when {@link com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome} is
 * {@code NOT_ATTEMPTED}; maps to HTTP {@code 400} with no ZIP body.
 */
public class RuntimeTraceReplayComparisonBatchExportNotAttemptedException extends RuntimeException {

    public RuntimeTraceReplayComparisonBatchExportNotAttemptedException(String message) {
        super(message);
    }
}
