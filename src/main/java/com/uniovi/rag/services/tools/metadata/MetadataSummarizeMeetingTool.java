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
 * Enhanced MetadataSummarizeMeetingTool for summarizing meeting minutes with intelligent analysis.
 * 
 * Features:
 * - Intelligent meeting summarization with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Summary clustering and pattern analysis
 * - Quality ranking and synthesis of summaries
 * - Advanced NER-based filtering
 */
public class MetadataSummarizeMeetingTool extends AbstractMetadataTool {

    public MetadataSummarizeMeetingTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing summarize meeting query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"}
        );
        if (docs.isEmpty()) {
            log().debug("No documents found for summarize meeting query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for summarize meeting query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for summarize meeting query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Generate summaries in parallel
        List<SummaryResult> results = generateSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().debug("No summaries generated for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank summaries
        List<SummaryResult> rankedResults = analyzeAndRankSummaries(query, results);

        // Step 6: Cluster similar summaries
        List<InfoExtractor.Cluster<SummaryResult>> clusters = clusterSummaries(rankedResults);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedSummaryAnswer(query, rankedResults, clusters);
        log().debug("Generated summarize meeting answer for query: {} with {} summaries in {} clusters", 
                   query, results.size(), clusters.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Generates summaries in parallel
     */
    private List<SummaryResult> generateSummariesInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<SummaryResult>> futures = minutes.stream()
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
    private SummaryResult generateSummary(String query, Minute minute) {
        String summary = extractOrGenerateSummary(query, minute);
        
        if (summary.isBlank()) {
            return null;
        }
        
        // Calculate relevance score
        double relevanceScore = calculateRelevanceScore(query, summary, minute.toString());
        
        // Extract key information from summary
        String keyInfo = extractKeyInformation(summary, query);
        
        return new SummaryResult(
            minute.id(),
            minute.date(),
            minute.place(),
            summary,
            keyInfo,
            relevanceScore,
            System.currentTimeMillis()
        );
    }

    /**
     * Extracts or generates summary with enhanced context analysis
     */
    private String extractOrGenerateSummary(String query, Minute minute) {
        String queryType = analyzeQueryType(query);
        
        // If minute already has a summary, use it as base
        String baseSummary = minute.summary() != null ? minute.summary() : "";
        
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
            Agenda: %s
            
            Existing summary: %s
            
            Generate a comprehensive summary that directly addresses the query.
            Focus on the specific information requested, not general summaries.
            If the existing summary is relevant, enhance it; otherwise, create a new one.
            Write the response in the same language as the query.
            """,
            query,
            queryType,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.agenda() != null ? minute.agenda().toString() : "unknown",
            baseSummary
        );
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Extracts key information from summary
     */
    private String extractKeyInformation(String summary, String query) {
        String prompt = String.format("""
            Given the following summary:
            "%s"
            
            And the original query:
            "%s"
            
            Extract the key information that directly relates to the query.
            Focus on facts, numbers, dates, names, or specific details.
            Write a brief summary (1-2 sentences) in the same language as the query.
            """, summary, query);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Analyzes and ranks summaries by relevance and quality
     */
    private List<SummaryResult> analyzeAndRankSummaries(String query, List<SummaryResult> results) {
        // Sort by relevance score (descending)
        return results.stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .collect(Collectors.toList());
    }

    /**
     * Clusters similar summaries to avoid redundancy
     */
    private List<InfoExtractor.Cluster<SummaryResult>> clusterSummaries(List<SummaryResult> results) {
        return InfoExtractor.clusterItems(
            results,
            result -> result.summary,
            result -> result.date != null ? result.date : "unknown",
            0.5 // Similarity threshold for summaries
        );
    }

    /**
     * Generates enhanced summary answer with clustering and analysis
     */
    private String generateEnhancedSummaryAnswer(String query, List<SummaryResult> results, 
                                                List<InfoExtractor.Cluster<SummaryResult>> clusters) {
        String summarySummary = formatSummarySummary(results, clusters);
        String clusterAnalysis = formatClusterAnalysis(clusters);
        
        String prompt = String.format("""
            Given the following summarize meeting query (in any language):
            "%s"
            
            Found %d relevant meeting summaries grouped into %d clusters:
            
            %s
            
            Cluster analysis:
            %s
            
            Write a clear, comprehensive summary in the same language as the query, 
            presenting the most relevant information from the summaries found.
            Group similar information together and highlight the most important findings.
            """, query, results.size(), clusters.size(), summarySummary, clusterAnalysis);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Formats summary summary for LLM prompt
     */
    private String formatSummarySummary(List<SummaryResult> results, List<InfoExtractor.Cluster<SummaryResult>> clusters) {
        StringBuilder summary = new StringBuilder();
        
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<SummaryResult> cluster = clusters.get(i);
            SummaryResult representative = cluster.getRepresentativeItem();
            
            summary.append(String.format("Cluster %d (%d summaries) - Date: %s\n", 
                                        i + 1, cluster.getSize(), representative.date));
            summary.append(String.format("Place: %s\n", representative.place));
            summary.append(String.format("Key Info: %s\n", representative.keyInfo));
            summary.append(String.format("Summary: %s\n", representative.summary));
            summary.append("\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats cluster analysis for LLM prompt
     */
    private String formatClusterAnalysis(List<InfoExtractor.Cluster<SummaryResult>> clusters) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total clusters: %d\n", clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<SummaryResult> cluster = clusters.get(i);
            SummaryResult representative = cluster.getRepresentativeItem();
            
            double avgRelevance = cluster.getItems().stream()
                    .mapToDouble(r -> r.relevanceScore)
                    .average()
                    .orElse(0.0);
            
            analysis.append(String.format("- Cluster %d: %d summaries, avg relevance: %.2f, date: %s\n", 
                                        i + 1, cluster.getSize(), avgRelevance, representative.date));
        }
        
        return analysis.toString();
    }

    /**
     * Represents a summary result with enhanced metadata
     */
    private static class SummaryResult {
        final String minuteId;
        final String date;
        final String place;
        final String summary;
        final String keyInfo;
        final double relevanceScore;
        final long timestamp;

        SummaryResult(String minuteId, String date, String place, String summary, String keyInfo, 
                     double relevanceScore, long timestamp) {
            this.minuteId = minuteId;
            this.date = date;
            this.place = place;
            this.summary = summary;
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
            return String.format("SummaryResult[%s, score=%.2f, age=%dms]", getIdentifier(), relevanceScore, getAge());
        }
    }
}
