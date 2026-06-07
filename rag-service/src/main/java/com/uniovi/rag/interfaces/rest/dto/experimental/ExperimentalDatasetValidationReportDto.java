package com.uniovi.rag.interfaces.rest.dto.experimental;

import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;

import com.uniovi.rag.domain.evaluation.workbook.ValidationIssuePayload;

import java.util.List;

public record ExperimentalDatasetValidationReportDto(
        List<ValidationIssueDto> issues, boolean hasErrors, boolean hasWarnings) {

    public static ExperimentalDatasetValidationReportDto from(ValidationReport report) {
        List<ValidationIssueDto> issues =
                report.issues().stream().map(ValidationIssueDto::from).toList();
        return new ExperimentalDatasetValidationReportDto(
                issues, report.hasErrors(), report.hasWarnings());
    }

    public List<ValidationIssuePayload> toPayloads() {
        return issues.stream()
                .map(
                        i ->
                                new ValidationIssuePayload(
                                        i.severity(),
                                        i.code(),
                                        i.sheet(),
                                        i.rowNumber(),
                                        i.column(),
                                        i.message()))
                .toList();
    }
}
