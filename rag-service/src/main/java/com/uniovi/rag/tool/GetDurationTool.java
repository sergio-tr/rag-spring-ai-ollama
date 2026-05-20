package com.uniovi.rag.tool;

import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

/**
 * Enhanced GetDurationTool for retrieving meeting durations with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic duration relevance evaluation
 * - Enhanced duration extraction and comparison
 */
public class GetDurationTool extends AbstractTool {

    private static final String PLACEHOLDER_UNKNOWN_DATE = "unknown date";

    /** Fallback label when metadata has no date but times are present (display convention). */
    private static final String METADATA_FALLBACK_UNKNOWN_DATE = "Unknown date";

    public GetDurationTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        log().info("Executing get duration query: '{}' with NER: {}",
                query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();

        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for get duration query", docs.size());
        List<MeetingDuration> durations = new ArrayList<>();

        if (ner != null && !docs.isEmpty()) {
            collectDurationsWithNerMatch(durations, docs, ner);
        }

        if (durations.isEmpty() && !docs.isEmpty()) {
            collectDurationsWithLlmRelevance(durations, docs, query);
        }

        if (durations.isEmpty()) {
            collectDurationsWithLlmRelevance(durations, retrieveAllDocuments(query, ner), query);
        }

        String answer;
        if (!durations.isEmpty()) {
            log().debug("Found {} durations for query, generating answer", durations.size());
            answer = generateFinalAnswer(query, durations);
        } else {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No durations found for query: '{}' (execution time: {} ms)", query, totalTime);
            answer = generateNotFoundMessage(query);
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated get duration answer for query: '{}' (execution time: {} ms)", query, totalTime);
        // Apply formatResponse to clean the response
        String formattedAnswer = formatResponse(answer, query);
        return ToolResult.from(formattedAnswer, getClass());
    }

    private void addDurationIfPositive(List<MeetingDuration> out, Document doc) {
        MeetingDuration duration = extractMeetingDuration(doc);
        if (duration != null && duration.durationMinutes > 0) {
            out.add(duration);
        }
    }

    private void collectDurationsWithNerMatch(List<MeetingDuration> out, List<Document> docs, JSONObject ner) {
        List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
        for (Document doc : filteredDocs) {
            if (doc != null && doc.getText() != null && !doc.getText().trim().isEmpty()
                    && nerHandler.matchesDocumentWithNER(doc, ner)) {
                addDurationIfPositive(out, doc);
            }
        }
    }

    private void collectDurationsWithLlmRelevance(List<MeetingDuration> out, List<Document> docs, String query) {
        for (Document doc : docs) {
            if (doc != null && doc.getText() != null && !doc.getText().trim().isEmpty()
                    && isRelevantByLLM(doc.getText(), query)) {
                addDurationIfPositive(out, doc);
            }
        }
    }

    /** Prefer metadata startTime/endTime when present (avoids "No durations found" when data exists in metadata). */
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");

    /**
     * Extracts meeting duration from document with validation.
     * Uses metadata startTime/endTime when available (P2), otherwise parses content.
     */
    private MeetingDuration extractMeetingDuration(Document doc) {
        if (doc == null) {
            log().debug("Cannot extract duration: document is null");
            return null;
        }
        // Try metadata first (documents may have startTime/endTime from MetadataMinuteDocumentService)
        Map<String, Object> meta = doc.getMetadata();
        Object startObj = meta.get("startTime");
        Object endObj = meta.get("endTime");
        if (startObj != null && endObj != null) {
            String startTime = startObj.toString().trim();
            String endTime = endObj.toString().trim();
            if (!startTime.isEmpty() && !endTime.isEmpty()) {
                int durationMinutes = durationMinutesFromTimes(startTime, endTime);
                if (durationMinutes > 0 && durationMinutes <= 24 * 60) {
                    String date = null;
                    if (meta.containsKey("date_iso")) date = meta.get("date_iso").toString().trim();
                    else if (meta.containsKey("date")) date = meta.get("date").toString().trim();
                    if (date == null || date.isEmpty()) {
                        date = METADATA_FALLBACK_UNKNOWN_DATE;
                    }
                    log().debug("Extracted duration from metadata for document {}: {} minutes ({} - {})",
                            doc.getId(), durationMinutes, startTime, endTime);
                    return new MeetingDuration(date, startTime, endTime, durationMinutes);
                }
            }
        }
        // Fallback: content-based extraction
        if (doc.getText() == null || doc.getText().trim().isEmpty()) {
            log().debug("Cannot extract duration: document content is null or empty");
            return null;
        }
        String content = doc.getText();
        String date = extractor.extractDate(content);
        String startTime = extractor.extractTime(content, "start");
        String endTime = extractor.extractTime(content, "end");
        if (startTime == null || startTime.trim().isEmpty()) {
            log().debug("Cannot extract duration: startTime is null or empty for document {}", doc.getId());
            return null;
        }
        if (endTime == null || endTime.trim().isEmpty()) {
            log().debug("Cannot extract duration: endTime is null or empty for document {}", doc.getId());
            return null;
        }
        int duration = extractor.calculateDuration(content);
        if (duration <= 0) {
            log().debug("Invalid duration: {} minutes (too short) for document {}", duration, doc.getId());
            return null;
        }
        if (duration > 24 * 60) {
            log().warn("Invalid duration: {} minutes (too long, >24h) for document {}", duration, doc.getId());
            return null;
        }
        log().debug("Extracted duration for document {}: {} minutes ({} - {})", doc.getId(), duration, startTime, endTime);
        return new MeetingDuration(date, startTime, endTime, duration);
    }

    /** Parses "HH:mm" or "H:mm" and returns duration in minutes; 0 if invalid. */
    private int durationMinutesFromTimes(String startTime, String endTime) {
        Matcher startM = TIME_PATTERN.matcher(startTime);
        Matcher endM = TIME_PATTERN.matcher(endTime);
        if (startM.find() && endM.find()) {
            int startMin = Integer.parseInt(startM.group(1)) * 60 + Integer.parseInt(startM.group(2));
            int endMin = Integer.parseInt(endM.group(1)) * 60 + Integer.parseInt(endM.group(2));
            int d = endMin - startMin;
            return d > 0 ? d : 0;
        }
        return 0;
    }

    /**
     * Generates final answer with found durations.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateFinalAnswer(String query, List<MeetingDuration> durations) {
        if (query == null || query.trim().isEmpty() || durations == null || durations.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        if (isComparisonQuery(query)) {
            MeetingDuration result = getComparisonResult(query, durations);
            if (result != null) {
                return generateComparisonFinalAnswer(query, durations, result);
            }
        }

        return generateNonComparisonFinalAnswer(query, durations);
    }

    private String formatMeetingsBlock(List<MeetingDuration> durations) {
        return durations.stream().map(MeetingDuration::toString).collect(Collectors.joining("\n"));
    }

    private String generateComparisonFinalAnswer(String query, List<MeetingDuration> durations, MeetingDuration result) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"

            The following meetings were found (date, start, end, duration in minutes):
            %s

            Write a brief and clear answer, in the same language as the query,
            indicating which meeting had the longest/shortest duration and its details.
            DO NOT repeat the question or any part of it.
            Start directly with the answer content.
            Be concise and direct.
            """, query, formatMeetingsBlock(durations));

        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateFinalAnswer (comparison) for query: '{}', using fallback", query);
                return generateFallbackAnswer(query, durations, result);
            }

            return formatResponse(response.strip(), query);
        } catch (Exception e) {
            log().error("Error generating final answer (comparison), using fallback", e);
            return generateFallbackAnswer(query, durations, result);
        }
    }

    private String generateNonComparisonFinalAnswer(String query, List<MeetingDuration> durations) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"

            The following meetings were found (date, start, end, duration in minutes):
            %s

            CRITICAL RULES:
            1. Write in the EXACT SAME LANGUAGE as the user's question
            2. Be CONCISE - maximum 2-3 sentences per meeting
            3. Do NOT repeat the question
            4. Focus on the duration and key details
            5. If multiple meetings, summarize each briefly

            Write a brief and clear answer, in the same language as the query,
            indicating the duration and details of each meeting found.
            DO NOT repeat the question or any part of it.
            Start directly with the answer content.
            Be concise and direct.
            """, query, formatMeetingsBlock(durations));

        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateFinalAnswer for query: '{}', using fallback", query);
                return generateFallbackAnswer(query, durations, null);
            }

            return formatResponse(response.strip(), query);
        } catch (Exception e) {
            log().error("Error generating final answer, using fallback", e);
            return generateFallbackAnswer(query, durations, null);
        }
    }

    /**
     * Determines if query is asking for comparison.
     * Uses English for internal processing, but preserves original language in query.
     */
    private boolean isComparisonQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Does the query ask for a comparison (e.g., longest, shortest, most, least, etc.)?
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in isComparisonQuery, defaulting to false");
                return false;
            }
            
            // Use LLM to interpret boolean response
            return interpretBooleanResponse(result, "isComparisonQuery");
        } catch (Exception e) {
            log().error("Error in isComparisonQuery, defaulting to false", e);
            return false;
        }
    }

    /**
     * Gets comparison result from durations.
     * Uses English for internal processing, but preserves original language in query.
     */
    private MeetingDuration getComparisonResult(String query, List<MeetingDuration> durations) {
        if (query == null || query.trim().isEmpty() || durations == null || durations.isEmpty()) {
            return null;
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Does the query ask for the longest or the shortest duration?
            
            Respond with ONLY one word in English: "longest" or "shortest".
            Do not include any explanation or additional text.
            """, query);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in getComparisonResult, defaulting to longest");
                return durations.stream().max(Comparator.comparingInt(d -> d.durationMinutes)).orElse(null);
            }
            
            String normalized = result.strip().toLowerCase();
            String cleaned = normalized.split("\\s+")[0].trim();
            return pickComparisonMeetingDuration(cleaned, durations);
        } catch (Exception e) {
            log().error("Error in getComparisonResult, defaulting to longest", e);
            return durations.stream().max(Comparator.comparingInt(d -> d.durationMinutes)).orElse(null);
        }
    }

    private static MeetingDuration pickComparisonMeetingDuration(String cleaned, List<MeetingDuration> durations) {
        if (cleaned.contains("shortest") || cleaned.contains("corta") || cleaned.contains("menor")) {
            return durations.stream().min(Comparator.comparingInt(d -> d.durationMinutes)).orElse(null);
        }
        return durations.stream().max(Comparator.comparingInt(d -> d.durationMinutes)).orElse(null);
    }

    /**
     * Generates a fallback answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackAnswer(String query, List<MeetingDuration> durations, MeetingDuration comparisonResult) {
        String durationsText = durations.stream()
                .limit(5)
                .map(d -> String.format("- %s: %d minutes", d.date != null ? d.date : PLACEHOLDER_UNKNOWN_DATE, d.durationMinutes))
                .collect(Collectors.joining("\n"));
        
        String prompt;
        if (comparisonResult != null) {
            prompt = String.format("""
                The user asked (in any language): "%s"
                
                The meeting with longest/shortest duration was on %s, with a duration of %d minutes.
                
                Respond with a short message in the EXACT SAME LANGUAGE as the question,
                stating the comparison result.
                Be concise and direct.
                Do not repeat the question.
                """, query != null ? query : "", 
                comparisonResult.date != null ? comparisonResult.date : PLACEHOLDER_UNKNOWN_DATE,
                comparisonResult.durationMinutes);
        } else {
            prompt = String.format("""
                The user asked (in any language): "%s"
                
                Found the following durations:
                %s
                
                Respond with a short message in the EXACT SAME LANGUAGE as the question,
                listing the found durations.
                Be concise and direct.
                Do not repeat the question.
                """, query != null ? query : "", durationsText);
        }
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback answer with LLM", e);
        }
        
        // Ultimate fallback
        if (comparisonResult != null) {
            return String.format("The meeting with longest/shortest duration was on %s, with a duration of %d minutes.",
                               comparisonResult.date != null ? comparisonResult.date : PLACEHOLDER_UNKNOWN_DATE,
                               comparisonResult.durationMinutes);
        } else {
            return "Durations found:\n" + durationsText;
        }
    }

    private static class MeetingDuration {
        String date;
        String startTime;
        String endTime;
        int durationMinutes;

        public MeetingDuration(String date, String startTime, String endTime, int durationMinutes) {
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
        }

        @Override
        public String toString() {
            return date + ", " + (startTime != null ? startTime : "?") + " - " + (endTime != null ? endTime : "?") + ", " + durationMinutes + " minutos";
        }
    }
}
