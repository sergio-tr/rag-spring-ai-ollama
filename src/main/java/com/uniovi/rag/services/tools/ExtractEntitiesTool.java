package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.*;

/**
 * Enhanced ExtractEntitiesTool for extracting entities from meeting minutes.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Semantic analysis instead of literal matching
 * - Support for all NER fields including section and answerType
 * - Multilingual support with adaptive prompts
 * - Decoupled from literal word matching
 */
public class ExtractEntitiesTool extends AbstractTool {

    public ExtractEntitiesTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> results = new ArrayList<>();

        if (ner != null) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    String entities = extractRequestedEntities(content, query, ner);
                    if (!entities.isBlank()) {
                        results.add(generateEntityResult(date, entities));
                    }
                }
            }
        } else {
            // Baseline: extract entities from all documents
            for (Document doc : docs) {
                String content = doc.getContent();
                String date = extractDate(content);
                String entities = extractRequestedEntities(content, query, null);
                if (!entities.isBlank()) {
                    results.add(generateEntityResult(date, entities));
                }
            }
        }

        String response;
        if (!results.isEmpty()) {
            response = generateResponseWithLLM(query, results);
        } else {
            response = generateNotFoundResponse(query);
        }
        return ToolResult.from(response, getClass());
    }

    /**
     * Extracts requested entities using intelligent analysis.
     * Uses English for internal processing, but preserves original language in query and content.
     */
    private String extractRequestedEntities(String content, String query, JSONObject ner) {
        if (content == null || content.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return "";
        }
        
        String answerType = nerHandler.determineAnswerType(query, ner);
        List<String> sections = nerHandler.extractSections(ner);
        
        String attendees = String.join(", ", extractAttendees(content));
        String agenda = extractAgenda(content);
        String fragment = extractRelevantFragment(content, query);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            Target sections: %s
            
            This is the content of a meeting minute (may be in any language):
            "%s"
            
            Extracted attendees: %s
            Extracted agenda: %s
            Relevant fragment: %s
            
            Extract and list only the entities (people, attendees, positions, topics, etc.) 
            that are relevant to the query. Consider the query type and target sections.
            If no relevant entities are found, respond exactly: [EMPTY]
            """, 
            query, 
            answerType,
            sections.isEmpty() ? "all sections" : String.join(", ", sections),
            content.substring(0, Math.min(1000, content.length())), 
            attendees, 
            agenda != null ? agenda : "", 
            fragment != null ? fragment : "");
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in extractRequestedEntities, returning empty string");
                return "";
            }
            
            String trimmed = result.strip();
            return trimmed.equalsIgnoreCase("[empty]") ? "" : trimmed;
        } catch (Exception e) {
            log().error("Error extracting requested entities, returning empty string", e);
            return "";
        }
    }

    /**
     * Generates entity result with proper formatting
     */
    private String generateEntityResult(String date, String entities) {
        return String.format("Minutes from %s:\n%s", 
                           date != null ? date : "unknown date", entities);
    }

    /**
     * Generates response using LLM.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateResponseWithLLM(String query, List<String> results) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String joinedResults = results.stream()
                .filter(r -> r != null && !r.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        
        if (joinedResults.trim().isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            The following relevant entities were found in the meeting minutes:
            %s
            
            Write a clear, concise response in the same language as the query, 
            summarizing the entities found and their context.
            """, query, joinedResults);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateResponseWithLLM, using fallback");
                return generateFallbackResponse(query, results);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating response with LLM, using fallback", e);
            return generateFallbackResponse(query, results);
        }
    }
    
    /**
     * Generates a fallback response when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackResponse(String query, List<String> results) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "Entidades relevantes encontradas:\n\n" + 
                   results.stream().limit(5).collect(Collectors.joining("\n\n"));
        } else {
            return "Relevant entities found:\n\n" + 
                   results.stream().limit(5).collect(Collectors.joining("\n\n"));
        }
    }

    /**
     * Generates not found response using LLM.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundResponse(String query) {
        if (query == null || query.trim().isEmpty()) {
            return generateFallbackNotFoundMessage("");
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Write a short message indicating that no relevant entities were found for the query, 
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
            log().error("Error generating not found response, using fallback", e);
            return generateFallbackNotFoundMessage(query);
        }
    }
    
    /**
     * Generates a fallback "not found" message when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackNotFoundMessage(String query) {
        String queryLower = query != null ? query.toLowerCase() : "";
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "No se encontraron entidades relevantes en los documentos disponibles para esta consulta.";
        } else {
            return "No relevant entities were found in the available documents for this query.";
        }
    }
}