package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.ValidationSeverity;

import java.util.Locale;

/**
 * Lab guardrails that block demo / empty / incomplete evaluation datasets.
 *
 * <p>This is intentionally strict for REFERENCE_BUNDLE: it is the canonical dataset and must never degrade
 * into a 1-row demo workbook.
 */
public final class LabDatasetGateValidator {

    private LabDatasetGateValidator() {}

    public static void validatePreRun(
            BenchmarkKind kind,
            ExperimentalDatasetType experimentalType,
            EvaluationWorkbook wb,
            ValidationReport report) {
        if (kind == null || wb == null || report == null) {
            return;
        }
        // Demo detection must apply everywhere (uploads + reference bundle).
        detectDemoContent(wb, report);

        boolean isReference = experimentalType == ExperimentalDatasetType.REFERENCE_BUNDLE;
        switch (kind) {
            case LLM_JUDGE_QA -> validateLlm(kind, isReference, wb, report);
            case EMBEDDING_RETRIEVAL -> validateEmbedding(kind, isReference, wb, report);
            case RAG_PRESET_END_TO_END -> validateRag(kind, isReference, wb, report);
            case CLASSIFIER_METRICS -> {
                // Not a workbook-typed benchmark run.
            }
        }
    }

    static void validateUpload(ExperimentalDatasetType type, EvaluationWorkbook wb, ValidationReport report) {
        if (type == null || wb == null || report == null) {
            return;
        }
        detectDemoContent(wb, report);

        // Uploads are typed by ExperimentalDatasetType, not BenchmarkKind.
        // Enforce "recommended" minimums so a template-only upload yields a clear 422 with DATASET_TOO_SMALL.
        switch (type) {
            case LLM_MODEL_BASELINE -> {
                int qa = wb.llmReaderQuestions().size();
                int role = wb.llmRoleEvalCases().size();
                if (qa < 10 && role < 3) {
                    report.add(tooSmall(WorkbookSheetNames.LLM_READER_QUESTIONS, 0, "LLM_JUDGE_QA requires at least 10 questions or 3 role-eval cases for uploads"));
                } else if (qa >= 1 && qa < 10 && role >= 3) {
                    // role-eval smoke slice upload: minimal QA + role cases
                } else if (qa < 10) {
                    report.add(tooSmall(WorkbookSheetNames.LLM_READER_QUESTIONS, 0, "LLM_JUDGE_QA requires at least 10 questions for uploads"));
                }
            }
            case EMBEDDING_MODEL_BASELINE -> {
                if (wb.embeddingRetrievalQueries().size() < 20) {
                    report.add(tooSmall(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES, 0, "EMBEDDING_RETRIEVAL requires at least 20 queries for uploads"));
                }
                if (wb.chunkRegistry().isEmpty()
                        && wb.embeddingRetrievalQueries().stream().anyMatch(q -> q.goldChunkIds() != null && !q.goldChunkIds().isEmpty())) {
                    report.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            ValidationIssueCode.MISSING_SHEET,
                            WorkbookSheetNames.CHUNK_REGISTRY,
                            0,
                            "",
                            "chunk_registry must not be empty when expected_chunk_id or expected_relevant_chunk_ids are used"));
                }
            }
            case RAG_PRESET_BENCHMARK -> {
                if (wb.ragPresetQuestionsEnriched().size() < 20) {
                    report.add(tooSmall(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED, 0, "RAG_PRESET_END_TO_END requires at least 20 questions for uploads"));
                }
                validateRagDimensions(wb, report);
            }
            case REFERENCE_BUNDLE -> {
                // Classpath canonical dataset: enforce mandatory minimums at load time (drives /lab/status validity).
                if (wb.llmReaderQuestions().size() < 30) {
                    report.add(tooSmall(WorkbookSheetNames.LLM_READER_QUESTIONS, 0, "REFERENCE_BUNDLE must include at least 30 llm_reader_questions rows"));
                }
                if (wb.embeddingRetrievalQueries().size() < 50) {
                    report.add(tooSmall(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES, 0, "REFERENCE_BUNDLE must include at least 50 embedding_retrieval_queries rows"));
                }
                if (wb.ragPresetQuestionsEnriched().size() < 50) {
                    report.add(tooSmall(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED, 0, "REFERENCE_BUNDLE must include at least 50 rag_preset_questions_enriched rows"));
                }
                if (wb.chunkRegistry().isEmpty()) {
                    report.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            ValidationIssueCode.MISSING_SHEET,
                            WorkbookSheetNames.CHUNK_REGISTRY,
                            0,
                            "",
                            "REFERENCE_BUNDLE requires non-empty chunk_registry"));
                }
                if (wb.ragPresetCatalog().size() != 15) {
                    report.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            ValidationIssueCode.DATASET_MISSING_PRESET_CATALOG,
                            WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14,
                            0,
                            "",
                            "REFERENCE_BUNDLE must include rag_preset_catalog_P0_P14 with exactly 15 presets (P0–P14)"));
                }
                validateRagDimensions(wb, report);
            }
            case CLASSIFIER_DATASET -> {
                // Classifier is validated by presence of a matching sheet; no row-count gate here.
            }
        }
    }

    private static void validateLlm(BenchmarkKind kind, boolean isReference, EvaluationWorkbook wb, ValidationReport report) {
        int n = wb.llmReaderQuestions().size();
        int min = isReference ? 30 : 10;
        if (n < min) {
            report.add(tooSmall(WorkbookSheetNames.LLM_READER_QUESTIONS, 0,
                    "Dataset too small for " + kind.name() + ": got " + n + ", need >= " + min + (isReference ? " for REFERENCE_BUNDLE" : "")));
        }
    }

    private static void validateEmbedding(BenchmarkKind kind, boolean isReference, EvaluationWorkbook wb, ValidationReport report) {
        int n = wb.embeddingRetrievalQueries().size();
        int min = isReference ? 50 : 20;
        if (n < min) {
            report.add(tooSmall(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES, 0,
                    "Dataset too small for " + kind.name() + ": got " + n + ", need >= " + min + (isReference ? " for REFERENCE_BUNDLE" : "")));
        }
        if (wb.chunkRegistry().isEmpty()
                && wb.embeddingRetrievalQueries().stream().anyMatch(q -> q.goldChunkIds() != null && !q.goldChunkIds().isEmpty())) {
            report.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    ValidationIssueCode.MISSING_SHEET,
                    WorkbookSheetNames.CHUNK_REGISTRY,
                    0,
                    "",
                    "chunk_registry must not be empty when gold_chunk_ids are referenced"));
        }
    }

    private static void validateRag(BenchmarkKind kind, boolean isReference, EvaluationWorkbook wb, ValidationReport report) {
        int n = wb.ragPresetQuestionsEnriched().size();
        int min = isReference ? 50 : 20;
        if (n < min) {
            report.add(tooSmall(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED, 0,
                    "Dataset too small for " + kind.name() + ": got " + n + ", need >= " + min + (isReference ? " for REFERENCE_BUNDLE" : "")));
        }
        if (isReference && wb.ragPresetCatalog().size() != 15) {
            report.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    ValidationIssueCode.DATASET_MISSING_PRESET_CATALOG,
                    WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14,
                    0,
                    "",
                    "REFERENCE_BUNDLE must include rag_preset_catalog_P0_P14 with exactly 15 presets (P0–P14)"));
        }
        validateRagDimensions(wb, report);
    }

    private static void validateRagDimensions(EvaluationWorkbook wb, ValidationReport report) {
        int row = 2;
        for (RagPresetQuestion q : wb.ragPresetQuestionsEnriched()) {
            if (q.queryType().isEmpty()) {
                report.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.DATASET_MISSING_QUERY_TYPE,
                        WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED,
                        row,
                        "query_type",
                        "query_type is required for RAG preset questions"));
            }
            if (q.difficulty().isEmpty()) {
                report.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.DATASET_MISSING_DIFFICULTY,
                        WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED,
                        row,
                        "difficulty",
                        "difficulty is required for RAG preset questions"));
            }
            row++;
        }
    }

    private static void detectDemoContent(EvaluationWorkbook wb, ValidationReport report) {
        if (wb == null) {
            return;
        }
        boolean demo = false;
        for (RagPresetQuestion q : wb.ragPresetQuestionsEnriched()) {
            demo |= equalsIgnoreCase(q.id(), "RAG_Q1");
            demo |= containsAny(q.question(), "what does the sample acta contain?");
            demo |= containsAny(q.expectedAnswer(), "evidence: acta sample 1");
            demo |= containsAny(q.notes(), "minimal dev reference question");
        }
        for (LlmReaderQuestion q : wb.llmReaderQuestions()) {
            demo |= containsAny(q.question(), "sample acta");
            demo |= containsAny(q.expectedAnswer(), "evidence: acta sample 1");
        }
        if (demo) {
            report.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    ValidationIssueCode.DATASET_DEMO_CONTENT_DETECTED,
                    "",
                    0,
                    "",
                    "Demo content detected (RAG_Q1 / sample acta / Evidence: Acta sample 1). This dataset must not be used for Lab benchmarks."));
        }
    }

    private static ValidationIssue tooSmall(String sheet, int row, String msg) {
        return new ValidationIssue(
                ValidationSeverity.ERROR,
                ValidationIssueCode.DATASET_TOO_SMALL,
                sheet != null ? sheet : "",
                row,
                "",
                msg != null ? msg : "Dataset too small");
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean containsAny(String haystack, String needleLower) {
        if (haystack == null || haystack.isBlank() || needleLower == null || needleLower.isBlank()) {
            return false;
        }
        String h = haystack.toLowerCase(Locale.ROOT);
        return h.contains(needleLower.toLowerCase(Locale.ROOT));
    }
}

