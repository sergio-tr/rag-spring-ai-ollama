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
 * Enhanced MetadataFilterAndListTool for filtering and listing meeting minutes with intelligent analysis.
 * 
 * Features:
 * - Intelligent filtering with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Result clustering and pattern analysis
 * - Quality ranking and synthesis of results
 * - Advanced NER-based filtering
 */
public class MetadataFilterAndListTool extends AbstractMetadataTool {

    public MetadataFilterAndListTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing filter and list query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"}
        );
        if (docs.isEmpty()) {
            log().debug("No documents found for filter and list query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for filter and list query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for filter and list query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Generate summaries in parallel
        List<FilterResult> results = generateSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().debug("No summaries generated for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank results
        List<FilterResult> rankedResults = analyzeAndRankResults(query, results);

        // Step 6: Cluster similar results
        List<InfoExtractor.Cluster<FilterResult>> clusters = clusterResults(rankedResults);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedFilterAnswer(query, rankedResults, clusters);
        log().debug("Generated filter and list answer for query: {} with {} results in {} clusters", 
                   query, results.size(), clusters.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Generates summaries in parallel
     */
    private List<FilterResult> generateSummariesInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<FilterResult>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> generateSummary(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> !result.summary.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Generates summary for a minute with enhanced context
     */
    private FilterResult generateSummary(String query, Minute minute) {
        String summary = buildSummaryExplanation(query, minute);
        
        if (summary.isBlank()) {
            return null;
        }
        
        // Calculate relevance score
        double relevanceScore = calculateRelevanceScore(query, summary, minute.toString());
        
        return new FilterResult(
            minute.id(),
            minute.date(),
            minute.place(),
            summary,
            relevanceScore,
            System.currentTimeMillis()
        );
    }

    /**
     * Builds summary explanation with enhanced context
     */
    private String buildSummaryExplanation(String query, Minute minute) {
        String queryType = analyzeQueryType(query);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            Meeting metadata:
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            Agenda: %s
            
            Write a brief summary (2-3 sentences) in the same language as the query,
            focusing on the aspects most relevant to the query.
            Highlight the most important information that directly relates to the query.
            """,
            query,
            queryType,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown",
            minute.agenda() != null ? minute.agenda().toString() : "unknown"
        );
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Analyzes and ranks results by relevance and quality
     */
    private List<FilterResult> analyzeAndRankResults(String query, List<FilterResult> results) {
        // Sort by relevance score (descending)
        return results.stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .collect(Collectors.toList());
    }

    /**
     * Clusters similar results to avoid redundancy
     */
    private List<InfoExtractor.Cluster<FilterResult>> clusterResults(List<FilterResult> results) {
        return InfoExtractor.clusterItems(
            results,
            result -> result.summary,
            result -> result.date != null ? result.date : "unknown",
            0.3 // Similarity threshold
        );
    }

    /**
     * Generates enhanced filter answer with clustering and analysis
     */
    private String generateEnhancedFilterAnswer(String query, List<FilterResult> results, 
                                               List<InfoExtractor.Cluster<FilterResult>> clusters) {
        String resultSummary = formatResultSummary(results, clusters);
        String clusterAnalysis = formatClusterAnalysis(clusters);
        
        String prompt = String.format("""
            Given the following filter and list query (in any language):
            "%s"
            
            Found %d relevant meeting minutes grouped into %d clusters:
            
            %s
            
            Cluster analysis:
            %s
            
            Write a clear, comprehensive answer in the same language as the query, 
            listing the minutes and summarizing the relevant content for each.
            Group similar information together and highlight the most important findings.
            """, query, results.size(), clusters.size(), resultSummary, clusterAnalysis);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Formats result summary for LLM prompt
     */
    private String formatResultSummary(List<FilterResult> results, List<InfoExtractor.Cluster<FilterResult>> clusters) {
        StringBuilder summary = new StringBuilder();
        
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<FilterResult> cluster = clusters.get(i);
            FilterResult representative = cluster.getRepresentativeItem();
            
            summary.append(String.format("Cluster %d (%d results) - Date: %s\n", 
                                        i + 1, cluster.getSize(), representative.date));
            summary.append(String.format("Place: %s\n", representative.place));
            summary.append(representative.summary);
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats cluster analysis for LLM prompt
     */
    private String formatClusterAnalysis(List<InfoExtractor.Cluster<FilterResult>> clusters) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total clusters: %d\n", clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<FilterResult> cluster = clusters.get(i);
            FilterResult representative = cluster.getRepresentativeItem();
            
            double avgRelevance = cluster.getItems().stream()
                    .mapToDouble(r -> r.relevanceScore)
                    .average()
                    .orElse(0.0);
            
            analysis.append(String.format("- Cluster %d: %d results, avg relevance: %.2f, date: %s\n", 
                                        i + 1, cluster.getSize(), avgRelevance, representative.date));
        }
        
        return analysis.toString();
    }

    /**
     * Represents a filter result with enhanced metadata
     */
    private static class FilterResult {
        final String minuteId;
        final String date;
        final String place;
        final String summary;
        final double relevanceScore;
        final long timestamp;

        FilterResult(String minuteId, String date, String place, String summary, double relevanceScore, long timestamp) {
            this.minuteId = minuteId;
            this.date = date;
            this.place = place;
            this.summary = summary;
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
            return String.format("FilterResult[%s, score=%.2f, age=%dms]", getIdentifier(), relevanceScore, getAge());
        }
    }
}
