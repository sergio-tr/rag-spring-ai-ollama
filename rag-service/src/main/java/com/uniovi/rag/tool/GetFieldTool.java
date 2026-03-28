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

        // Try with NER filtering if available
        if (ner != null && !docs.isEmpty()) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            log().debug("Filtered {} documents by temporal context, {} remaining", docs.size(), filteredDocs.size());
            
            int matchedCount = 0;
            for (Document doc : filteredDocs) {
                if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                    log().debug("Skipping document {}: null or empty content", doc != null ? doc.getId() : "null");
                    continue;
                }
                
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    matchedCount++;
                    String value = extractLiteralFieldByIntent(query, ner, doc.getText());
                    if (value != null && !value.isBlank()) {
                        long totalTime = System.currentTimeMillis() - startTime;
                        log().info(LOG_FOUND_FIELD,
                                 query, doc.getId(), totalTime);
                        // Apply formatResponse to clean the extracted value
                        String formattedValue = formatResponse(value, query);
                        return ToolResult.from(formattedValue, getClass());
                    } else {
                        log().debug("Document {} matched NER but no field value extracted", doc.getId());
                    }
                }
            }
            log().debug("NER filtering: {} documents matched NER conditions out of {} filtered", matchedCount, filteredDocs.size());
        }
        
        if (!docs.isEmpty()) {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                    continue;
                }
                
                if (isRelevantByLLM(doc.getText(), query)) {
                    String value = extractLiteralFieldByIntent(query, null, doc.getText());
                    if (value != null && !value.isBlank()) {
                        long totalTime = System.currentTimeMillis() - startTime;
                        log().info(LOG_FOUND_FIELD,
                                 query, doc.getId(), totalTime);
                        // Apply formatResponse to clean the extracted value
                        String formattedValue = formatResponse(value, query);
                        return ToolResult.from(formattedValue, getClass());
                    }
                }
            }
        }
        
        docs = retrieveAllDocuments(query, ner);
        if (!docs.isEmpty()) {
            for (Document doc : docs) {
                if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                    continue;
                }
                
                if (isRelevantByLLM(doc.getText(), query)) {
                    String value = extractLiteralFieldByIntent(query, null, doc.getText());
                    if (value != null && !value.isBlank()) {
                        long totalTime = System.currentTimeMillis() - startTime;
                        log().info(LOG_FOUND_FIELD,
                                 query, doc.getId(), totalTime);
                        // Apply formatResponse to clean the extracted value
                        String formattedValue = formatResponse(value, query);
                        return ToolResult.from(formattedValue, getClass());
                    }
                }
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("No field value found for query: '{}' (execution time: {} ms, documents checked: {})", 
                  query, totalTime, docs.size());
        String notFound = generateNotFoundMessage(query);
        // Apply formatResponse to clean the not found message
        String formattedNotFound = formatResponse(notFound, query);
        return ToolResult.from(formattedNotFound, getClass());
    }

    /**
     * Determines if content is relevant to query using LLM.
     * Uses English for internal processing, but preserves original language in query and content.
     */
    private boolean isRelevantByLLM(String content, String query) {
        if (content == null || content.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String contentSnippet = content.substring(0, Math.min(1000, content.length()));
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And the following meeting minutes content (may be in any language):
            "%s"
            
            Does this minutes document match all the conditions in the query?
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, contentSnippet);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in isRelevantByLLM, defaulting to false");
                return false;
            }
            
            // Use LLM to interpret boolean response
            return interpretBooleanResponse(result, "isRelevantByLLM");
        } catch (Exception e) {
            log().error("Error in isRelevantByLLM, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /** Intent→field mapping: "date of the minute where X was president" must return date, not president. */
    private static boolean asksForDateOfActaWherePerson(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        return (q.contains("fecha del acta") || q.contains("date of the acta") || (q.contains("fecha") && q.contains("donde")))
                && (q.contains("presidente") || q.contains("president") || q.contains("secretaria") || q.contains("secretary"));
    }

    private String extractLiteralFieldByIntent(String query, JSONObject ner, String content) {
        // When user asks for "date of the acta where X was president", extract date only (not president name)
        String detectedField = asksForDateOfActaWherePerson(query) ? "date" : classifyLiteralIntentWithLLM(query);
        switch (detectedField) {
            case "date":
            case "fecha":
                return extractor.extractDate(content);
            case "startTime":
            case "hora_inicio":
                return extractor.extractTime(content, "start");
            case "endTime":
            case "hora_fin":
                return extractor.extractTime(content, "end");
            case "place":
            case "lugar":
                return extractor.extractLiteralField("place", content);
            case "president", "presidente":
                return extractor.extractLiteralField("president", content);
            case "secretary":
            case "secretario":
                return extractor.extractLiteralField("secretary", content);
            case "attendees_list":
            case "asistentes_lista":
                return String.join(", ", extractor.extractAttendees(content));
            case "attendees_number":
            case "asistentes_numero":
                return String.valueOf(extractor.extractAttendeeCount(content));
            case "agenda":
            case "orden_dia":
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

    /**
     * Interprets LLM response as boolean using another LLM call.
     */
    private boolean interpretBooleanResponse(String response, String context) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Context: %s
            
            The LLM generated this response: "%s"
            
            Task: Interpret this response as a boolean answer.
            - If it means YES/TRUE/POSITIVE, respond with: YES
            - If it means NO/FALSE/NEGATIVE, respond with: NO
            
            Consider semantic meaning, not just exact words.
            
            Respond with ONLY one word: YES or NO.
            """, context, response);
        
        try {
            String interpretation = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toUpperCase();
            
            return interpretation.contains("YES");
        } catch (Exception e) {
            log().warn("Error interpreting boolean response in {}, defaulting to false", context, e);
            return false;
        }
    }
}
