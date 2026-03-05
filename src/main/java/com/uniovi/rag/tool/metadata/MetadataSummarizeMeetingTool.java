package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
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

    public MetadataSummarizeMeetingTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
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
        
        // Validate date if present in query
        String requestedDate = extractDateFromQuery(query, ner);
        if (requestedDate != null && docs.isEmpty()) {
            // Date was specified but no documents match
            String errorMessage = generateDateNotFoundMessage(query, requestedDate);
            log().info("No documents found for specified date: {} in query: {}", requestedDate, query);
            return ToolResult.from(formatResponse(errorMessage, query), getClass());
        }
        
        if (docs.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        List<SummaryResult> results = generateSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            return ToolResult.from(formatResponse(generateNoDataMessage(query), query), getClass());
        }

        List<SummaryResult> rankedResults = analyzeAndRankSummaries(results);

        String answer = generateSummaryAnswer(query, rankedResults);
        log().info("Generated summarize meeting answer for query: {} with {} summaries", query, results.size());

        return ToolResult.from(formatResponse(answer, query), getClass());
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
        boolean asksForTopicsOrPoints = asksForTopicsOrPoints(query);
        String summary;
        if (asksForTopicsOrPoints) {
            summary = generateSummaryWithLLM(query, minute);
            if (summary.isBlank()) {
                summary = buildSummaryFromMetadata(minute);
            }
        } else {
            summary = buildSummaryFromMetadata(minute);
            if (summary.isBlank()) {
                summary = generateSummaryWithLLM(query, minute);
            }
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

    /** True when the query asks for points discussed, topics, decisions or agreements (so we must include topics/agenda). */
    private boolean asksForTopicsOrPoints(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        return q.contains("puntos tratados") || q.contains("qué se discutió") || q.contains("temas de la reunión")
                || q.contains("qué se habló") || q.contains("temas tratados") || q.contains("qué se trató")
                || q.contains("orden del día") || q.contains("qué decisiones") || q.contains("qué acuerdos")
                || q.contains("qué se acordó") || q.contains("explica los puntos") || q.contains("indica los puntos")
                || q.contains("qué se habló") || q.contains("resume los puntos") || q.contains("qué se trató en la reunión");
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
        
        // P9: When user asks for "puntos tratados" or "qué se discutió", insist on including topics/agenda in the summary
        boolean asksForTopics = asksForTopicsOrPoints(query);
        boolean asksForGeneralSummary = query != null && (query.toLowerCase().contains("resumen general")
                || query.toLowerCase().contains("general summary") || query.toLowerCase().contains("overview of the meeting"));
        // Item 54: When user asks for "acuerdos" or "decisiones", prioritize the list of decisions in the answer
        boolean asksForAgreementsOrDecisions = query != null && (query.toLowerCase().contains("acuerdos")
                || query.toLowerCase().contains("decisiones") || query.toLowerCase().contains("qué se decidió")
                || query.toLowerCase().contains("what agreements") || query.toLowerCase().contains("what decisions"));
        // Item 43: "resumen general" must include topics and decisions, not only date/place/attendees
        String topicInstruction = asksForTopics
                ? " OBLIGATORY: The user asked for topics/points discussed. Your response MUST include the list of topics (Temas) and/or agenda items (Orden del día) from the Meeting information below. Do not summarize only date, place and attendees; list the actual points discussed (e.g. iluminación, limpieza, seguridad, presupuesto). If the query asks for topics or points discussed, your answer MUST list the main topics from the Meeting information above (e.g. lighting, cleaning of common areas, security, budget). Do not focus only on one secondary topic (e.g. water supply) and omit the rest."
                : (asksForGeneralSummary
                ? " OBLIGATORY: The user asked for a general summary. Your response MUST include the main topics discussed (Topics) and the main decisions or agreements (Decisions), not only date, place and attendees. Include content such as budget, pests, heating, corrective actions, etc. when present in the Meeting information."
                : "");
        if (asksForAgreementsOrDecisions) {
            topicInstruction += " OBLIGATORY: The user asked specifically for agreements/decisions. Your answer MUST list or summarize the agreements/decisions from the list below (e.g. security, lighting of common areas), not only the first topic. Base your answer mainly on the List of decisions/agreements.";
        }
        String pointsBlock = "";
        if (asksForAgreementsOrDecisions && minute != null && minute.decisions() != null && !minute.decisions().isEmpty()) {
            pointsBlock = "\n\nList of decisions/agreements from the meeting (you MUST base your answer mainly on these):\n"
                    + String.join("\n", minute.decisions()) + "\n";
        }
        if ((asksForTopics || asksForGeneralSummary) && minute != null && pointsBlock.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (minute.topics() != null && !minute.topics().isEmpty()) {
                sb.append("Temas: ").append(String.join(", ", minute.topics()));
            }
            if (minute.decisions() != null && !minute.decisions().isEmpty() && (asksForGeneralSummary || asksForTopics)) {
                if (sb.length() > 0) sb.append(". ");
                sb.append("Decisiones: ").append(String.join("; ", minute.decisions()));
            }
            if (minute.agenda() != null && !minute.agenda().isEmpty()) {
                if (sb.length() > 0) sb.append(". ");
                sb.append("Orden del día: ").append(minute.agenda().values().stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(", ")));
            }
            if (sb.length() > 0) {
                pointsBlock = "\n\nPoints/topics discussed in this meeting (you MUST include these in your answer): " + sb + "\n";
            }
        }
        if (minute == null) {
            return "";
        }
        String prompt = String.format("""
            You are summarizing a meeting minute. The user asked: "%s"
            %s
            CRITICAL: Your summary MUST directly answer what the user is asking for in the query.
            - Focus on the specific information requested in the query
            - If the query asks about a specific topic/aspect, prioritize that in your summary
            - If the query asks for a general summary, provide a comprehensive overview
            - Write in the same language as the query.%s
            
            Meeting information:
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Agenda: %s
            Previous summary: %s
            
            Write a CONCISE summary (2-4 sentences) that directly addresses the user's query. Write concisely so the answer is complete without truncation.
            - Focus ONLY on what the user is asking for
            - If the query asks for agreements/decisions, list them (e.g. security, lighting of common areas) and do not truncate the list
            - If the query asks about a specific topic/aspect, prioritize that
            - Remove any redundant or unnecessary information
            - Be brief and to the point - every word counts
            - Do NOT include generic meeting descriptions unless specifically requested
            - Do NOT repeat information that's already obvious from the context
            - Use the most important information first
            """,
            query,
            pointsBlock,
            topicInstruction,
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

        // Build summary content (limit to 3 meetings max). Use higher char limit when query asks for agreements/decisions to avoid truncation (item 54)
        boolean asksForAgreements = query != null && (query.toLowerCase().contains("acuerdos") || query.toLowerCase().contains("decisiones"));
        int maxCharsPerSummary = asksForAgreements ? 500 : 200;
        StringBuilder summaryContent = new StringBuilder();
        results.stream().limit(3).forEach(r -> {
            if (r.getDate() != null) {
                summaryContent.append("Date: ").append(r.getDate());
                if (r.getPlace() != null) {
                    summaryContent.append(", Place: ").append(r.getPlace());
                }
                summaryContent.append("\n");
            }
            String content = r.getSummary() != null ? r.getSummary() : "";
            summaryContent.append(content.length() > maxCharsPerSummary ? content.substring(0, maxCharsPerSummary) + "..." : content);
            summaryContent.append("\n\n");
        });

        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Meeting summary information:
            %s
            
            CRITICAL RULES:
            1. Write in the EXACT SAME LANGUAGE as the user's question
            2. Be CONCISE - maximum 2-3 sentences TOTAL (not per meeting), focus on key points only
            3. DO NOT repeat the question or any part of it at the beginning
            4. DO NOT start with phrases like "Dame un resumen...", "Hazme un resumen...", "Resume la reunión...", etc.
            5. Start directly with the summary content
            6. Do NOT include redundant information - every word must add value
            7. Focus on what the user is asking for - if they ask about a specific topic, prioritize that
            8. If multiple meetings, provide a unified summary of key points across all meetings, not individual summaries
            9. Remove any technical details or internal processing information
            10. Use the most important information first - prioritize relevance over completeness
            
            Examples of CORRECT responses:
            - Query: "Dame un resumen de la reunión celebrada el 25 de agosto de 2026"
              Correct: "La reunión de la Comunidad de Vecinos [summary content]"
              Wrong: "Dame un resumen de la reunión celebrada el 25 de agosto de 2026.\\n\\nLa reunión..."
            
            - Query: "Resume la reunión del 24 de febrero de 2025"
              Correct: "La reunión de la Comunidad de Vecinos [summary content]"
              Wrong: "Resume la reunión del 24 de febrero de 2025.\\n\\nLa reunión..."
            
            Format and present this summary information concisely and clearly.
            """, query, summaryContent.toString());

        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                // Post-process to format and clean response
                return formatResponse(response, query);
            }
        } catch (Exception e) {
            log().warn("Error generating summary answer with LLM, using raw content", e);
        }

        // Fallback: return raw content
        return summaryContent.toString().trim();
    }

}

