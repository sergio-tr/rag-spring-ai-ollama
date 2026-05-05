package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.ValidationSeverity;

import java.util.Map;
import java.util.Optional;

/**
 * Cached outcome of loading and parsing the internal reference workbook from the classpath.
 *
 * @param classpathResourcePresent {@code true} when {@link EvaluationReferenceBundleLoader#CLASSPATH_LOCATION} exists
 * @param workbook parsed workbook (possibly empty when the resource was absent or unreadable)
 * @param validationReport parser and {@link com.uniovi.rag.application.evaluation.workbook.ExperimentalWorkbookValidator} output
 * @param counts derived counts for Lab status
 * @param protocolVersion optional label parsed from README when identifiable
 * @param sha256Hex optional SHA-256 of the raw XLSX bytes (for reproducibility audits)
 * @param byteSize raw XLSX size in bytes (for quick sanity checks)
 */
public record ReferenceBundleSnapshot(
        boolean classpathResourcePresent,
        EvaluationWorkbook workbook,
        ValidationReport validationReport,
        ReferenceBundleCounts counts,
        Optional<String> protocolVersion,
        Optional<String> sha256Hex,
        long byteSize) {

    public ReferenceBundleSnapshot {
        if (workbook == null) {
            workbook = EvaluationWorkbook.builder().build();
        }
        if (validationReport == null) {
            validationReport = new ValidationReport();
        }
        if (counts == null) {
            counts = ReferenceBundleCounts.fromWorkbook(workbook);
        }
        protocolVersion = protocolVersion != null ? protocolVersion : Optional.empty();
        sha256Hex = sha256Hex != null ? sha256Hex : Optional.empty();
        byteSize = Math.max(0, byteSize);
    }

    public boolean validForReferenceUse() {
        return classpathResourcePresent && !validationReport.hasErrors();
    }

    public Map<String, Integer> countsByDatasetKind() {
        return counts.toDatasetKindMap();
    }

    public static ReferenceBundleSnapshot classpathMissing() {
        EvaluationWorkbook wb = EvaluationWorkbook.builder().build();
        return new ReferenceBundleSnapshot(false, wb, new ValidationReport(), ReferenceBundleCounts.empty(), Optional.empty(), Optional.empty(), 0);
    }

    static ReferenceBundleSnapshot loadFailedIo(String message) {
        ValidationReport r = new ValidationReport();
        r.add(
                new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.WORKBOOK_IO_ERROR,
                        "",
                        0,
                        "",
                        message != null ? message : "IO error"));
        EvaluationWorkbook wb = EvaluationWorkbook.builder().build();
        return new ReferenceBundleSnapshot(true, wb, r, ReferenceBundleCounts.empty(), Optional.empty(), Optional.empty(), 0);
    }
}
