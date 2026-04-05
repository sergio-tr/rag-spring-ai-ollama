package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvaluationRunDetailDto(
        UUID id,
        String name,
        String status,
        String benchmarkKind,
        String runKind,
        String workflowSchemaVersion,
        String datasetSha256,
        UUID datasetId,
        UUID asyncTaskId,
        UUID resolvedConfigSnapshotId,
        UUID indexSnapshotId,
        String indexSignatureHash,
        UUID presetId,
        String llmModelId,
        String embeddingModelId,
        String classifierModelId,
        Map<String, Object> aggregatesJson,
        Instant createdAt,
        Instant completedAt) {}
