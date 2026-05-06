package com.uniovi.rag.interfaces.rest.dto.experimental;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentalDatasetListItemDto(
        UUID id,
        String name,
        String experimentalDatasetType,
        String datasetType,
        boolean readOnly,
        String validationStatus,
        ExperimentalDatasetQuestionCountsDto questionCounts,
        boolean isReferenceBundle,
        boolean isDemoDataset,
        boolean canRunLlmBaseline,
        boolean canRunEmbeddingBaseline,
        boolean canRunRagPresetBenchmark,
        List<ValidationIssueDto> validationIssues,
        Instant uploadedAt,
        String description) {}
