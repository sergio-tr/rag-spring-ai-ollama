package com.uniovi.rag.domain.evaluation.workbook;

/**
 * JSON-friendly snapshot of {@link ValidationIssue} for persistence and REST (no enums serialized oddly).
 */
public record ValidationIssuePayload(
        String severity, String code, String sheet, int rowNumber, String column, String message) {

    public static ValidationIssuePayload from(ValidationIssue issue) {
        return new ValidationIssuePayload(
                issue.severity().name(),
                issue.code().name(),
                issue.sheet(),
                issue.rowNumber(),
                issue.column(),
                issue.message());
    }
}
