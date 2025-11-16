package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced SummarizeTopicTool for summarizing specific topics from meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced topic summarization and analysis
 */
public class SummarizeTopicTool extends AbstractTool {

    public SummarizeTopicTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing summarize topic query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> fragments = new ArrayList<>();

        // Try with NER filtering if available
        if (ner != null && !docs.isEmpty()) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    fragments.addAll(extractRelevantFragments(doc, query));
                }
                if (fragments.size() >= 10) break;
            }
        }
        
        if (fragments.isEmpty() && !docs.isEmpty()) {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (isRelevantByLLM(doc.getContent(), query)) {
                    fragments.addAll(extractRelevantFragments(doc, query));
                }
                if (fragments.size() >= 10) break;
            }
        }
        
        if (fragments.isEmpty()) {
            docs = retrieveAllDocuments(query);
            if (!docs.isEmpty()) {
                for (Document doc : docs) {
                    if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                        continue;
                    }
                    
                    if (isRelevantByLLM(doc.getContent(), query)) {
                        fragments.addAll(extractRelevantFragments(doc, query));
                    }
                    if (fragments.size() >= 10) break;
                }
            }
        }

        if (fragments.isEmpty()) {
            String notFound = generateNotFoundMessage(query);
            return ToolResult.from(notFound, getClass());
        }

        String summary = generateSummaryWithLLM(query, fragments);
        return ToolResult.from(summary, getClass());
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
    
    private List<String> extractRelevantFragments(Document doc, String query) {
        if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> relevant = new ArrayList<>();
        String content = doc.getContent();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        for (String p : paragraphs) {
            if (p != null && !p.trim().isEmpty() && isParagraphRelevantByLLM(query, p)) {
                relevant.add(p.trim());
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
            Given the following user query (in any language):
            "%s"
            
            And this is a paragraph from the minutes (may be in any language):
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
            return normalized.startsWith("yes") || normalized.startsWith("sí");
        } catch (Exception e) {
            log().error("Error in isParagraphRelevantByLLM, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Generates summary using LLM.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateSummaryWithLLM(String query, List<String> fragments) {
        if (query == null || query.trim().isEmpty() || fragments == null || fragments.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String joined = fragments.stream()
                .filter(f -> f != null && !f.trim().isEmpty())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            The following are relevant fragments from the minutes:
            %s
            
            Write a brief and clear summary in the same language as the query, 
            indicating the key points mentioned about the topic. 
            Avoid literal repetition and organize the information clearly.
            """, query, joined);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateSummaryWithLLM, using fallback");
                return generateFallbackSummary(query, fragments);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating summary with LLM, using fallback", e);
            return generateFallbackSummary(query, fragments);
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
     * Generates a fallback summary when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackSummary(String query, List<String> fragments) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "Resumen de los fragmentos relevantes encontrados:\n\n" + 
                   fragments.stream().limit(5).collect(java.util.stream.Collectors.joining("\n\n"));
        } else {
            return "Summary of relevant fragments found:\n\n" + 
                   fragments.stream().limit(5).collect(java.util.stream.Collectors.joining("\n\n"));
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
