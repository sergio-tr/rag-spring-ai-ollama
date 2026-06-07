package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.domain.evaluation.workbook.ValidationSeverity;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationWorkbookParserTest {

    private final EvaluationWorkbookParser parser = new EvaluationWorkbookParser();

    @Test
    void llmBaseline_minimalValid_hasNoErrors() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet llm = wb.createSheet(WorkbookSheetNames.LLM_READER_QUESTIONS);
        writeRow(llm.createRow(0), "id", "question");
        for (int i = 1; i <= 10; i++) {
            writeRow(llm.createRow(i), "q" + i, "What is RAG? " + i);
        }

        WorkbookParseResult result = parse(wb, ExperimentalDatasetType.LLM_MODEL_BASELINE);
        assertThat(result.validationReport().hasErrors()).isFalse();
        assertThat(result.workbook().llmReaderQuestions()).hasSize(10);
    }

    @Test
    void llmBaseline_missingRequiredSheet_reportsMissingSheet() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet other = wb.createSheet("other");
        writeRow(other.createRow(0), "x");
        writeRow(other.createRow(1), "y");

        WorkbookParseResult result = parse(wb, ExperimentalDatasetType.LLM_MODEL_BASELINE);
        assertThat(codes(result, ValidationSeverity.ERROR)).contains(ValidationIssueCode.MISSING_SHEET);
    }

    @Test
    void llmBaseline_duplicateQuestionId_reportsDuplicate() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet llm = wb.createSheet(WorkbookSheetNames.LLM_READER_QUESTIONS);
        writeRow(llm.createRow(0), "id", "question");
        writeRow(llm.createRow(1), "same", "One");
        writeRow(llm.createRow(2), "same", "Two");

        WorkbookParseResult result = parse(wb, ExperimentalDatasetType.LLM_MODEL_BASELINE);
        assertThat(codes(result, ValidationSeverity.ERROR)).contains(ValidationIssueCode.DUPLICATE_ID);
    }

    @Test
    void corpusDocuments_missingDocumentIdColumn_reportsMissingColumn() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet llm = wb.createSheet(WorkbookSheetNames.LLM_READER_QUESTIONS);
        writeRow(llm.createRow(0), "id", "question");
        writeRow(llm.createRow(1), "q1", "Q");
        Sheet corpus = wb.createSheet(WorkbookSheetNames.CORPUS_DOCUMENTS);
        writeRow(corpus.createRow(0), "title_only");
        writeRow(corpus.createRow(1), "doc-a");

        WorkbookParseResult result = parse(wb, ExperimentalDatasetType.LLM_MODEL_BASELINE);
        assertThat(codes(result, ValidationSeverity.ERROR)).contains(ValidationIssueCode.MISSING_COLUMN);
    }

    @Test
    void embeddingBaseline_matchingGoldChunk_hasNoErrors() throws Exception {
        Workbook wb = new XSSFWorkbook();
        addChunkRegistry(wb, "c1", "d1");
        Sheet emb = wb.createSheet(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES);
        writeRow(emb.createRow(0), "id", "query", "gold_chunk_ids");
        for (int i = 1; i <= 20; i++) {
            writeRow(emb.createRow(i), "e" + i, "hello " + i, "c1");
        }

        WorkbookParseResult result = parse(wb, ExperimentalDatasetType.EMBEDDING_MODEL_BASELINE);
        assertThat(result.validationReport().hasErrors()).isFalse();
        assertThat(result.workbook().embeddingRetrievalQueries()).hasSize(20);
    }

    @Test
    void embeddingBaseline_unknownGoldChunk_reportsUnknownRef() throws Exception {
        Workbook wb = new XSSFWorkbook();
        addChunkRegistry(wb, "c1", "d1");
        Sheet emb = wb.createSheet(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES);
        writeRow(emb.createRow(0), "id", "query", "gold_chunk_ids");
        for (int i = 1; i <= 20; i++) {
            writeRow(emb.createRow(i), "e" + i, "hello " + i, "not-in-registry");
        }

        WorkbookParseResult result = parse(wb, ExperimentalDatasetType.EMBEDDING_MODEL_BASELINE);
        assertThat(codes(result, ValidationSeverity.ERROR)).contains(ValidationIssueCode.UNKNOWN_CHUNK_REF);
    }

    @Test
    void ragPresetBenchmark_valid_hasNoErrors() throws Exception {
        Workbook wb = new XSSFWorkbook();
        addChunkRegistry(wb, "c1", "d1");
        Sheet rag = wb.createSheet(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED);
        writeRow(rag.createRow(0), "id", "question", "query_type", "difficulty");
        for (int i = 1; i <= 20; i++) {
            writeRow(rag.createRow(i), "r" + i, "Summarize the policy " + i, "COUNT_DOCUMENTS", "LOW");
        }

        WorkbookParseResult result = parse(wb, ExperimentalDatasetType.RAG_PRESET_BENCHMARK);
        assertThat(result.validationReport().hasErrors()).isFalse();
        assertThat(result.workbook().ragPresetQuestionsEnriched()).hasSize(20);
    }

    @Test
    void referenceBundle_classpathArtifact_hasNoErrors() throws Exception {
        ClassPathResource r = new ClassPathResource(EvaluationReferenceBundleLoader.CLASSPATH_LOCATION);
        assertThat(r.exists()).isTrue();
        try (InputStream in = r.getInputStream()) {
            WorkbookParseResult result = parser.parse(in, ExperimentalDatasetType.REFERENCE_BUNDLE);
            assertThat(result.validationReport().hasErrors()).isFalse();
        }
    }

    @Test
    void classifierDataset_sheetWithQuestionAndQueryType_hasNoErrors() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("classifier");
        writeRow(s.createRow(0), "Question", "QueryType");
        writeRow(s.createRow(1), "How many?", "COUNT_DOCUMENTS");

        WorkbookParseResult result = parse(wb, ExperimentalDatasetType.CLASSIFIER_DATASET);
        assertThat(result.validationReport().hasErrors()).isFalse();
        assertThat(result.workbook().classifierQuestions()).hasSize(1);
    }

    @Test
    void classifierDataset_noMatchingSheet_reportsMissing() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("notes");
        writeRow(s.createRow(0), "foo");
        writeRow(s.createRow(1), "bar");

        WorkbookParseResult result = parse(wb, ExperimentalDatasetType.CLASSIFIER_DATASET);
        assertThat(codes(result, ValidationSeverity.ERROR)).contains(ValidationIssueCode.MISSING_SHEET);
    }

    private static void addChunkRegistry(Workbook wb, String chunkId, String documentId) {
        Sheet reg = wb.createSheet(WorkbookSheetNames.CHUNK_REGISTRY);
        writeRow(reg.createRow(0), "chunk_id", "document_id");
        writeRow(reg.createRow(1), chunkId, documentId);
    }

    private static void writeRow(Row row, String... values) {
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private WorkbookParseResult parse(Workbook wb, ExperimentalDatasetType type) throws IOException {
        return parser.parse(toStream(wb), type);
    }

    private static byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        wb.close();
        return baos.toByteArray();
    }

    private static ByteArrayInputStream toStream(Workbook wb) throws IOException {
        return new ByteArrayInputStream(toBytes(wb));
    }

    private static Set<ValidationIssueCode> codes(WorkbookParseResult result, ValidationSeverity sev) {
        List<ValidationIssue> issues =
                result.validationReport().issues().stream().filter(i -> i.severity() == sev).toList();
        return issues.stream().map(ValidationIssue::code).collect(Collectors.toSet());
    }
}
