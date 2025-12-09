package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.*;

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

    public GetFieldTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing get field query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);

        // Try with NER filtering if available
        if (ner != null && !docs.isEmpty()) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String value = extractLiteralFieldByIntent(query, ner, doc.getContent());
                    if (value != null && !value.isBlank()) {
                        return ToolResult.from(value, getClass());
                    }
                }
            }
        }
        
        if (!docs.isEmpty()) {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (isRelevantByLLM(doc.getContent(), query)) {
                    String value = extractLiteralFieldByIntent(query, null, doc.getContent());
                    if (value != null && !value.isBlank()) {
                        return ToolResult.from(value, getClass());
                    }
                }
            }
        }
        
        docs = retrieveAllDocuments(query);
        if (!docs.isEmpty()) {
            for (Document doc : docs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (isRelevantByLLM(doc.getContent(), query)) {
                    String value = extractLiteralFieldByIntent(query, null, doc.getContent());
                    if (value != null && !value.isBlank()) {
                        return ToolResult.from(value, getClass());
                    }
                }
            }
        }
        
        String notFound = generateNotFoundMessage(query);
        return ToolResult.from(notFound, getClass());
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
            
            String normalized = result.strip().toLowerCase();
            // Check for positive responses in multiple languages
            return normalized.startsWith("yes") || normalized.startsWith("sí") || normalized.startsWith("si") || 
                   normalized.startsWith("oui") || normalized.startsWith("ja") || normalized.startsWith("da");
        } catch (Exception e) {
            log().error("Error in isRelevantByLLM, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    private String extractLiteralFieldByIntent(String query, JSONObject ner, String content) {
        String detectedField = classifyLiteralIntentWithLLM(query);
        return switch (detectedField) {
            case "date", "fecha" -> extractDate(content);
            case "startTime", "hora_inicio" -> extractTime(content, "start");
            case "endTime", "hora_fin" -> extractTime(content, "end");
            case "place", "lugar" -> extractLiteralField("place", content);
            case "president", "presidente" -> extractLiteralField("president", content);
            case "secretary", "secretario" -> extractLiteralField("secretary", content);
            case "attendees_list", "asistentes_lista" -> String.join(", ", extractAttendees(content));
            case "attendees_number", "asistentes_numero" -> String.valueOf(extractAttendeeCount(content));
            case "agenda", "orden_dia" -> extractAgenda(content);
            default -> null;
        };
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
                return "unknown";
            }
            
            String normalized = result.strip().toLowerCase();
            if (normalized.contains("unknown") || normalized.contains("desconocido")) {
                return "unknown";
            }
            
            // Extract the first word
            String cleaned = normalized.split("\\s+")[0].trim();
            return cleaned;
        } catch (Exception e) {
            log().error("Error in classifyLiteralIntentWithLLM, defaulting to unknown", e);
            return "unknown";
        }
    }

    /**
     * Generates not found message.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundMessage(String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Write a short message indicating that no information was found related to the query, 
            in the same language as the query.
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                return generateFallbackNotFoundMessage(query);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating not found message, using fallback", e);
            return generateFallbackNotFoundMessage(query);
        }
    }
    
    /**
     * Generates a fallback "not found" message when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackNotFoundMessage(String query) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "No se encontró información relacionada con esta consulta en los documentos disponibles.";
        } else {
            return "No information related to this query was found in the available documents.";
        }
    }
}
