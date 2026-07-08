package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.query.ActaDocumentAnchorSupport;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceHolder;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.domain.runtime.advisor.PackedContextBlock;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds response source maps when workflows or routes omit provenance attachment. */
public final class ResponseSourcesBackfill {

    private static final int MAX_SOURCES = 8;
    private static final int SNIPPET_MAX = 240;
    private ResponseSourcesBackfill() {}

    public static List<Map<String, Object>> resolve(RagExecutionResult result) {
        if (result == null) {
            return List.of();
        }
        List<Map<String, Object>> existing = result.responseSources();
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        if (result.retrievalUsed()) {
            List<Map<String, Object>> fromDiagnostics =
                    result.retrievalDiagnostics().map(ResponseSourcesBackfill::fromDiagnostics).orElse(List.of());
            if (!fromDiagnostics.isEmpty()) {
                return fromDiagnostics;
            }
        }
        if (result.metadataUsed() || result.usedTool()) {
            List<Map<String, Object>> fromTool =
                    fromToolExecution(Map.of(), result.answerText() != null ? result.answerText() : "");
            if (!fromTool.isEmpty()) {
                return fromTool;
            }
        }
        return List.of();
    }

    /** Merges source rows without duplicating by documentId, filename, or chunkId. */
    public static List<Map<String, Object>> merge(List<Map<String, Object>> primary, List<Map<String, Object>> secondary) {
        LinkedHashMap<String, Map<String, Object>> merged = new LinkedHashMap<>();
        appendUniqueSources(merged, primary);
        appendUniqueSources(merged, secondary);
        return List.copyOf(merged.values());
    }

    /** Resolves tool-route provenance from payload lists, matched minutes, or answer text. */
    public static List<Map<String, Object>> fromToolExecution(
            Map<String, Object> normalizedPayload, String answerText) {
        List<Map<String, Object>> fromPayload = fromPayloadSourceLists(normalizedPayload);
        if (!fromPayload.isEmpty()) {
            return fromPayload;
        }
        List<Map<String, Object>> fromEvidence = fromToolEvidenceHolder();
        if (!fromEvidence.isEmpty()) {
            return fromEvidence;
        }
        return fromActaFilenamesInText(answerText);
    }

    public static List<Map<String, Object>> fromPackedContext(PackedContextSet packed) {
        if (packed == null || packed.blocks().isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Map<String, Object>> byDocument = new LinkedHashMap<>();
        for (PackedContextBlock block : packed.blocks()) {
            if (block == null) {
                continue;
            }
            String documentId = block.documentId() != null ? block.documentId().trim() : "";
            String key = !documentId.isBlank() ? documentId : block.blockId();
            if (key == null || key.isBlank() || byDocument.containsKey(key)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            if (!documentId.isBlank()) {
                row.put("documentId", documentId);
            }
            String snippet = snippet(block.blockText());
            if (snippet != null) {
                row.put("snippet", snippet);
            }
            String filename = filenameFromBlock(block);
            if (filename == null) {
                filename = filenameFromBlockText(block.blockText());
            }
            if (filename != null) {
                row.put("filename", filename);
            }
            String detectedDate = dateFromBlockText(block.blockText());
            if (detectedDate != null) {
                row.put("detectedDate", detectedDate);
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("chunkId", block.blockId());
            metadata.put("supportingAnswer", true);
            String sectionType = sectionTypeFromBlockText(block.blockText());
            if (sectionType != null) {
                metadata.put("sectionType", sectionType);
            }
            if (detectedDate != null) {
                metadata.put("detectedDate", detectedDate);
            }
            row.put("metadata", Map.copyOf(metadata));
            byDocument.put(key, Map.copyOf(row));
            if (byDocument.size() >= MAX_SOURCES) {
                break;
            }
        }
        return List.copyOf(byDocument.values());
    }

    public static List<Map<String, Object>> fromCorpusDocuments(
            List<SnapshotCorpusAssembler.CorpusDocumentRef> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(MAX_SOURCES, documents.size());
        for (int i = 0; i < n; i++) {
            SnapshotCorpusAssembler.CorpusDocumentRef doc = documents.get(i);
            if (doc == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            if (doc.documentId() != null && !doc.documentId().isBlank()) {
                row.put("documentId", doc.documentId());
            }
            if (doc.filename() != null && !doc.filename().isBlank()) {
                row.put("filename", doc.filename());
            }
            row.put("metadata", Map.of("provenance", "full_corpus", "supportingAnswer", true));
            out.add(Map.copyOf(row));
        }
        return List.copyOf(out);
    }

    private static List<Map<String, Object>> fromDiagnostics(RetrievalDiagnostics diagnostics) {
        if (diagnostics == null) {
            return List.of();
        }
        List<String> candidateIds = diagnostics.afterRerankTopCandidateIds();
        if (candidateIds == null || candidateIds.isEmpty()) {
            candidateIds = diagnostics.beforeRerankTopCandidateIds();
        }
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(MAX_SOURCES, candidateIds.size());
        for (int i = 0; i < n; i++) {
            String chunkId = candidateIds.get(i);
            if (chunkId == null || chunkId.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = Map.of("chunkId", chunkId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunkId", chunkId);
            row.put("metadata", metadata);
            out.add(Map.copyOf(row));
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fromPayloadSourceLists(Map<String, Object> normalizedPayload) {
        if (normalizedPayload == null || normalizedPayload.isEmpty()) {
            return List.of();
        }
        Object sources = normalizedPayload.get("responseSources");
        if (sources == null) {
            sources = normalizedPayload.get("sources");
        }
        if (!(sources instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                out.add(copy);
            }
            if (out.size() >= MAX_SOURCES) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static List<Map<String, Object>> fromToolEvidenceHolder() {
        return DeterministicToolEvidenceHolder.get()
                .map(DeterministicToolEvidenceHolder.Evidence::matchedMinutes)
                .filter(minutes -> minutes != null && !minutes.isEmpty())
                .map(ResponseSourcesBackfill::fromMinutes)
                .orElse(List.of());
    }

    private static List<Map<String, Object>> fromMinutes(List<Minute> minutes) {
        LinkedHashMap<String, Map<String, Object>> seen = new LinkedHashMap<>();
        for (Minute minute : minutes) {
            if (minute == null) {
                continue;
            }
            String key =
                    firstNonBlank(minute.id(), minute.filename(), "minute-" + seen.size());
            if (seen.containsKey(key)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            if (minute.id() != null && !minute.id().isBlank()) {
                row.put("documentId", minute.id());
            }
            if (minute.filename() != null && !minute.filename().isBlank()) {
                row.put("filename", minute.filename());
            }
            if (minute.date() != null && !minute.date().isBlank()) {
                row.put("detectedDate", minute.date());
            }
            row.put("metadata", Map.of("provenance", "deterministic_tool", "supportingAnswer", true));
            seen.put(key, Map.copyOf(row));
            if (seen.size() >= MAX_SOURCES) {
                break;
            }
        }
        return List.copyOf(seen.values());
    }

    private static List<Map<String, Object>> fromActaFilenamesInText(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> filenames = new LinkedHashSet<>(ActaDocumentAnchorSupport.extractActaFilenamesFromText(answerText));
        if (filenames.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String filename : filenames) {
            if (out.size() >= MAX_SOURCES) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("filename", filename);
            row.put("metadata", Map.of("provenance", "answer_text", "supportingAnswer", true));
            out.add(Map.copyOf(row));
        }
        return List.copyOf(out);
    }

    private static void appendUniqueSources(
            LinkedHashMap<String, Map<String, Object>> merged, List<Map<String, Object>> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : sources) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String key = sourceDedupeKey(row);
            if (key.isBlank() || merged.containsKey(key)) {
                continue;
            }
            merged.put(key, Map.copyOf(row));
            if (merged.size() >= MAX_SOURCES) {
                return;
            }
        }
    }

    private static String sourceDedupeKey(Map<String, Object> row) {
        String documentId = firstNonBlank(str(row.get("documentId")), str(row.get("document_id")));
        if (!documentId.isBlank()) {
            return "doc:" + documentId;
        }
        String filename = str(row.get("filename"));
        if (filename != null && !filename.isBlank()) {
            return "file:" + filename.toLowerCase(Locale.ROOT);
        }
        String chunkId = str(row.get("chunkId"));
        if (chunkId == null || chunkId.isBlank()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = row.get("metadata") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
            if (metadata != null) {
                chunkId = str(metadata.get("chunkId"));
            }
        }
        if (chunkId != null && !chunkId.isBlank()) {
            return "chunk:" + chunkId;
        }
        return "";
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String filenameFromBlock(PackedContextBlock block) {
        String sourceId = block.sourceId() != null ? block.sourceId().trim() : "";
        if (sourceId.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return sourceId;
        }
        return null;
    }

    private static final Pattern ACTA_FILENAME_IN_TEXT =
            Pattern.compile("(?i)\\b(ACTA\\s+\\d+\\.pdf)\\b");

    private static String filenameFromBlockText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = ACTA_FILENAME_IN_TEXT.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static final Pattern ISO_DATE_IN_TEXT =
            Pattern.compile("\\b(20\\d{2}-\\d{2}-\\d{2})\\b");

    private static String dateFromBlockText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = ISO_DATE_IN_TEXT.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String sectionTypeFromBlockText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("asistent") || lower.contains("particip")) {
            return "attendees";
        }
        if (lower.contains("orden del día") || lower.contains("orden del dia") || lower.contains("agenda")) {
            return "agenda";
        }
        if (lower.contains("decision") || lower.contains("acuerdo")) {
            return "decisions";
        }
        return null;
    }

    private static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > SNIPPET_MAX ? trimmed.substring(0, SNIPPET_MAX) + "…" : trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
