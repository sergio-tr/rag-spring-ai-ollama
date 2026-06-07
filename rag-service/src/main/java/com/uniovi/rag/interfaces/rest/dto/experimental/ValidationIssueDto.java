package com.uniovi.rag.interfaces.rest.dto.experimental;

import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssuePayload;

/** Serializable validation row for REST JSON (no stack traces). */
public record ValidationIssueDto(
        String severity, String code, String sheet, int rowNumber, String column, String message) {

    public static ValidationIssueDto from(ValidationIssue issue) {
        return from(ValidationIssuePayload.from(issue));
    }

    public static ValidationIssueDto from(ValidationIssuePayload payload) {
        return new ValidationIssueDto(
                payload.severity(),
                payload.code(),
                payload.sheet(),
                payload.rowNumber(),
                payload.column(),
                payload.message());
    }
}
