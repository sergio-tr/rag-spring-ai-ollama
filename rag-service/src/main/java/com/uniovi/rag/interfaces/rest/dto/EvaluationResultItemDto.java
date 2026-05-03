package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvaluationResultItemDto(
        UUID id,
        String questionText,
        String expectedAnswer,
        String actualAnswer,
        Integer correctness,
        String queryType,
        Long latencyMs,
        String benchmarkKind,
        Map<String, Object> metricsPayload,
        Instant evaluatedAt) {}
