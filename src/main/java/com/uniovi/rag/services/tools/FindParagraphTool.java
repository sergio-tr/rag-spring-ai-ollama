package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;

/**
 * Enhanced FindParagraphTool for finding relevant paragraphs in meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic paragraph relevance evaluation
 * - Enhanced paragraph extraction and summarization
 */
public class FindParagraphTool extends AbstractTool {

    public FindParagraphTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing find paragraph query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> results = new ArrayList<>();

        // Try with NER filtering if available
        if (ner != null && !docs.isEmpty()) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    results.addAll(findRelevantParagraphs(doc, query));
                }
            }
        }
        
        if (results.isEmpty() && !docs.isEmpty()) {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                results.addAll(findRelevantParagraphsByLLM(doc, query));
            }
        }
        
        if (results.isEmpty()) {
            docs = retrieveAllDocuments(query);
            if (!docs.isEmpty()) {
                for (Document doc : docs) {
                    if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                        continue;
                    }
                    
                    results.addAll(findRelevantParagraphsByLLM(doc, query));
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
     * Finds relevant paragraphs in a document.
     * Uses English for internal processing, but preserves original language in content.
     */
    private List<String> findRelevantParagraphs(Document doc, String query) {
        if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> relevant = new ArrayList<>();
        String content = doc.getContent();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        String date = extractDate(content);
        
        for (String paragraph : paragraphs) {
            if (paragraph != null && !paragraph.trim().isEmpty() && isParagraphRelevantByLLM(query, paragraph)) {
                relevant.add("Meeting minutes from " + (date != null ? date : "unknown date") + ":\n" + paragraph.trim());
            }
        }
        return relevant;
    }

    /**
     * Finds relevant paragraphs using LLM-based relevance.
     * Uses English for internal processing, but preserves original language in content.
     */
    private List<String> findRelevantParagraphsByLLM(Document doc, String query) {
        if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> relevant = new ArrayList<>();
        String content = doc.getContent();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        String date = extractDate(content);
        
        for (String paragraph : paragraphs) {
            if (paragraph != null && !paragraph.trim().isEmpty() && isParagraphRelevantByLLM(query, paragraph)) {
                relevant.add("Meeting minutes from " + (date != null ? date : "unknown date") + ":\n" + paragraph.trim());
            }
        }
        return relevant;
    }

    /**
     * Determines if a paragraph is relevant to the query using LLM.
     * Uses English for internal processing, but preserves original language in query and paragraph.
     */
    private boolean isParagraphRelevantByLLM(String query, String paragraph) {
        if (query == null || query.trim().isEmpty() || paragraph == null || paragraph.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            This is the user's query (in any language):
            "%s"
            
            And this is a paragraph from the meeting minutes (may be in any language):
            "%s"
            
            Does the paragraph clearly or partially answer the query?
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, paragraph);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in isParagraphRelevantByLLM, defaulting to false");
                return false;
            }
            
            String normalized = result.strip().toLowerCase();
            // Check for positive responses in multiple languages
            return normalized.startsWith("yes") || normalized.contains("sí") || normalized.contains("si") || 
                   normalized.contains("oui") || normalized.contains("ja") || normalized.contains("da");
        } catch (Exception e) {
            log().error("Error in isParagraphRelevantByLLM, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Generates final answer with found paragraphs.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateFinalAnswer(String query, List<String> results) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String joined = results.stream()
                .filter(r -> r != null && !r.trim().isEmpty())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The following paragraphs from the meeting minutes are relevant:
            %s
            
            Write a brief and clear answer in the same language as the query, 
            summarizing the relevant information from all the paragraphs found.
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
     * Generates not found response.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundResponse(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No relevant paragraphs were found for this query in the available meeting minutes.
            
            Write a polite response in the same language as the query explaining that no relevant paragraphs were found.
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
     * Generates a fallback answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackAnswer(String query, List<String> results) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "Párrafos relevantes encontrados:\n\n" + 
                   results.stream().limit(5).collect(java.util.stream.Collectors.joining("\n\n"));
        } else {
            return "Relevant paragraphs found:\n\n" + 
                   results.stream().limit(5).collect(java.util.stream.Collectors.joining("\n\n"));
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
            return "No se encontraron párrafos relevantes en los documentos disponibles para esta consulta.";
        } else {
            return "No relevant paragraphs were found in the available documents for this query.";
        }
    }
}
