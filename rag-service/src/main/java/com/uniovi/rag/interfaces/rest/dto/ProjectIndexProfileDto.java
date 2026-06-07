package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectIndexProfileDto(
        UUID projectId,
        String materializationStrategy,
        boolean metadataEnabled,
        String metadataProfile,
        String embeddingModelId,
        int chunkMaxChars,
        Integer chunkOverlap,
        String profileHash,
        Instant createdAt,
        Instant updatedAt
) {}

