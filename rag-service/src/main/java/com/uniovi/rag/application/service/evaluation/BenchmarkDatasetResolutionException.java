package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;

/**
 * Failure resolving dataset bytes into a typed benchmark payload (handlers translate to failed runs / tasks).
 */
public class BenchmarkDatasetResolutionException extends RuntimeException {

    private final ValidationReport validationReport;

    public BenchmarkDatasetResolutionException(String message) {
        super(message);
        this.validationReport = null;
    }

    public BenchmarkDatasetResolutionException(String message, Throwable cause) {
        super(message, cause);
        this.validationReport = null;
    }

    public BenchmarkDatasetResolutionException(ValidationReport validationReport) {
        super("Workbook validation failed for benchmark dataset");
        this.validationReport = validationReport;
    }

    public ValidationReport validationReport() {
        return validationReport;
    }
}
