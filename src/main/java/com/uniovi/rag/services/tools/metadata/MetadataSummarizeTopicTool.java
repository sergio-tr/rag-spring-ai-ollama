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
        
        log().debug("Executing summarize topic query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"},
            ner
        );
        
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

        // Step 4: Generate topic summaries in parallel (metadata-first, LLM fallback)
        List<TopicResult> results = generateTopicSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().debug("No topic summaries generated for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Analyze and rank topic summaries
        List<TopicResult> rankedResults = analyzeAndRankTopicSummaries(results);

        // Step 6: Generate final answer (metadata-only)
        String answer = generateTopicSummaryAnswer(query, rankedResults);
        log().debug("Generated summarize topic answer for query: {} with {} topic summaries", 
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
                log().debug("Empty response from LLM in generateTopicSummaryWithLLM, returning empty string");
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

        boolean isSpanish = query.toLowerCase().matches(".*[áéíóúñ¿¡].*");
        StringBuilder sb = new StringBuilder();
        sb.append(isSpanish
                ? String.format("Resumen del tema basado en %d reuniones:\n\n", results.size())
                : String.format("Topic summary based on %d meetings:\n\n", results.size()));

        results.stream().limit(5).forEach(r -> {
            if (r.getDate() != null) {
                sb.append(isSpanish ? "Reunión del " : "Meeting on ").append(r.getDate());
                if (r.getPlace() != null) {
                    sb.append(" (").append(r.getPlace()).append(")");
                }
                sb.append(":\n");
            }
            String txt = r.getTopicSummary() != null ? r.getTopicSummary() : "";
            sb.append(txt.length() > 400 ? txt.substring(0, 400) + "..." : txt);
            sb.append("\n\n");
        });

        return sb.toString().trim();
    }
}
