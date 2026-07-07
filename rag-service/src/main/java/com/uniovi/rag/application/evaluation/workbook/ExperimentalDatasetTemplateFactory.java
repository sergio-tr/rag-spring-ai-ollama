package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builds minimal XLSX templates with correct sheet names and headers for each experimental kind.
 */
public final class ExperimentalDatasetTemplateFactory {

    private ExperimentalDatasetTemplateFactory() {}

    public static byte[] buildTemplate(ExperimentalDatasetType kind) throws IOException {
        return switch (kind) {
            case LLM_MODEL_BASELINE -> buildLlmBaseline();
            case EMBEDDING_MODEL_BASELINE -> buildEmbeddingBaseline();
            case RAG_PRESET_BENCHMARK -> buildRagPresetBenchmark();
            case CLASSIFIER_DATASET -> buildClassifier();
            case REFERENCE_BUNDLE ->
                    throw new IllegalArgumentException("REFERENCE_BUNDLE has no user template download");
        };
    }

    private static byte[] toBytes(XSSFWorkbook wb) throws IOException {
        try (wb;
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private static byte[] buildLlmBaseline() throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(WorkbookSheetNames.LLM_READER_QUESTIONS);
        headerRow(sheet, "id", "question", "context_text", "expected_answer", "evaluation_notes");
        Sheet roleCases = wb.createSheet(WorkbookSheetNames.LLM_ROLE_EVAL_CASES);
        headerRow(
                roleCases,
                "case_id",
                "subset",
                "role_family",
                "role_profile",
                "input",
                "context",
                "expected_output",
                "expected_keywords",
                "forbidden_terms",
                "scoring_type",
                "required_json_keys",
                "notes");
        return toBytes(wb);
    }

    private static byte[] buildEmbeddingBaseline() throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet chunk = wb.createSheet(WorkbookSheetNames.CHUNK_REGISTRY);
        headerRow(chunk, "chunk_id", "document_id");
        Sheet emb = wb.createSheet(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES);
        headerRow(
                emb,
                "id",
                "query",
                "expected_document_id",
                "expected_chunk_id",
                "expected_content",
                "expected_relevant_chunk_ids",
                "evaluation_notes");
        return toBytes(wb);
    }

    private static byte[] buildRagPresetBenchmark() throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet rag = wb.createSheet(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED);
        headerRow(
                rag,
                "id",
                "question",
                "expected_answer",
                "expected_sources",
                "context_text",
                "evaluation_notes");
        return toBytes(wb);
    }

    private static byte[] buildClassifier() throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("classifier");
        headerRow(sheet, "Question", "QueryType");
        return toBytes(wb);
    }

    private static void headerRow(Sheet sheet, String... headers) {
        Row r = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            r.createCell(i).setCellValue(headers[i]);
        }
    }
}
