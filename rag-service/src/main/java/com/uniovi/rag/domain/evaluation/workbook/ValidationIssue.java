package com.uniovi.rag.domain.evaluation.workbook;

import java.util.Objects;

/**
 * One validation finding. {@code rowNumber} uses 1-based Excel row index; use 0 for sheet-level issues.
 * {@code column} is the header name when known, or empty.
 */
public record ValidationIssue(
        ValidationSeverity severity,
        ValidationIssueCode code,
        String sheet,
        int rowNumber,
        String column,
        String message) {

    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        sheet = sheet != null ? sheet : "";
        column = column != null ? column : "";
        message = message != null ? message : "";
    }
}
