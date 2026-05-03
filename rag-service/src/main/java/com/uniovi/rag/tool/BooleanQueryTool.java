package com.uniovi.rag.tool;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    public BooleanQueryTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing boolean query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
        
        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for boolean query", docs.size());
        List<String> evidence = new ArrayList<>();
        boolean found = false;

        // Try with NER filtering if available
        if (ner != null && !docs.isEmpty()) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                    continue;
                }
                
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String fragment = extractor.extractRelevantFragment(doc.getText(), query);
                    if (fragment != null && !fragment.trim().isEmpty() && fragmentConfirmsClaim(query, fragment, ner)) {
                        String date = extractor.extractDate(doc.getText());
                        evidence.add(generateEvidenceMessage(date, fragment));
                        found = true;
                    }
                }
            }
        }
        
        if (!found && !docs.isEmpty()) {
            // Try without NER filtering
            for (Document doc : docs) {
                if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                    continue;
                }
                
                String fragment = extractor.extractRelevantFragment(doc.getText(), query);
                if (fragment != null && !fragment.trim().isEmpty() && fragmentConfirmsClaim(query, fragment, ner)) {
                    String date = extractor.extractDate(doc.getText());
                    evidence.add(generateEvidenceMessage(date, fragment));
                    found = true;
                }
            }
        }
        
        if (!found) {
            docs = retrieveAllDocuments(query, ner);
            if (!docs.isEmpty()) {
                for (Document doc : docs) {
                    if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                        continue;
                    }
                    
                    String fragment = extractor.extractRelevantFragment(doc.getText(), query);
                    if (fragment != null && !fragment.trim().isEmpty() && fragmentConfirmsClaim(query, fragment, ner)) {
                        String date = extractor.extractDate(doc.getText());
                        evidence.add(generateEvidenceMessage(date, fragment));
                        found = true;
                    }
                }
            }
        }

        String response;
        if (found) {
            log().debug("Found evidence for boolean query, generating response with {} evidence items", evidence.size());
            response = generateResponseWithLLM(query, evidence);
        } else {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No evidence found for boolean query: '{}' (execution time: {} ms)", query, totalTime);
            response = generateNotFoundResponse(query);
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated boolean query answer for query: '{}' (execution time: {} ms, found: {})", 
                  query, totalTime, found);
        // Apply formatResponse to clean the response
        String formattedResponse = formatResponse(response, query);
        return ToolResult.from(formattedResponse, getClass());
    }

    /**
     * Checks if fragment confirms the claim using intelligent analysis.
     * Uses NER when available for answer type; otherwise falls back to LLM.
     */
    private boolean fragmentConfirmsClaim(String query, String fragment, JSONObject ner) {
        if (query == null || query.trim().isEmpty() || fragment == null || fragment.trim().isEmpty()) {
            return false;
        }
        
        String answerType = nerHandler.determineAnswerType(query, ner);
        
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
            
            // Use LLM to interpret boolean response
            return Boolean.TRUE.equals(interpretLLMYesNoResponse(result, "fragmentConfirmsClaim"));
        } catch (Exception e) {
            log().error("Error in fragmentConfirmsClaim, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Generates evidence message with proper formatting
     */
    private String generateEvidenceMessage(String date, String fragment) {
        return String.format(
                "Yes, evidence found in the meeting of %s:%n%s",
                date != null ? date : "unknown date",
                fragment);
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
            DO NOT repeat the question or any part of it at the beginning.
            DO NOT start with phrases like "Dime qué...", "The user asked...", etc.
            Start directly with the answer (YES/NO or Sí/No) followed by brief evidence.
            Be concise - maximum 2-3 sentences.
            """, query, joinedEvidence);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateResponseWithLLM for query: '{}', using fallback", query);
                return generateNotFoundResponse(query);
            }
            
            // Apply formatResponse to clean and format the response
            return formatResponse(response.strip(), query);
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
            Be concise and direct.
            DO NOT repeat the question or any part of it.
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
            
            // Apply formatResponse to clean the response
            return formatResponse(response.strip(), query);
        } catch (Exception e) {
            log().error("Error generating not found response, using fallback", e);
            return generateFallbackNotFoundMessage(query);
        }
    }
}