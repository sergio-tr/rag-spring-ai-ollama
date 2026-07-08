package com.uniovi.rag.application.service.runtime.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Structured conversation anchors persisted on grounded assistant {@code execution_metadata} rows (BL-013). */
public final class ConversationMemoryAnchorMetadata {

    public static final String ANCHORED_ACTA_DATE = "anchoredActaDate";
    public static final String LAST_REFERENCED_DATE = "lastReferencedDate";
    public static final String TOP_SOURCE_DOCUMENT_ID = "topSourceDocumentId";
    public static final String TOP_SOURCE_DOCUMENT_TITLE = "topSourceDocumentTitle";
    public static final String TOP_SOURCE_ORIGINAL_FILE_NAME = "topSourceOriginalFileName";
    public static final String ANSWER_SCOPE = "answerScope";

    public static final String SCOPE_DOCUMENT_BOUND = "DOCUMENT_BOUND";
    public static final String SCOPE_SINGLE_SOURCE = "SINGLE_SOURCE";
    public static final String SCOPE_MULTI_SOURCE = "MULTI_SOURCE";

    private ConversationMemoryAnchorMetadata() {}

    public static Optional<String> readAnchoredActaDate(Map<String, Object> executionMetadata) {
        return readString(executionMetadata, ANCHORED_ACTA_DATE);
    }

    public static Optional<String> readLastReferencedDate(Map<String, Object> executionMetadata) {
        return readString(executionMetadata, LAST_REFERENCED_DATE);
    }

    public static Optional<String> readTopSourceDocumentId(Map<String, Object> executionMetadata) {
        return readString(executionMetadata, TOP_SOURCE_DOCUMENT_ID);
    }

    public static Optional<String> readTopSourceDocumentTitle(Map<String, Object> executionMetadata) {
        return readString(executionMetadata, TOP_SOURCE_DOCUMENT_TITLE);
    }

    public static Optional<String> readTopSourceOriginalFileName(Map<String, Object> executionMetadata) {
        return readString(executionMetadata, TOP_SOURCE_ORIGINAL_FILE_NAME);
    }

    public static Optional<String> readAnswerScope(Map<String, Object> executionMetadata) {
        return readString(executionMetadata, ANSWER_SCOPE);
    }

    /**
     * Merges structured anchor fields into chat telemetry when the turn is grounded (sources present, no abstention).
     */
    public static void enrichGroundedAnchor(Map<String, Object> telemetry, List<Map<String, Object>> responseSources) {
        if (telemetry == null) {
            return;
        }
        if (!isGroundedAnswer(telemetry, responseSources)) {
            return;
        }
        Map<String, Object> anchor = buildGroundedAnchor(telemetry, responseSources);
        telemetry.putAll(anchor);
    }

    static Map<String, Object> buildGroundedAnchor(
            Map<String, Object> telemetry, List<Map<String, Object>> responseSources) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!isGroundedAnswer(telemetry, responseSources)) {
            return Map.copyOf(out);
        }
        Map<String, Object> top = responseSources.get(0);
        String actaDate = resolveAnchoredActaDate(telemetry, top).orElse(null);
        putIfPresent(out, ANCHORED_ACTA_DATE, actaDate);
        putIfPresent(out, LAST_REFERENCED_DATE, actaDate);
        putIfPresent(out, TOP_SOURCE_DOCUMENT_ID, resolveTopDocumentId(top));
        putIfPresent(out, TOP_SOURCE_DOCUMENT_TITLE, resolveTopDocumentTitle(top));
        putIfPresent(out, TOP_SOURCE_ORIGINAL_FILE_NAME, resolveTopOriginalFileName(top));
        putIfPresent(out, ANSWER_SCOPE, resolveAnswerScope(telemetry, responseSources));
        return Map.copyOf(out);
    }

    static boolean isGroundedAnswer(Map<String, Object> telemetry, List<Map<String, Object>> responseSources) {
        if (responseSources == null || responseSources.isEmpty()) {
            return false;
        }
        if (telemetry == null || telemetry.isEmpty()) {
            return true;
        }
        if (Boolean.TRUE.equals(telemetry.get("abstentionTriggered"))) {
            return false;
        }
        if (Boolean.TRUE.equals(telemetry.get("clarificationRequired"))) {
            return false;
        }
        Object workflow = telemetry.get("workflowName");
        if (workflow != null && "clarification".equalsIgnoreCase(String.valueOf(workflow).trim())) {
            return false;
        }
        return true;
    }

    private static Optional<String> resolveAnchoredActaDate(Map<String, Object> telemetry, Map<String, Object> topSource) {
        Optional<String> fromTelemetry = firstNonBlank(
                telemetry,
                "topSourceDate",
                "requestedDate");
        if (fromTelemetry.isPresent()) {
            return fromTelemetry;
        }
        Optional<String> fromSource = firstNonBlank(topSource, "detectedDate", "date_iso");
        if (fromSource.isPresent()) {
            return fromSource;
        }
        if (topSource != null && topSource.get("metadata") instanceof Map<?, ?> meta) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mm = (Map<String, Object>) meta;
            Optional<String> fromMeta = firstNonBlank(mm, "date_iso", "actaDate", "date", "detectedDate");
            if (fromMeta.isPresent()) {
                return fromMeta;
            }
        }
        Object matched = telemetry.get("matchedDocumentDates");
        if (matched instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            String s = String.valueOf(list.get(0)).trim();
            if (!s.isBlank()) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    private static String resolveTopDocumentId(Map<String, Object> topSource) {
        if (topSource == null) {
            return null;
        }
        return firstNonBlank(topSource, "documentId", "document_id", "projectDocumentId", "project_document_id")
                .orElse(null);
    }

    private static String resolveTopDocumentTitle(Map<String, Object> topSource) {
        if (topSource == null) {
            return null;
        }
        if (topSource.get("metadata") instanceof Map<?, ?> meta) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mm = (Map<String, Object>) meta;
            Optional<String> title = firstNonBlank(mm, "documentTitle", "sourceTitle", "title");
            if (title.isPresent()) {
                return title.get();
            }
        }
        return firstNonBlank(topSource, "documentTitle", "sourceTitle", "title").orElse(null);
    }

    private static String resolveTopOriginalFileName(Map<String, Object> topSource) {
        if (topSource == null) {
            return null;
        }
        return firstNonBlank(topSource, "filename", "fileName", "originalFileName").orElse(null);
    }

    private static String resolveAnswerScope(Map<String, Object> telemetry, List<Map<String, Object>> responseSources) {
        if (Boolean.TRUE.equals(telemetry != null ? telemetry.get("documentBound") : null)) {
            return SCOPE_DOCUMENT_BOUND;
        }
        int count = responseSources != null ? responseSources.size() : 0;
        if (count <= 1) {
            return SCOPE_SINGLE_SOURCE;
        }
        return SCOPE_MULTI_SOURCE;
    }

    private static Optional<String> firstNonBlank(Map<String, Object> row, String... keys) {
        if (row == null) {
            return Optional.empty();
        }
        for (String key : keys) {
            Object v = row.get(key);
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (!s.isBlank()) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> readString(Map<String, Object> meta, String key) {
        if (meta == null || meta.isEmpty()) {
            return Optional.empty();
        }
        Object v = meta.get(key);
        if (v == null) {
            return Optional.empty();
        }
        String s = String.valueOf(v).trim();
        return s.isBlank() ? Optional.empty() : Optional.of(s);
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
