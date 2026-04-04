package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.ProjectDocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record ProjectDocumentDto(
        UUID id,
        String fileName,
        ProjectDocumentStatus status,
        Integer chunkCount,
        String errorMessage,
        Instant uploadedAt,
        Instant reindexedAt
) {
}
