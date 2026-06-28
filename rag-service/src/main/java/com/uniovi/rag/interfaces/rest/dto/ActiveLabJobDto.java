package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Active Lab job (server source of truth) for automatic recovery after reload/tab focus.
 */
public record ActiveLabJobDto(
        UUID jobId,
        String benchmarkKind,
        UUID evaluationRunId,
        UUID projectId,
        UUID datasetId,
        String status,
        String progress,
        Instant startedAt,
        Instant updatedAt,
        String pollPath,
        String streamPath,
        boolean cancellable) {}

