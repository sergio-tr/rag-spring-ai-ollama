package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectDocumentDebugDto(
        UUID documentId,
        UUID projectId,
        String fileName,
        String status,
        String errorMessage,
        Integer chunkCount,
        long vectorRowCount,
        Instant uploadedAt,
        Instant reindexedAt,
        UUID currentIndexSnapshotId,
        String indexSignatureHash
) {}

