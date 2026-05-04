package com.uniovi.rag.interfaces.rest.dto.experimental;

import java.time.Instant;
import java.util.UUID;

public record ExperimentalDatasetListItemDto(
        UUID id,
        String name,
        String experimentalDatasetType,
        String persistedEvaluationDatasetType,
        boolean readOnly,
        Integer questionCount,
        Integer rowCount,
        String validationStatus,
        Instant uploadedAt,
        String description) {}
