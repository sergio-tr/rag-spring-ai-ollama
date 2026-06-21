package com.uniovi.rag.tool;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Enhanced GetFieldTool for extracting specific fields from meeting minutes with intelligent NER analysis.
 */
public class GetFieldTool extends AbstractTool {

    private static final String VALUE_UNKNOWN = "unknown";

    private static final String QUERY_TOKEN_PRESIDENT = "president";

    private static final String QUERY_TOKEN_SECRETARY = "secretary";

    private static final String FIELD_INTENT_PRESIDENT = "president";

    private static final String FIELD_INTENT_SECRETARY = "secretary";

    private static final String FIELD_ATTENDEES_LIST = "attendees_list";

    private static final String LOG_FOUND_FIELD =
            "Found field value for query: '{}' in document {} (execution time: {} ms)";

    public GetFieldTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        log().info("Executing get field query: '{}' with NER: {}",
                query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();

        String fieldIntent = resolveFieldIntent(query, ner);
        if (isStructuredAuthoritativeField(fieldIntent)) {
            ToolResult structured = tryStructuredFieldExtraction(query, ner, fieldIntent, startTime);
            if (structured != null) {
                return structured;
            }
        }

        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for get field query", docs.size());

        ToolResult fromNer = tryExtractFieldWithNer(query, ner, docs, startTime, fieldIntent);
        if (fromNer != null) {
            return fromNer;
        }

        ToolResult fromDocs = tryExtractFieldWithLlmOnDocs(query, docs, startTime, fieldIntent);
        if (fromDocs != null) {
            return fromDocs;
        }

        List<Document> allDocs = retrieveAllDocuments(query, ner);
        ToolResult fromAll = tryExtractFieldWithLlmOnDocs(query, allDocs, startTime, fieldIntent);
        if (fromAll != null) {
            return fromAll;
        }

        return buildFieldNotFoundResult(query, startTime, allDocs.isEmpty() ? docs.size() : allDocs.size());
    }

    private ToolResult tryStructuredFieldExtraction(
            String query, JSONObject ner, String fieldIntent, long startTime) {
        Map<String, DocumentBundle> bundles = loadDocumentBundles(query, ner);
        String requestedDate = ner != null ? ner.optString("date", "").trim() : "";

        if (FIELD_ATTENDEES_LIST.equals(fieldIntent) || FIELD_INTENT_PRESIDENT.equals(fieldIntent)
                || FIELD_INTENT_SECRETARY.equals(fieldIntent)) {
            Optional<DocumentBundle> bestMatching = selectBestStructuredBundle(bundles, requestedDate);
            if (bestMatching.isPresent()) {
                DocumentBundle bundle = bestMatching.get();
                String value = extractStructuredFieldValue(fieldIntent, bundle.combinedText());
                if (value != null && !value.isBlank()) {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log().info(LOG_FOUND_FIELD, query, bundle.displayName(), totalTime);
                    return ToolResult.from(
                            formatStructuredAnswer(query, fieldIntent, bundle, value), getClass());
                }
            }
        }

        DocumentBundle best = null;
        String bestValue = null;
        for (DocumentBundle bundle : bundles.values()) {
            if (!requestedDate.isBlank() && !textContainsRequestedDate(bundle.combinedText(), requestedDate)) {
                continue;
            }
            String value = extractStructuredFieldValue(fieldIntent, bundle.combinedText());
            if (value == null || value.isBlank()) {
                continue;
            }
            if (best == null || value.length() > Objects.requireNonNullElse(bestValue, "").length()) {
                best = bundle;
                bestValue = value;
            }
        }
        if (best == null || bestValue == null || bestValue.isBlank()) {
            return null;
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log().info(LOG_FOUND_FIELD, query, best.documentId(), totalTime);
        return ToolResult.from(formatStructuredAnswer(query, fieldIntent, best, bestValue), getClass());
    }

    private ToolResult tryExtractFieldWithNer(
            String query, JSONObject ner, List<Document> docs, long startTime, String fieldIntent) {
        if (ner == null || docs.isEmpty()) {
            return null;
        }
        List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
        log().debug("Filtered {} documents by temporal context, {} remaining", docs.size(), filteredDocs.size());

        int matchedCount = 0;
        for (Document doc : filteredDocs) {
            if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                log().debug("Skipping document {}: null or empty content", doc != null ? doc.getId() : "null");
            } else if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                matchedCount++;
                String value = extractFieldValue(fieldIntent, query, doc.getText());
                if (value != null && !value.isBlank()) {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log().info(LOG_FOUND_FIELD, query, doc.getId(), totalTime);
                    return ToolResult.from(formatResponse(value, query), getClass());
                }
                log().debug("Document {} matched NER but no field value extracted", doc.getId());
            }
        }
        log().debug("NER filtering: {} documents matched NER conditions out of {} filtered", matchedCount, filteredDocs.size());
        return null;
    }

    private ToolResult tryExtractFieldWithLlmOnDocs(
            String query, List<Document> docs, long startTime, String fieldIntent) {
        if (docs.isEmpty() || isStructuredAuthoritativeField(fieldIntent)) {
            return null;
        }
        for (Document doc : docs) {
            if (doc != null && doc.getText() != null && !doc.getText().trim().isEmpty()
                    && isRelevantByLLM(doc.getText(), query)) {
                String value = extractFieldValue(fieldIntent, query, doc.getText());
                if (value != null && !value.isBlank()) {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log().info(LOG_FOUND_FIELD, query, doc.getId(), totalTime);
                    return ToolResult.from(formatResponse(value, query), getClass());
                }
            }
        }
        return null;
    }

    private ToolResult buildFieldNotFoundResult(String query, long startTime, int documentsChecked) {
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("No field value found for query: '{}' (execution time: {} ms, documents checked: {})",
                query, totalTime, documentsChecked);
        return buildFormattedNotFoundToolResult(query);
    }

    private static boolean isStructuredAuthoritativeField(String fieldIntent) {
        if (fieldIntent == null || fieldIntent.isBlank()) {
            return false;
        }
        return FIELD_ATTENDEES_LIST.equals(fieldIntent)
                || FIELD_INTENT_PRESIDENT.equals(fieldIntent)
                || FIELD_INTENT_SECRETARY.equals(fieldIntent)
                || "date".equals(fieldIntent)
                || "startTime".equals(fieldIntent)
                || "endTime".equals(fieldIntent);
    }

    private String resolveFieldIntent(String query, JSONObject ner) {
        if (ner != null && ner.has("field")) {
            String nerField = ner.optString("field", "").trim().toLowerCase(Locale.ROOT);
            if ("attendees".equals(nerField) || "asistentes".equals(nerField) || "participantes".equals(nerField)) {
                return FIELD_ATTENDEES_LIST;
            }
            if ("president".equals(nerField) || "presidente".equals(nerField)) {
                return FIELD_INTENT_PRESIDENT;
            }
            if ("secretary".equals(nerField) || "secretario".equals(nerField) || "secretaria".equals(nerField)) {
                return FIELD_INTENT_SECRETARY;
            }
            if ("date".equals(nerField) || "fecha".equals(nerField)) {
                return "date";
            }
        }
        if (query == null) {
            return VALUE_UNKNOWN;
        }
        String q = query.toLowerCase(Locale.ROOT);
        if (q.contains("participante") || q.contains("asistente") || q.contains("attendee")) {
            return FIELD_ATTENDEES_LIST;
        }
        if (q.contains("presidente") || q.contains("presidió") || q.contains("presidio") || q.contains("presided")) {
            return FIELD_INTENT_PRESIDENT;
        }
        if (q.contains("secretari")) {
            return FIELD_INTENT_SECRETARY;
        }
        if (asksForDateOfActaWherePerson(query)) {
            return "date";
        }
        return VALUE_UNKNOWN;
    }

    private String extractFieldValue(String fieldIntent, String query, String content) {
        String detected = fieldIntent;
        if (VALUE_UNKNOWN.equals(detected)) {
            detected = asksForDateOfActaWherePerson(query) ? "date" : classifyLiteralIntentWithLLM(query);
        }
        return extractStructuredFieldValue(detected, content);
    }

    private String extractStructuredFieldValue(String detectedField, String content) {
        switch (detectedField) {
            case "date", "fecha":
                return extractor.extractDate(content);
            case "startTime", "hora_inicio":
                return extractor.extractTime(content, "start");
            case "endTime", "hora_fin":
                return extractor.extractTime(content, "end");
            case "place", "lugar":
                return extractor.extractLiteralField("place", content);
            case FIELD_INTENT_PRESIDENT, "presidente":
                return extractor.extractLiteralField(FIELD_INTENT_PRESIDENT, content);
            case FIELD_INTENT_SECRETARY, "secretario":
                return extractor.extractLiteralField(FIELD_INTENT_SECRETARY, content);
            case FIELD_ATTENDEES_LIST, "asistentes_lista":
                List<String> attendees = extractor.extractAttendees(content);
                return attendees.isEmpty() ? null : String.join(", ", attendees);
            case "attendees_number", "asistentes_numero":
                return String.valueOf(extractor.extractAttendeeCount(content));
            case "agenda", "orden_dia":
                return extractor.extractAgenda(content);
            default:
                return null;
        }
    }

    private String formatStructuredAnswer(String query, String fieldIntent, DocumentBundle bundle, String value) {
        if (FIELD_ATTENDEES_LIST.equals(fieldIntent)) {
            List<String> names = new ArrayList<>();
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    names.add(trimmed);
                }
            }
            if (names.isEmpty()) {
                return generateNotFoundMessage(query);
            }
            String date = extractor.extractDate(bundle.combinedText());
            if (date == null || date.isBlank() || "Unknown date".equalsIgnoreCase(date)) {
                date = "fecha desconocida";
            }
            String source = bundle.displayName();
            StringBuilder answer = new StringBuilder();
            answer.append("En el acta del ").append(date);
            if (!source.isBlank()) {
                answer.append(" (").append(source).append(")");
            }
            answer.append(", los participantes fueron: ");
            answer.append(String.join(", ", names));
            answer.append(" (").append(names.size()).append(" en total).");
            return formatResponse(answer.toString(), query);
        }
        if (FIELD_INTENT_PRESIDENT.equals(fieldIntent)) {
            return formatResponse(value + " presidió el acta.", query);
        }
        if (FIELD_INTENT_SECRETARY.equals(fieldIntent)) {
            return formatResponse(value + " fue la secretaria del acta.", query);
        }
        return formatResponse(value, query);
    }

    private Map<String, DocumentBundle> documentBundles(List<Document> docs) {
        Map<String, List<Document>> grouped = new LinkedHashMap<>();
        for (Document doc : docs) {
            if (doc == null) {
                continue;
            }
            String key = bundleKey(doc);
            if (key == null || key.isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(doc);
        }
        Map<String, DocumentBundle> bundles = new LinkedHashMap<>();
        for (Map.Entry<String, List<Document>> entry : grouped.entrySet()) {
            List<Document> chunks =
                    entry.getValue().stream()
                            .sorted(Comparator.comparingInt(GetFieldTool::chunkOrder))
                            .toList();
            StringBuilder text = new StringBuilder();
            String label = "";
            for (Document chunk : chunks) {
                if (label.isBlank()) {
                    label = displayName(chunk);
                }
                String chunkText = chunk.getText();
                if (chunkText == null || chunkText.isBlank()) {
                    continue;
                }
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(chunkText.trim());
            }
            String combined = text.toString().trim();
            if (!combined.isBlank()) {
                bundles.put(entry.getKey(), new DocumentBundle(entry.getKey(), label, combined));
            }
        }
        return bundles;
    }

    private static String bundleKey(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata != null) {
            for (String key : List.of("projectDocumentId", "sourceDocumentId", "documentId", "document_id")) {
                Object value = metadata.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value).trim();
                }
            }
        }
        return documentId(doc);
    }

    private static int chunkOrder(Document doc) {
        if (doc == null || doc.getMetadata() == null) {
            return 0;
        }
        Object chunkIndex = doc.getMetadata().get("chunk_index");
        if (chunkIndex == null) {
            chunkIndex = doc.getMetadata().get("chunkIndex");
        }
        if (chunkIndex instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static String documentId(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata != null) {
            Object documentId = metadata.get("document_id");
            if (documentId != null && !String.valueOf(documentId).isBlank()) {
                return String.valueOf(documentId).trim();
            }
        }
        return doc.getId();
    }

    private static String displayName(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata != null) {
            for (String key : List.of("fileName", "filename", "source", "title")) {
                Object v = metadata.get(key);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return String.valueOf(v).trim();
                }
            }
        }
        return documentId(doc);
    }

    private Map<String, DocumentBundle> loadDocumentBundles(String query, JSONObject ner) {
        String requestedDate = ner != null ? ner.optString("date", "").trim() : "";
        Map<String, DocumentBundle> merged = new LinkedHashMap<>();
        List<List<Document>> batches = new ArrayList<>();
        batches.add(retrieveAllDocuments(query, ner));
        batches.add(retrieveAllDocuments(query, null));
        if (!requestedDate.isBlank()) {
            batches.add(retrieveAllDocuments(requestedDate, null));
        }
        for (List<Document> batch : batches) {
            for (Map.Entry<String, DocumentBundle> entry : documentBundles(batch).entrySet()) {
                DocumentBundle existing = merged.get(entry.getKey());
                DocumentBundle candidate = entry.getValue();
                if (existing == null
                        || candidate.combinedText().length() > existing.combinedText().length()) {
                    merged.put(entry.getKey(), candidate);
                }
            }
        }
        return merged;
    }

    private Optional<DocumentBundle> selectBestStructuredBundle(
            Map<String, DocumentBundle> bundles, String requestedDate) {
        return bundles.values().stream()
                .filter(
                        bundle ->
                                requestedDate.isBlank()
                                        || textContainsRequestedDate(bundle.combinedText(), requestedDate)
                                        || requestedDate.equalsIgnoreCase(
                                                extractor.extractDate(bundle.combinedText())))
                .max(Comparator.comparingInt(bundle -> bundle.combinedText().length()));
    }

    private static boolean textContainsRequestedDate(String text, String requestedDate) {
        if (text == null || requestedDate == null || requestedDate.isBlank()) {
            return true;
        }
        String foldedText = text.toLowerCase(Locale.ROOT);
        String foldedDate = requestedDate.toLowerCase(Locale.ROOT);
        return foldedText.contains(foldedDate);
    }

    private static boolean asksForDateOfActaWherePerson(String query) {
        if (query == null) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return (q.contains("fecha del acta") || q.contains("date of the acta") || (q.contains("fecha") && q.contains("donde")))
                && (q.contains("presidente") || q.contains(QUERY_TOKEN_PRESIDENT) || q.contains("secretaria")
                        || q.contains(QUERY_TOKEN_SECRETARY));
    }

    private String classifyLiteralIntentWithLLM(String query) {
        String prompt = String.format("""
            Given the following user question (in any language):
            "%s"
            
            Determine which literal field the user wants to query. Choose one of the following (respond with the field name in English):
            - date
            - place
            - startTime
            - endTime
            - %s
            - %s
            - attendees_list
            - attendees_number
            - agenda
            
            If you cannot determine, answer exactly: unknown
            
            Respond with ONLY the field name in English (one word).
            Do not include any explanation or additional text.
            """, query, FIELD_INTENT_PRESIDENT, FIELD_INTENT_SECRETARY);

        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in classifyLiteralIntentWithLLM, defaulting to unknown");
                return VALUE_UNKNOWN;
            }

            String normalized = result.strip().toLowerCase(Locale.ROOT);
            if (normalized.contains(VALUE_UNKNOWN) || normalized.contains("desconocido")) {
                return VALUE_UNKNOWN;
            }

            return normalized.split("\\s+")[0].trim();
        } catch (Exception e) {
            log().error("Error in classifyLiteralIntentWithLLM, defaulting to unknown", e);
            return VALUE_UNKNOWN;
        }
    }

    private static final class DocumentBundle {
        private final String documentId;
        private final String displayName;
        private final StringBuilder text;

        private DocumentBundle(String documentId, String displayName, String initialText) {
            this.documentId = documentId;
            this.displayName = displayName != null ? displayName : documentId;
            this.text = new StringBuilder(initialText);
        }

        private String combinedText() {
            return text.toString().trim();
        }

        private String documentId() {
            return documentId;
        }

        private String displayName() {
            return displayName;
        }
    }
}
