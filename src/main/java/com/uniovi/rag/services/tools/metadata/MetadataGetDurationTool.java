package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.time.LocalDate;
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

        // Step 4: Check if query includes a date
        List<String> dateCandidates = extractDateCandidates(query, ner);
        boolean hasDateInQuery = !dateCandidates.isEmpty();
        log().info("Date candidates extracted: {} (hasDateInQuery: {})", dateCandidates, hasDateInQuery);

        List<Minute> targetMinutes;
        if (hasDateInQuery) {
            // Filter by date first (reduces work)
            log().info("Filtering {} relevant minutes by date", relevantMinutes.size());
            targetMinutes = filterMinutesByDate(query, ner, relevantMinutes);
            log().info("After date filtering: {} minutes", targetMinutes.size());
            
            if (targetMinutes.isEmpty()) {
                // User asked about a specific date but no minutes matched
                log().warn("No minutes found for the specified date in query: {}. Date candidates: {}", query, dateCandidates);
                // Check if we have minutes with dates that might match
                List<String> availableDates = relevantMinutes.stream()
                        .map(Minute::date)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                log().info("Available dates in relevant minutes: {}", availableDates);
                return ToolResult.from(generateNotFoundMessage(query), getClass());
            }
        } else {
            // No date in query: evaluate each minute with LLM to find the one being asked about
            log().info("No date in query, evaluating {} minutes with LLM", relevantMinutes.size());
            targetMinutes = evaluateMinutesWithLLM(query, relevantMinutes);
            log().info("After LLM evaluation: {} minutes", targetMinutes.size());
            
            if (targetMinutes.isEmpty()) {
                log().info("No minutes validated by LLM for get duration query: {}", query);
                return ToolResult.from(generateClarificationMessage(query), getClass());
            }
        }

        // Step 5: Extract durations in parallel (only from target minutes)
        List<DurationResult> results = extractDurationsInParallel(targetMinutes);
        if (results.isEmpty()) {
            log().info("No durations extracted for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 6: Select the target minute
        DurationResult selected = selectTargetMinute(query, ner, results, hasDateInQuery);
        if (selected == null) {
            if (hasDateInQuery) {
                // Date was specified but not found
                return ToolResult.from(generateNotFoundMessage(query), getClass());
            } else {
                // No date specified, ask for clarification
                return ToolResult.from(generateClarificationMessage(query), getClass());
            }
        }

        // Step 7: Generate final answer (only that minute)
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
     * Extracts duration for a minute with enhanced context.
     * 
     * FASE 5: Enhanced duration extraction with better logging and validation.
     */
    private DurationResult extractDuration(Minute minute) {
        if (minute.startTime() == null || minute.startTime().trim().isEmpty() ||
            minute.endTime() == null || minute.endTime().trim().isEmpty()) {
            log().debug("Minute {} has no startTime or endTime: startTime={}, endTime={}", 
                    minute.id(), minute.startTime(), minute.endTime());
            return null;
        }
        
        int duration = calculateDurationFromMinute(minute);
        
        if (duration <= 0) {
            log().debug("Minute {} has invalid duration: {} minutes (startTime={}, endTime={})", 
                    minute.id(), duration, minute.startTime(), minute.endTime());
            return null;
        }
        
        log().debug("Extracted duration for minute {}: {} minutes ({} - {})", 
                minute.id(), duration, minute.startTime(), minute.endTime());
        
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
     * Evaluates minutes with LLM to validate they are the ones being asked about.
     * Only minutes that pass validation are used for duration extraction.
     */
    private List<Minute> evaluateMinutesWithLLM(String query, List<Minute> minutes) {
        List<CompletableFuture<Minute>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> {
                    if (evaluateMinuteContainsRequestedInfo(query, minute)) {
                        return minute;
                    }
                    return null;
                }))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Selects the target minute from results.
     * If date was specified, matches by date. Otherwise, uses first result (already validated by LLM).
     */
    private DurationResult selectTargetMinute(String query, JSONObject ner, List<DurationResult> results, boolean hasDateInQuery) {
        if (results.isEmpty()) {
            return null;
        }

        if (hasDateInQuery) {
            // Match by date (should be exact match since we already filtered)
            List<String> dateCandidates = extractDateCandidates(query, ner);
            for (String candidate : dateCandidates) {
                LocalDate qDate = parseDateFlexible(candidate);
                if (qDate == null) continue;

                for (DurationResult r : results) {
                    LocalDate rDate = parseDateFlexible(r.getDate());
                    if (rDate != null && rDate.equals(qDate)) {
                        return r;
                    }
                }
            }
            // If no exact match found, return first result (date parsing might have failed)
            return results.get(0);
        } else {
            // No date specified: if multiple results, use LLM to select, otherwise return first
            if (results.size() == 1) {
                return results.get(0);
            }
            // Multiple results: return first (already validated by LLM)
            return results.get(0);
        }
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


}