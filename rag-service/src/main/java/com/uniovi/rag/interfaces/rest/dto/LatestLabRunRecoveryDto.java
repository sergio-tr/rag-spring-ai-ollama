package com.uniovi.rag.interfaces.rest.dto;

import java.util.Map;
import java.util.UUID;

/** Latest benchmark run for Lab page recovery when no active job is listed. */
public record LatestLabRunRecoveryDto(
        UUID evaluationRunId,
        UUID jobId,
        String benchmarkKind,
        UUID projectId,
        String status,
        boolean terminal,
        String pollPath,
        String streamPath,
        Map<String, Object> result) {}
