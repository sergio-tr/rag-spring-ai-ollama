package com.uniovi.rag.domain.evaluation.workbook;

/** Result of parsing + validating an experimental workbook. */
public record WorkbookParseResult(EvaluationWorkbook workbook, ValidationReport validationReport) {

    public WorkbookParseResult {
        if (workbook == null) {
            throw new IllegalArgumentException("workbook required");
        }
        validationReport = validationReport != null ? validationReport : new ValidationReport();
    }

    public boolean isSuccess() {
        return !validationReport.hasErrors();
    }
}
