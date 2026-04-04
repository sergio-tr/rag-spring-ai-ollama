package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummaryDto(
        UUID id,
        String name,
        String description,
        long docCount,
        long convCount,
        Instant updatedAt,
        String projectPrompt) {
}
