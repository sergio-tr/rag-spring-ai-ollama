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
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant meeting minutes.
            
            Information:
            %s
            
            Write a clear, direct answer in the same language as the query.
            CRITICAL: You MUST respond with a complete sentence responding to the user query, NOT just a number.
            Example if the query is in English: "Found 5 meeting minutes" (NOT just "5")
            
            Provide only the information requested by the user.
            DO NOT mention any technical details like "análisis temporal", "análisis de distribución", "temporal analysis", "distribution analysis", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, 
            query, 
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
            
            return response;
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
