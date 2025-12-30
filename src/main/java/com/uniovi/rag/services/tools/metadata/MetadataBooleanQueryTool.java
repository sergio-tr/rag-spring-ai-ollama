package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataBooleanQueryTool for answering yes/no questions about meeting minutes.
 */
public class MetadataBooleanQueryTool extends AbstractMetadataTool {

    public MetadataBooleanQueryTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing boolean query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(query, new String[] {"date", "place", "decisions", "topics", "summary"}, ner);
        
        // Step 1.5: Extract keyword from query and validate it exists
        String keyword = extractTopicFromQuery(query, ner);
        if (keyword != null && !docs.isEmpty()) {
            log().info("Validating keyword '{}' exists in documents", keyword);
            if (!validateKeywordExists(docs, keyword)) {
                log().info("Keyword '{}' not found in any documents for query: {}", keyword, query);
                String errorMessage = generateSpecificErrorMessage(query, "keyword", keyword, docs.size(), "The keyword was not found in any documents");
                return ToolResult.from(errorMessage, getClass());
            }
        }
        
        // Step 1.6: Filter by year if mentioned in query
        String requestedYear = extractYearFromQuery(query, ner);
        if (requestedYear != null && !docs.isEmpty()) {
            log().info("Filtering documents by year: {}", requestedYear);
            List<Document> filteredDocs = filterDocumentsByYear(docs, requestedYear);
            if (filteredDocs.isEmpty()) {
                log().info("No documents found for year {} in query: {}", requestedYear, query);
                String errorMessage = generateSpecificErrorMessage(query, "year", requestedYear, docs.size(), "No documents found for this year");
                return ToolResult.from(errorMessage, getClass());
            }
            docs = filteredDocs;
            log().info("Filtered to {} documents for year {}", docs.size(), requestedYear);
        }
        
        if (docs.isEmpty()) {
            log().info("No documents found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Extract evidence in parallel (metadata-first, LLM as fallback per minute)
        List<String> evidence = extractEvidenceInParallel(query, relevantMinutes);
        
        if (evidence.isEmpty()) {
            log().info("No evidence found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 5: Generate final answer
        String answer = generateBooleanAnswerWithLLM(query, evidence, relevantMinutes.size());
        log().info("Generated answer for query: {} with {} evidence pieces", query, evidence.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Extracts evidence from minutes in parallel
     */
    private List<String> extractEvidenceInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<String>> evidenceFutures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractEvidencePreferMetadata(query, minute)))
                .collect(Collectors.toList());

        return evidenceFutures.stream()
                .map(CompletableFuture::join)
                .filter(evidence -> !evidence.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Prefers metadata-based evidence; falls back to LLM only if metadata has no usable signal.
     */
    private String extractEvidencePreferMetadata(String query, Minute minute) {
        String metadataEvidence = buildEvidenceFromMetadata(minute, query);
        if (metadataEvidence != null && !metadataEvidence.isBlank()) {
            return metadataEvidence;
        }
        return extractEvidenceFromMinute(query, minute);
    }

    /**
     * Extracts relevant evidence from a minute using LLM.
     */
    private String extractEvidenceFromMinute(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }
        
        String prompt = generateEvidenceExtractionPrompt(query, minute);
        String response = getLLMResponseCached(prompt);
        
        if (response == null || response.trim().isEmpty()) {
            log().info("Empty evidence extracted from minute: {}", minute.id());
            return "";
        }
        
        return response;
    }

    /**
     * Generates adaptive evidence extraction prompt
     */
    private String generateEvidenceExtractionPrompt(String query, Minute minute) {
        String queryType = analyzeQueryType(query);
        
        return String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            Meeting information:
            Date: %s
            Place: %s
            Decisions: %s
            Topics: %s
            Summary: %s
            
            Extract the most relevant evidence that helps answer the query.
            Format each piece of evidence with its type (Decision/Topic/Summary/Date/Place).
            If no evidence is relevant, return an empty string.
            """,
            query,
            queryType,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "none",
            minute.topics() != null ? String.join(", ", minute.topics()) : "none",
            minute.summary() != null ? minute.summary() : "none"
        );
    }

    /**
     * Generates final boolean answer with enhanced context.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateBooleanAnswerWithLLM(String query, List<String> evidence, int minuteCount) {
        if (query == null || query.trim().isEmpty() || evidence == null || evidence.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String joined = evidence.stream()
                .filter(e -> e != null && !e.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant meeting minutes with the following evidence:
            %s
            
            Write a clear and direct answer in the same language as the query, 
            indicating if the evidence allows to answer YES, NO, or PARTIALLY to the query.
            Be concise but informative. Include specific details from the evidence when relevant.
            """, query, minuteCount, joined);
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateBooleanAnswerWithLLM, using fallback");
                return generateFallbackAnswer(query, evidence);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating boolean answer with LLM, using fallback", e);
            return generateFallbackAnswer(query, evidence);
        }
    }
    
    /**
     * Builds evidence directly from minute metadata using LLM to determine relevance.
     * This is a fallback to avoid false negatives.
     */
    private String buildEvidenceFromMetadata(Minute minute, String query) {
        if (minute == null || query == null || query.trim().isEmpty()) {
            return "";
        }
        
        // Build minute context
        StringBuilder context = new StringBuilder();
        if (minute.date() != null) {
            context.append("Date: ").append(minute.date()).append("\n");
        }
        if (minute.place() != null) {
            context.append("Place: ").append(minute.place()).append("\n");
        }
        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            context.append("Decisions: ").append(String.join(", ", minute.decisions())).append("\n");
        }
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            context.append("Topics: ").append(String.join(", ", minute.topics())).append("\n");
        }
        if (minute.summary() != null && !minute.summary().trim().isEmpty()) {
            context.append("Summary: ").append(minute.summary()).append("\n");
        }
        
        if (context.length() == 0) {
            return "";
        }
        
        // Use LLM to extract relevant evidence based on query
        String prompt = String.format("""
            Task: Extract relevant evidence from meeting minute metadata that helps answer the query.
            
            Query (may be in any language): "%s"
            
            Meeting minute metadata:
            %s
            
            Extract only the fields that are relevant to answering the query.
            Format each piece of evidence with its type (Decision/Topic/Summary/Date/Place).
            If no evidence is relevant, return an empty string.
            
            Return ONLY the relevant evidence, without explanations.
            """, query, context.toString());
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error building evidence from metadata with LLM: {}", e.getMessage());
        }
        
        // Fallback: return all metadata
        return context.toString().trim();
    }
    
    /**
     * Generates a fallback answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackAnswer(String query, List<String> evidence) {
        String evidenceText = evidence.stream()
                .limit(3)
                .collect(Collectors.joining("; "));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found %d pieces of evidence:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            indicating if the evidence allows to answer YES, NO, or PARTIALLY.
            Be concise and direct.
            Do not repeat the question.
            """, query, evidence.size(), evidenceText);
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback answer with LLM", e);
        }
        
        // Ultimate fallback
        return String.format("Based on the evidence found (%d pieces), the answer is: YES/NO/PARTIALLY. Evidence: %s",
                          evidence.size(), evidenceText);
    }
}
