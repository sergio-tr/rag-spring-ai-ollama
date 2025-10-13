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
 * Enhanced MetadataFindParagraphTool for finding relevant paragraphs in meeting minutes with intelligent analysis.
 * 
 * Features:
 * - Intelligent paragraph extraction with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Paragraph clustering and pattern analysis
 * - Quality ranking and synthesis of paragraphs
 * - Advanced NER-based filtering
 */
public class MetadataFindParagraphTool extends AbstractMetadataTool {

    public MetadataFindParagraphTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing find paragraph query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"}
        );
        if (docs.isEmpty()) {
            log().debug("No documents found for find paragraph query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for find paragraph query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for find paragraph query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Find relevant paragraphs in parallel
        List<ParagraphResult> results = findParagraphsInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().debug("No paragraphs found for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank paragraphs
        List<ParagraphResult> rankedResults = analyzeAndRankParagraphs(query, results);

        // Step 6: Cluster similar paragraphs
        List<InfoExtractor.Cluster<ParagraphResult>> clusters = clusterParagraphs(rankedResults);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedParagraphAnswer(query, rankedResults, clusters);
        log().debug("Generated find paragraph answer for query: {} with {} paragraphs in {} clusters", 
                   query, results.size(), clusters.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Finds relevant paragraphs in parallel
     */
    private List<ParagraphResult> findParagraphsInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<ParagraphResult>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> findRelevantParagraph(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> !result.paragraph.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Finds relevant paragraph for a minute with enhanced context
     */
    private ParagraphResult findRelevantParagraph(String query, Minute minute) {
        String paragraph = extractRelevantParagraph(query, minute);
        
        if (paragraph.isBlank()) {
            return null;
        }
        
        // Calculate relevance score
        double relevanceScore = calculateRelevanceScore(query, paragraph, minute.toString());
        
        // Extract key information from paragraph
        String keyInfo = extractKeyInformation(paragraph, query);
        
        return new ParagraphResult(
            minute.id(),
            minute.date(),
            minute.place(),
            paragraph,
            keyInfo,
            relevanceScore,
            System.currentTimeMillis()
        );
    }

    /**
     * Extracts relevant paragraph with enhanced context analysis
     */
    private String extractRelevantParagraph(String query, Minute minute) {
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
            
            Extract the most relevant paragraph or section that directly answers the query.
            Focus on the specific information requested, not general summaries.
            If no single paragraph is relevant, combine the most relevant information into a coherent paragraph.
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
            minute.summary() != null ? minute.summary() : "unknown",
            minute.agenda() != null ? minute.agenda().toString() : "unknown"
        );
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Extracts key information from paragraph
     */
    private String extractKeyInformation(String paragraph, String query) {
        String prompt = String.format("""
            Given the following paragraph:
            "%s"
            
            And the original query:
            "%s"
            
            Extract the key information that directly relates to the query.
            Focus on facts, numbers, dates, names, or specific details.
            Write a brief summary (1-2 sentences) in the same language as the query.
            """, paragraph, query);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Analyzes and ranks paragraphs by relevance and quality
     */
    private List<ParagraphResult> analyzeAndRankParagraphs(String query, List<ParagraphResult> results) {
        // Sort by relevance score (descending)
        return results.stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .collect(Collectors.toList());
    }

    /**
     * Clusters similar paragraphs to avoid redundancy
     */
    private List<InfoExtractor.Cluster<ParagraphResult>> clusterParagraphs(List<ParagraphResult> results) {
        return InfoExtractor.clusterItems(
            results,
            result -> result.paragraph,
            result -> result.date != null ? result.date : "unknown",
            0.4 // Similarity threshold for paragraphs
        );
    }

    /**
     * Generates enhanced paragraph answer with clustering and analysis
     */
    private String generateEnhancedParagraphAnswer(String query, List<ParagraphResult> results, 
                                                  List<InfoExtractor.Cluster<ParagraphResult>> clusters) {
        String paragraphSummary = formatParagraphSummary(results, clusters);
        String clusterAnalysis = formatClusterAnalysis(clusters);
        
        String prompt = String.format("""
            Given the following find paragraph query (in any language):
            "%s"
            
            Found %d relevant paragraphs grouped into %d clusters:
            
            %s
            
            Cluster analysis:
            %s
            
            Write a clear, comprehensive answer in the same language as the query, 
            presenting the most relevant information from the paragraphs found.
            Group similar information together and highlight the most important findings.
            """, query, results.size(), clusters.size(), paragraphSummary, clusterAnalysis);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Formats paragraph summary for LLM prompt
     */
    private String formatParagraphSummary(List<ParagraphResult> results, List<InfoExtractor.Cluster<ParagraphResult>> clusters) {
        StringBuilder summary = new StringBuilder();
        
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<ParagraphResult> cluster = clusters.get(i);
            ParagraphResult representative = cluster.getRepresentativeItem();
            
            summary.append(String.format("Cluster %d (%d paragraphs) - Date: %s\n", 
                                        i + 1, cluster.getSize(), representative.date));
            summary.append(String.format("Place: %s\n", representative.place));
            summary.append(String.format("Key Info: %s\n", representative.keyInfo));
            summary.append(String.format("Paragraph: %s\n", representative.paragraph));
            summary.append("\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats cluster analysis for LLM prompt
     */
    private String formatClusterAnalysis(List<InfoExtractor.Cluster<ParagraphResult>> clusters) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total clusters: %d\n", clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<ParagraphResult> cluster = clusters.get(i);
            ParagraphResult representative = cluster.getRepresentativeItem();
            
            double avgRelevance = cluster.getItems().stream()
                    .mapToDouble(r -> r.relevanceScore)
                    .average()
                    .orElse(0.0);
            
            analysis.append(String.format("- Cluster %d: %d paragraphs, avg relevance: %.2f, date: %s\n", 
                                        i + 1, cluster.getSize(), avgRelevance, representative.date));
        }
        
        return analysis.toString();
    }

    /**
     * Represents a paragraph result with enhanced metadata
     */
    private static class ParagraphResult {
        final String minuteId;
        final String date;
        final String place;
        final String paragraph;
        final String keyInfo;
        final double relevanceScore;
        final long timestamp;

        ParagraphResult(String minuteId, String date, String place, String paragraph, String keyInfo, 
                       double relevanceScore, long timestamp) {
            this.minuteId = minuteId;
            this.date = date;
            this.place = place;
            this.paragraph = paragraph;
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
            return String.format("ParagraphResult[%s, score=%.2f, age=%dms]", getIdentifier(), relevanceScore, getAge());
        }
    }
}
