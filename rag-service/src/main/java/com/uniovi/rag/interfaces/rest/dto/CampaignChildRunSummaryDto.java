package com.uniovi.rag.interfaces.rest.dto;

import java.util.UUID;

/** One child {@code evaluation_run} axis within a multi-preset or multi-model campaign. */
public record CampaignChildRunSummaryDto(
        UUID runId,
        String presetKey,
        String presetLabel,
        String comparisonLabel,
        String modelId,
        String status,
        int persistedItemCount) {}
