package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataGetDurationTool for analyzing meeting durations with intelligent analysis.
 */
public class MetadataGetDurationTool extends AbstractMetadataTool {

    public MetadataGetDurationTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing get duration query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "startTime", "endTime", "topics", "decisions", "summary", "president", "secretary"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().info("No documents found for get duration query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for get duration query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for get duration query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Extract durations in parallel
        List<DurationResult> results = extractDurationsInParallel(relevantMinutes);
        if (results.isEmpty()) {
            log().info("No durations extracted for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 5: Select the target minute based on query/NER (by date)
        DurationResult selected = selectTargetMinuteByDate(query, ner, results);
        if (selected == null) {
            return ToolResult.from(generateClarificationMessage(query), getClass());
        }

        // Step 6: Generate final answer (only that minute)
        String answer = generateSingleDurationAnswer(query, selected);
        log().info("Generated get duration answer for query: {} (selected minuteId={})", query, selected.getMinuteId());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Extracts durations in parallel
     */
    private List<DurationResult> extractDurationsInParallel(List<Minute> minutes) {
        List<CompletableFuture<DurationResult>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractDuration(minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> result.getDurationMinutes() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Extracts duration for a minute with enhanced context
     */
    private DurationResult extractDuration(Minute minute) {
        int duration = calculateDurationFromMinute(minute);
        
        if (duration <= 0) {
            return null;
        }
        
        return new DurationResult(
            minute.id(),
            minute.date(),
            minute.place(),
            minute.startTime(),
            minute.endTime(),
            duration
        );
    }

    /**
     * Analyzes and ranks durations by relevance and quality
     */
    private DurationResult selectTargetMinuteByDate(String query, JSONObject ner, List<DurationResult> results) {
        List<String> dateCandidates = extractDateCandidates(query, ner);
        if (dateCandidates.isEmpty()) {
            return null;
        }

        // 1) Match exact LocalDate if possible
        for (String candidate : dateCandidates) {
            LocalDate qDate = parseDate(candidate);
            if (qDate == null) continue;

            for (DurationResult r : results) {
                LocalDate rDate = parseDate(r.getDate());
                if (rDate != null && rDate.equals(qDate)) {
                    return r;
                }
            }
        }

        // 2) Fallback: string contains (when parsing fails)
        String queryLower = query != null ? query.toLowerCase() : "";
        for (DurationResult r : results) {
            if (r.getDate() != null && !r.getDate().isBlank()) {
                String rDateLower = r.getDate().toLowerCase();
                if (queryLower.contains(rDateLower)) {
                    return r;
                }
            }
        }

        return null;
    }

    /**
     * Final answer: only the selected minute.
     */
    private String generateSingleDurationAnswer(String query, DurationResult r) {
        boolean isSpanish = query != null && query.toLowerCase().matches(".*[áéíóúñ¿¡].*");
        String date = r.getDate() != null ? r.getDate() : (isSpanish ? "fecha desconocida" : "unknown date");
        String start = r.getStartTime() != null ? r.getStartTime() : "?";
        String end = r.getEndTime() != null ? r.getEndTime() : "?";

        int minutes = r.getDurationMinutes();
        String human = formatDurationHuman(minutes, isSpanish);

        if (isSpanish) {
            return String.format("La reunión del %s comenzó a las %s y finalizó a las %s. Duración: %s.", date, start, end, human);
        }
        return String.format("The meeting on %s started at %s and ended at %s. Duration: %s.", date, start, end, human);
    }

    /**
     * If the query doesn't specify the minute (date), ask for clarification.
     */
    private String generateClarificationMessage(String query) {
        boolean isSpanish = query != null && query.toLowerCase().matches(".*[áéíóúñ¿¡].*");
        if (isSpanish) {
            return "¿De qué acta o fecha concreta necesitas la duración? (por ejemplo: “24 de febrero de 2025”).";
        }
        return "Which meeting minute/date do you need the duration for? (e.g., “24 February 2025”).";
    }

    private String formatDurationHuman(int minutes, boolean isSpanish) {
        if (minutes <= 0) {
            return isSpanish ? "duración desconocida" : "unknown duration";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        if (h <= 0) {
            return String.format("%d min", m);
        }
        if (m == 0) {
            return isSpanish ? String.format("%d hora%s", h, h == 1 ? "" : "s") : String.format("%d hour%s", h, h == 1 ? "" : "s");
        }
        return isSpanish
                ? String.format("%d hora%s y %d min", h, h == 1 ? "" : "s", m)
                : String.format("%d hour%s %d min", h, h == 1 ? "" : "s", m);
    }

    private List<String> extractDateCandidates(String query, JSONObject ner) {
        List<String> out = new ArrayList<>();

        // From NER
        if (ner != null && ner.has("date")) {
            try {
                org.json.JSONArray arr = ner.getJSONArray("date");
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isBlank()) out.add(s);
                }
            } catch (Exception ignored) {
            }
        }

        // From query (simple patterns)
        if (query != null) {
            String q = query;
            java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(q);
            while (m1.find()) out.add(m1.group(1));

            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{4})").matcher(q);
            while (m2.find()) out.add(m2.group(1));

            java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("(\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4})", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(q);
            while (m3.find()) out.add(m3.group(1));
        }

        return out.stream().distinct().collect(Collectors.toList());
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"))
        );
        for (DateTimeFormatter f : formatters) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

}