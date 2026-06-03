package com.uniovi.rag.interfaces.rest.dto.evaluation;

import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lab RAG evaluation knowledge base (EvaluationCorpus facade for product UI). */
public record LabRagKnowledgeBaseDto(
        UUID knowledgeBaseId,
        String name,
        List<ProjectDocumentDto> documents,
        int readyCount,
        int totalCount,
        int failedCount,
        Instant createdAt,
        Instant updatedAt) {}
