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
 * Enhanced BooleanQueryTool for answering yes/no questions about meeting minutes.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Semantic analysis instead of literal matching
 * - Support for all NER fields including new ones
 * - Multilingual support with adaptive prompts
 * - Decoupled from literal word matching
 */
public class BooleanQueryTool extends AbstractTool {

    public BooleanQueryTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        List<Document> docs = retrieveDocuments(query);
        List<String> evidence = new ArrayList<>();
        boolean found = false;

        // Try with NER filtering if available
        if (ner != null && !docs.isEmpty()) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String fragment = extractRelevantFragment(doc.getContent(), query);
                    if (fragment != null && !fragment.trim().isEmpty() && fragmentConfirmsClaim(query, fragment)) {
                        String date = extractDate(doc.getContent());
                        evidence.add(generateEvidenceMessage(date, fragment));
                        found = true;
                    }
                }
            }
        }
        
        if (!found && !docs.isEmpty()) {
            // Try without NER filtering
            for (Document doc : docs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                String fragment = extractRelevantFragment(doc.getContent(), query);
                if (fragment != null && !fragment.trim().isEmpty() && fragmentConfirmsClaim(query, fragment)) {
                    String date = extractDate(doc.getContent());
                    evidence.add(generateEvidenceMessage(date, fragment));
                    found = true;
                }
            }
        }
        
        if (!found) {
            docs = retrieveAllDocuments(query);
            if (!docs.isEmpty()) {
                for (Document doc : docs) {
                    if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                        continue;
                    }
                    
                    String fragment = extractRelevantFragment(doc.getContent(), query);
                    if (fragment != null && !fragment.trim().isEmpty() && fragmentConfirmsClaim(query, fragment)) {
                        String date = extractDate(doc.getContent());
                        evidence.add(generateEvidenceMessage(date, fragment));
                        found = true;
                    }
                }
            }
        }

        String response;
        if (found) {
            response = generateResponseWithLLM(query, evidence);
        } else {
            response = generateNotFoundResponse(query);
        }
        return ToolResult.from(response, getClass());
    }

    /**
     * Checks if fragment confirms the claim using intelligent analysis.
     * Uses English for internal processing, but preserves original language in query and fragment.
     */
    private boolean fragmentConfirmsClaim(String query, String fragment) {
        if (query == null || query.trim().isEmpty() || fragment == null || fragment.trim().isEmpty()) {
            return false;
        }
        
        String answerType = nerHandler.determineAnswerType(query, null);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            And this fragment from meeting minutes (may be in any language):
            "%s"
            
            Does this fragment confirm or support the claim made in the query?
            Consider semantic meaning, not just exact matches.
            Consider the context and intent of the query.
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, answerType, fragment);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in fragmentConfirmsClaim, defaulting to false");
                return false;
            }
            
            String normalized = result.strip().toLowerCase();
            return normalized.contains("yes") || normalized.contains("sí");
        } catch (Exception e) {
            log().error("Error in fragmentConfirmsClaim, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Generates evidence message with proper formatting
     */
    private String generateEvidenceMessage(String date, String fragment) {
        return String.format("Yes, evidence found in the meeting of %s:\n%s", 
                           date != null ? date : "unknown date", fragment);
    }

    /**
     * Generates response using LLM with evidence.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateResponseWithLLM(String query, List<String> evidence) {
        if (query == null || query.trim().isEmpty() || evidence == null || evidence.isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String joinedEvidence = evidence.stream()
                .filter(e -> e != null && !e.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        
        if (joinedEvidence.trim().isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            The following evidence was found in the meeting minutes:
            %s
            
            Write a clear, concise response in the same language as the query, 
            using the evidence found. Be direct and factual.
            """, query, joinedEvidence);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateResponseWithLLM, using fallback");
                return generateNotFoundResponse(query);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating response with LLM, using fallback", e);
            return generateNotFoundResponse(query);
        }
    }

    /**
     * Generates not found response using LLM.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundResponse(String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Write a short message indicating that no evidence was found for this claim, 
            in the same language as the query.
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                // Fallback to simple message
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
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*") || 
                           queryLower.contains("quién") || queryLower.contains("qué") || 
                           queryLower.contains("cuándo") || queryLower.contains("dónde") ||
                           queryLower.contains("cuántos") || queryLower.contains("cómo");
        
        if (isSpanish) {
            return "No se encontró evidencia en los documentos disponibles para confirmar esta afirmación.";
        } else {
            return "No evidence was found in the available documents to confirm this claim.";
        }
    }
}