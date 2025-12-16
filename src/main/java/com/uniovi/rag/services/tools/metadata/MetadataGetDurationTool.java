package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
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
 * Enhanced MetadataGetDurationTool for analyzing meeting durations with intelligent analysis.
 */
public class MetadataGetDurationTool extends AbstractMetadataTool {

    public MetadataGetDurationTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing get duration query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "startTime", "endTime", "topics", "decisions", "summary", "president", "secretary"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().info("No documents found for get duration query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for get duration query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for get duration query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Extract durations in parallel
        List<DurationResult> results = extractDurationsInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().info("No durations extracted for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank durations
        List<DurationResult> rankedResults = analyzeAndRankDurations(results);

        // Step 6: Perform statistical analysis
        DurationAnalysis analysis = performStatisticalAnalysis(rankedResults);

        // Step 7: Generate final answer (metadata-only)
        String answer = generateDurationAnswer(query, rankedResults, analysis);
        log().info("Generated get duration answer for query: {} with {} durations", 
                   query, results.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Extracts durations in parallel
     */
    private List<DurationResult> extractDurationsInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<DurationResult>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractDuration(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> result.getDurationMinutes() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Extracts duration for a minute with enhanced context
     */
    private DurationResult extractDuration(String query, Minute minute) {
        int duration = calculateDurationFromMinute(minute);
        
        if (duration <= 0) {
            return null;
        }
        
        return new DurationResult(
            minute.id(),
            minute.date(),
            minute.place(),
            minute.startTime(),
            minute.endTime(),
            duration
        );
    }

    /**
     * Analyzes and ranks durations by relevance and quality
     */
    private List<DurationResult> analyzeAndRankDurations(List<DurationResult> results) {
        // Sort by duration (descending) to surface longer meetings first
        return results.stream()
                .sorted((a, b) -> Integer.compare(b.getDurationMinutes(), a.getDurationMinutes()))
                .collect(Collectors.toList());
    }

    /**
     * Performs statistical analysis on durations
     */
    private DurationAnalysis performStatisticalAnalysis(List<DurationResult> results) {
        if (results.isEmpty()) {
            return new DurationAnalysis(0, 0, 0, Collections.emptyList());
        }
        
        List<Integer> durations = results.stream()
                .mapToInt(r -> r.getDurationMinutes())
                .boxed()
                .collect(Collectors.toList());
        
        int min = Collections.min(durations);
        int max = Collections.max(durations);
        double average = durations.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        
        return new DurationAnalysis(min, max, average, durations);
    }

    /**
     * Generates duration answer using only metadata and simple stats.
     */
    private String generateDurationAnswer(String query, List<DurationResult> results, DurationAnalysis analysis) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }

        boolean isSpecificQuery = isSpecificDateQuery(query);
        
        // If specific query and we have exactly one result, return specific format
        if (isSpecificQuery && results.size() == 1) {
            DurationResult result = results.get(0);
            String queryLower = query.toLowerCase();
            boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
            
            if (isSpanish) {
                return String.format("La reunión del %s duró %d minutos (%s - %s).",
                    result.getDate() != null ? result.getDate() : "fecha desconocida",
                    result.getDurationMinutes(),
                    result.getStartTime() != null ? result.getStartTime() : "?",
                    result.getEndTime() != null ? result.getEndTime() : "?");
            } else {
                return String.format("The meeting on %s lasted %d minutes (%s - %s).",
                    result.getDate() != null ? result.getDate() : "unknown date",
                    result.getDurationMinutes(),
                    result.getStartTime() != null ? result.getStartTime() : "?",
                    result.getEndTime() != null ? result.getEndTime() : "?");
            }
        }

        // Generic format for multiple results or non-specific queries
        StringBuilder sb = new StringBuilder();
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");

        if (isSpanish) {
            sb.append(String.format("Se encontraron %d reuniones con duración conocida.\n", results.size()));
            if (!analysis.getAllDurations().isEmpty()) {
                sb.append(String.format("Mínima: %d min, Máxima: %d min, Media: %.1f min.\n", analysis.getMinDuration(), analysis.getMaxDuration(), analysis.getAverageDuration()));
            }
            sb.append("Ejemplos:\n");
        } else {
            sb.append(String.format("Found %d meetings with known duration.\n", results.size()));
            if (!analysis.getAllDurations().isEmpty()) {
                sb.append(String.format("Min: %d min, Max: %d min, Avg: %.1f min.\n", analysis.getMinDuration(), analysis.getMaxDuration(), analysis.getAverageDuration()));
            }
            sb.append("Examples:\n");
        }

        results.stream().limit(5).forEach(r -> {
            sb.append(String.format("- %s: %d min", r.getDate() != null ? r.getDate() : (isSpanish ? "fecha desconocida" : "unknown date"), r.getDurationMinutes()));
            if (r.getStartTime() != null || r.getEndTime() != null) {
                sb.append(String.format(" (%s - %s)", r.getStartTime() != null ? r.getStartTime() : "?", r.getEndTime() != null ? r.getEndTime() : "?"));
            }
            if (r.getPlace() != null) {
                sb.append(String.format(" | %s", r.getPlace()));
            }
            sb.append("\n");
        });

        return sb.toString().trim();
    }
    
    /**
     * Detects if query contains a specific date.
     */
    private boolean isSpecificDateQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        // Check for date patterns in query
        String queryLower = query.toLowerCase();
        
        // Spanish date patterns
        if (queryLower.matches(".*\\d{1,2}\\s+de\\s+[a-z]+\\s+de\\s+\\d{4}.*") ||
            queryLower.matches(".*\\d{1,2}/\\d{1,2}/\\d{4}.*") ||
            queryLower.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            return true;
        }
        
        // English date patterns
        if (queryLower.matches(".*\\d{1,2}/\\d{1,2}/\\d{4}.*") ||
            queryLower.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            return true;
        }
        
        // Check for phrases that indicate specific date query
        if (queryLower.contains("del ") && queryLower.matches(".*\\d{4}.*")) {
            return true;
        }
        
        return false;
    }

}