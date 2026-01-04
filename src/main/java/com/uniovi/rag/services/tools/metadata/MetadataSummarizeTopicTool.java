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
 * Enhanced MetadataSummarizeTopicTool for summarizing specific topics from meeting minutes with intelligent analysis.
 */
public class MetadataSummarizeTopicTool extends AbstractMetadataTool {

    public MetadataSummarizeTopicTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing summarize topic query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"},
            ner
        );
        
        // Validate date if present in query
        String requestedDate = extractDateFromQuery(query, ner);
        if (requestedDate != null && docs.isEmpty()) {
            // Date was specified but no documents match
            String errorMessage = generateDateNotFoundMessage(query, requestedDate);
            log().info("No documents found for specified date: {} in query: {}", requestedDate, query);
            return ToolResult.from(errorMessage, getClass());
        }
        
        if (docs.isEmpty()) {
            log().info("No documents found for summarize topic query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for summarize topic query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for summarize topic query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }
        
        // Step 3.5: Additional filtering by specific topic with relevance threshold
        // Only include minutes where the topic is actually mentioned (not just vaguely related)
        String topic = extractTopicFromQuery(query, ner);
        if (topic != null && !topic.isEmpty()) {
            // Check if query explicitly asks about a specific topic (not just general summary)
            if (detectSpecificTopicQuery(query)) {
                List<Minute> topicFiltered = filterMinutesByTopicWithThreshold(relevantMinutes, topic);
                if (!topicFiltered.isEmpty()) {
                    log().info("Filtered {} minutes by topic '{}' with relevance threshold, {} remaining", 
                              relevantMinutes.size(), topic, topicFiltered.size());
                    relevantMinutes = topicFiltered;
                } else {
                    // Topic was specified but no minutes match - this indicates absence of information
                    log().info("Topic '{}' was specified but no minutes match the relevance threshold", topic);
                    return ToolResult.from(generateTopicNotFoundMessage(query, topic), getClass());
                }
            }
        }

        // Step 4: Generate topic summaries in parallel (metadata-first, LLM fallback)
        List<TopicResult> results = generateTopicSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().info("No topic summaries generated for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank topic summaries
        List<TopicResult> rankedResults = analyzeAndRankTopicSummaries(results);

        // Step 6: Generate final answer (metadata-only)
        String answer = generateTopicSummaryAnswer(query, rankedResults);
        log().info("Generated summarize topic answer for query: {} with {} topic summaries", 
                   query, results.size());
        
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
                .filter(result -> result.getTopicSummary() != null && !result.getTopicSummary().isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Generates topic summary for a minute with enhanced context
     */
    private TopicResult generateTopicSummary(String query, Minute minute) {
        String topicSummary = buildTopicSummaryFromMetadata(minute);
        
        if (topicSummary.isBlank()) {
            topicSummary = generateTopicSummaryWithLLM(query, minute);
        }

        if (topicSummary.isBlank()) {
            return null;
        }

        return new TopicResult(minute.id(), minute.date(), minute.place(), topicSummary);
    }

    /**
     * Topic summary from metadata fields.
     */
    private String buildTopicSummaryFromMetadata(Minute minute) {
        if (minute == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            parts.add("Temas: " + String.join("; ", minute.topics()));
        }
        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            parts.add("Decisiones: " + String.join("; ", minute.decisions()));
        }
        if (minute.summary() != null && !minute.summary().isBlank()) {
            parts.add("Resumen: " + minute.summary());
        }
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            parts.add("Agenda: " + minute.agenda().values().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining("; ")));
        }
        return String.join(" | ", parts).trim();
    }

    /**
     * Fallback LLM topic summary when metadata is insufficient.
     */
    private String generateTopicSummaryWithLLM(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }

        String prompt = String.format("""
            Summarize what is related to the topic(s) in the query in 3-4 sentences.
            Write in the same language as the query.
            Query: %s
            Date: %s
            Place: %s
            Topics: %s
            Decisions: %s
            Previous summary: %s
            Agenda: %s
            """,
            query,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "",
            minute.agenda() != null ? minute.agenda().toString() : "unknown"
        );

        try {
            String response = getLLMResponseCached(prompt);
            if (response == null || response.trim().isEmpty()) {
                log().info("Empty response from LLM in generateTopicSummaryWithLLM, returning empty string");
                return "";
            }
            return response;
        } catch (Exception e) {
            log().error("Error generating topic summary with LLM, returning empty string", e);
            return "";
        }
    }

    /**
     * Analyzes and ranks topic summaries by relevance and quality
     */
    private List<TopicResult> analyzeAndRankTopicSummaries(List<TopicResult> results) {
        // Orden simple por longitud de texto
        return results.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getTopicSummary() != null ? b.getTopicSummary().length() : 0,
                        a.getTopicSummary() != null ? a.getTopicSummary().length() : 0))
                .collect(Collectors.toList());
    }

    private String generateTopicSummaryAnswer(String query, List<TopicResult> results) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }

        // Build topic summary content
        StringBuilder summaryContent = new StringBuilder();
        summaryContent.append(String.format("Based on %d meetings:\n\n", results.size()));

        results.stream().limit(5).forEach(r -> {
            if (r.getDate() != null) {
                summaryContent.append("Date: ").append(r.getDate());
                if (r.getPlace() != null) {
                    summaryContent.append(", Place: ").append(r.getPlace());
                }
                summaryContent.append("\n");
            }
            String txt = r.getTopicSummary() != null ? r.getTopicSummary() : "";
            summaryContent.append(txt.length() > 400 ? txt.substring(0, 400) + "..." : txt);
            summaryContent.append("\n\n");
        });

        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Topic summary information:
            %s
            
            Format and present this topic summary in the EXACT SAME LANGUAGE as the user's question.
            Keep the structure clear and readable.
            Do not repeat the question.
            """, query, summaryContent.toString());

        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating topic summary answer with LLM, using raw content", e);
        }

        // Fallback: return raw content
        return summaryContent.toString().trim();
    }
    
    /**
     * Filters minutes by topic with relevance threshold.
     * Only includes minutes where the topic is actually mentioned (not just vaguely related).
     * 
     * @param minutes List of minutes to filter
     * @param topic Topic to filter by
     * @return Filtered list of minutes that mention the topic
     */
    private List<Minute> filterMinutesByTopicWithThreshold(List<Minute> minutes, String topic) {
        if (minutes.isEmpty() || topic == null || topic.isEmpty()) {
            return minutes;
        }
        
        String topicLower = topic.toLowerCase();
        
        log().info("Filtering {} minutes by topic '{}' with relevance threshold", minutes.size(), topic);
        
        List<Minute> filtered = minutes.stream()
                .filter(minute -> {
                    // Check if topic is mentioned in topics list (exact or partial match)
                    boolean inTopics = false;
                    if (minute.topics() != null) {
                        inTopics = minute.topics().stream()
                                .anyMatch(t -> t != null && t.toLowerCase().contains(topicLower));
                    }
                    
                    // Check if topic is mentioned in decisions (exact or partial match)
                    boolean inDecisions = false;
                    if (minute.decisions() != null) {
                        inDecisions = minute.decisions().stream()
                                .anyMatch(d -> d != null && d.toLowerCase().contains(topicLower));
                    }
                    
                    // Check if topic is mentioned in summary (exact or partial match)
                    boolean inSummary = minute.summary() != null && 
                                       minute.summary().toLowerCase().contains(topicLower);
                    
                    // Topic must be mentioned in at least one of these fields (relevance threshold)
                    return inTopics || inDecisions || inSummary;
                })
                .collect(Collectors.toList());
        
        log().info("Filtered {} minutes by topic '{}', {} remaining (relevance threshold met)", 
                  minutes.size(), topic, filtered.size());
        
        return filtered;
    }
    
    /**
     * Generates a message when topic is not found in any minutes.
     */
    private String generateTopicNotFoundMessage(String query, String topic) {
        if (query == null || query.trim().isEmpty()) {
            return String.format("No se encontró información sobre el tema '%s' en las actas disponibles.", topic);
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The topic '%s' was not found in any of the available meeting minutes.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating that no information was found about this topic.
            Be concise and direct.
            Do not repeat the question.
            """, query, topic != null ? topic : "the requested topic");
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating topic not found message with LLM", e);
        }
        
        // Fallback
        return String.format("No se encontró información sobre el tema '%s' en las actas disponibles.", 
                            topic != null ? topic : "solicitado");
    }
}
