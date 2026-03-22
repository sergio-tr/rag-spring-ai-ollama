package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.uniovi.rag.observability.ContextPropagatingFutures.supplyAsync;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataCountAndExplainTool for counting and explaining meeting minutes with intelligent analysis.
 */
public class MetadataCountAndExplainTool extends AbstractMetadataTool {

    public MetadataCountAndExplainTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing count and explain query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query,
            new String[] {"date", "place", "topics", "decisions", "summary", "agenda"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().info("No documents found for count and explain query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for count and explain query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for count and explain query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 4: Generate explanations in parallel
        List<Explanation> explanations = generateExplanationsInParallel(query, relevantMinutes);
        if (explanations.isEmpty()) {
            log().info("No explanations generated for query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 5: Analyze and rank explanations
        List<Explanation> rankedExplanations = analyzeAndRankExplanations(query, explanations);

        // Step 6: Cluster similar explanations
        List<ExplanationCluster> clusters = clusterExplanations(rankedExplanations);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedFinalAnswer(query, rankedExplanations, clusters);
        log().info("Generated count and explain answer for query: {} with {} explanations in {} clusters", 
                   query, explanations.size(), clusters.size());
        
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    /**
     * Generates explanations in parallel
     */
    private List<Explanation> generateExplanationsInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<Explanation>> futures = minutes.stream()
                .map(minute -> supplyAsync(() -> generateExplanation(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(explanation -> explanation.getContent() != null && !explanation.getContent().isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Generates explanation for a minute with enhanced context.
     */
    private Explanation generateExplanation(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return null;
        }
        
        // Try metadata-first explanation to avoid LLM call when possible
        String metadataExplanation = buildExplanationFromMetadata(minute);
        if (metadataExplanation != null && !metadataExplanation.isBlank()) {
            double relevanceScore = 0.6; // heuristic when built from metadata
            return new Explanation(
                minute.id(),
                minute.date(),
                minute.place(),
                metadataExplanation,
                relevanceScore,
                System.currentTimeMillis()
            );
        }

        String prompt = generateEnhancedExplanationPrompt(query, minute);
        String content = getLLMResponseCached(prompt);
        
        if (content == null || content.trim().isEmpty()) {
            log().info("Empty response from LLM in generateExplanation, returning null");
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
     * Builds a concise explanation using only metadata fields (no LLM).
     */
    private String buildExplanationFromMetadata(Minute minute) {
        List<String> parts = new ArrayList<>();

        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            parts.add("Decisiones: " + String.join("; ", minute.decisions()));
        }
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            parts.add("Temas: " + String.join("; ", minute.topics()));
        }
        if (minute.summary() != null && !minute.summary().isBlank()) {
            parts.add("Resumen: " + minute.summary());
        }
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            parts.add("Agenda: " + minute.agenda().toString());
        }
        if (parts.isEmpty()) {
            return "";
        }
        return String.join(" | ", parts);
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
                .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
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
        String explanationContent = explanation.getContent().toLowerCase();
        String clusterContent = cluster.getRepresentativeContent().toLowerCase();
        
        // Calculate simple word overlap
        Set<String> explanationWords = new HashSet<>(Arrays.asList(explanationContent.split("\\s+")));
        Set<String> clusterWords = new HashSet<>(Arrays.asList(clusterContent.split("\\s+")));
        
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
            You need to answer a question about meeting minutes. The question asked was about counting and explaining meeting minutes that meet certain criteria.
            
            Found %d relevant meeting minutes:
            
            %s
            
            Write a clear, direct answer in the same language as the user's question (detect from context).
            CRITICAL RULES:
            1. DO NOT repeat or echo the user's question in your response.
            2. DO NOT start your answer with the question.
            3. Answer directly with the count and explanations.
            4. Example if the query is in English: "Found 5 meeting minutes. [explanations]" (NOT "The question was... Found 5...")
            5. Example if the query is in Spanish: "Se encontraron 5 actas. [explicaciones]" (NOT "La pregunta era... Se encontraron 5...")
            
            Base your answer ONLY on the meeting minutes and explanations listed above. Do not invent matching minutes or explanations.
            Provide only the information requested.
            DO NOT mention any technical details like "clusters", "análisis", "analysis", "grouped into", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering naturally and concisely, as if you were a helpful assistant.
            """, explanations.size(), 
            explanationSummary != null ? explanationSummary : "No information found.");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedFinalAnswer, using fallback");
                return generateFallbackFinalAnswer(query, explanations);
            }
            
            String cleaned = removeQuestionEcho(response.trim(), query);
            return cleaned;
        } catch (Exception e) {
            log().error("Error generating enhanced final answer, using fallback", e);
            return generateFallbackFinalAnswer(query, explanations);
        }
    }
    
    /**
     * Generates a fallback final answer when LLM fails.
     */
    private String generateFallbackFinalAnswer(String query, List<Explanation> explanations) {
        String explanationsText = explanations.stream()
                .limit(3)
                .map(e -> String.format("- %s: %s", 
                    e.getDate() != null ? e.getDate() : "unknown date",
                    e.getContent() != null && e.getContent().length() > 200 ? e.getContent().substring(0, 200) + "..." : (e.getContent() != null ? e.getContent() : "")))
                .collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found %d relevant meeting minutes with the following explanations:
            
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating how many meeting minutes were found and summarizing the explanations.
            Be concise and direct.
            Do not repeat the question.
            """, query, explanations.size(), explanationsText);
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback final answer with LLM", e);
        }
        
        // Ultimate fallback
        return String.format("Found %d relevant minute(s).\n\nExplanations:\n%s",
                           explanations.size(), explanationsText);
    }

    /**
     * Removes question echo from response using LLM.
     * The LLM analyzes if the response repeats the question and extracts only the answer.
     */
    private String removeQuestionEcho(String response, String query) {
        if (response == null || query == null || response.trim().isEmpty()) {
            return response;
        }
        
        // If response is very short, likely no echo
        if (response.length() < 20) {
            return response;
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The system generated this response: "%s"
            
            Task: If the response repeats or echoes the question, extract ONLY the actual answer part.
            Remove any phrases like "the question was", "la pregunta era", "the user asked", etc.
            Remove the question itself if it appears at the beginning.
            
            Return ONLY the cleaned answer, without any explanation or additional text.
            If the response doesn't echo the question, return it as-is.
            """, query, response);
        
        try {
            String cleaned = getLLMResponseCached(prompt);
            if (cleaned != null && !cleaned.trim().isEmpty()) {
                return cleaned.trim();
            }
        } catch (Exception e) {
            log().warn("Error removing question echo with LLM, returning original response", e);
        }
        
        // Fallback: return original response
        return response;
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
            
            if (representative.getDate() != null) {
                summary.append(String.format("Reunión del %s:\n", representative.getDate()));
            }
            summary.append(representative.getContent() != null ? representative.getContent() : "");
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /**
     * Cached LLM response with error handling and validation.
     * Uses parent class implementation which includes error handling.
     */
    @Cacheable(value = "llmResponses", key = "#prompt.hashCode()")
    public String getLLMResponseCached(String prompt) {
        return super.getLLMResponseCached(prompt);
    }

}
