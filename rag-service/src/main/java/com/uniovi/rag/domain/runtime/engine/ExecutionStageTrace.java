package com.uniovi.rag.domain.runtime.engine;

/**
 * Single stage within an execution trace. Immutable.
 */
public record ExecutionStageTrace(
        String stageName, long durationMs, ExecutionStageOutcome outcome, String message) {

    public ExecutionStageTrace {
        if (stageName == null || stageName.isBlank()) {
            throw new IllegalArgumentException("stageName required");
        }
        message = message == null ? "" : message;
    }
}
