package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ResolvedConfigSnapshotResponse(
        UUID id,
        Instant createdAt,
        Map<String, Object> payload,
        Map<String, Object> capabilitySet,
        Map<String, Object> compatibilityResult,
        Map<String, Object> reindexImpact,
        Map<String, Object> systemPromptLayers,
        String effectiveSystemPrompt,
        Map<String, Object> provenance,
        String configHash,
        UUID conversationId,
        UUID messageId,
        UUID jobId) {}
