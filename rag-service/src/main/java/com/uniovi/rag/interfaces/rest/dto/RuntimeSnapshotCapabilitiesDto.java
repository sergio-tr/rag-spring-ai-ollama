package com.uniovi.rag.interfaces.rest.dto;

/**
 * Normalized active snapshot capabilities (index-time).
 */
public record RuntimeSnapshotCapabilitiesDto(
        String materializationStrategy,
        Boolean supportsMetadata,
        String embeddingModelId,
        Integer chunkMaxChars,
        Integer chunkOverlap
) {}

