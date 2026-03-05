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

    public ExtractEntitiesTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing extract entities query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
        
        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for extract entities query", docs.size());
        List<String> results = new ArrayList<>();

        if (ner != null) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            log().debug("Filtered {} documents by temporal context, {} remaining", docs.size(), filteredDocs.size());
            
            int matchedCount = 0;
            int entitiesFoundCount = 0;
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    matchedCount++;
                    String content = doc.getText();
                    String date = extractor.extractDate(content);
                    String entities = extractRequestedEntities(content, query, ner);
                    if (!entities.isBlank()) {
                        entitiesFoundCount++;
                        results.add(generateEntityResult(date, entities));
                    } else {
                        log().debug("Document {} matched NER but no entities extracted", doc.getId());
                    }
                }
            }
            log().debug("NER filtering: {} documents matched NER, {} had entities extracted out of {} filtered", 
                       matchedCount, entitiesFoundCount, filteredDocs.size());
        } else {
            // Baseline: extract entities from all documents
            log().debug("No NER available, extracting entities from all {} documents", docs.size());
            int entitiesFoundCount = 0;
            for (Document doc : docs) {
                String content = doc.getText();
                String date = extractor.extractDate(content);
                String entities = extractRequestedEntities(content, query, null);
                if (!entities.isBlank()) {
                    entitiesFoundCount++;
                    results.add(generateEntityResult(date, entities));
                }
            }
            log().debug("Extracted entities from {} documents out of {} total", entitiesFoundCount, docs.size());
        }

        String response;
        if (!results.isEmpty()) {
            log().debug("Extracted {} entities for query, generating response", results.size());
            response = generateResponseWithLLM(query, results);
        } else {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No entities found for query: '{}' (execution time: {} ms)", query, totalTime);
            response = generateNotFoundResponse(query);
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated extract entities answer for query: '{}' (execution time: {} ms)", query, totalTime);
        // Apply formatResponse to clean the response
        String formattedResponse = formatResponse(response, query);
        return ToolResult.from(formattedResponse, getClass());
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
        
        String attendees = String.join(", ", extractor.extractAttendees(content));
        String agenda = extractor.extractAgenda(content);
        String fragment = extractor.extractRelevantFragment(content, query) ;
        
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
            
            Relevant information found:
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "entities found", "extraction", "analysis", or internal processing.
            DO NOT include phrases like "La extracción de entidades ha identificado" or "Según el análisis".
            DO NOT repeat the question or any part of it at the beginning.
            DO NOT start with phrases like "Dime qué...", "The user asked...", etc.
            Start directly with the answer content.
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            Be concise and direct.
            """, query, joinedResults);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateResponseWithLLM for query: '{}', using fallback", query);
                return generateFallbackResponse(query, results);
            }
            
            // Apply formatResponse to clean and format the response
            return formatResponse(response.strip(), query);
        } catch (Exception e) {
            log().error("Error generating response with LLM, using fallback", e);
            return generateFallbackResponse(query, results);
        }
    }
    
    /**
     * Generates a fallback response when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackResponse(String query, List<String> results) {
        String resultsText = results.stream()
                .limit(5)
                .collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found the following relevant entities:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            listing the found entities.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", resultsText);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback response with LLM", e);
        }
        
        // Ultimate fallback
        return "Relevant entities found:\n\n" + resultsText;
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
}