package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;

import java.time.Instant;
import java.util.UUID;

public record ProjectDocumentDto(
        UUID id,
        String fileName,
        ProjectDocumentStatus status,
        Integer chunkCount,
        String errorMessage,
        Instant uploadedAt,
        Instant reindexedAt,
        CorpusScope corpusScope,
        UUID conversationId,
        UUID currentIndexSnapshotId,
        String indexSignatureHash,
        boolean storagePresent
) {
}
