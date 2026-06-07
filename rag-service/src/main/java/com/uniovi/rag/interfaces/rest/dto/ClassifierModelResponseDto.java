package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Registered classifier artifact: {@link #name()} is the display label; {@link #inferenceTag()} is the classifier-service
 * model id (directory tag / {@code default}) written to RAG {@code classifierModelId}.
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
