package com.uniovi.rag.domain.preset;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UserRagPreset(
        UUID id,
        String name,
        String description,
        List<String> tags,
        Map<String, Object> values,
        boolean system,
        Instant createdAt,
        Instant updatedAt) {}
