package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.ChunkRegistryEntry;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalQuery;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.ValidationSeverity;
import com.uniovi.rag.domain.model.QueryType;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates {@link EvaluationWorkbook} content for a requested {@link ExperimentalDatasetType},
 * including cross-sheet references (gold chunks).
 */
public final class ExperimentalWorkbookValidator {

    private static final List<String> REFERENCE_REQUIRED_SHEETS = List.of(
            WorkbookSheetNames.README,
            WorkbookSheetNames.CORPUS_DOCUMENTS,
            WorkbookSheetNames.CHUNK_REGISTRY,
            WorkbookSheetNames.LLM_READER_QUESTIONS,
            WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES,
            WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED,
            WorkbookSheetNames.LLM_CANDIDATES,
            WorkbookSheetNames.EMBEDDING_CANDIDATES,
            WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14,
            WorkbookSheetNames.METRIC_SPEC,
            WorkbookSheetNames.RESULT_SCHEMA,
            WorkbookSheetNames.SUMMARY_COUNTS);

    private ExperimentalWorkbookValidator() {}

    public static void validate(ExperimentalDatasetType type, EvaluationWorkbook wb, ValidationReport report) {
        switch (type) {
            case REFERENCE_BUNDLE -> validateReferenceBundle(wb, report);
            case LLM_MODEL_BASELINE -> validateLlmBaseline(wb, report);
            case EMBEDDING_MODEL_BASELINE -> validateEmbeddingBaseline(wb, report);
            case RAG_PRESET_BENCHMARK -> validateRagPresetBenchmark(wb, report);
            case CLASSIFIER_DATASET -> validateClassifier(wb, report);
        }
    }

    private static void validateReferenceBundle(EvaluationWorkbook wb, ValidationReport report) {
        for (String sheet : REFERENCE_REQUIRED_SHEETS) {
            if (!physicalSheetPresent(sheet, wb)) {
                report.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.MISSING_SHEET,
                        sheet,
                        0,
                        "",
                        "Required sheet is missing for REFERENCE_BUNDLE"));
            }
        }
        validateGoldChunksAgainstRegistry(wb, report);
    }

    /** True if a tab with this logical name exists in the XLSX (case-insensitive). */
    private static boolean physicalSheetPresent(String logicalSheetName, EvaluationWorkbook wb) {
        for (String n : wb.sheetNamesPresent()) {
            if (logicalSheetName != null && logicalSheetName.equalsIgnoreCase(n)) {
                return true;
            }
        }
        return false;
    }

    private static void validateLlmBaseline(EvaluationWorkbook wb, ValidationReport report) {
        if (!physicalSheetPresent(WorkbookSheetNames.LLM_READER_QUESTIONS, wb)) {
            report.add(missingSheetIssue(WorkbookSheetNames.LLM_READER_QUESTIONS));
        }
    }

    private static void validateEmbeddingBaseline(EvaluationWorkbook wb, ValidationReport report) {
        if (!physicalSheetPresent(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES, wb)) {
            report.add(missingSheetIssue(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES));
        }
        if (!physicalSheetPresent(WorkbookSheetNames.CHUNK_REGISTRY, wb)) {
            report.add(missingSheetIssue(WorkbookSheetNames.CHUNK_REGISTRY));
        }
        validateGoldChunksAgainstRegistry(wb, report);
    }

    private static void validateRagPresetBenchmark(EvaluationWorkbook wb, ValidationReport report) {
        if (!physicalSheetPresent(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED, wb)) {
            report.add(missingSheetIssue(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED));
        }
        validateGoldChunksAgainstRegistry(wb, report);
    }

    private static void validateClassifier(EvaluationWorkbook wb, ValidationReport report) {
        if (wb.classifierQuestions().isEmpty()) {
            report.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    ValidationIssueCode.MISSING_SHEET,
                    "classifier",
                    0,
                    "",
                    "No rows with columns Question and QueryType found"));
        }
    }

    private static ValidationIssue missingSheetIssue(String sheet) {
        return new ValidationIssue(
                ValidationSeverity.ERROR,
                ValidationIssueCode.MISSING_SHEET,
                sheet,
                0,
                "",
                "Required sheet or data missing");
    }

    private static void validateGoldChunksAgainstRegistry(EvaluationWorkbook wb, ValidationReport report) {
        Set<String> chunkIds = new HashSet<>();
        for (ChunkRegistryEntry e : wb.chunkRegistry()) {
            chunkIds.add(e.chunkId().trim());
        }
        int row = 2;
        for (EmbeddingRetrievalQuery q : wb.embeddingRetrievalQueries()) {
            for (String gid : q.goldChunkIds()) {
                String id = gid.trim();
                if (!id.isEmpty() && !isIgnoredGoldChunkSentinel(id) && !chunkIds.contains(id)) {
                    report.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            ValidationIssueCode.UNKNOWN_CHUNK_REF,
                            WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES,
                            row,
                            "gold_chunk_ids",
                            "Unknown gold_chunk_id: " + id));
                }
            }
            row++;
        }
        row = 2;
        for (RagPresetQuestion q : wb.ragPresetQuestionsEnriched()) {
            for (String gid : q.goldChunkIds()) {
                String id = gid.trim();
                if (!id.isEmpty() && !isIgnoredGoldChunkSentinel(id) && !chunkIds.contains(id)) {
                    report.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            ValidationIssueCode.UNKNOWN_CHUNK_REF,
                            WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED,
                            row,
                            "gold_chunk_ids",
                            "Unknown gold_chunk_id: " + id));
                }
            }
            row++;
        }
    }

    /** Workbook sentinel meaning “no gold chunk”; must not be validated against {@link ChunkRegistryEntry}. */
    private static boolean isIgnoredGoldChunkSentinel(String id) {
        String u = id.trim().toUpperCase(Locale.ROOT);
        return "NONE".equals(u) || "N/A".equals(u);
    }

    /** Resolve classifier QueryType label against domain enum names (optional validation helper). */
    public static boolean isKnownQueryTypeLabel(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        try {
            QueryType.valueOf(label.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
