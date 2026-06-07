package com.uniovi.rag.interfaces.rest.dto;

public record UpsertProjectIndexProfileRequest(
        String materializationStrategy,
        Boolean metadataEnabled,
        String metadataProfile,
        String embeddingModelId,
        Integer chunkMaxChars,
        Integer chunkOverlap
) {}

