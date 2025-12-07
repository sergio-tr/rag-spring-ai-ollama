package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;

/**
 * Enhanced CountDocumentsTool for counting meeting minutes based on specific criteria.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Semantic analysis instead of literal matching
 * - Support for all NER fields including temporalContext and answerType
 * - Multilingual support with adaptive prompts
 * - Decoupled from literal word matching
 */
public class CountDocumentsTool extends AbstractTool {

    public CountDocumentsTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
                
        List<Document> docs = retrieveDocuments(query);
        List<String> dates = new ArrayList<>();
        long count = 0;

        // Try with NER filtering if available
        if (ner != null && !docs.isEmpty()) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            count = filteredDocs.stream()
                    .filter(doc -> {                        
                        if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                            return false;
                        }
                        return nerHandler.matchesDocumentWithNER(doc, ner);
                    })
                    .peek(doc -> {
                        String date = extractDate(doc.getContent());
                        if (date != null && !date.trim().isEmpty()) {
                            dates.add(date);
                        }
                    })
                    .count();
        }
                
        if (count == 0 && !docs.isEmpty()) {
            // Try without NER filtering
            count = docs.stream()
                    .filter(doc -> {                        
                        if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                            return false;
                        }
                        return isRelevantToQuery(doc, query);
                    })
                    .peek(doc -> {
                        String date = extractDate(doc.getContent());
                        if (date != null && !date.trim().isEmpty()) {
                            dates.add(date);
                        }
                    })
                    .count();
        }
                
        if (count == 0) {
            docs = retrieveAllDocuments(query);
            if (!docs.isEmpty()) {
                count = docs.stream()
                        .filter(doc -> {                            
                            if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                                return false;
                            }
                            return isRelevantToQuery(doc, query);
                        })
                        .peek(doc -> {
                            String date = extractDate(doc.getContent());
                            if (date != null && !date.trim().isEmpty()) {
                                dates.add(date);
                            }
                        })
                        .count();
            }
        }

        String response = generateResponseWithLLM(query, count, dates);
        return ToolResult.from(response, getClass());
    }

    /**
     * Checks if document is relevant to query using intelligent analysis.
     * Uses English for internal processing, but preserves original language in query and content.
     */
    private boolean isRelevantToQuery(Document doc, String query) {        
        if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
            return false;
        }
        
        String answerType = nerHandler.determineAnswerType(query, null);
        String content = doc.getContent().substring(0, Math.min(1000, doc.getContent().length()));
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            This is the content of a meeting minute (may be in any language):
            "%s"
            
            Does this meeting minute clearly or partially answer the query?
            Consider semantic meaning, not just exact matches.
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, 
            query, 
            answerType,
            content);
        
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
            return normalized.contains("yes") || normalized.contains("sí");
        } catch (Exception e) {
            log().error("Error in isRelevantToQuery, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Generates response using LLM.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateResponseWithLLM(String query, long count, List<String> dates) {
        String datesStr = dates.stream()
                .filter(date -> date != null && !date.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            %d relevant documents were found.
            The dates of the relevant documents are: %s
            
            Write a clear, concise response in the same language as the query, 
            using the number and dates.
            """, query, count, datesStr.isBlank() ? "[no dates]" : datesStr);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
                        
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateResponseWithLLM, using fallback");
                return generateFallbackResponse(query, count, datesStr);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating response with LLM, using fallback", e);
            return generateFallbackResponse(query, count, datesStr);
        }
    }
    
    /**
     * Generates a fallback response when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackResponse(String query, long count, String datesStr) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*") || 
                           queryLower.contains("cuántos") || queryLower.contains("cuántas");
        
        if (isSpanish) {
            if (count == 0) {
                return "No se encontraron documentos relevantes para esta consulta.";
            } else {
                return String.format("Se encontraron %d documento(s) relevante(s).", count);
            }
        } else {
            if (count == 0) {
                return "No relevant documents were found for this query.";
            } else {
                return String.format("Found %d relevant document(s).", count);
            }
        }
    }
}