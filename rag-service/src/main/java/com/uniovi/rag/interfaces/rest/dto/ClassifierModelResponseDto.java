package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Registered classifier artifact: {@link #inferenceTag()} is the value written to {@code classifierModelId} in RAG
 * config (classifier-service model tag).
 */
public record ClassifierModelResponseDto(
        UUID id,
        String name,
        String inferenceTag,
        String status,
        Instant trainedAt,
        Double accuracy,
        Double f1Macro,
        boolean active,
        Map<String, Object> hyperparams) {}
