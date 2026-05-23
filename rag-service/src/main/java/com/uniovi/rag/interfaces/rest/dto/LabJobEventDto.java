package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Single Lab job event for SSE streams and resume (`?since=eventId`). */
public record LabJobEventDto(
        long eventId,
        UUID jobId,
        String type,
        String status,
        String progress,
        String message,
        Instant timestamp,
        Map<String, Object> payload) {}
