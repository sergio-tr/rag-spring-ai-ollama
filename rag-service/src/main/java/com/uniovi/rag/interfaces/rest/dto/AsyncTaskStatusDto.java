package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AsyncTaskStatusDto(
        UUID id,
        String taskType,
        String status,
        String progressText,
        Map<String, Object> result,
        String errorMessage,
        boolean terminal,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt) {}
