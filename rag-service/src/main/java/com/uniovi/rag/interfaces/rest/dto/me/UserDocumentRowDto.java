package com.uniovi.rag.interfaces.rest.dto.me;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;

import java.time.Instant;
import java.util.UUID;

public record UserDocumentRowDto(
        UUID documentId,
        UUID projectId,
        UUID conversationId,
        CorpusScope corpusScope,
        String fileName,
        ProjectDocumentStatus status,
        Instant uploadedAt,
        Instant reindexedAt,
        String indexSignatureHash,
        Integer chunkCount,
        boolean storagePresent) {}
