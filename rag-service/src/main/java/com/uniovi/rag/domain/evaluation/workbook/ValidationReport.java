package com.uniovi.rag.domain.evaluation.workbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Accumulates validation issues without throwing for domain validation failures. */
public final class ValidationReport {

    private final List<ValidationIssue> issues;

    public ValidationReport() {
        this.issues = new ArrayList<>();
    }

    public ValidationReport(List<ValidationIssue> issues) {
        this.issues = new ArrayList<>(issues);
    }

    public void add(ValidationIssue issue) {
        issues.add(issue);
    }

    public void merge(ValidationReport other) {
        issues.addAll(other.issues);
    }

    public List<ValidationIssue> issues() {
        return Collections.unmodifiableList(issues);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.WARNING);
    }
}
