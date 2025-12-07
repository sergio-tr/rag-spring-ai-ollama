package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import com.uniovi.rag.utils.InfoExtractor;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataGetDurationTool for analyzing meeting durations with intelligent analysis.
 * 
 * Features:
 * - Intelligent duration analysis with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Duration clustering and pattern analysis
 * - Quality ranking and synthesis of durations
 * - Advanced NER-based filtering
 * - Statistical analysis and comparisons
 */
public class MetadataGetDurationTool extends AbstractMetadataTool {

    public MetadataGetDurationTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing get duration query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query, 
            new String[] {"date", "place", "startTime", "endTime", "topics", "decisions", "summary", "president", "secretary"}
        );
        if (docs.isEmpty()) {
            log().debug("No documents found for get duration query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for get duration query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for get duration query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Extract durations in parallel
        List<DurationResult> results = extractDurationsInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().debug("No durations extracted for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank durations
        List<DurationResult> rankedResults = analyzeAndRankDurations(query, results);

        // Step 6: Perform statistical analysis
        DurationAnalysis analysis = performStatisticalAnalysis(rankedResults);

        // Step 7: Cluster similar durations
        List<InfoExtractor.Cluster<DurationResult>> clusters = clusterDurations(rankedResults);

        // Step 8: Generate enhanced final answer
        String answer = generateEnhancedDurationAnswer(query, rankedResults, analysis, clusters);
        log().debug("Generated get duration answer for query: {} with {} durations in {} clusters", 
                   query, results.size(), clusters.size());
        
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
                .filter(result -> result.durationMinutes > 0)
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
        
        // Calculate relevance score
        double relevanceScore = calculateRelevanceScore(query, 
            String.format("Duration: %d minutes", duration), minute.toString());
        
        // Extract key information about the meeting
        String keyInfo = extractKeyMeetingInfo(minute, query);
        
        return new DurationResult(
            minute.id(),
            minute.date(),
            minute.place(),
            minute.startTime(),
            minute.endTime(),
            duration,
            keyInfo,
            relevanceScore,
            System.currentTimeMillis()
        );
    }

    /**
     * Extracts key meeting information.
     */
    private String extractKeyMeetingInfo(Minute minute, String query) {
        if (minute == null || query == null || query.trim().isEmpty()) {
            return "";
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Meeting metadata (values may be in any language):
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            
            Extract the key information about this meeting that might be relevant to the query.
            Focus on facts, numbers, dates, names, or specific details.
            Write a brief summary (1-2 sentences) in the same language as the query.
            """,
            query,
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
                log().debug("Empty response from LLM in extractKeyMeetingInfo, returning empty string");
                return "";
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error extracting key meeting info, returning empty string", e);
            return "";
        }
    }

    /**
     * Analyzes and ranks durations by relevance and quality
     */
    private List<DurationResult> analyzeAndRankDurations(String query, List<DurationResult> results) {
        // Sort by relevance score (descending)
        return results.stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .collect(Collectors.toList());
    }

    /**
     * Performs statistical analysis on durations
     */
    private DurationAnalysis performStatisticalAnalysis(List<DurationResult> results) {
        if (results.isEmpty()) {
            return new DurationAnalysis(0, 0, 0, 0, 0, Collections.emptyList());
        }
        
        List<Integer> durations = results.stream()
                .mapToInt(r -> r.durationMinutes)
                .boxed()
                .collect(Collectors.toList());
        
        int min = Collections.min(durations);
        int max = Collections.max(durations);
        double average = durations.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        
        // Calculate median
        Collections.sort(durations);
        int median = durations.size() % 2 == 0 
            ? (durations.get(durations.size() / 2 - 1) + durations.get(durations.size() / 2)) / 2
            : durations.get(durations.size() / 2);
        
        // Calculate standard deviation
        double variance = durations.stream()
                .mapToDouble(d -> Math.pow(d - average, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);
        
        return new DurationAnalysis(min, max, average, median, standardDeviation, durations);
    }

    /**
     * Clusters similar durations to identify patterns
     */
    private List<InfoExtractor.Cluster<DurationResult>> clusterDurations(List<DurationResult> results) {
        return InfoExtractor.clusterItems(
            results,
            result -> String.format("Duration: %d minutes, Date: %s", result.durationMinutes, result.date),
            result -> result.date != null ? result.date : "unknown",
            0.3 // Similarity threshold for durations
        );
    }

    /**
     * Generates enhanced duration answer with analysis and clustering.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedDurationAnswer(String query, List<DurationResult> results, 
                                                 DurationAnalysis analysis, 
                                                 List<InfoExtractor.Cluster<DurationResult>> clusters) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String durationSummary = formatDurationSummary(results, clusters);
        String simpleStats = formatSimpleStats(analysis);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant meeting durations:
            
            %s
            
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "statistical analysis", "análisis estadístico", "clusters", "analysis", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, results.size(), 
            durationSummary != null ? durationSummary : "No duration information available.",
            simpleStats != null ? simpleStats : "");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedDurationAnswer, using fallback");
                return generateFallbackDurationAnswer(query, results, analysis);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced duration answer, using fallback", e);
            return generateFallbackDurationAnswer(query, results, analysis);
        }
    }
    
    /**
     * Generates a fallback duration answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackDurationAnswer(String query, List<DurationResult> results, DurationAnalysis analysis) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            StringBuilder answer = new StringBuilder();
            answer.append(String.format("Se encontraron %d reuniones con duraciones:\n", results.size()));
            if (analysis != null && analysis.allDurations.size() > 0) {
                answer.append(String.format("Duración mínima: %d minutos\n", analysis.minDuration));
                answer.append(String.format("Duración máxima: %d minutos\n", analysis.maxDuration));
                answer.append(String.format("Duración promedio: %.1f minutos\n", analysis.averageDuration));
            }
            answer.append("\nDuraciones encontradas:\n");
            results.stream().limit(5).forEach(r -> 
                answer.append(String.format("- %s: %d minutos (%s - %s)\n", 
                    r.date != null ? r.date : "fecha desconocida",
                    r.durationMinutes,
                    r.startTime != null ? r.startTime : "?",
                    r.endTime != null ? r.endTime : "?")));
            return answer.toString();
        } else {
            StringBuilder answer = new StringBuilder();
            answer.append(String.format("Found %d meetings with durations:\n", results.size()));
            if (analysis != null && analysis.allDurations.size() > 0) {
                answer.append(String.format("Minimum duration: %d minutes\n", analysis.minDuration));
                answer.append(String.format("Maximum duration: %d minutes\n", analysis.maxDuration));
                answer.append(String.format("Average duration: %.1f minutes\n", analysis.averageDuration));
            }
            answer.append("\nDurations found:\n");
            results.stream().limit(5).forEach(r -> 
                answer.append(String.format("- %s: %d minutes (%s - %s)\n", 
                    r.date != null ? r.date : "unknown date",
                    r.durationMinutes,
                    r.startTime != null ? r.startTime : "?",
                    r.endTime != null ? r.endTime : "?")));
            return answer.toString();
        }
    }

    /**
     * Formats duration summary for LLM prompt (without technical details)
     */
    private String formatDurationSummary(List<DurationResult> results, List<InfoExtractor.Cluster<DurationResult>> clusters) {
        StringBuilder summary = new StringBuilder();
        
        // Format durations naturally without mentioning clusters
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<DurationResult> cluster = clusters.get(i);
            DurationResult representative = cluster.getRepresentativeItem();
            
            if (representative.date != null) {
                summary.append(String.format("Reunión del %s", representative.date));
                if (representative.place != null) {
                    summary.append(String.format(" (%s)", representative.place));
                }
                summary.append(": ");
            }
            summary.append(String.format("%d minutos", representative.durationMinutes));
            if (representative.startTime != null && representative.endTime != null) {
                summary.append(String.format(" (%s - %s)", representative.startTime, representative.endTime));
            }
            summary.append("\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats simple statistics for LLM prompt (without technical terms)
     */
    private String formatSimpleStats(DurationAnalysis analysis) {
        if (analysis.allDurations.isEmpty()) {
            return "";
        }
        
        return String.format("""
            Resumen: Duración mínima: %d minutos, Duración máxima: %d minutos, Duración promedio: %.1f minutos
            """, 
            analysis.minDuration, analysis.maxDuration, analysis.averageDuration);
    }

    /**
     * Formats cluster analysis for LLM prompt
     */
    private String formatClusterAnalysis(List<InfoExtractor.Cluster<DurationResult>> clusters) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total clusters: %d\n", clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<DurationResult> cluster = clusters.get(i);
            DurationResult representative = cluster.getRepresentativeItem();
            
            double avgRelevance = cluster.getItems().stream()
                    .mapToDouble(r -> r.relevanceScore)
                    .average()
                    .orElse(0.0);
            
            int avgDuration = (int) cluster.getItems().stream()
                    .mapToInt(r -> r.durationMinutes)
                    .average()
                    .orElse(0.0);
            
            analysis.append(String.format("- Cluster %d: %d durations, avg duration: %d min, avg relevance: %.2f, date: %s\n", 
                                        i + 1, cluster.getSize(), avgDuration, avgRelevance, representative.date));
        }
        
        return analysis.toString();
    }

    /**
     * Represents a duration result with enhanced metadata
     */
    private static class DurationResult {
        final String minuteId;
        final String date;
        final String place;
        final String startTime;
        final String endTime;
        final int durationMinutes;
        final String keyInfo;
        final double relevanceScore;
        final long timestamp;

        DurationResult(String minuteId, String date, String place, String startTime, String endTime, 
                      int durationMinutes, String keyInfo, double relevanceScore, long timestamp) {
            this.minuteId = minuteId;
            this.date = date;
            this.place = place;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
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
            return String.format("DurationResult[%s, %d min, score=%.2f, age=%dms]", 
                               getIdentifier(), durationMinutes, relevanceScore, getAge());
        }
    }

    /**
     * Represents statistical analysis of durations
     */
    private static class DurationAnalysis {
        final int minDuration;
        final int maxDuration;
        final double averageDuration;
        final int medianDuration;
        final double standardDeviation;
        final List<Integer> allDurations;

        DurationAnalysis(int minDuration, int maxDuration, double averageDuration, 
                        int medianDuration, double standardDeviation, List<Integer> allDurations) {
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
            this.averageDuration = averageDuration;
            this.medianDuration = medianDuration;
            this.standardDeviation = standardDeviation;
            this.allDurations = allDurations;
        }
    }
}