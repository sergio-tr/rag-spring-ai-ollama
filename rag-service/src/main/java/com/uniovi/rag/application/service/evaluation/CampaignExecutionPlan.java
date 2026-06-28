package com.uniovi.rag.application.service.evaluation;

import java.util.List;
import java.util.UUID;

/**
 * Materialized campaign work units executed under a single {@code async_task} (one SSE stream).
 */
public record CampaignExecutionPlan(UUID campaignId, List<CampaignRunAxis> axes, int totalItems) {

    public record CampaignRunAxis(UUID runId, String axisLabel, int itemCount) {}
}
