package com.uniovi.rag.interfaces.rest.dto.evaluation;

import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvaluationCorpusSummaryDto(
        UUID id,
        String name,
        String sourceType,
        int documentCount,
        int readyCount,
        int failedCount,
        List<ProjectDocumentDto> documents,
        Instant createdAt,
        Instant updatedAt) {}
