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
 * Enhanced MetadataGetFieldTool for extracting specific fields from meeting minutes with intelligent analysis.
 * 
 * Features:
 * - Intelligent field extraction with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Field clustering and pattern analysis
 * - Quality ranking and synthesis of field values
 * - Advanced NER-based filtering
 * - Multi-field analysis and comparison
 */
public class MetadataGetFieldTool extends AbstractMetadataTool {

    public MetadataGetFieldTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing get field query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query, 
            new String[] {"date", "place", "startTime", "endTime", "topics", "decisions", "summary", "president", "secretary", "attendees"}
        );
        
        if (docs.isEmpty()) {
            log().debug("No documents found with metadata filter, trying basic retrieval");
            docs = retrieveDocuments(query);
        }
        
        if (docs.isEmpty()) {
            log().debug("No documents found for get field query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for get field query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for get field query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Classify field intent with LLM
        String detectedField = classifyFieldIntentWithLLM(query);
        if (detectedField.equals("unknown")) {
            log().debug("Could not classify field intent for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 5: Extract field values in parallel
        List<FieldResult> results = extractFieldValuesInParallel(query, relevantMinutes, detectedField);
        if (results.isEmpty()) {
            log().debug("No field values extracted for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 6: Analyze and rank field results
        List<FieldResult> rankedResults = analyzeAndRankFieldResults(query, results);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedFieldAnswer(query, rankedResults, detectedField);
        log().debug("Generated get field answer for query: {} with {} field values for field: {}", 
                   query, results.size(), detectedField);
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Extracts field values in parallel
     */
    private List<FieldResult> extractFieldValuesInParallel(String query, List<Minute> minutes, String detectedField) {
        List<CompletableFuture<FieldResult>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractFieldValue(query, minute, detectedField)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> result.fieldValue != null && !result.fieldValue.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Extracts field value for a minute with enhanced context
     */
    private FieldResult extractFieldValue(String query, Minute minute, String detectedField) {
        String fieldValue = extractFieldFromMinute(detectedField, minute);
        
        if (fieldValue == null || fieldValue.isBlank()) {
            return null;
        }
        
        // Calculate relevance score
        double relevanceScore = calculateRelevanceScore(query, 
            String.format("Field '%s': %s", detectedField, fieldValue), minute.toString());
        
        // Extract key information about the field value
        String keyInfo = extractKeyFieldInfo(minute, detectedField, fieldValue, query);
        
        return new FieldResult(
            minute.id(),
            minute.date(),
            minute.place(),
            detectedField,
            fieldValue,
            keyInfo,
            relevanceScore,
            System.currentTimeMillis()
        );
    }

    /**
     * Extracts key information about the field value.
     */
    private String extractKeyFieldInfo(Minute minute, String fieldName, String fieldValue, String query) {
        if (minute == null || fieldName == null || fieldValue == null || fieldValue.trim().isEmpty() || 
            query == null || query.trim().isEmpty()) {
            return "";
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Field name: %s
            Field value (may be in any language): %s
            
            Meeting metadata (values may be in any language):
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            
            Extract the key information about this field value that might be relevant to the query.
            Focus on facts, numbers, dates, names, or specific details.
            Write a brief summary (1-2 sentences) in the same language as the query.
            """,
            query,
            fieldName,
            fieldValue,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown"
        );
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().debug("Empty response from LLM in extractKeyFieldInfo, returning empty string");
                return "";
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error extracting key field info, returning empty string", e);
            return "";
        }
    }

    /**
     * Classifies field intent with enhanced LLM analysis.
     * Uses English for internal processing, but preserves original language in query.
     */
    private String classifyFieldIntentWithLLM(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "unknown";
        }
        
        String queryType = analyzeQueryType(query);
        
        String prompt = String.format("""
            Given the following user question (in any language):
            "%s"
            
            Query type: %s
            
            Determine which field the user wants to query. Choose one of the following (respond with the field name in English):
            - date
            - place
            - startTime
            - endTime
            - president
            - secretary
            - topics
            - decisions
            - summary
            - attendees
            
            Consider the query type and context to make the best choice.
            
            Respond with ONLY the field name in English (one word).
            If the intent is unclear, answer with "unknown".
            Do not include any explanation or additional text.
            """, query, queryType);
        
        try {
            String result = getLLMResponseCached(prompt);
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in classifyFieldIntentWithLLM, defaulting to unknown");
                return "unknown";
            }
            
            String normalized = result.strip().toLowerCase();
            if (normalized.contains("unknown")) {
                return "unknown";
            }
            
            // Extract first word
            String cleaned = normalized.split("\\s+")[0].trim();
            
            // Validate the result against known fields
            String[] validFields = {"date", "place", "starttime", "endtime", "president", "secretary", "topics", "decisions", "summary", "attendees"};
            for (String field : validFields) {
                if (cleaned.contains(field)) {
                    return field;
                }
            }
            
            return "unknown";
        } catch (Exception e) {
            log().error("Error classifying field intent, defaulting to unknown", e);
            return "unknown";
        }
    }

    /**
     * Analyzes and ranks field results by relevance and quality
     */
    private List<FieldResult> analyzeAndRankFieldResults(String query, List<FieldResult> results) {
        // Sort by relevance score (descending)
        return results.stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .collect(Collectors.toList());
    }

    /**
     * Generates enhanced field answer with analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedFieldAnswer(String query, List<FieldResult> results, String detectedField) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty() || 
            detectedField == null || detectedField.equals("unknown")) {
            return generateNotFoundMessage(query);
        }
        
        String fieldSummary = formatFieldResults(results, detectedField);
        
        String prompt = String.format("""
            Given the following get field query (in any language):
            "%s"
            
            Detected field: %s
            Found %d relevant field values:
            
            %s
            
            Write a clear, comprehensive answer in the same language as the query, 
            presenting the field values in a user-friendly way.
            If multiple values are found, organize them logically and highlight the most relevant ones.
            Include context and additional information that might be helpful.
            """, query, detectedField, results.size(), 
            fieldSummary != null ? fieldSummary : "No field values found.");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedFieldAnswer, using fallback");
                return generateFallbackFieldAnswer(query, results, detectedField);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced field answer, using fallback", e);
            return generateFallbackFieldAnswer(query, results, detectedField);
        }
    }
    
    /**
     * Generates a fallback field answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackFieldAnswer(String query, List<FieldResult> results, String detectedField) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Se encontraron %d valores para el campo '%s':\n%s",
                              results.size(),
                              detectedField,
                              results.stream()
                                      .limit(5)
                                      .map(r -> String.format("- %s: %s", r.date != null ? r.date : "fecha desconocida", r.fieldValue))
                                      .collect(Collectors.joining("\n")));
        } else {
            return String.format("Found %d values for field '%s':\n%s",
                              results.size(),
                              detectedField,
                              results.stream()
                                      .limit(5)
                                      .map(r -> String.format("- %s: %s", r.date != null ? r.date : "unknown date", r.fieldValue))
                                      .collect(Collectors.joining("\n")));
        }
    }

    /**
     * Formats field results for LLM prompt
     */
    private String formatFieldResults(List<FieldResult> results, String detectedField) {
        StringBuilder summary = new StringBuilder();
        
        for (int i = 0; i < results.size(); i++) {
            FieldResult result = results.get(i);
            
            summary.append(String.format("Result %d:\n", i + 1));
            summary.append(String.format("Date: %s\n", result.date));
            summary.append(String.format("Place: %s\n", result.place));
            summary.append(String.format("Field '%s': %s\n", result.fieldName, result.fieldValue));
            summary.append(String.format("Key Info: %s\n", result.keyInfo));
            summary.append(String.format("Relevance Score: %.2f\n", result.relevanceScore));
            summary.append("\n");
        }
        
        return summary.toString();
    }

    /**
     * Represents a field result with enhanced metadata
     */
    private static class FieldResult {
        final String minuteId;
        final String date;
        final String place;
        final String fieldName;
        final String fieldValue;
        final String keyInfo;
        final double relevanceScore;
        final long timestamp;

        FieldResult(String minuteId, String date, String place, String fieldName, String fieldValue, 
                   String keyInfo, double relevanceScore, long timestamp) {
            this.minuteId = minuteId;
            this.date = date;
            this.place = place;
            this.fieldName = fieldName;
            this.fieldValue = fieldValue;
            this.keyInfo = keyInfo;
            this.relevanceScore = relevanceScore;
            this.timestamp = timestamp;
        }
        
        /**
         * Gets a formatted identifier for the result
         */
        String getIdentifier() {
            return String.format("%s (%s - %s)", minuteId, date != null ? date : "unknown", place != null ? place : "unknown");
        }
        
        /**
         * Gets the age of the result in milliseconds
         */
        long getAge() {
            return System.currentTimeMillis() - timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("FieldResult[%s, %s=%s, score=%.2f, age=%dms]", 
                               getIdentifier(), fieldName, fieldValue, relevanceScore, getAge());
        }
    }
}