package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.domain.evaluation.workbook.ValidationSeverity;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentalDatasetTemplateContractTest {

    private final EvaluationWorkbookParser parser = new EvaluationWorkbookParser();

    @Test
    void llmTemplate_containsRoleEvalCasesHeaders() throws Exception {
        byte[] bytes = ExperimentalDatasetTemplateFactory.buildTemplate(ExperimentalDatasetType.LLM_MODEL_BASELINE);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet(WorkbookSheetNames.LLM_ROLE_EVAL_CASES);
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            assertThat(cell(header, 0)).isEqualTo("case_id");
            assertThat(cell(header, 3)).isEqualTo("role_profile");
            assertThat(cell(header, 9)).isEqualTo("scoring_type");
        }
    }

    @Test
    void llmTemplate_containsContextAndExpectedAnswerHeaders() throws Exception {
        byte[] bytes = ExperimentalDatasetTemplateFactory.buildTemplate(ExperimentalDatasetType.LLM_MODEL_BASELINE);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet(WorkbookSheetNames.LLM_READER_QUESTIONS);
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            assertThat(cell(header, 0)).isEqualTo("id");
            assertThat(cell(header, 1)).isEqualTo("question");
            assertThat(cell(header, 2)).isEqualTo("context_text");
            assertThat(cell(header, 3)).isEqualTo("expected_answer");
            assertThat(cell(header, 4)).isEqualTo("evaluation_notes");
        }
    }

    @Test
    void embeddingTemplate_containsQueryAndExpectedResultHeaders() throws Exception {
        byte[] bytes = ExperimentalDatasetTemplateFactory.buildTemplate(ExperimentalDatasetType.EMBEDDING_MODEL_BASELINE);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet emb = wb.getSheet(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES);
            assertThat(emb).isNotNull();
            Row header = emb.getRow(0);
            assertThat(cell(header, 0)).isEqualTo("id");
            assertThat(cell(header, 1)).isEqualTo("query");
            assertThat(cell(header, 2)).isEqualTo("expected_document_id");
            assertThat(cell(header, 3)).isEqualTo("expected_chunk_id");
            assertThat(cell(header, 4)).isEqualTo("expected_content");
            assertThat(cell(header, 5)).isEqualTo("expected_relevant_chunk_ids");
            assertThat(cell(header, 6)).isEqualTo("evaluation_notes");
        }
    }

    @Test
    void ragTemplate_containsExpectedAnswerHeader() throws Exception {
        byte[] bytes = ExperimentalDatasetTemplateFactory.buildTemplate(ExperimentalDatasetType.RAG_PRESET_BENCHMARK);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet rag = wb.getSheet(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED);
            assertThat(rag).isNotNull();
            Row header = rag.getRow(0);
            assertThat(cell(header, 2)).isEqualTo("expected_answer");
            assertThat(cell(header, 3)).isEqualTo("expected_sources");
            assertThat(cell(header, 4)).isEqualTo("context_text");
        }
    }

    @Test
    void generatedLlmTemplate_hasValidStructureWithoutRowGateErrors() throws Exception {
        byte[] bytes = ExperimentalDatasetTemplateFactory.buildTemplate(ExperimentalDatasetType.LLM_MODEL_BASELINE);
        WorkbookParseResult result =
                parser.parse(new ByteArrayInputStream(bytes), ExperimentalDatasetType.LLM_MODEL_BASELINE);
        assertThat(result.validationReport().issues()).noneMatch(
                i -> i.code() == ValidationIssueCode.MISSING_SHEET
                        || i.code() == ValidationIssueCode.MISSING_COLUMN
                        || i.code() == ValidationIssueCode.EMPTY_REQUIRED_CELL);
    }

    @Test
    void oldIncompleteLlmTemplate_rejectsMissingExpectedAnswer() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet llm = wb.createSheet(WorkbookSheetNames.LLM_READER_QUESTIONS);
        writeRow(llm.createRow(0), "id", "question");
        for (int i = 1; i <= 10; i++) {
            writeRow(llm.createRow(i), "q" + i, "Question " + i);
        }
        WorkbookParseResult result = parseWorkbook(wb, ExperimentalDatasetType.LLM_MODEL_BASELINE);
        assertThat(result.validationReport().hasErrors()).isTrue();
        assertThat(issueCodes(result)).contains(ValidationIssueCode.EMPTY_REQUIRED_CELL);
    }

    @Test
    void classifierTemplate_unchanged() throws Exception {
        byte[] bytes = ExperimentalDatasetTemplateFactory.buildTemplate(ExperimentalDatasetType.CLASSIFIER_DATASET);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("classifier");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            assertThat(cell(header, 0)).isEqualTo("Question");
            assertThat(cell(header, 1)).isEqualTo("QueryType");
        }
        WorkbookParseResult result =
                parser.parse(new ByteArrayInputStream(bytes), ExperimentalDatasetType.CLASSIFIER_DATASET);
        assertThat(result.validationReport().hasErrors()).isTrue();
    }

    private static WorkbookParseResult parseWorkbook(XSSFWorkbook wb, ExperimentalDatasetType type) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        wb.close();
        EvaluationWorkbookParser parser = new EvaluationWorkbookParser();
        return parser.parse(new ByteArrayInputStream(bos.toByteArray()), type);
    }

    private static Set<ValidationIssueCode> issueCodes(WorkbookParseResult result) {
        return result.validationReport().issues().stream()
                .filter(i -> i.severity() == ValidationSeverity.ERROR)
                .map(i -> i.code())
                .collect(Collectors.toSet());
    }

    private static String cell(Row row, int idx) {
        return row.getCell(idx).getStringCellValue();
    }

    private static void writeRow(Row row, String... values) {
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
