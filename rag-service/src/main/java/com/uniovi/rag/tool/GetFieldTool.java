package com.uniovi.rag.tool;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;

import java.util.List;

/**
 * Enhanced GetFieldTool for extracting specific fields from meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic field relevance evaluation
 * - Enhanced field extraction and analysis
 */
public class GetFieldTool extends AbstractTool {

    private static final String VALUE_UNKNOWN = "unknown";

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

        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for get field query", docs.size());

        ToolResult fromNer = tryExtractFieldWithNer(query, ner, docs, startTime);
        if (fromNer != null) {
            return fromNer;
        }

        ToolResult fromDocs = tryExtractFieldWithLlmOnDocs(query, docs, startTime);
        if (fromDocs != null) {
            return fromDocs;
        }

        List<Document> allDocs = retrieveAllDocuments(query, ner);
        ToolResult fromAll = tryExtractFieldWithLlmOnDocs(query, allDocs, startTime);
        if (fromAll != null) {
            return fromAll;
        }

        return buildFieldNotFoundResult(query, startTime, allDocs.isEmpty() ? docs.size() : allDocs.size());
    }

    private ToolResult tryExtractFieldWithNer(String query, JSONObject ner, List<Document> docs, long startTime) {
        if (ner == null || docs.isEmpty()) {
            return null;
        }
        List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
        log().debug("Filtered {} documents by temporal context, {} remaining", docs.size(), filteredDocs.size());

        int matchedCount = 0;
        for (Document doc : filteredDocs) {
            if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                log().debug("Skipping document {}: null or empty content", doc != null ? doc.getId() : "null");
                continue;
            }
            if (!nerHandler.matchesDocumentWithNER(doc, ner)) {
                continue;
            }
            matchedCount++;
            String value = extractLiteralFieldByIntent(query, doc.getText());
            if (value != null && !value.isBlank()) {
                long totalTime = System.currentTimeMillis() - startTime;
                log().info(LOG_FOUND_FIELD, query, doc.getId(), totalTime);
                return ToolResult.from(formatResponse(value, query), getClass());
            }
            log().debug("Document {} matched NER but no field value extracted", doc.getId());
        }
        log().debug("NER filtering: {} documents matched NER conditions out of {} filtered", matchedCount, filteredDocs.size());
        return null;
    }

    private ToolResult tryExtractFieldWithLlmOnDocs(String query, List<Document> docs, long startTime) {
        if (docs.isEmpty()) {
            return null;
        }
        for (Document doc : docs) {
            if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                continue;
            }
            if (!isRelevantByLLM(doc.getText(), query)) {
                continue;
            }
            String value = extractLiteralFieldByIntent(query, doc.getText());
            if (value != null && !value.isBlank()) {
                long totalTime = System.currentTimeMillis() - startTime;
                log().info(LOG_FOUND_FIELD, query, doc.getId(), totalTime);
                return ToolResult.from(formatResponse(value, query), getClass());
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

    /** Intent→field mapping: "date of the minute where X was president" must return date, not president. */
    private static boolean asksForDateOfActaWherePerson(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        return (q.contains("fecha del acta") || q.contains("date of the acta") || (q.contains("fecha") && q.contains("donde")))
                && (q.contains("presidente") || q.contains("president") || q.contains("secretaria") || q.contains("secretary"));
    }

    private String extractLiteralFieldByIntent(String query, String content) {
        // When user asks for "date of the acta where X was president", extract date only (not president name)
        String detectedField = asksForDateOfActaWherePerson(query) ? "date" : classifyLiteralIntentWithLLM(query);
        switch (detectedField) {
            case "date", "fecha":
                return extractor.extractDate(content);
            case "startTime", "hora_inicio":
                return extractor.extractTime(content, "start");
            case "endTime", "hora_fin":
                return extractor.extractTime(content, "end");
            case "place", "lugar":
                return extractor.extractLiteralField("place", content);
            case "president", "presidente":
                return extractor.extractLiteralField("president", content);
            case "secretary", "secretario":
                return extractor.extractLiteralField("secretary", content);
            case "attendees_list", "asistentes_lista":
                return String.join(", ", extractor.extractAttendees(content));
            case "attendees_number", "asistentes_numero":
                return String.valueOf(extractor.extractAttendeeCount(content));
            case "agenda", "orden_dia":
                return extractor.extractAgenda(content);
            default:
                return null;
        }
    }

    /**
     * Classifies field intent using LLM.
     * Uses English for internal processing, but preserves original language in query.
     */
    private String classifyLiteralIntentWithLLM(String query) {
        String prompt = String.format("""
            Given the following user question (in any language):
            "%s"
            
            Determine which literal field the user wants to query. Choose one of the following (respond with the field name in English):
            - date
            - place
            - startTime
            - endTime
            - president
            - secretary
            - attendees_list
            - attendees_number
            - agenda
            
            If you cannot determine, answer exactly: unknown
            
            Respond with ONLY the field name in English (one word).
            Do not include any explanation or additional text.
            """, query);
        
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
            
            String normalized = result.strip().toLowerCase();
            if (normalized.contains(VALUE_UNKNOWN) || normalized.contains("desconocido")) {
                return VALUE_UNKNOWN;
            }
            
            // Extract the first word
            String cleaned = normalized.split("\\s+")[0].trim();
            return cleaned;
        } catch (Exception e) {
            log().error("Error in classifyLiteralIntentWithLLM, defaulting to unknown", e);
            return VALUE_UNKNOWN;
        }
    }

}
