package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataCountAndExplainTool for counting and explaining meeting minutes with intelligent analysis.
 * 
 * Features:
 * - Intelligent explanation generation with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Pattern analysis and clustering of explanations
 * - Quality ranking and synthesis of explanations
 * - Advanced NER-based filtering
 */
public class MetadataCountAndExplainTool extends AbstractMetadataTool {

    public MetadataCountAndExplainTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing count and explain query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query,
            new String[] {"date", "place", "topics", "decisions", "summary", "agenda"}
        );
        if (docs.isEmpty()) {
            log().debug("No documents found for count and explain query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for count and explain query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for count and explain query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Generate explanations in parallel
        List<Explanation> explanations = generateExplanationsInParallel(query, relevantMinutes);
        if (explanations.isEmpty()) {
            log().debug("No explanations generated for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 5: Analyze and rank explanations
        List<Explanation> rankedExplanations = analyzeAndRankExplanations(query, explanations);

        // Step 6: Cluster similar explanations
        List<ExplanationCluster> clusters = clusterExplanations(rankedExplanations);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedFinalAnswer(query, rankedExplanations, clusters);
        log().debug("Generated count and explain answer for query: {} with {} explanations in {} clusters", 
                   query, explanations.size(), clusters.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Cached extraction of minute objects
     */
    @Cacheable(value = "minuteObjects", key = "#doc.id")
    public Minute getMinuteFromMetadataCached(Document doc) {
        return getMinuteFromMetadata(doc);
    }

    /**
     * Cached NER matching evaluation
     */
    @Cacheable(value = "nerMatching", key = "#minute.hashCode() + '_' + #ner.hashCode()")
    public boolean matchesMinuteWithNERCached(Minute minute, JSONObject ner) {
        return matchesMinuteWithNER(minute, ner);
    }

    /**
     * Cached query relevance evaluation for explanation queries
     */
    @Cacheable(value = "explanationQueryRelevance", key = "#query.hashCode() + '_' + #minute.hashCode()")
    public boolean isRelevantToExplanationQueryCached(String query, Minute minute) {
        return isRelevantToExplanationQueryByLLM(query, minute);
    }

    /**
     * Determines if a minute is relevant to the explanation query using LLM.
     */
    private boolean isRelevantToExplanationQueryByLLM(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return false;
        }
        
        String prompt = generateExplanationRelevancePrompt(query, minute);
        String result = getLLMResponseCached(prompt);
        
        if (result == null || result.trim().isEmpty()) {
            log().warn("Empty response from LLM in isRelevantToExplanationQueryByLLM, defaulting to false");
            return false;
        }
        
        String normalized = result.toLowerCase();
        return normalized.contains("yes") || normalized.contains("sí");
    }

    /**
     * Generates adaptive relevance prompt for explanation queries
     */
    private String generateExplanationRelevancePrompt(String query, Minute minute) {
        return String.format("""
            Given the following explanation query (in any language):
            "%s"
            
            Meeting metadata:
            Date: %s
            Place: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            
            Does this meeting contain information that could help explain or provide context for the query?
            Consider that the query asks for explanations or detailed information.
            Answer only with YES or NO.
            """,
            query,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown"
        );
    }

    /**
     * Generates explanations in parallel
     */
    private List<Explanation> generateExplanationsInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<Explanation>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> generateExplanation(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(explanation -> !explanation.content.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Generates explanation for a minute with enhanced context.
     */
    private Explanation generateExplanation(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return null;
        }
        
        String prompt = generateEnhancedExplanationPrompt(query, minute);
        String content = getLLMResponseCached(prompt);
        
        if (content == null || content.trim().isEmpty()) {
            log().debug("Empty response from LLM in generateExplanation, returning null");
            return null;
        }
        
        // Calculate relevance score
        double relevanceScore = calculateRelevanceScore(query, content, minute.toString());
        
        return new Explanation(
            minute.id(),
            minute.date(),
            minute.place(),
            content,
            relevanceScore,
            System.currentTimeMillis()
        );
    }

    /**
     * Generates enhanced explanation prompt with context analysis
     */
    private String generateEnhancedExplanationPrompt(String query, Minute minute) {
        String queryType = analyzeQueryType(query);
        
        return String.format("""
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
            
            Write a clear, informative explanation of what was discussed/decided regarding the query topic.
            Focus on the most relevant aspects and provide specific details.
            Write in the same language as the query.
            """,
            query,
            queryType,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown"
        );
    }

    /**
     * Calculates relevance score for an explanation.
     * Uses English for internal processing, but preserves original language in query and explanation.
     */
    protected double calculateRelevanceScore(String query, String explanation, String context) {
        if (query == null || query.trim().isEmpty() || explanation == null || explanation.trim().isEmpty()) {
            return 0.5; // Default score
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And this explanation (may be in any language):
            "%s"
            
            Rate the relevance of this explanation to the query on a scale of 0.0 to 1.0.
            Consider: direct relevance, completeness, clarity, and usefulness.
            
            Respond with ONLY a number between 0.0 and 1.0.
            Do not include any explanation or additional text.
            """, query, explanation);
        
        try {
            String result = getLLMResponseCached(prompt);
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in calculateRelevanceScore, defaulting to 0.5");
                return 0.5;
            }
            
            String cleaned = result.strip();
            // Extract first number from response
            String numberStr = cleaned.replaceAll("[^0-9.]", "").split("\\s+")[0];
            if (numberStr.isEmpty()) {
                return 0.5;
            }
            
            return Double.parseDouble(numberStr);
        } catch (NumberFormatException e) {
            log().warn("Error parsing relevance score in calculateRelevanceScore, defaulting to 0.5", e);
            return 0.5; // Default score if parsing fails
        } catch (Exception e) {
            log().error("Error calculating relevance score, defaulting to 0.5", e);
            return 0.5;
        }
    }

    /**
     * Analyzes and ranks explanations by relevance and quality
     */
    private List<Explanation> analyzeAndRankExplanations(String query, List<Explanation> explanations) {
        // Sort by relevance score (descending)
        return explanations.stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .collect(Collectors.toList());
    }

    /**
     * Clusters similar explanations to avoid redundancy
     */
    private List<ExplanationCluster> clusterExplanations(List<Explanation> explanations) {
        List<ExplanationCluster> clusters = new ArrayList<>();
        
        for (Explanation explanation : explanations) {
            boolean addedToCluster = false;
            
            // Try to add to existing cluster
            for (ExplanationCluster cluster : clusters) {
                if (isSimilarToCluster(explanation, cluster)) {
                    cluster.addExplanation(explanation);
                    addedToCluster = true;
                    break;
                }
            }
            
            // Create new cluster if not similar to any existing one
            if (!addedToCluster) {
                clusters.add(new ExplanationCluster(explanation));
            }
        }
        
        return clusters;
    }

    /**
     * Checks if an explanation is similar to a cluster
     */
    private boolean isSimilarToCluster(Explanation explanation, ExplanationCluster cluster) {
        // Simple similarity check based on content overlap
        String explanationContent = explanation.content.toLowerCase();
        String clusterContent = cluster.getRepresentativeContent().toLowerCase();
        
        // Calculate simple word overlap
        Set<String> explanationWords = Set.of(explanationContent.split("\\s+"));
        Set<String> clusterWords = Set.of(clusterContent.split("\\s+"));
        
        long commonWords = explanationWords.stream()
                .filter(clusterWords::contains)
                .count();
        
        double similarity = (double) commonWords / Math.max(explanationWords.size(), clusterWords.size());
        
        return similarity > 0.3; // Threshold for similarity
    }

    /**
     * Generates enhanced final answer with clustering and analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedFinalAnswer(String query, List<Explanation> explanations, List<ExplanationCluster> clusters) {
        if (query == null || query.trim().isEmpty() || explanations == null || explanations.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String explanationSummary = formatExplanationSummary(explanations, clusters);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant meeting minutes:
            
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "clusters", "análisis", "analysis", "grouped into", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, explanations.size(), 
            explanationSummary != null ? explanationSummary : "No information found.");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedFinalAnswer, using fallback");
                return generateFallbackFinalAnswer(query, explanations);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced final answer, using fallback", e);
            return generateFallbackFinalAnswer(query, explanations);
        }
    }
    
    /**
     * Generates a fallback final answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackFinalAnswer(String query, List<Explanation> explanations) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Se encontraron %d acta(s) relevante(s).\n\nExplicaciones:\n%s",
                               explanations.size(),
                               explanations.stream()
                                       .limit(3)
                                       .map(e -> String.format("- %s: %s", 
                                           e.date != null ? e.date : "fecha desconocida",
                                           e.content.length() > 200 ? e.content.substring(0, 200) + "..." : e.content))
                                       .collect(Collectors.joining("\n\n")));
        } else {
            return String.format("Found %d relevant minute(s).\n\nExplanations:\n%s",
                               explanations.size(),
                               explanations.stream()
                                       .limit(3)
                                       .map(e -> String.format("- %s: %s", 
                                           e.date != null ? e.date : "unknown date",
                                           e.content.length() > 200 ? e.content.substring(0, 200) + "..." : e.content))
                                       .collect(Collectors.joining("\n\n")));
        }
    }

    /**
     * Formats explanation summary for LLM prompt (without technical details)
     */
    private String formatExplanationSummary(List<Explanation> explanations, List<ExplanationCluster> clusters) {
        StringBuilder summary = new StringBuilder();
        
        // Format explanations naturally without mentioning clusters
        for (int i = 0; i < clusters.size(); i++) {
            ExplanationCluster cluster = clusters.get(i);
            Explanation representative = cluster.getRepresentativeExplanation();
            
            if (representative.date != null) {
                summary.append(String.format("Reunión del %s:\n", representative.date));
            }
            summary.append(representative.content);
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats cluster analysis for LLM prompt
     */
    private String formatClusterAnalysis(List<ExplanationCluster> clusters) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total clusters: %d\n", clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            ExplanationCluster cluster = clusters.get(i);
            analysis.append(String.format("- Cluster %d: %d explanations, avg relevance: %.2f\n", 
                                        i + 1, cluster.getSize(), cluster.getAverageRelevance()));
        }
        
        return analysis.toString();
    }

    /**
     * Cached LLM response with error handling and validation.
     * Uses parent class implementation which includes error handling.
     */
    @Cacheable(value = "llmResponses", key = "#prompt.hashCode()")
    public String getLLMResponseCached(String prompt) {
        return super.getLLMResponseCached(prompt);
    }

    /**
     * Represents an explanation with metadata
     */
    private static class Explanation {
        final String minuteId;
        final String date;
        final String place;
        final String content;
        final double relevanceScore;
        final long timestamp;

        Explanation(String minuteId, String date, String place, String content, double relevanceScore, long timestamp) {
            this.minuteId = minuteId;
            this.date = date;
            this.place = place;
            this.content = content;
            this.relevanceScore = relevanceScore;
            this.timestamp = timestamp;
        }
        
        /**
         * Gets a formatted identifier for the explanation
         */
        String getIdentifier() {
            return String.format("%s (%s - %s)", minuteId, date != null ? date : "unknown", place != null ? place : "unknown");
        }
        
        /**
         * Gets the age of the explanation in milliseconds
         */
        long getAge() {
            return System.currentTimeMillis() - timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("Explanation[%s, score=%.2f, age=%dms]", getIdentifier(), relevanceScore, getAge());
        }
    }

    /**
     * Represents a cluster of similar explanations
     */
    private static class ExplanationCluster {
        private final List<Explanation> explanations = new ArrayList<>();

        ExplanationCluster(Explanation initialExplanation) {
            explanations.add(initialExplanation);
        }

        void addExplanation(Explanation explanation) {
            explanations.add(explanation);
        }

        int getSize() {
            return explanations.size();
        }

        Explanation getRepresentativeExplanation() {
            // Return the explanation with highest relevance score
            return explanations.stream()
                    .max((a, b) -> Double.compare(a.relevanceScore, b.relevanceScore))
                    .orElse(explanations.get(0));
        }

        String getRepresentativeContent() {
            return getRepresentativeExplanation().content;
        }

        double getAverageRelevance() {
            return explanations.stream()
                    .mapToDouble(e -> e.relevanceScore)
                    .average()
                    .orElse(0.0);
        }
    }
}
