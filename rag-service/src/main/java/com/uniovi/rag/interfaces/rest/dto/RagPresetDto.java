package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RagPresetDto(
        UUID id,
        String name,
        String description,
        List<String> tags,
        Map<String, Object> values,
        boolean system,
        Instant createdAt,
        Instant updatedAt) {}
