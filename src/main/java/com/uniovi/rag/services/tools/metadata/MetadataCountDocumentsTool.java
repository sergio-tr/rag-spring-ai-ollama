package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataCountDocumentsTool for counting meeting minutes with intelligent analysis.
 */
public class MetadataCountDocumentsTool extends AbstractMetadataTool {

    public MetadataCountDocumentsTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing count documents query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query,
            new String[] {"date", "place", "topics", "decisions", "summary"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().info("No documents found for count query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for count query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for count query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Perform comprehensive counting analysis
        CountingAnalysis analysis = performCountingAnalysis(query, relevantMinutes);

        // Step 5: Generate enhanced count answer
        String answer = generateEnhancedCountAnswer(query, analysis);
        log().info("Generated count answer for query: {} with {} documents", query, analysis.getTotalCount());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Performs comprehensive counting analysis
     */
    private CountingAnalysis performCountingAnalysis(String query, List<Minute> minutes) {
        int totalCount = minutes.size();
        
        // Extract and analyze dates
        List<String> dates = extractAndAnalyzeDates(minutes);
        
        // Extract and analyze places
        List<String> places = extractAndAnalyzePlaces(minutes);
        
        // Extract and analyze topics
        List<String> topics = extractAndAnalyzeTopics(minutes);
        
        return new CountingAnalysis(
            totalCount,
            dates,
            places,
            topics
        );
    }

    /**
     * Extracts and analyzes dates from minutes
     */
    private List<String> extractAndAnalyzeDates(List<Minute> minutes) {
        return minutes.stream()
                .map(Minute::date)
                .filter(Objects::nonNull)
                .filter(date -> !date.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Extracts and analyzes places from minutes
     */
    private List<String> extractAndAnalyzePlaces(List<Minute> minutes) {
        return minutes.stream()
                .map(Minute::place)
                .filter(Objects::nonNull)
                .filter(place -> !place.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Extracts and analyzes topics from minutes
     */
    private List<String> extractAndAnalyzeTopics(List<Minute> minutes) {
        return minutes.stream()
                .map(Minute::topics)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Generates enhanced count answer with comprehensive analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedCountAnswer(String query, CountingAnalysis analysis) {
        if (query == null || query.trim().isEmpty() || analysis == null) {
            return generateNotFoundMessage(query);
        }
        
        String simpleData = formatSimpleData(analysis);
        
        String prompt = String.format("""
            You need to answer a question about meeting minutes. The question asked was about counting meeting minutes that meet certain criteria.
            
            Found %d relevant meeting minutes.
            
            Information:
            %s
            
            Write a clear, direct answer in the same language as the user's question (detect from context).
            CRITICAL RULES:
            1. DO NOT repeat or echo the user's question in your response.
            2. DO NOT start your answer with the question.
            3. Answer directly with the count and relevant information.
            4. Example if the query is in English: "Found 5 meeting minutes" (NOT "The question was... Found 5 meeting minutes")
            5. Example if the query is in Spanish: "Se encontraron 5 actas" (NOT "La pregunta era... Se encontraron 5 actas")
            
            Provide only the information requested.
            DO NOT mention any technical details like "análisis temporal", "análisis de distribución", "temporal analysis", "distribution analysis", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering naturally and concisely, as if you were a helpful assistant.
            """, 
            analysis.getTotalCount(),
            simpleData != null ? simpleData : "No additional information available."
        );
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedCountAnswer, using fallback");
                return generateFallbackCountAnswer(query, analysis);
            }
            
            String trimmed = response.trim();
            if (trimmed.length() < 10 || trimmed.matches("^\\d+$")) {
                log().warn("Response too short or just a number (length: {}), reformatting automatically", trimmed.length());
                return generateFallbackCountAnswer(query, analysis);
            }
            
            String cleaned = removeQuestionEcho(trimmed, query);
            return cleaned;
        } catch (Exception e) {
            log().error("Error generating enhanced count answer, using fallback", e);
            return generateFallbackCountAnswer(query, analysis);
        }
    }
    
    /**
     * Generates a fallback count answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackCountAnswer(String query, CountingAnalysis analysis) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Se encontraron %d actas de reunión relevantes para esta consulta.", analysis.getTotalCount());
        } else {
            return String.format("Found %d relevant meeting minutes for this query.", analysis.getTotalCount());
        }
    }

    /**
     * Removes question echo from response.
     */
    private String removeQuestionEcho(String response, String query) {
        if (response == null || query == null) {
            return response;
        }
        
        // Remove common echo patterns
        String lowerResponse = response.toLowerCase();
        String lowerQuery = query.toLowerCase();
        
        // If response starts with the question, remove it
        if (lowerResponse.startsWith(lowerQuery)) {
            String cleaned = response.substring(query.length()).trim();
            // Remove common separators
            cleaned = cleaned.replaceAll("^[.:;,\\-\\s]+", "").trim();
            if (!cleaned.isEmpty()) {
                log().debug("Removed question echo from response");
                return cleaned;
            }
        }
        
        // Check for common echo patterns like "La pregunta era...", "The question was..."
        String[] echoPatterns = {
            "la pregunta era", "la pregunta es", "la consulta era", "la consulta es",
            "the question was", "the question is", "the query was", "the query is",
            "en cuántas actas", "dime qué actas", "qué actas"
        };
        
        for (String pattern : echoPatterns) {
            if (lowerResponse.contains(pattern)) {
                // Try to find where the actual answer starts
                int patternIndex = lowerResponse.indexOf(pattern);
                if (patternIndex >= 0 && patternIndex < response.length() / 2) {
                    // Pattern is in first half, likely an echo
                    String afterPattern = response.substring(patternIndex + pattern.length()).trim();
                    afterPattern = afterPattern.replaceAll("^[.:;,\\-\\s]+", "").trim();
                    if (!afterPattern.isEmpty() && afterPattern.length() > 10) {
                        log().debug("Removed echo pattern '{}' from response", pattern);
                        return afterPattern;
                    }
                }
            }
        }
        
        return response;
    }

    /**
     * Formats simple data for LLM prompt (without technical analysis terms)
     */
    private String formatSimpleData(CountingAnalysis analysis) {
        StringBuilder data = new StringBuilder();
        
        if (analysis.getDates() != null && !analysis.getDates().isEmpty()) {
            data.append("Fechas: ").append(String.join(", ", analysis.getDates())).append("\n");
        }
        
        if (analysis.getPlaces() != null && !analysis.getPlaces().isEmpty()) {
            data.append("Lugares: ").append(String.join(", ", analysis.getPlaces())).append("\n");
        }
        
        if (analysis.getTopics() != null && !analysis.getTopics().isEmpty()) {
            data.append("Temas principales: ").append(String.join(", ", analysis.getTopics().stream().limit(10).collect(Collectors.toList()))).append("\n");
        }
        return data.toString();
    }

}
