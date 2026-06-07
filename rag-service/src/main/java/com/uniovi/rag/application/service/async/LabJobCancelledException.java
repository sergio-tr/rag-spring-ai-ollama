package com.uniovi.rag.application.service.async;

/**
 * Internal control-flow exception used to stop Lab async handlers after cancellation.
 */
public final class LabJobCancelledException extends RuntimeException {

    public LabJobCancelledException(String message) {
        super(message != null ? message : "Job cancelled");
    }
}

