package com.uniovi.rag.application.service.evaluation.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.model.QueryType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes uncertain answerability rows to internal CSV/JSON artifacts. */
public final class AnswerabilityReviewQueueExporter {

    public static final String DEFAULT_EXPORT_DIR = "exports/evaluation-evidence/answerability-labeling";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private AnswerabilityReviewQueueExporter() {}

    public static Path defaultExportDir() {
        String configured = System.getProperty("rag.evaluation.answerabilityReviewQueueDir");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        return Path.of(DEFAULT_EXPORT_DIR);
    }

    public static ExportResult exportReviewQueue(
            List<RagPresetQuestion> questions, String labelledDatasetSha256, Path exportDir) throws IOException {
        if (questions == null || questions.isEmpty()) {
            return new ExportResult(List.of(), null, null);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RagPresetQuestion question : questions) {
            AnswerabilityLabelResult label = AnswerabilityLabelingService.label(question);
            if (label.label() != Answerability.NEEDS_REVIEW) {
                continue;
            }
            rows.add(toRow(question, label, labelledDatasetSha256));
        }
        if (rows.isEmpty()) {
            return new ExportResult(List.copyOf(rows), null, null);
        }
        Path dir = exportDir != null ? exportDir : defaultExportDir();
        Files.createDirectories(dir);
        String sha8 =
                labelledDatasetSha256 != null && labelledDatasetSha256.length() >= 8
                        ? labelledDatasetSha256.substring(0, 8)
                        : "unknown";
        String baseName = "review-queue-" + sha8 + "-v" + AnswerabilityLabelingService.rulesVersion();
        Path csvPath = dir.resolve(baseName + ".csv");
        Path jsonPath = dir.resolve(baseName + ".json");
        writeCsv(csvPath, rows);
        MAPPER.writeValue(jsonPath.toFile(), rows);
        return new ExportResult(List.copyOf(rows), csvPath, jsonPath);
    }

    static Map<String, Object> toRow(
            RagPresetQuestion question, AnswerabilityLabelResult label, String labelledDatasetSha256) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("datasetQuestionId", question.id());
        row.put("question", question.question());
        row.put("expectedAnswer", question.expectedAnswer());
        row.put("proposedLabel", label.label().name());
        row.put("answerabilitySource", label.source().name());
        row.put("ruleId", label.ruleId());
        row.put("reason", label.reason());
        row.put("confidence", label.confidence() != null ? label.confidence().name() : "");
        row.put("queryTypeExpected", question.queryType().map(QueryType::name).orElse(""));
        row.put("notes", "");
        row.put("labelledDatasetSha256", labelledDatasetSha256 != null ? labelledDatasetSha256 : "");
        row.put("answerabilityRulesVersion", AnswerabilityLabelingService.rulesVersion());
        return row;
    }

    private static void writeCsv(Path csvPath, List<Map<String, Object>> rows) throws IOException {
        List<String> headers =
                List.of(
                        "datasetQuestionId",
                        "question",
                        "expectedAnswer",
                        "proposedLabel",
                        "answerabilitySource",
                        "ruleId",
                        "reason",
                        "confidence",
                        "queryTypeExpected",
                        "notes",
                        "labelledDatasetSha256",
                        "answerabilityRulesVersion");
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append('\n');
        for (Map<String, Object> row : rows) {
            List<String> cells = new ArrayList<>();
            for (String header : headers) {
                cells.add(csvEscape(String.valueOf(row.getOrDefault(header, ""))));
            }
            sb.append(String.join(",", cells)).append('\n');
        }
        Files.writeString(csvPath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    public record ExportResult(List<Map<String, Object>> rows, Path csvPath, Path jsonPath) {}
}
