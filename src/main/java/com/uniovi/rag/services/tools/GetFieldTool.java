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
        
        log().debug("Executing get field query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String value = extractLiteralFieldByIntent(query, ner, doc.getContent());
                    if (value != null && !value.isBlank()) {
                        return ToolResult.from(value, getClass());
                    }
                }
            }
        } else {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
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
     * Determines if content is relevant to query using LLM
     */
    private boolean isRelevantByLLM(String content, String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And the following meeting minutes content:
            "%s"
            
            Does this minutes document match all the conditions in the query? 
            Answer only YES or NO in the same language as the query.
            """, query, content.substring(0, Math.min(1000, content.length())));
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        // Check for positive responses in multiple languages
        return result.startsWith("yes") || result.startsWith("sí") || result.startsWith("si") || 
               result.startsWith("oui") || result.startsWith("ja") || result.startsWith("da");
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

    private String classifyLiteralIntentWithLLM(String query) {
        String prompt = """
            Given the following user question (in any language):
            "%s"
            Determine which literal field the user wants to query. Choose one of the following (answer with the field name in the language of the question if possible):
            - date/fecha
            - place/lugar
            - startTime/hora_inicio
            - endTime/hora_fin
            - president/presidente
            - secretary/secretario
            - attendees_list/asistentes_lista
            - attendees_number/asistentes_numero
            - agenda/orden_dia
            If you cannot determine, answer exactly: unknown/desconocido
            """.formatted(query);
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        if (result.contains("unknown") || result.contains("desconocido")) return "unknown";
        return result;
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            Write a short message indicating that no information was found related to the query, in the same language as the query.
            """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
