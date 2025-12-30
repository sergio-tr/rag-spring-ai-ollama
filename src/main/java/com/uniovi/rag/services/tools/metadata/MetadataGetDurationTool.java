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
        
        // Extract date early for error messages
        List<String> dateCandidates = extractDateCandidates(query, ner);
        String date = dateCandidates.isEmpty() ? null : dateCandidates.get(0);
        boolean hasDateInQuery = !dateCandidates.isEmpty();
        
        // Validate date if present in query
        String requestedDate = extractDateFromQuery(query, ner);
        if (requestedDate != null && docs.isEmpty()) {
            // Date was specified but no documents match
            String errorMessage = generateDateNotFoundMessage(query, requestedDate);
            log().info("No documents found for specified date: {} in query: {}", requestedDate, query);
            return ToolResult.from(errorMessage, getClass());
        }
        
        if (docs.isEmpty()) {
            log().info("No documents found for get duration query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, "duration", date, 0, "no_documents"), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for get duration query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, "duration", date, docs.size(), "no_valid_minutes"), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for get duration query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, "duration", date, minutes.size(), "no_relevant_minutes"), getClass());
        }

        // Step 4: Check if query includes a date
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
                return ToolResult.from(generateSpecificErrorMessage(query, "duration", date, relevantMinutes.size(), "date_not_found"), getClass());
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
            log().info("No durations extracted for query: {} (no startTime/endTime in {} minutes)", query, targetMinutes.size());
            return ToolResult.from(generateSpecificErrorMessage(query, "duration", date, targetMinutes.size(), "no_start_end_time"), getClass());
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
     * Uses LLM to generate answer in correct language.
     */
    private String generateSingleDurationAnswer(String query, DurationResult r) {
        String date = r.getDate() != null ? r.getDate() : "unknown date";
        String start = r.getStartTime() != null ? r.getStartTime() : "?";
        String end = r.getEndTime() != null ? r.getEndTime() : "?";
        int totalMinutes = r.getDurationMinutes();
        
        // Calculate hours and minutes for LLM
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Meeting information:
            - Date: %s
            - Start time: %s
            - End time: %s
            - Duration: %d minutes (%d hours and %d minutes)
            
            Respond with a short, clear answer in the EXACT SAME LANGUAGE as the question,
            stating the meeting duration information in a natural way.
            Format the duration appropriately for the language (e.g., "1 hour and 30 minutes" in English, 
            "1 hora y 30 minutos" in Spanish).
            Be concise and direct.
            Do not repeat the question.
            """, query, date, start, end, totalMinutes, hours, mins);
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating duration answer with LLM, using fallback", e);
        }
        
        // Fallback - simple format
        String durationStr = hours > 0 && mins > 0 
            ? String.format("%d hour%s %d min", hours, hours == 1 ? "" : "s", mins)
            : hours > 0 
                ? String.format("%d hour%s", hours, hours == 1 ? "" : "s")
                : String.format("%d min", mins);
        return String.format("The meeting on %s started at %s and ended at %s. Duration: %s.", date, start, end, durationStr);
    }

    /**
     * If the query doesn't specify the minute (date), ask for clarification.
     * Uses LLM to generate message in correct language.
     */
    private String generateClarificationMessage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "Which meeting minute/date do you need the duration for?";
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The question is about meeting duration but doesn't specify which meeting/date.
            
            Respond with a short clarification question in the EXACT SAME LANGUAGE as the user's question,
            asking which specific meeting or date they need the duration for.
            Be polite and helpful.
            """, query);
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating clarification message with LLM", e);
        }
        
        // Fallback
        return "Which meeting minute/date do you need the duration for?";
    }


}