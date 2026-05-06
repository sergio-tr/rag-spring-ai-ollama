package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;

import java.util.Objects;

/**
 * Thrown when a dataset is not eligible for a Lab benchmark run (demo/too small/missing required dimensions).
 *
 * <p>Mapped to HTTP 422 with a stable {@code code} and machine-readable validation issues.
 */
public final class LabDatasetGateException extends RuntimeException {

    private final String code;
    private final ValidationReport validationReport;

    public LabDatasetGateException(String code, String message, ValidationReport validationReport) {
        super(message != null ? message : "Dataset is not eligible for this Lab benchmark");
        this.code = Objects.requireNonNullElse(code, "DATASET_INVALID");
        this.validationReport = validationReport != null ? validationReport : new ValidationReport();
    }

    public String code() {
        return code;
    }

    public ValidationReport validationReport() {
        return validationReport;
    }
}

