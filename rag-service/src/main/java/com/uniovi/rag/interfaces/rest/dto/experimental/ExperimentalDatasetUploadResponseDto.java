package com.uniovi.rag.interfaces.rest.dto.experimental;

import java.util.UUID;

public record ExperimentalDatasetUploadResponseDto(
        UUID datasetId,
        String experimentalDatasetType,
        String persistedEvaluationDatasetType,
        String validationStatus,
        int questionCount,
        int rowCount,
        ExperimentalDatasetValidationReportDto validationReport) {}
