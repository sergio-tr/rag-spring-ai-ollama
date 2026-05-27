package com.uniovi.rag.application.service.evaluation;

import java.util.Optional;
import java.util.UUID;

/** 202 response payload: canonical run id + async task id (optional campaign id for multi-model starts). */
public record BenchmarkJobAccepted(
        UUID evaluationRunId,
        UUID asyncTaskId,
        Optional<UUID> campaignId,
        Optional<Integer> totalItems) {

    public static BenchmarkJobAccepted of(UUID evaluationRunId, UUID asyncTaskId) {
        return new BenchmarkJobAccepted(evaluationRunId, asyncTaskId, Optional.empty(), Optional.empty());
    }

    public static BenchmarkJobAccepted ofCampaign(UUID evaluationRunId, UUID asyncTaskId, UUID campaignId) {
        return ofCampaign(evaluationRunId, asyncTaskId, campaignId, null);
    }

    public static BenchmarkJobAccepted ofCampaign(
            UUID evaluationRunId, UUID asyncTaskId, UUID campaignId, Integer totalItems) {
        return new BenchmarkJobAccepted(
                evaluationRunId,
                asyncTaskId,
                Optional.of(campaignId),
                totalItems != null && totalItems > 0 ? Optional.of(totalItems) : Optional.empty());
    }
}
