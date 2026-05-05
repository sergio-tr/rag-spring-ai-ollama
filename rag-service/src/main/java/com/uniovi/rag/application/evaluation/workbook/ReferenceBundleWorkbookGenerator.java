package com.uniovi.rag.application.evaluation.workbook;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Build-time generator for the canonical reference bundle workbook.
 *
 * <p>This project intentionally does not commit XLSX binaries to git. Instead, the build generates the canonical
 * workbook into {@code target/classes/evaluation/} so it is packaged into the JAR and available via
 * {@link EvaluationReferenceBundleLoader#CLASSPATH_LOCATION}.
 */
public final class ReferenceBundleWorkbookGenerator {

    public static void main(String[] args) throws IOException {
        String outDir = args != null && args.length > 0 ? args[0] : "target/classes";
        Path out = Paths.get(outDir).resolve(EvaluationReferenceBundleLoader.CLASSPATH_LOCATION);
        Files.createDirectories(out.getParent());

        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet(WorkbookSheetNames.README);
            wb.createSheet(WorkbookSheetNames.CORPUS_DOCUMENTS);
            wb.createSheet(WorkbookSheetNames.CHUNK_REGISTRY);
            wb.createSheet(WorkbookSheetNames.LLM_READER_QUESTIONS);
            wb.createSheet(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES);
            wb.createSheet(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED);
            wb.createSheet(WorkbookSheetNames.LLM_CANDIDATES);
            wb.createSheet(WorkbookSheetNames.EMBEDDING_CANDIDATES);
            wb.createSheet(WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14);
            wb.createSheet(WorkbookSheetNames.METRIC_SPEC);
            wb.createSheet(WorkbookSheetNames.RESULT_SCHEMA);
            wb.createSheet(WorkbookSheetNames.SUMMARY_COUNTS);

            fillReadme(wb.getSheet(WorkbookSheetNames.README));
            fillCorpusDocuments(wb.getSheet(WorkbookSheetNames.CORPUS_DOCUMENTS));
            fillChunkRegistry(wb.getSheet(WorkbookSheetNames.CHUNK_REGISTRY));
            fillLlmReaderQuestions(wb.getSheet(WorkbookSheetNames.LLM_READER_QUESTIONS));
            fillEmbeddingQueries(wb.getSheet(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES));
            fillRagPresetQuestions(wb.getSheet(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED));
            fillLlmCandidates(wb.getSheet(WorkbookSheetNames.LLM_CANDIDATES));
            fillEmbeddingCandidates(wb.getSheet(WorkbookSheetNames.EMBEDDING_CANDIDATES));
            fillPresetCatalog(wb.getSheet(WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14));
            fillMetricSpec(wb.getSheet(WorkbookSheetNames.METRIC_SPEC));
            fillResultSchema(wb.getSheet(WorkbookSheetNames.RESULT_SCHEMA));
            fillSummaryCounts(wb.getSheet(WorkbookSheetNames.SUMMARY_COUNTS));

            try (OutputStream os = Files.newOutputStream(out)) {
                wb.write(os);
            }
        }
    }

    private static void fillReadme(Sheet s) {
        row(s, 0, List.of("Item", "Decision"));
        row(s, 1, List.of("Protocol version", "experimental-workbook-v1-dev"));
    }

    private static void fillCorpusDocuments(Sheet s) {
        row(s, 0, List.of("document_id", "title"));
        row(s, 1, List.of("DOC_1", "Acta sample 1"));
    }

    private static void fillChunkRegistry(Sheet s) {
        row(s, 0, List.of("chunk_id", "document_id", "chunk_type", "gold_evidence_text"));
        row(s, 1, List.of("CHUNK_1", "DOC_1", "TEXT", "Evidence: Acta sample 1"));
    }

    private static void fillLlmReaderQuestions(Sheet s) {
        row(s, 0, List.of("id", "question", "expected_answer", "query_type", "difficulty", "source_document_id", "gold_evidence"));
        row(
                s,
                1,
                List.of(
                        "LLM_Q1",
                        "Provide a grounded summary of the sample acta.",
                        "Evidence: Acta sample 1",
                        "",
                        "",
                        "DOC_1",
                        "Evidence: Acta sample 1"));
    }

    private static void fillEmbeddingQueries(Sheet s) {
        row(s, 0, List.of("id", "query", "gold_chunk_ids", "gold_document_ids", "query_type", "difficulty"));
        row(s, 1, List.of("EMB_Q1", "sample acta evidence", "CHUNK_1", "DOC_1", "", ""));
    }

    private static void fillRagPresetQuestions(Sheet s) {
        row(
                s,
                0,
                List.of(
                        "id",
                        "question",
                        "expected_answer",
                        "query_type",
                        "difficulty",
                        "answer_mode",
                        "gold_document_ids",
                        "gold_chunk_ids",
                        "unanswerable",
                        "notes"));
        row(
                s,
                1,
                List.of(
                        "RAG_Q1",
                        "What does the sample acta contain?",
                        "Evidence: Acta sample 1",
                        "",
                        "",
                        "EXTRACTIVE",
                        "DOC_1",
                        "CHUNK_1",
                        "false",
                        "Minimal dev reference question"));
    }

    private static void fillLlmCandidates(Sheet s) {
        row(s, 0, List.of("candidate_id", "model", "role"));
        row(s, 1, List.of("LLM_C1", "gemma3:4b", "default"));
    }

    private static void fillEmbeddingCandidates(Sheet s) {
        row(s, 0, List.of("candidate_id", "model", "role"));
        row(s, 1, List.of("EMB_C1", "mxbai-embed-large", "default"));
    }

    private static void fillPresetCatalog(Sheet s) {
        row(
                s,
                0,
                List.of(
                        "preset_id",
                        "family",
                        "name",
                        "retrieval",
                        "query_understanding",
                        "tools",
                        "memory",
                        "judges",
                        "main_or_complement",
                        "objective",
                        "dataset_policy"));
        int r = 1;
        for (int i = 0; i <= 14; i++) {
            String code = "P" + i;
            row(
                    s,
                    r++,
                    List.of(
                            code,
                            i <= 1 ? "S0" : i <= 4 ? "S1" : i <= 8 ? "S2" : i <= 12 ? "S3" : "S4",
                            code + " preset",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "main",
                            "Minimal dev preset row",
                            "dev"));
        }
    }

    private static void fillMetricSpec(Sheet s) {
        row(s, 0, List.of("metric_id", "scope", "description"));
        row(s, 1, List.of("semantic_score", "rag_item", "Semantic similarity score (dev placeholder)"));
    }

    private static void fillResultSchema(Sheet s) {
        row(s, 0, List.of("field", "type", "required", "description"));
        row(s, 1, List.of("outcome", "string", "true", "EXECUTED / FAILED / NOT_SUPPORTED / SKIPPED"));
    }

    private static void fillSummaryCounts(Sheet s) {
        row(s, 0, List.of("Dataset", "Rows", "Purpose", "Primary branch"));
        row(s, 1, List.of("REFERENCE_BUNDLE", "1", "Minimal bundle for dev/test packaging", "eval-models-and-presets"));
    }

    private static void row(Sheet s, int rowIdx, List<String> values) {
        var row = s.createRow(rowIdx);
        for (int i = 0; i < values.size(); i++) {
            row.createCell(i).setCellValue(values.get(i));
        }
    }
}

