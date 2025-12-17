package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Summarize meeting tool that uses metadata to summarize the meeting.
 */
public class MetadataSummarizeMeetingTool extends AbstractMetadataTool {

    public MetadataSummarizeMeetingTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        log().info("Executing summarize meeting query: {} with NER: {}", query, ner != null ? ner.toString() : "null");

        List<Document> docs = retrieveDocumentsWithFallback(
            query,
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"},
            ner
        );
        if (docs.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        List<SummaryResult> results = generateSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        List<SummaryResult> rankedResults = analyzeAndRankSummaries(results);

        String answer = generateSummaryAnswer(query, rankedResults);
        log().info("Generated summarize meeting answer for query: {} with {} summaries", query, results.size());

        return ToolResult.from(answer, getClass());
    }

    private List<SummaryResult> generateSummariesInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<SummaryResult>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> generateSummary(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> result.getSummary() != null && !result.getSummary().isBlank())
                .collect(Collectors.toList());
    }

    private SummaryResult generateSummary(String query, Minute minute) {
        String summary = buildSummaryFromMetadata(minute);

        if (summary.isBlank()) {
            summary = generateSummaryWithLLM(query, minute);
        }

        if (summary.isBlank()) {
            return null;
        }

        return new SummaryResult(
            minute.id(),
            minute.date(),
            minute.place(),
            summary
        );
    }

    private String buildSummaryFromMetadata(Minute minute) {
        if (minute == null) {
            return "";
        }
        // Build summary from metadata - this will be refined by LLM if needed
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
            parts.add("Agenda: " + minute.agenda().values().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining("; ")));
        }
        return String.join(" | ", parts).trim();
    }

    private String generateSummaryWithLLM(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }
        
        String prompt = String.format("""
            You are summarizing a meeting minute. The user asked: "%s"
            
            CRITICAL: Your summary MUST directly answer what the user is asking for in the query.
            - Focus on the specific information requested in the query
            - If the query asks about a specific topic/aspect, prioritize that in your summary
            - If the query asks for a general summary, provide a comprehensive overview
            - Write in the same language as the query
            
            Meeting information:
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Agenda: %s
            Previous summary: %s
            
            Write a concise summary (3-4 sentences) that directly addresses the user's query.
            Make sure the summary is relevant to what was asked, not just a generic meeting description.
            """,
            query,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.agenda() != null ? minute.agenda().toString() : "unknown",
            minute.summary() != null ? minute.summary() : ""
        );

        try {
            String response = getLLMResponseCached(prompt);

            if (response == null || response.trim().isEmpty()) {
                log().info("Empty response from LLM in generateSummaryWithLLM, returning empty string");
                return "";
            }

            return response;
        } catch (Exception e) {
            log().error("Error generating summary with LLM, returning empty string", e);
            return "";
        }
    }

    private List<SummaryResult> analyzeAndRankSummaries(List<SummaryResult> results) {
        return results.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getSummary() != null ? b.getSummary().length() : 0,
                        a.getSummary() != null ? a.getSummary().length() : 0))
                .collect(Collectors.toList());
    }

    private String generateSummaryAnswer(String query, List<SummaryResult> results) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }

        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");

        StringBuilder sb = new StringBuilder();

        results.stream().limit(5).forEach(r -> {
            if (r.getDate() != null) {
                sb.append(isSpanish ? "Reunión del " : "Meeting on ").append(r.getDate());
                if (r.getPlace() != null) {
                    sb.append(" (").append(r.getPlace()).append(")");
                }
                sb.append(":\n");
            }
            String content = r.getSummary() != null ? r.getSummary() : "";
            sb.append(content.length() > 400 ? content.substring(0, 400) + "..." : content);
            sb.append("\n\n");
        });

        return sb.toString().trim();
    }

}

