package com.uniovi.rag.interfaces.rest.dto;

import java.util.UUID;

/** HTTP 202 body: canonical evaluation run + async task for polling. */
public record BenchmarkJobAcceptedDto(
        UUID evaluationRunId,
        UUID asyncTaskId,
        UUID campaignId,
        Integer totalItems,
        String status,
        String pollPath,
        String streamPath) {}
