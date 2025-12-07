package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;
import static com.uniovi.rag.utils.InfoExtractor.extractRelevantFragment;

/**
 * Enhanced FilterAndListTool for filtering and listing meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced summarization and listing
 */
public class FilterAndListTool extends AbstractTool {

    public FilterAndListTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing filter and list query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> results = new ArrayList<>();

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    String summary = extractAndSummarize(content, query);
                    results.add("Meeting minutes from " + date + ":\n" + summary);
                }
            }
        } else {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                String content = doc.getContent();
                String date = extractDate(content);
                if (isRelevantByLLM(content, query)) {
                    String summary = extractAndSummarize(content, query);
                    results.add("Meeting minutes from " + date + ":\n" + summary);
                }
            }
        }

        String answer;
        if (!results.isEmpty()) {
            answer = generateFinalAnswer(query, results);
        } else {
            answer = generateNotFoundResponse(query);
        }
        return ToolResult.from(answer, getClass());
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

    /**
     * Extracts and summarizes relevant content.
     * Uses English for internal processing, but preserves original language in query and content.
     */
    private String extractAndSummarize(String content, String query) {
        if (content == null || content.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return "";
        }
        
        String fragment = extractRelevantFragment(content, query);
        if (fragment == null || fragment.trim().isEmpty()) {
            return "";
        }
        
        String prompt = String.format("""
            Summarize in at most two sentences the fragment of the following text that answers this query (in any language): "%s"
            
            Text (may be in any language):
            %s
            
            Write the summary in the same language as the query.
            """, query, fragment);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in extractAndSummarize, returning empty string");
                return "";
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error in extractAndSummarize, returning empty string", e);
            return "";
        }
    }

    /**
     * Generates final answer with found results.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateFinalAnswer(String query, List<String> results) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String joined = results.stream()
                .filter(r -> r != null && !r.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Relevant meeting minutes found:
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "matched the filters", "análisis", "analysis", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, joined);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateFinalAnswer, using fallback");
                return generateFallbackAnswer(query, results);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating final answer, using fallback", e);
            return generateFallbackAnswer(query, results);
        }
    }
    
    /**
     * Generates a fallback answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackAnswer(String query, List<String> results) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "Reuniones encontradas que coinciden con los filtros:\n\n" + 
                   results.stream().limit(5).collect(Collectors.joining("\n\n"));
        } else {
            return "Meetings found matching the filters:\n\n" + 
                   results.stream().limit(5).collect(Collectors.joining("\n\n"));
        }
    }
    
    /**
     * Generates not found response.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundResponse(String query) {
        if (query == null || query.trim().isEmpty()) {
            return generateFallbackNotFoundMessage("");
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No meeting minutes were found that match all the conditions specified in the query.
            
            Write a polite response in the same language as the query explaining that no matching minutes were found.
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
            return "No se encontraron actas de reunión que coincidan con todas las condiciones especificadas en la consulta.";
        } else {
            return "No meeting minutes were found that match all the conditions specified in the query.";
        }
    }
}
