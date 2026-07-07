package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.ClassifierQuestionRow;
import com.uniovi.rag.domain.evaluation.workbook.ChunkRegistryEntry;
import com.uniovi.rag.domain.evaluation.workbook.CorpusDocument;
import com.uniovi.rag.domain.evaluation.workbook.DifficultyLevel;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingCandidate;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalQuery;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.LlmRoleEvalCase;
import com.uniovi.rag.domain.evaluation.workbook.MetricSpec;
import com.uniovi.rag.domain.evaluation.workbook.ModelCandidate;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.evaluation.workbook.ReadmeEntry;
import com.uniovi.rag.domain.evaluation.workbook.ResultSchemaField;
import com.uniovi.rag.domain.evaluation.workbook.SummaryCountRow;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.ValidationSeverity;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import com.uniovi.rag.domain.model.QueryType;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads experimental evaluation XLSX workbooks by header name (column-index independent).
 */
@Component
public final class EvaluationWorkbookParser {

    public WorkbookParseResult parse(InputStream inputStream, ExperimentalDatasetType datasetType) {
        ValidationReport report = new ValidationReport();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            List<String> sheetNames = new ArrayList<>();
            workbook.forEach(s -> sheetNames.add(s.getSheetName()));

            EvaluationWorkbook.Builder builder = EvaluationWorkbook.builder().sheetNamesPresent(sheetNames);

            parseReadme(workbook, builder, report);
            parseCorpus(workbook, builder, report);
            parseChunkRegistry(workbook, builder, report);
            parseLlmReader(workbook, builder, report);
            parseLlmRoleEvalCases(workbook, builder, report);
            parseEmbeddingQueries(workbook, builder, report);
            parseRagPresetQuestions(workbook, builder, report);
            parseLlmCandidates(workbook, builder, report);
            parseEmbeddingCandidates(workbook, builder, report);
            parsePresetCatalog(workbook, builder, report);
            parseMetricSpec(workbook, builder, report);
            parseResultSchema(workbook, builder, report);
            parseSummaryCounts(workbook, builder, report);
            parseClassifierIfPresent(workbook, builder, report);

            EvaluationWorkbook wb = builder.build();
            ExperimentalWorkbookValidator.validate(datasetType, wb, report);
            return new WorkbookParseResult(wb, report);
        } catch (IOException | RuntimeException e) {
            ValidationReport r = new ValidationReport();
            r.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    ValidationIssueCode.WORKBOOK_IO_ERROR,
                    "",
                    0,
                    "",
                    e.getMessage() != null ? e.getMessage() : "Failed to read workbook"));
            return new WorkbookParseResult(EvaluationWorkbook.builder().build(), r);
        }
    }

    private static void parseReadme(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.README);
        if (sheet == null) {
            return;
        }
        List<ReadmeEntry> rows = new ArrayList<>();
        Row header = sheet.getRow(0);
        if (header == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(header);
        if (!hi.containsKey(ExcelCellSupport.normalizeHeader("Item"))
                || !hi.containsKey(ExcelCellSupport.normalizeHeader("Decision"))) {
            report.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    ValidationIssueCode.MISSING_COLUMN,
                    WorkbookSheetNames.README,
                    1,
                    "",
                    "Expected columns Item and Decision"));
            return;
        }
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String item = ExcelCellSupport.cellString(row, hi, "Item", r + 1, false);
            String decision = ExcelCellSupport.cellString(row, hi, "Decision", r + 1, false);
            rows.add(new ReadmeEntry(item, decision));
        }
        b.readme(rows);
    }

    private static void parseCorpus(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.CORPUS_DOCUMENTS);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        if (!hi.containsKey("document_id")) {
            report.add(missingCol(WorkbookSheetNames.CORPUS_DOCUMENTS, "document_id"));
            return;
        }
        List<CorpusDocument> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String docId = ExcelCellSupport.cellString(row, hi, "document_id", r + 1, true);
            if (docId == null || docId.isBlank()) {
                report.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.EMPTY_REQUIRED_CELL,
                        WorkbookSheetNames.CORPUS_DOCUMENTS,
                        r + 1,
                        "document_id",
                        "document_id is required"));
                continue;
            }
            if (!ids.add(docId.trim())) {
                report.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.DUPLICATE_ID,
                        WorkbookSheetNames.CORPUS_DOCUMENTS,
                        r + 1,
                        "document_id",
                        "Duplicate document_id: " + docId));
                continue;
            }
            Map<String, String> extra = extraColumns(row, hi, "document_id");
            list.add(new CorpusDocument(docId.trim(), extra));
        }
        b.corpusDocuments(list);
    }

    private static void parseChunkRegistry(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.CHUNK_REGISTRY);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        requireCols(report, WorkbookSheetNames.CHUNK_REGISTRY, hi, "chunk_id", "document_id");
        if (!hi.containsKey("chunk_id") || !hi.containsKey("document_id")) {
            return;
        }
        List<ChunkRegistryEntry> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String chunkId = ExcelCellSupport.cellString(row, hi, "chunk_id", r + 1, true);
            String docId = ExcelCellSupport.cellString(row, hi, "document_id", r + 1, true);
            String ctype = ExcelCellSupport.cellString(row, hi, "chunk_type", r + 1, false);
            String gold = ExcelCellSupport.cellString(row, hi, "gold_evidence_text", r + 1, false);
            if (chunkId == null || chunkId.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.CHUNK_REGISTRY, r + 1, "chunk_id"));
                continue;
            }
            if (!ids.add(chunkId.trim())) {
                report.add(dupId(WorkbookSheetNames.CHUNK_REGISTRY, r + 1, "chunk_id", chunkId));
                continue;
            }
            list.add(new ChunkRegistryEntry(chunkId.trim(), nvl(docId), nvl(ctype), nvl(gold)));
        }
        b.chunkRegistry(list);
    }

    private static void parseLlmReader(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.LLM_READER_QUESTIONS);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        requireCols(report, WorkbookSheetNames.LLM_READER_QUESTIONS, hi, "id", "question");
        if (!hi.containsKey("id") || !hi.containsKey("question")) {
            return;
        }
        List<LlmReaderQuestion> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String id = ExcelCellSupport.cellString(row, hi, "id", r + 1, true);
            String question = ExcelCellSupport.cellString(row, hi, "question", r + 1, true);
            if (id == null || id.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.LLM_READER_QUESTIONS, r + 1, "id"));
                continue;
            }
            if (!ids.add(id.trim())) {
                report.add(dupId(WorkbookSheetNames.LLM_READER_QUESTIONS, r + 1, "id", id));
                continue;
            }
            if (question == null || question.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.LLM_READER_QUESTIONS, r + 1, "question"));
                continue;
            }
            String ctx = ExcelCellSupport.cellString(row, hi, "context_text", r + 1, false);
            String exp = ExcelCellSupport.cellString(row, hi, "expected_answer", r + 1, false);
            String evalNotes = ExcelCellSupport.cellString(row, hi, "evaluation_notes", r + 1, false);
            Optional<QueryType> qt = parseQueryType(
                    ExcelCellSupport.cellString(row, hi, "query_type", r + 1, false),
                    WorkbookSheetNames.LLM_READER_QUESTIONS,
                    r + 1,
                    report);
            String diffRaw = ExcelCellSupport.cellString(row, hi, "difficulty", r + 1, false);
            Optional<DifficultyLevel> diff = DifficultyLevel.tryParse(diffRaw);
            if (diffRaw != null && !diffRaw.isBlank() && diff.isEmpty()) {
                report.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.INVALID_DIFFICULTY,
                        WorkbookSheetNames.LLM_READER_QUESTIONS,
                        r + 1,
                        "difficulty",
                        "Invalid difficulty value"));
            }
            String answerMode = ExcelCellSupport.cellString(row, hi, "answer_mode", r + 1, false);
            String srcDoc = ExcelCellSupport.cellString(row, hi, "source_document_id", r + 1, false);
            String gold = ExcelCellSupport.cellString(row, hi, "gold_evidence", r + 1, false);
            boolean unanswerable =
                    ExcelCellSupport.parseBooleanCell(ExcelCellSupport.cellString(row, hi, "unanswerable", r + 1, false));
            String evalMethod = ExcelCellSupport.cellString(row, hi, "evaluation_method", r + 1, false);
            if ((evalMethod == null || evalMethod.isBlank()) && evalNotes != null && !evalNotes.isBlank()) {
                evalMethod = evalNotes;
            }
            list.add(new LlmReaderQuestion(
                    id.trim(),
                    question,
                    nvl(ctx),
                    nvl(exp),
                    qt,
                    diff,
                    nvl(answerMode),
                    nvl(srcDoc),
                    nvl(gold),
                    unanswerable,
                    nvl(evalMethod)));
        }
        b.llmReaderQuestions(list);
    }

    private static void parseLlmRoleEvalCases(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.LLM_ROLE_EVAL_CASES);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        requireCols(
                report,
                WorkbookSheetNames.LLM_ROLE_EVAL_CASES,
                hi,
                "case_id",
                "subset",
                "role_family",
                "role_profile",
                "input",
                "expected_output",
                "scoring_type");
        if (!hi.containsKey("case_id") || !hi.containsKey("input")) {
            return;
        }
        List<LlmRoleEvalCase> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String caseId = ExcelCellSupport.cellString(row, hi, "case_id", r + 1, true);
            if (caseId == null || caseId.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.LLM_ROLE_EVAL_CASES, r + 1, "case_id"));
                continue;
            }
            if (!ids.add(caseId.trim())) {
                report.add(dupId(WorkbookSheetNames.LLM_ROLE_EVAL_CASES, r + 1, "case_id", caseId));
                continue;
            }
            String input = ExcelCellSupport.cellString(row, hi, "input", r + 1, true);
            if (input == null || input.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.LLM_ROLE_EVAL_CASES, r + 1, "input"));
                continue;
            }
            String expected = ExcelCellSupport.cellString(row, hi, "expected_output", r + 1, false);
            if (expected == null || expected.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.LLM_ROLE_EVAL_CASES, r + 1, "expected_output"));
                continue;
            }
            list.add(
                    new LlmRoleEvalCase(
                            caseId.trim(),
                            nvl(ExcelCellSupport.cellString(row, hi, "subset", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "role_family", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "role_profile", r + 1, false)),
                            input,
                            nvl(ExcelCellSupport.cellString(row, hi, "context", r + 1, false)),
                            expected,
                            nvl(ExcelCellSupport.cellString(row, hi, "expected_keywords", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "forbidden_terms", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "scoring_type", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "required_json_keys", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "notes", r + 1, false))));
        }
        b.llmRoleEvalCases(list);
    }

    private static void parseEmbeddingQueries(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        requireCols(report, WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES, hi, "id", "query");
        if (!hi.containsKey("id") || !hi.containsKey("query")) {
            return;
        }
        List<EmbeddingRetrievalQuery> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String id = ExcelCellSupport.cellString(row, hi, "id", r + 1, true);
            String query = ExcelCellSupport.cellString(row, hi, "query", r + 1, true);
            if (id == null || id.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES, r + 1, "id"));
                continue;
            }
            if (!ids.add(id.trim())) {
                report.add(dupId(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES, r + 1, "id", id));
                continue;
            }
            if (query == null || query.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES, r + 1, "query"));
                continue;
            }
            String variant = ExcelCellSupport.cellString(row, hi, "query_variant_type", r + 1, false);
            Optional<QueryType> qt = parseQueryType(
                    ExcelCellSupport.cellString(row, hi, "query_type", r + 1, false),
                    WorkbookSheetNames.EMBEDDING_RETRIEVAL_QUERIES,
                    r + 1,
                    report);
            Optional<DifficultyLevel> diff = DifficultyLevel.tryParse(
                    ExcelCellSupport.cellString(row, hi, "difficulty", r + 1, false));
            String expectedContent = ExcelCellSupport.cellString(row, hi, "expected_content", r + 1, false);
            String expected = ExcelCellSupport.cellString(row, hi, "expected_answer", r + 1, false);
            if ((expected == null || expected.isBlank()) && expectedContent != null && !expectedContent.isBlank()) {
                expected = expectedContent;
            }
            List<String> gDocs =
                    mergeIdLists(
                            ExcelCellSupport.cellString(row, hi, "gold_document_ids", r + 1, false),
                            ExcelCellSupport.cellString(row, hi, "expected_document_id", r + 1, false));
            List<String> gChunks =
                    mergeIdLists(
                            ExcelCellSupport.cellString(row, hi, "gold_chunk_ids", r + 1, false),
                            ExcelCellSupport.cellString(row, hi, "expected_chunk_id", r + 1, false),
                            ExcelCellSupport.cellString(row, hi, "expected_relevant_chunk_ids", r + 1, false));
            String any = ExcelCellSupport.cellString(row, hi, "must_retrieve_any", r + 1, false);
            String all = ExcelCellSupport.cellString(row, hi, "must_retrieve_all", r + 1, false);
            String notes = firstNonBlank(
                    ExcelCellSupport.cellString(row, hi, "evaluation_notes", r + 1, false),
                    ExcelCellSupport.cellString(row, hi, "notes", r + 1, false));
            list.add(new EmbeddingRetrievalQuery(
                    id.trim(),
                    query,
                    nvl(variant),
                    qt,
                    diff,
                    nvl(expected),
                    gDocs,
                    gChunks,
                    nvl(any),
                    nvl(all),
                    nvl(notes)));
        }
        b.embeddingRetrievalQueries(list);
    }

    private static void parseRagPresetQuestions(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        requireCols(report, WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED, hi, "id", "question");
        if (!hi.containsKey("id") || !hi.containsKey("question")) {
            return;
        }
        List<RagPresetQuestion> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String id = ExcelCellSupport.cellString(row, hi, "id", r + 1, true);
            String question = ExcelCellSupport.cellString(row, hi, "question", r + 1, true);
            if (id == null || id.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED, r + 1, "id"));
                continue;
            }
            if (!ids.add(id.trim())) {
                report.add(dupId(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED, r + 1, "id", id));
                continue;
            }
            if (question == null || question.isBlank()) {
                report.add(emptyRequired(WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED, r + 1, "question"));
                continue;
            }
            String exp = ExcelCellSupport.cellString(row, hi, "expected_answer", r + 1, false);
            String contextText = ExcelCellSupport.cellString(row, hi, "context_text", r + 1, false);
            Optional<QueryType> qt = parseQueryType(
                    ExcelCellSupport.cellString(row, hi, "query_type", r + 1, false),
                    WorkbookSheetNames.RAG_PRESET_QUESTIONS_ENRICHED,
                    r + 1,
                    report);
            Optional<DifficultyLevel> diff = DifficultyLevel.tryParse(
                    ExcelCellSupport.cellString(row, hi, "difficulty", r + 1, false));
            String answerMode = ExcelCellSupport.cellString(row, hi, "answer_mode", r + 1, false);
            List<String> gDocs =
                    mergeIdLists(
                            ExcelCellSupport.cellString(row, hi, "gold_document_ids", r + 1, false),
                            ExcelCellSupport.cellString(row, hi, "expected_sources", r + 1, false));
            List<String> gChunks = splitIds(ExcelCellSupport.cellString(row, hi, "gold_chunk_ids", r + 1, false));
            String evc = ExcelCellSupport.cellString(row, hi, "expected_evidence_count", r + 1, false);
            boolean unanswerableDeclared = hi.containsKey("unanswerable");
            boolean un = unanswerableDeclared
                    ? ExcelCellSupport.parseBooleanCell(
                            ExcelCellSupport.cellString(row, hi, "unanswerable", r + 1, false))
                    : false;
            boolean ambiguousDeclared = hi.containsKey("ambiguous");
            boolean amb = ambiguousDeclared
                    ? ExcelCellSupport.parseBooleanCell(
                            ExcelCellSupport.cellString(row, hi, "ambiguous", r + 1, false))
                    : false;
            boolean rmd = ExcelCellSupport.parseBooleanCell(
                    ExcelCellSupport.cellString(row, hi, "requires_multi_document", r + 1, false));
            boolean rtr = ExcelCellSupport.parseBooleanCell(
                    ExcelCellSupport.cellString(row, hi, "requires_temporal_reasoning", r + 1, false));
            boolean rag = ExcelCellSupport.parseBooleanCell(
                    ExcelCellSupport.cellString(row, hi, "requires_aggregation", r + 1, false));
            boolean rxe = ExcelCellSupport.parseBooleanCell(
                    ExcelCellSupport.cellString(row, hi, "requires_exact_entities", r + 1, false));
            String notes = firstNonBlank(
                    ExcelCellSupport.cellString(row, hi, "evaluation_notes", r + 1, false),
                    ExcelCellSupport.cellString(row, hi, "notes", r + 1, false));
            if ((notes == null || notes.isBlank()) && contextText != null && !contextText.isBlank()) {
                notes = "context_text=" + contextText.trim();
            }
            list.add(new RagPresetQuestion(
                    id.trim(),
                    question,
                    nvl(exp),
                    qt,
                    diff,
                    nvl(answerMode),
                    gDocs,
                    gChunks,
                    nvl(evc),
                    un,
                    unanswerableDeclared,
                    amb,
                    ambiguousDeclared,
                    rmd,
                    rtr,
                    rag,
                    rxe,
                    nvl(notes)));
        }
        b.ragPresetQuestionsEnriched(list);
    }

    private static void parseLlmCandidates(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.LLM_CANDIDATES);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        if (!hi.containsKey("candidate_id")) {
            report.add(missingCol(WorkbookSheetNames.LLM_CANDIDATES, "candidate_id"));
            return;
        }
        List<ModelCandidate> list = new ArrayList<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String cid = ExcelCellSupport.cellString(row, hi, "candidate_id", r + 1, true);
            if (cid == null || cid.isBlank()) {
                continue;
            }
            Map<String, String> extra = extraColumns(row, hi, "candidate_id", "model", "role", "priority", "expected_fit", "hardware_note", "protocols");
            list.add(
                    new ModelCandidate(
                            cid.trim(),
                            nvl(ExcelCellSupport.cellString(row, hi, "model", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "role", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "priority", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "expected_fit", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "hardware_note", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "protocols", r + 1, false)),
                            extra));
        }
        b.llmCandidates(list);
    }

    private static void parseEmbeddingCandidates(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.EMBEDDING_CANDIDATES);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        if (!hi.containsKey("candidate_id")) {
            report.add(missingCol(WorkbookSheetNames.EMBEDDING_CANDIDATES, "candidate_id"));
            return;
        }
        List<EmbeddingCandidate> list = new ArrayList<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String cid = ExcelCellSupport.cellString(row, hi, "candidate_id", r + 1, true);
            if (cid == null || cid.isBlank()) {
                continue;
            }
            Map<String, String> extra =
                    extraColumns(row, hi, "candidate_id", "model", "role", "priority", "expected_fit", "profile_notes", "protocols");
            list.add(
                    new EmbeddingCandidate(
                            cid.trim(),
                            nvl(ExcelCellSupport.cellString(row, hi, "model", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "role", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "priority", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "expected_fit", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "profile_notes", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "protocols", r + 1, false)),
                            extra));
        }
        b.embeddingCandidates(list);
    }

    private static void parsePresetCatalog(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        requireCols(report, WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14, hi, "preset_id");
        if (!hi.containsKey("preset_id")) {
            return;
        }
        List<RagPresetDefinition> list = new ArrayList<>();
        Set<RagExperimentalPresetCode> seen = new HashSet<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String rawPreset = ExcelCellSupport.cellString(row, hi, "preset_id", r + 1, true);
            Optional<RagExperimentalPresetCode> code = RagExperimentalPresetCode.tryParse(rawPreset);
            if (code.isEmpty()) {
                report.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.INVALID_PRESET_CODE,
                        WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14,
                        r + 1,
                        "preset_id",
                        "Invalid preset_id: " + rawPreset));
                continue;
            }
            if (!seen.add(code.get())) {
                report.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.DUPLICATE_ID,
                        WorkbookSheetNames.RAG_PRESET_CATALOG_P0_P14,
                        r + 1,
                        "preset_id",
                        "Duplicate preset_id: " + code.get()));
                continue;
            }
            list.add(
                    new RagPresetDefinition(
                            code.get(),
                            nvl(ExcelCellSupport.cellString(row, hi, "family", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "name", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "retrieval", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "query_understanding", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "tools", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "memory", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "judges", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "main_or_complement", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "objective", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "dataset_policy", r + 1, false))));
        }
        b.ragPresetCatalog(list);
    }

    private static void parseMetricSpec(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.METRIC_SPEC);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        if (!hi.containsKey("metric_id")) {
            report.add(missingCol(WorkbookSheetNames.METRIC_SPEC, "metric_id"));
            return;
        }
        List<MetricSpec> list = new ArrayList<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String mid = ExcelCellSupport.cellString(row, hi, "metric_id", r + 1, true);
            if (mid == null || mid.isBlank()) {
                continue;
            }
            list.add(
                    new MetricSpec(
                            mid.trim(),
                            nvl(ExcelCellSupport.cellString(row, hi, "scope", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "description", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "primary_for", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "formula_or_rule", r + 1, false))));
        }
        b.metricSpec(list);
    }

    private static void parseResultSchema(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.RESULT_SCHEMA);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        List<ResultSchemaField> list = new ArrayList<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            String field = ExcelCellSupport.cellString(row, hi, "field", r + 1, false);
            String type = ExcelCellSupport.cellString(row, hi, "type", r + 1, false);
            String req = ExcelCellSupport.cellString(row, hi, "required", r + 1, false);
            String desc = ExcelCellSupport.cellString(row, hi, "description", r + 1, false);
            if (field == null || field.isBlank()) {
                continue;
            }
            list.add(new ResultSchemaField(field, nvl(type), ExcelCellSupport.parseBooleanCell(req), nvl(desc)));
        }
        b.resultSchema(list);
    }

    private static void parseSummaryCounts(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        Sheet sheet = wb.getSheet(WorkbookSheetNames.SUMMARY_COUNTS);
        if (sheet == null) {
            return;
        }
        Row h = sheet.getRow(0);
        if (h == null) {
            return;
        }
        Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
        List<SummaryCountRow> list = new ArrayList<>();
        int last = sheet.getLastRowNum();
        for (int r = 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                continue;
            }
            list.add(
                    new SummaryCountRow(
                            nvl(ExcelCellSupport.cellString(row, hi, "Dataset", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "Rows", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "Purpose", r + 1, false)),
                            nvl(ExcelCellSupport.cellString(row, hi, "Primary branch", r + 1, false))));
        }
        b.summaryCounts(list);
    }

    /** Classifier: first sheet whose headers include Question + query_type or legacy querytype. */
    private static void parseClassifierIfPresent(Workbook wb, EvaluationWorkbook.Builder b, ValidationReport report) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet sheet = wb.getSheetAt(i);
            Row h = sheet.getRow(0);
            if (h == null) {
                continue;
            }
            Map<String, Integer> hi = ExcelCellSupport.headerIndexMap(h);
            if (!hi.containsKey("question")) {
                continue;
            }
            String queryTypeHeader = resolveClassifierQueryTypeHeader(hi);
            if (queryTypeHeader == null) {
                continue;
            }
            List<ClassifierQuestionRow> rows = new ArrayList<>();
            int last = sheet.getLastRowNum();
            for (int r = 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellSupport.rowIsCompletelyEmpty(row, row.getLastCellNum())) {
                    continue;
                }
                String q = ExcelCellSupport.cellString(row, hi, "question", r + 1, true);
                String qt = ExcelCellSupport.cellString(row, hi, queryTypeHeader, r + 1, false);
                if (q == null || q.isBlank()) {
                    continue;
                }
                rows.add(new ClassifierQuestionRow(q.trim(), nvl(qt)));
            }
            b.classifierQuestions(rows);
            return;
        }
    }

    /** Prefer canonical {@code query_type}; accept legacy {@code querytype} for backward compatibility. */
    private static String resolveClassifierQueryTypeHeader(Map<String, Integer> hi) {
        if (hi.containsKey("query_type")) {
            return "query_type";
        }
        if (hi.containsKey("querytype")) {
            return "querytype";
        }
        return null;
    }

    private static Map<String, String> extraColumns(Row row, Map<String, Integer> hi, String... knownHeaders) {
        Set<String> known = new HashSet<>();
        for (String k : knownHeaders) {
            known.add(ExcelCellSupport.normalizeHeader(k));
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : hi.entrySet()) {
            if (known.contains(e.getKey())) {
                continue;
            }
            Integer idx = e.getValue();
            String val = ExcelCellSupport.cellValueToString(row.getCell(idx));
            if (val != null && !val.isBlank()) {
                out.put(e.getKey(), val);
            }
        }
        return out;
    }

    private static Optional<QueryType> parseQueryType(String raw, String sheet, int excelRow, ValidationReport report) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return Optional.of(QueryType.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            report.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    ValidationIssueCode.INVALID_QUERY_TYPE,
                    sheet,
                    excelRow,
                    "query_type",
                    "Unknown QueryType: " + raw));
            return Optional.empty();
        }
    }

    private static List<String> splitIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("[;,]");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<String> mergeIdLists(String... rawValues) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (rawValues == null) {
            return out;
        }
        for (String raw : rawValues) {
            for (String id : splitIds(raw)) {
                if (seen.add(id)) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static void requireCols(ValidationReport report, String sheet, Map<String, Integer> hi, String... cols) {
        for (String c : cols) {
            if (!hi.containsKey(c)) {
                report.add(missingCol(sheet, c));
            }
        }
    }

    private static ValidationIssue missingCol(String sheet, String col) {
        return new ValidationIssue(
                ValidationSeverity.ERROR,
                ValidationIssueCode.MISSING_COLUMN,
                sheet,
                1,
                col,
                "Missing required column: " + col);
    }

    private static ValidationIssue emptyRequired(String sheet, int row, String col) {
        return new ValidationIssue(
                ValidationSeverity.ERROR,
                ValidationIssueCode.EMPTY_REQUIRED_CELL,
                sheet,
                row,
                col,
                "Required cell is empty");
    }

    private static ValidationIssue dupId(String sheet, int row, String col, String id) {
        return new ValidationIssue(
                ValidationSeverity.ERROR,
                ValidationIssueCode.DUPLICATE_ID,
                sheet,
                row,
                col,
                "Duplicate " + col + ": " + id);
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
