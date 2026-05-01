package com.uniovi.rag.application.service.evaluation;

import java.util.UUID;

/** 202 response payload: canonical run id + async task id. */
public record BenchmarkJobAccepted(UUID evaluationRunId, UUID asyncTaskId) {

    public static BenchmarkJobAccepted of(UUID evaluationRunId, UUID asyncTaskId) {
        return new BenchmarkJobAccepted(evaluationRunId, asyncTaskId);
    }
}
