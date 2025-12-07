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
 * Enhanced MetadataSummarizeTopicTool for summarizing specific topics from meeting minutes with intelligent analysis.
 * 
 * Features:
 * - Intelligent topic summarization with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Topic clustering and pattern analysis
 * - Quality ranking and synthesis of topic fragments
 * - Advanced NER-based filtering
 */
public class MetadataSummarizeTopicTool extends AbstractMetadataTool {

    public MetadataSummarizeTopicTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing summarize topic query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"}
        );
        
        if (docs.isEmpty()) {
            log().debug("No documents found with metadata filter, trying basic retrieval");
            docs = retrieveDocuments(query);
        }
        
        if (docs.isEmpty()) {
            log().debug("No documents found for summarize topic query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for summarize topic query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for summarize topic query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Generate topic summaries in parallel
        List<TopicResult> results = generateTopicSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().debug("No topic summaries generated for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank topic summaries
        List<TopicResult> rankedResults = analyzeAndRankTopicSummaries(query, results);

        // Step 6: Cluster similar topic summaries
        List<InfoExtractor.Cluster<TopicResult>> clusters = clusterTopicSummaries(rankedResults);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedTopicSummaryAnswer(query, rankedResults, clusters);
        log().debug("Generated summarize topic answer for query: {} with {} topic summaries in {} clusters", 
                   query, results.size(), clusters.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Generates topic summaries in parallel
     */
    private List<TopicResult> generateTopicSummariesInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<TopicResult>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> generateTopicSummary(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> !result.topicSummary.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Generates topic summary for a minute with enhanced context
     */
    private TopicResult generateTopicSummary(String query, Minute minute) {
        String topicSummary = extractOrGenerateTopicSummary(query, minute);
        
        if (topicSummary.isBlank()) {
            return null;
        }
        
        // Calculate relevance score
        double relevanceScore = calculateRelevanceScore(query, topicSummary, minute.toString());
        
        // Extract key information from topic summary
        String keyInfo = extractKeyInformation(topicSummary, query);
        
        // Extract relevant topics
        List<String> relevantTopics = extractRelevantTopics(minute, query);
        
        return new TopicResult(
            minute.id(),
            minute.date(),
            minute.place(),
            topicSummary,
            keyInfo,
            relevantTopics,
            relevanceScore,
            System.currentTimeMillis()
        );
    }

    /**
     * Extracts or generates topic summary with enhanced context analysis.
     */
    private String extractOrGenerateTopicSummary(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }
        
        String queryType = analyzeQueryType(query);
        
        // Check if minute has topics
        if (minute.topics() == null || minute.topics().isEmpty()) {
            return "";
        }
        
        // Use existing summary as base if available
        String baseSummary = minute.summary() != null ? minute.summary() : "";
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            Meeting metadata (values may be in any language):
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Agenda: %s
            
            Existing summary: %s
            
            Generate a comprehensive summary focused on the specific topic(s) mentioned in the query.
            Extract and synthesize information related to the topic from the meeting data.
            Focus on the specific information requested, not general summaries.
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
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().debug("Empty response from LLM in extractOrGenerateTopicSummary, returning empty string");
                return "";
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error extracting or generating topic summary, returning empty string", e);
            return "";
        }
    }

    /**
     * Extracts relevant topics from minute based on query.
     * Uses English for internal processing, but preserves original language in query and topics.
     */
    private List<String> extractRelevantTopics(Minute minute, String query) {
        if (minute == null || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        if (minute.topics() == null || minute.topics().isEmpty()) {
            return Collections.emptyList();
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And these meeting topics (may be in any language):
            %s
            
            Which topics are most relevant to the query?
            Return only the topic names that are directly related to the query.
            Separate multiple topics with commas.
            """,
            query,
            String.join(", ", minute.topics())
        );
        
        try {
            String result = getLLMResponseCached(prompt);
            
            if (result == null || result.trim().isEmpty()) {
                log().debug("Empty response from LLM in extractRelevantTopics, returning empty list");
                return Collections.emptyList();
            }
            
            return Arrays.stream(result.split(","))
                    .map(String::trim)
                    .filter(topic -> !topic.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log().error("Error extracting relevant topics, returning empty list", e);
            return Collections.emptyList();
        }
    }

    /**
     * Extracts key information from topic summary.
     * Uses English for internal processing, but preserves original language in query and summary.
     */
    private String extractKeyInformation(String topicSummary, String query) {
        if (topicSummary == null || topicSummary.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return "";
        }
        
        String prompt = String.format("""
            Given the following topic summary (may be in any language):
            "%s"
            
            And the original query (may be in any language):
            "%s"
            
            Extract the key information that directly relates to the query.
            Focus on facts, numbers, dates, names, or specific details about the topic.
            Write a brief summary (1-2 sentences) in the same language as the query.
            """, topicSummary, query);
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().debug("Empty response from LLM in extractKeyInformation, returning empty string");
                return "";
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error extracting key information, returning empty string", e);
            return "";
        }
    }

    /**
     * Analyzes and ranks topic summaries by relevance and quality
     */
    private List<TopicResult> analyzeAndRankTopicSummaries(String query, List<TopicResult> results) {
        // Sort by relevance score (descending)
        return results.stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .collect(Collectors.toList());
    }

    /**
     * Clusters similar topic summaries to avoid redundancy
     */
    private List<InfoExtractor.Cluster<TopicResult>> clusterTopicSummaries(List<TopicResult> results) {
        return InfoExtractor.clusterItems(
            results,
            result -> result.topicSummary,
            result -> result.date != null ? result.date : "unknown",
            0.6 // Similarity threshold for topic summaries
        );
    }

    /**
     * Generates enhanced topic summary answer with clustering and analysis
     */
    /**
     * Generates enhanced topic summary answer.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedTopicSummaryAnswer(String query, List<TopicResult> results, 
                                                     List<InfoExtractor.Cluster<TopicResult>> clusters) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String topicSummarySummary = formatTopicSummarySummary(results, clusters);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant topic summaries:
            
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "clusters", "análisis", "analysis", "grouped into", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, results.size(), 
            topicSummarySummary != null ? topicSummarySummary : "No topic summaries available.");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedTopicSummaryAnswer, using fallback");
                return generateFallbackTopicSummary(query, results);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced topic summary answer, using fallback", e);
            return generateFallbackTopicSummary(query, results);
        }
    }
    
    /**
     * Generates a fallback topic summary when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackTopicSummary(String query, List<TopicResult> results) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            StringBuilder answer = new StringBuilder();
            answer.append(String.format("Resumen del tema basado en %d reuniones:\n\n", results.size()));
            results.stream().limit(3).forEach(r -> {
                answer.append(String.format("Reunión del %s:\n", r.date != null ? r.date : "fecha desconocida"));
                answer.append(String.format("%s\n\n", r.topicSummary != null && !r.topicSummary.isBlank() ? 
                    r.topicSummary.substring(0, Math.min(200, r.topicSummary.length())) : "Sin resumen disponible"));
            });
            return answer.toString();
        } else {
            StringBuilder answer = new StringBuilder();
            answer.append(String.format("Topic summary based on %d meetings:\n\n", results.size()));
            results.stream().limit(3).forEach(r -> {
                answer.append(String.format("Meeting on %s:\n", r.date != null ? r.date : "unknown date"));
                answer.append(String.format("%s\n\n", r.topicSummary != null && !r.topicSummary.isBlank() ? 
                    r.topicSummary.substring(0, Math.min(200, r.topicSummary.length())) : "No summary available"));
            });
            return answer.toString();
        }
    }

    /**
     * Formats topic summary summary for LLM prompt (without technical details)
     */
    private String formatTopicSummarySummary(List<TopicResult> results, List<InfoExtractor.Cluster<TopicResult>> clusters) {
        StringBuilder summary = new StringBuilder();
        
        // Format topic summaries naturally without mentioning clusters
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<TopicResult> cluster = clusters.get(i);
            TopicResult representative = cluster.getRepresentativeItem();
            
            if (representative.date != null) {
                summary.append(String.format("Reunión del %s", representative.date));
                if (representative.place != null) {
                    summary.append(String.format(" (%s)", representative.place));
                }
                summary.append(":\n");
            }
            summary.append(representative.topicSummary);
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats cluster analysis for LLM prompt
     */
    private String formatClusterAnalysis(List<InfoExtractor.Cluster<TopicResult>> clusters) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total clusters: %d\n", clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<TopicResult> cluster = clusters.get(i);
            TopicResult representative = cluster.getRepresentativeItem();
            
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
     * Represents a topic result with enhanced metadata
     */
    private static class TopicResult {
        final String minuteId;
        final String date;
        final String place;
        final String topicSummary;
        final String keyInfo;
        final List<String> relevantTopics;
        final double relevanceScore;
        final long timestamp;

        TopicResult(String minuteId, String date, String place, String topicSummary, String keyInfo, 
                   List<String> relevantTopics, double relevanceScore, long timestamp) {
            this.minuteId = minuteId;
            this.date = date;
            this.place = place;
            this.topicSummary = topicSummary;
            this.keyInfo = keyInfo;
            this.relevantTopics = relevantTopics;
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
            return String.format("TopicResult[%s, topics=%s, score=%.2f, age=%dms]", 
                               getIdentifier(), relevantTopics.size(), relevanceScore, getAge());
        }
    }
}
