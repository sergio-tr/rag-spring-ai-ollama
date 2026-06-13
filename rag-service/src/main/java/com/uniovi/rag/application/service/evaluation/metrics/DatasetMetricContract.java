package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.model.QueryType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Copies dataset gold and answerability fields into persisted evaluation metrics. */
public final class DatasetMetricContract {

    public static final String KEY_ANSWERABILITY = "answerability";
    public static final String KEY_ANSWERABILITY_SOURCE = "answerabilitySource";
    public static final String KEY_QUERY_TYPE_EXPECTED = "queryTypeExpected";
    public static final String KEY_GOLD_DOCUMENT_IDS = "goldDocumentIds";
    public static final String KEY_GOLD_CHUNK_IDS = "goldChunkIds";
    public static final String KEY_ANSWER_MODE = "answerMode";
    public static final String KEY_EXPECTED_EVIDENCE_COUNT = "expectedEvidenceCount";
    public static final String KEY_UNANSWERABLE_DECLARED = "unanswerableDeclared";
    public static final String KEY_AMBIGUOUS_DECLARED = "ambiguousDeclared";
    public static final String KEY_EXPECTED_ANSWER_PRESENT = "expectedAnswerPresent";

    private DatasetMetricContract() {}

    public static void enrichFromQuestion(Map<String, Object> metrics, RagPresetQuestion question) {
        if (metrics == null || question == null) {
            return;
        }
        Answerability answerability =
                Answerability.fromDataset(
                        question.unanswerable(),
                        question.unanswerableDeclared(),
                        question.ambiguous(),
                        question.ambiguousDeclared());
        AnswerabilitySource source =
                question.unanswerableDeclared() || question.ambiguousDeclared()
                        ? AnswerabilitySource.DATASET_COLUMN
                        : AnswerabilitySource.UNKNOWN;
        metrics.put(KEY_ANSWERABILITY, answerability.name());
        metrics.put(KEY_ANSWERABILITY_SOURCE, source.name());
        metrics.put(KEY_UNANSWERABLE_DECLARED, question.unanswerableDeclared());
        metrics.put(KEY_AMBIGUOUS_DECLARED, question.ambiguousDeclared());
        metrics.put(
                KEY_EXPECTED_ANSWER_PRESENT,
                question.expectedAnswer() != null && !question.expectedAnswer().isBlank());
        question.queryType()
                .map(QueryType::name)
                .ifPresent(qt -> metrics.put(KEY_QUERY_TYPE_EXPECTED, qt));
        if (question.answerMode() != null && !question.answerMode().isBlank()) {
            metrics.put(KEY_ANSWER_MODE, question.answerMode());
        }
        if (question.expectedEvidenceCount() != null && !question.expectedEvidenceCount().isBlank()) {
            metrics.put(KEY_EXPECTED_EVIDENCE_COUNT, question.expectedEvidenceCount());
        }
        List<String> goldDocs = sanitizeGoldIds(question.goldDocumentIds());
        List<String> goldChunks = sanitizeGoldIds(question.goldChunkIds());
        if (!goldDocs.isEmpty()) {
            metrics.put(KEY_GOLD_DOCUMENT_IDS, goldDocs);
            metrics.put("gold_document_ids", goldDocs);
        }
        if (!goldChunks.isEmpty()) {
            metrics.put(KEY_GOLD_CHUNK_IDS, goldChunks);
            metrics.put("gold_chunk_ids", goldChunks);
        }
    }

    /** Copies expected query type from persisted row fields when the contract block is absent. */
    public static void mergeRowQueryType(Map<String, Object> row, Map<String, Object> metrics) {
        if (row == null || metrics == null) {
            return;
        }
        Object raw = row.get("query_type");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            ensureQueryTypeExpected(metrics, String.valueOf(raw).trim());
        }
    }

    /**
     * Ensures {@link #KEY_QUERY_TYPE_EXPECTED} is set from explicit fallbacks or legacy {@code query_type}.
     */
    public static void ensureQueryTypeExpected(Map<String, Object> metrics, String... fallbacks) {
        if (metrics == null || metrics.containsKey(KEY_QUERY_TYPE_EXPECTED)) {
            return;
        }
        if (fallbacks != null) {
            for (String fallback : fallbacks) {
                if (fallback != null && !fallback.isBlank()) {
                    metrics.put(KEY_QUERY_TYPE_EXPECTED, fallback.trim());
                    return;
                }
            }
        }
        Object legacy = metrics.get("query_type");
        if (legacy != null && !String.valueOf(legacy).isBlank()) {
            metrics.put(KEY_QUERY_TYPE_EXPECTED, String.valueOf(legacy).trim());
        }
    }

    public static void enrichFromMetricsMap(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (String key :
                List.of(
                        KEY_ANSWERABILITY,
                        KEY_ANSWERABILITY_SOURCE,
                        KEY_QUERY_TYPE_EXPECTED,
                        KEY_GOLD_DOCUMENT_IDS,
                        KEY_GOLD_CHUNK_IDS,
                        KEY_ANSWER_MODE,
                        KEY_EXPECTED_EVIDENCE_COUNT,
                        KEY_UNANSWERABLE_DECLARED,
                        KEY_AMBIGUOUS_DECLARED,
                        KEY_EXPECTED_ANSWER_PRESENT,
                        "gold_document_ids",
                        "gold_chunk_ids")) {
            if (source.containsKey(key) && !target.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }

    public static boolean hasGoldLabels(Map<String, Object> metrics) {
        return !sanitizeGoldIds(readStringList(metrics, KEY_GOLD_DOCUMENT_IDS)).isEmpty()
                || !sanitizeGoldIds(readStringList(metrics, KEY_GOLD_CHUNK_IDS)).isEmpty()
                || !sanitizeGoldIds(readStringList(metrics, "gold_document_ids")).isEmpty()
                || !sanitizeGoldIds(readStringList(metrics, "gold_chunk_ids")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    static List<String> readStringList(Map<String, Object> metrics, String key) {
        if (metrics == null || key == null) {
            return List.of();
        }
        Object raw = metrics.get(key);
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
            return List.copyOf(out);
        }
        if (raw instanceof String s && !s.isBlank()) {
            return List.of(s.trim());
        }
        return List.of();
    }

    static List<String> sanitizeGoldIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : raw) {
            if (id == null) {
                continue;
            }
            String t = id.trim();
            if (t.isEmpty() || "NONE".equalsIgnoreCase(t)) {
                continue;
            }
            out.add(t);
        }
        return List.copyOf(out);
    }

    static Answerability readAnswerability(Map<String, Object> metrics) {
        if (metrics == null) {
            return Answerability.UNKNOWN;
        }
        Object raw = metrics.get(KEY_ANSWERABILITY);
        if (raw == null) {
            return Answerability.UNKNOWN;
        }
        try {
            return Answerability.valueOf(String.valueOf(raw).trim());
        } catch (IllegalArgumentException ex) {
            return Answerability.UNKNOWN;
        }
    }
}
