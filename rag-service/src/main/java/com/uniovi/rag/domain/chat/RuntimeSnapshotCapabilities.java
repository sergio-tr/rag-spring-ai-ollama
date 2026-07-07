package com.uniovi.rag.domain.chat;

public record RuntimeSnapshotCapabilities(
        String materializationStrategy,
        Boolean supportsMetadata,
        String embeddingModelId,
        Integer chunkMaxChars,
        Integer chunkOverlap) {}
