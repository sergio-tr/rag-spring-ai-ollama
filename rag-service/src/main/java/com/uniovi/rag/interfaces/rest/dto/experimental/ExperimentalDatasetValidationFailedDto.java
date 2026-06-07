package com.uniovi.rag.interfaces.rest.dto.experimental;

/**
 * HTTP 422 body when an uploaded workbook fails validation (nothing is persisted).
 *
 * @param error stable machine code {@code EXPERIMENTAL_DATASET_INVALID}
 */
public record ExperimentalDatasetValidationFailedDto(
        String error, ExperimentalDatasetValidationReportDto validationReport) {

    public static ExperimentalDatasetValidationFailedDto of(ExperimentalDatasetValidationReportDto report) {
        return new ExperimentalDatasetValidationFailedDto("EXPERIMENTAL_DATASET_INVALID", report);
    }
}
