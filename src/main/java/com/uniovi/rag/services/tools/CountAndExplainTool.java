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
 * Enhanced CountAndExplainTool for counting and explaining meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced explanation generation
 */
public class CountAndExplainTool extends AbstractTool {

    public CountAndExplainTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing count and explain query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> explanations = new ArrayList<>();
        int count = 0;

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    String fragment = extractRelevantFragment(content, query);
                    explanations.add("Meeting minutes from " + date + ":\n" + fragment);
                    count++;
                }
            }
        } else {
            // Fallback to query-based relevance
            for (Document doc : docs) {
                String content = doc.getContent();
                String date = extractDate(content);
                String fragment = extractRelevantFragment(content, query);
                if (isRelevantToQuery(fragment, query)) {
                    explanations.add("Meeting minutes from " + date + ":\n" + fragment);
                    count++;
                }
            }
        }

        String response;
        if (count > 0) {
            response = generateResponseWithLLM(query, count, explanations);
        } else {
            response = generateNotFoundResponse(query);
        }
        return ToolResult.from(response, getClass());
    }

    /**
     * Determines if a fragment is relevant to the query using LLM.
     * Uses English for internal processing, but preserves original language in query and fragment.
     */
    private boolean isRelevantToQuery(String fragment, String query) {
        if (fragment == null || fragment.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            This is the user's question (in any language):
            "%s"
            
            This is a fragment from meeting minutes (may be in any language):
            "%s"
            
            Does this fragment clearly or partially answer the question?
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, fragment);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in isRelevantToQuery, defaulting to false");
                return false;
            }
            
            String normalized = result.strip().toLowerCase();
            // Check for positive responses in multiple languages
            return normalized.contains("yes") || normalized.contains("sí") || normalized.contains("si") || 
                   normalized.contains("oui") || normalized.contains("ja") || normalized.contains("da");
        } catch (Exception e) {
            log().error("Error in isRelevantToQuery, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Generates response with LLM using found explanations.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateResponseWithLLM(String query, int count, List<String> explanations) {
        if (query == null || query.trim().isEmpty() || explanations == null || explanations.isEmpty() || count <= 0) {
            return generateNotFoundResponse(query);
        }
        
        String joined = explanations.stream()
                .filter(e -> e != null && !e.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant meeting minutes:
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "analysis", "análisis", "context found", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, count, joined);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateResponseWithLLM, using fallback");
                return generateFallbackResponse(query, count, explanations);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating response with LLM, using fallback", e);
            return generateFallbackResponse(query, count, explanations);
        }
    }
    
    /**
     * Generates a fallback response when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackResponse(String query, int count, List<String> explanations) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Se encontraron %d acta(s) relevante(s).\n\nContexto encontrado:\n%s",
                               count,
                               explanations.stream().limit(3).collect(Collectors.joining("\n\n")));
        } else {
            return String.format("Found %d relevant minute(s).\n\nContext found:\n%s",
                               count,
                               explanations.stream().limit(3).collect(Collectors.joining("\n\n")));
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
            
            No relevant information was found for this query in the available meeting minutes.
            
            Write a polite response in the same language as the query explaining that no relevant information was found.
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
            return "No se encontró información relevante para esta consulta en las actas de reunión disponibles.";
        } else {
            return "No relevant information was found for this query in the available meeting minutes.";
        }
    }
}
