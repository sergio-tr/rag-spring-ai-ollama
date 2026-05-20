package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;

/** Thrown when an uploaded experimental workbook fails validation; mapped to HTTP 422 without persistence. */
public final class ExperimentalDatasetValidationException extends RuntimeException {

    private final ValidationReport validationReport;

    public ExperimentalDatasetValidationException(ValidationReport validationReport) {
        super("Experimental dataset validation failed");
        this.validationReport = validationReport != null ? validationReport : new ValidationReport();
    }

    public ValidationReport validationReport() {
        return validationReport;
    }
}
