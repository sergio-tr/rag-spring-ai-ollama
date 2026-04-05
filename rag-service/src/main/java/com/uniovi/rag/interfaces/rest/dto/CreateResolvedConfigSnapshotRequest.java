package com.uniovi.rag.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Same shape as {@link RuntimeConfigPreviewRequest} plus optional correlation and snapshot linkage IDs.
 */
public record CreateResolvedConfigSnapshotRequest(
        @NotNull UUID projectId,
        UUID presetId,
        UUID conversationId,
        UUID messageId,
        UUID jobId,
        Map<String, Object> runtimeOverride,
        List<String> touchedProfileTypes,
        CapabilitySetDto baselineCapabilitySnapshot,
        String correlationId) {}
