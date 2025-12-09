package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
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
 */
public class MetadataFilterAndListTool extends AbstractMetadataTool {

    public MetadataFilterAndListTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing filter and list query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().info("No documents found for filter and list query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for filter and list query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for filter and list query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Generate summaries in parallel (metadata-first, LLM fallback)
        List<FilterResult> results = generateSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().info("No summaries generated for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank results
        List<FilterResult> rankedResults = analyzeAndRankResults(results);

        // Step 6: Cluster similar results
        List<InfoExtractor.Cluster<FilterResult>> clusters = clusterResults(rankedResults);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedFilterAnswer(query, rankedResults, clusters);
        log().info("Generated filter and list answer for query: {} with {} results in {} clusters", 
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
                .filter(result -> result.getSummary() != null && !result.getSummary().isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Generates summary for a minute with enhanced context
     */
    private FilterResult generateSummary(String query, Minute minute) {
        // Metadata-first summary
        String summary = buildSummaryFromMetadata(minute);

        if (summary.isBlank()) {
            // Fallback to LLM summary
            summary = buildSummaryExplanation(query, minute);
        }

        if (summary.isBlank()) {
            return null;
        }

        int score = summary.length();

        return new FilterResult(
            minute.id(),
            minute.date(),
            minute.place(),
            summary,
            score
        );
    }

    /**
     * Metadata-first summary builder; uses existing summary, decisions, topics, agenda.
     */
    private String buildSummaryFromMetadata(Minute minute) {
        if (minute == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (minute.summary() != null && !minute.summary().isBlank()) {
            parts.add(minute.summary());
        }
        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            parts.add("Decisiones: " + String.join("; ", minute.decisions()));
        }
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            parts.add("Temas: " + String.join("; ", minute.topics()));
        }
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            parts.add("Agenda: " + minute.agenda().toString());
        }
        return String.join(" | ", parts).trim();
    }

    /**
     * LLM fallback summary when metadata is insufficient.
     */
    private String buildSummaryExplanation(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }
        
        String prompt = String.format("""
            Summarize the meeting in 2-3 sentences focusing on what is most relevant to the query:
            Query: %s
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            Agenda: %s
            """,
            query,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown",
            minute.agenda() != null ? minute.agenda().toString() : "unknown"
        );
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response == null || response.trim().isEmpty()) {
                log().info("Empty response from LLM in buildSummaryExplanation, returning empty string");
                return "";
            }
            return response.trim();
        } catch (Exception e) {
            log().error("Error building summary explanation, returning empty string", e);
            return "";
        }
    }

    /**
     * Analyzes and ranks results by relevance and quality
     */
    private List<FilterResult> analyzeAndRankResults(List<FilterResult> results) {
        // Sort by summary length (descending) as a simple signal of content richness
        return results.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getSummary() != null ? b.getSummary().length() : 0,
                        a.getSummary() != null ? a.getSummary().length() : 0))
                .collect(Collectors.toList());
    }

    /**
     * Clusters similar results to avoid redundancy
     */
    private List<InfoExtractor.Cluster<FilterResult>> clusterResults(List<FilterResult> results) {
        return InfoExtractor.clusterItems(
            results,
            result -> result.getSummary(),
            result -> result.getDate() != null ? result.getDate() : "unknown",
            0.3 // Similarity threshold
        );
    }

    /**
     * Generates enhanced filter answer with clustering and analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedFilterAnswer(String query, List<FilterResult> results, 
                                               List<InfoExtractor.Cluster<FilterResult>> clusters) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String resultSummary = formatResultSummary(results, clusters);
        
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
            """, query, results.size(), 
            resultSummary != null ? resultSummary : "No results found.");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedFilterAnswer, using fallback");
                return generateFallbackFilterAnswer(query, results);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced filter answer, using fallback", e);
            return generateFallbackFilterAnswer(query, results);
        }
    }
    
    /**
     * Generates a fallback filter answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackFilterAnswer(String query, List<FilterResult> results) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Se encontraron %d reuniones relevantes:\n%s",
                              results.size(),
                              results.stream()
                                      .limit(5)
                                      .map(r -> String.format("- %s: %s", 
                                          r.getDate() != null ? r.getDate() : "fecha desconocida",
                                          r.getSummary() != null && r.getSummary().length() > 150 ? r.getSummary().substring(0, 150) + "..." : (r.getSummary() != null ? r.getSummary() : "")))
                                      .collect(Collectors.joining("\n\n")));
        } else {
            return String.format("Found %d relevant meetings:\n%s",
                              results.size(),
                              results.stream()
                                      .limit(5)
                                      .map(r -> String.format("- %s: %s", 
                                          r.getDate() != null ? r.getDate() : "unknown date",
                                          r.getSummary() != null && r.getSummary().length() > 150 ? r.getSummary().substring(0, 150) + "..." : (r.getSummary() != null ? r.getSummary() : "")))
                                      .collect(Collectors.joining("\n\n")));
        }
    }

    /**
     * Formats result summary for LLM prompt (without technical details)
     */
    private String formatResultSummary(List<FilterResult> results, List<InfoExtractor.Cluster<FilterResult>> clusters) {
        StringBuilder summary = new StringBuilder();
        
        // Format results naturally without mentioning clusters
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<FilterResult> cluster = clusters.get(i);
            FilterResult representative = cluster.getRepresentativeItem();
            
            if (representative.getDate() != null) {
                summary.append(String.format("Reunión del %s", representative.getDate()));
                if (representative.getPlace() != null) {
                    summary.append(String.format(" (%s)", representative.getPlace()));
                }
                summary.append(":\n");
            }
            summary.append(representative.getSummary() != null ? representative.getSummary() : "");
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

}
