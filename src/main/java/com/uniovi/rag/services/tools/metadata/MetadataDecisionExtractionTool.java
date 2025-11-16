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
 * Enhanced MetadataDecisionExtractionTool for extracting and analyzing meeting decisions with intelligent processing.
 * 
 * Features:
 * - Intelligent decision extraction with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Decision clustering and pattern analysis
 * - Quality ranking and synthesis of decisions
 * - Advanced NER-based filtering
 */
public class MetadataDecisionExtractionTool extends AbstractMetadataTool {

    public MetadataDecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing decision extraction query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query,
            new String[] {"date", "place", "topics", "decisions", "summary"}
        );
        if (docs.isEmpty()) {
            log().debug("No documents found for decision extraction query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for decision extraction query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for decision extraction query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Extract decisions in parallel
        List<Decision> decisions = extractDecisionsInParallel(query, relevantMinutes);
        if (decisions.isEmpty()) {
            log().debug("No relevant decisions found for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank decisions
        List<Decision> rankedDecisions = analyzeAndRankDecisions(query, decisions);

        // Step 6: Cluster similar decisions
        List<DecisionCluster> clusters = clusterDecisions(rankedDecisions);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedDecisionAnswer(query, rankedDecisions, clusters);
        log().debug("Generated decision extraction answer for query: {} with {} decisions in {} clusters", 
                   query, decisions.size(), clusters.size());
        
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
     * Cached query relevance evaluation for decision queries
     */
    @Cacheable(value = "decisionQueryRelevance", key = "#query.hashCode() + '_' + #minute.hashCode()")
    public boolean isRelevantToDecisionQueryCached(String query, Minute minute) {
        return isRelevantToDecisionQueryByLLM(query, minute);
    }

    /**
     * Determines if a minute is relevant to the decision query using LLM
     */
    private boolean isRelevantToDecisionQueryByLLM(String query, Minute minute) {
        String prompt = generateDecisionRelevancePrompt(query, minute);
        String result = getLLMResponseCached(prompt);
        return result.toLowerCase().contains("yes") || result.toLowerCase().contains("sí");
    }

    /**
     * Generates adaptive relevance prompt for decision queries
     */
    private String generateDecisionRelevancePrompt(String query, Minute minute) {
        return String.format("""
            Given the following decision extraction query (in any language):
            "%s"
            
            Meeting metadata:
            Date: %s
            Place: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            
            Does this meeting contain decisions that could be relevant to the query?
            Consider that the query asks for extracting specific decisions or decision patterns.
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
     * Extracts decisions in parallel
     */
    private List<Decision> extractDecisionsInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<List<Decision>>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractDecisionsFromMinute(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Extracts decisions from a single minute
     */
    private List<Decision> extractDecisionsFromMinute(String query, Minute minute) {
        if (minute.decisions() == null || minute.decisions().isEmpty()) {
            return Collections.emptyList();
        }

        List<Decision> decisions = new ArrayList<>();
        for (String decisionText : minute.decisions()) {
            if (isDecisionRelevantToQueryCached(decisionText, query)) {
                Decision decision = buildDecisionWithContext(minute, decisionText, query);
                if (decision != null) {
                    decisions.add(decision);
                }
            }
        }
        
        return decisions;
    }

    /**
     * Cached decision relevance evaluation
     */
    @Cacheable(value = "decisionRelevance", key = "#decisionText.hashCode() + '_' + #query.hashCode()")
    public boolean isDecisionRelevantToQueryCached(String decisionText, String query) {
        return isDecisionRelevantToQueryByLLM(decisionText, query);
    }

    /**
     * Determines if a decision is relevant to the query using LLM.
     * Uses English for internal processing, but preserves original language in query and decision text.
     */
    private boolean isDecisionRelevantToQueryByLLM(String decisionText, String query) {
        if (decisionText == null || decisionText.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String prompt = generateDecisionRelevancePrompt(decisionText, query);
        String result = getLLMResponseCached(prompt);
        
        if (result == null || result.trim().isEmpty()) {
            log().warn("Empty response from LLM in isDecisionRelevantToQueryByLLM, defaulting to false");
            return false;
        }
        
        String normalized = result.toLowerCase();
        return normalized.contains("yes") || normalized.contains("sí");
    }

    /**
     * Generates decision relevance prompt
     */
    private String generateDecisionRelevancePrompt(String decisionText, String query) {
        return String.format("""
        Given the following user query (in any language):
            "%s"
            
        And the following decision from a meeting minute:
            "%s"
            
            Does this decision answer or relate to the query?
            Consider the context and intent of the query.
            Answer only with YES or NO.
            """, query, decisionText);
    }

    /**
     * Builds a decision with enhanced context
     */
    private Decision buildDecisionWithContext(Minute minute, String decisionText, String query) {
        // Calculate relevance score
        double relevanceScore = calculateDecisionRelevanceScore(query, minute, decisionText);
        
        // Extract decision type
        String decisionType = analyzeDecisionType(decisionText);
        
        // Extract key entities
        List<String> keyEntities = extractKeyEntitiesFromDecision(decisionText);
        
        return new Decision(
            minute.id(),
            minute.date(),
            minute.place(),
            decisionText,
            decisionType,
            keyEntities,
            relevanceScore,
            System.currentTimeMillis()
        );
    }

    /**
     * Calculates relevance score for a decision
     */
    private double calculateDecisionRelevanceScore(String query, Minute minute, String decisionText) {
        String prompt = String.format("""
        Given the following user query (in any language):
            "%s"
            
            Meeting context:
            Date: %s
            Place: %s
            Topics: %s
            
            Decision:
            "%s"
            
            Rate the relevance of this decision to the query on a scale of 0.0 to 1.0.
            Consider: direct relevance, completeness, clarity, and usefulness.
            Respond with only a number between 0.0 and 1.0.
            """, 
            query,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            decisionText
        );
        
        try {
            String result = getLLMResponseCached(prompt).strip();
            return Double.parseDouble(result);
        } catch (NumberFormatException e) {
            return 0.5; // Default score if parsing fails
        }
    }

    /**
     * Analyzes decision type
     */
    private String analyzeDecisionType(String decisionText) {
        String prompt = String.format("""
            Given the following decision from a meeting minute:
            "%s"
            
            Classify this decision into one of these types:
            - APPROVAL: Approval of proposals, budgets, or plans
            - REJECTION: Rejection of proposals or requests
            - ASSIGNMENT: Assignment of tasks or responsibilities
            - SCHEDULING: Scheduling of events or deadlines
            - POLICY: Policy changes or new regulations
            - FINANCIAL: Financial decisions or budget allocations
            - PERSONNEL: Personnel decisions or appointments
            - OTHER: Other types of decisions
            
            Respond with only the type name.
            """, decisionText);
        
        return getLLMResponseCached(prompt).strip();
    }

    /**
     * Extracts key entities from decision text
     */
    private List<String> extractKeyEntitiesFromDecision(String decisionText) {
        String prompt = String.format("""
            Given the following decision from a meeting minute:
            "%s"
            
            Extract the key entities mentioned in this decision (people, organizations, dates, amounts, etc.).
            Return them as a comma-separated list.
            """, decisionText);
        
        String result = getLLMResponseCached(prompt).strip();
        return Arrays.stream(result.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Analyzes and ranks decisions by relevance and quality
     */
    private List<Decision> analyzeAndRankDecisions(String query, List<Decision> decisions) {
        // Sort by relevance score (descending)
        return decisions.stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .collect(Collectors.toList());
    }

    /**
     * Clusters similar decisions to avoid redundancy
     */
    private List<DecisionCluster> clusterDecisions(List<Decision> decisions) {
        List<DecisionCluster> clusters = new ArrayList<>();
        
        for (Decision decision : decisions) {
            boolean addedToCluster = false;
            
            // Try to add to existing cluster
            for (DecisionCluster cluster : clusters) {
                if (isSimilarToCluster(decision, cluster)) {
                    cluster.addDecision(decision);
                    addedToCluster = true;
                    break;
                }
            }
            
            // Create new cluster if not similar to any existing one
            if (!addedToCluster) {
                clusters.add(new DecisionCluster(decision));
            }
        }
        
        return clusters;
    }

    /**
     * Checks if a decision is similar to a cluster
     */
    private boolean isSimilarToCluster(Decision decision, DecisionCluster cluster) {
        // Check if decision types match
        if (!decision.decisionType.equals(cluster.getRepresentativeDecision().decisionType)) {
            return false;
        }
        
        // Check content similarity
        String decisionContent = decision.decisionText.toLowerCase();
        String clusterContent = cluster.getRepresentativeContent().toLowerCase();
        
        Set<String> decisionWords = Set.of(decisionContent.split("\\s+"));
        Set<String> clusterWords = Set.of(clusterContent.split("\\s+"));
        
        long commonWords = decisionWords.stream()
                .filter(clusterWords::contains)
                .count();
        
        double similarity = (double) commonWords / Math.max(decisionWords.size(), clusterWords.size());
        
        return similarity > 0.4; // Threshold for decision similarity
    }

    /**
     * Generates enhanced decision answer with clustering and analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedDecisionAnswer(String query, List<Decision> decisions, List<DecisionCluster> clusters) {
        if (query == null || query.trim().isEmpty() || decisions == null || decisions.isEmpty()) {
            return generateNoDataMessage(query);
        }
        
        String decisionSummary = formatDecisionSummary(decisions, clusters);
        String clusterAnalysis = formatClusterAnalysis(clusters);
        
        String prompt = String.format("""
            Given the following decision extraction query (in any language):
            "%s"
            
            Found %d relevant decisions grouped into %d clusters:
            
            %s
            
            Cluster analysis:
            %s
            
            Write a clear, comprehensive answer in the same language as the query, 
            summarizing the relevant decisions and their context.
            Group similar decisions together and highlight the most important findings.
            """, query, decisions.size(), clusters.size(), 
            decisionSummary != null ? decisionSummary : "No decisions found.",
            clusterAnalysis != null ? clusterAnalysis : "No cluster analysis available.");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedDecisionAnswer, using fallback");
                return generateFallbackDecisionAnswer(query, decisions);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced decision answer, using fallback", e);
            return generateFallbackDecisionAnswer(query, decisions);
        }
    }
    
    /**
     * Generates a fallback decision answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackDecisionAnswer(String query, List<Decision> decisions) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Se encontraron %d decisiones relevantes:\n%s",
                              decisions.size(),
                              decisions.stream()
                                      .limit(5)
                                      .map(d -> String.format("- %s", d.decisionText))
                                      .collect(Collectors.joining("\n")));
        } else {
            return String.format("Found %d relevant decisions:\n%s",
                              decisions.size(),
                              decisions.stream()
                                      .limit(5)
                                      .map(d -> String.format("- %s", d.decisionText))
                                      .collect(Collectors.joining("\n")));
        }
    }

    /**
     * Formats decision summary for LLM prompt
     */
    private String formatDecisionSummary(List<Decision> decisions, List<DecisionCluster> clusters) {
        StringBuilder summary = new StringBuilder();
        
        for (int i = 0; i < clusters.size(); i++) {
            DecisionCluster cluster = clusters.get(i);
            summary.append(String.format("Cluster %d (%d decisions) - Type: %s\n", 
                                        i + 1, cluster.getSize(), cluster.getDecisionType()));
            summary.append(cluster.getRepresentativeDecision().decisionText);
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats cluster analysis for LLM prompt
     */
    private String formatClusterAnalysis(List<DecisionCluster> clusters) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total clusters: %d\n", clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            DecisionCluster cluster = clusters.get(i);
            analysis.append(String.format("- Cluster %d: %d decisions, avg relevance: %.2f, type: %s\n", 
                                        i + 1, cluster.getSize(), cluster.getAverageRelevance(), cluster.getDecisionType()));
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
     * Represents a decision with enhanced metadata
     */
    private static class Decision {
        final String minuteId;
        final String date;
        final String place;
        final String decisionText;
        final String decisionType;
        final List<String> keyEntities;
        final double relevanceScore;
        final long timestamp;

        Decision(String minuteId, String date, String place, String decisionText, String decisionType,
                List<String> keyEntities, double relevanceScore, long timestamp) {
            this.minuteId = minuteId;
            this.date = date;
            this.place = place;
            this.decisionText = decisionText;
            this.decisionType = decisionType;
            this.keyEntities = keyEntities;
            this.relevanceScore = relevanceScore;
            this.timestamp = timestamp;
        }
        
        /**
         * Gets a formatted identifier for the decision
         */
        String getIdentifier() {
            return String.format("%s (%s - %s)", minuteId, date != null ? date : "unknown", place != null ? place : "unknown");
        }
        
        /**
         * Gets the age of the decision in milliseconds
         */
        long getAge() {
            return System.currentTimeMillis() - timestamp;
        }
        
        /**
         * Gets the key entities as a formatted string
         */
        String getKeyEntitiesAsString() {
            return keyEntities.isEmpty() ? "none" : String.join(", ", keyEntities);
        }
        
        @Override
        public String toString() {
            return String.format("Decision[%s, type=%s, score=%.2f, age=%dms, entities=%s]", 
                               getIdentifier(), decisionType, relevanceScore, getAge(), getKeyEntitiesAsString());
        }
    }

    /**
     * Represents a cluster of similar decisions
     */
    private static class DecisionCluster {
        private final List<Decision> decisions = new ArrayList<>();

        DecisionCluster(Decision initialDecision) {
            decisions.add(initialDecision);
        }

        void addDecision(Decision decision) {
            decisions.add(decision);
        }

        int getSize() {
            return decisions.size();
        }

        Decision getRepresentativeDecision() {
            // Return the decision with highest relevance score
            return decisions.stream()
                    .max((a, b) -> Double.compare(a.relevanceScore, b.relevanceScore))
                    .orElse(decisions.get(0));
        }

        String getRepresentativeContent() {
            return getRepresentativeDecision().decisionText;
        }

        String getDecisionType() {
            return getRepresentativeDecision().decisionType;
        }

        double getAverageRelevance() {
            return decisions.stream()
                    .mapToDouble(d -> d.relevanceScore)
                    .average()
                    .orElse(0.0);
        }
    }
}
