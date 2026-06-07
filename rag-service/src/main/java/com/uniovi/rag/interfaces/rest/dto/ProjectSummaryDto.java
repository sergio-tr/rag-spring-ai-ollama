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
        String projectPrompt,
        String colorHex,
        String iconKey,
        /** Persisted project index profile; {@code null} omitted on list responses (avoid N+1). */
        ProjectIndexProfileDto indexProfile) {

    public ProjectSummaryDto(
            UUID id,
            String name,
            String description,
            long docCount,
            long convCount,
            Instant updatedAt,
            String projectPrompt,
            String colorHex,
            String iconKey) {
        this(id, name, description, docCount, convCount, updatedAt, projectPrompt, colorHex, iconKey, null);
    }
}
