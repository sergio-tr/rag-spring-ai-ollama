package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;
import static com.uniovi.rag.utils.InfoExtractor.extractTime;
import static com.uniovi.rag.utils.InfoExtractor.calculateDuration;

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

    public GetDurationTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing get duration query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
        
        List<Document> docs = retrieveDocuments(query);
        log().debug("Retrieved {} documents for get duration query", docs.size());
        List<MeetingDuration> durations = new ArrayList<>();

        // Try with NER filtering if available
        if (ner != null && !docs.isEmpty()) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    MeetingDuration duration = extractMeetingDuration(doc);
                    if (duration != null && duration.durationMinutes > 0) {
                        durations.add(duration);
                    }
                }
            }
        }
        
        if (durations.isEmpty() && !docs.isEmpty()) {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (isRelevantByLLM(doc.getContent(), query)) {
                    MeetingDuration duration = extractMeetingDuration(doc);
                    if (duration != null && duration.durationMinutes > 0) {
                        durations.add(duration);
                    }
                }
            }
        }
        
        if (durations.isEmpty()) {
            docs = retrieveAllDocuments(query);
            if (!docs.isEmpty()) {
                for (Document doc : docs) {
                    if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                        continue;
                    }
                    
                    if (isRelevantByLLM(doc.getContent(), query)) {
                        MeetingDuration duration = extractMeetingDuration(doc);
                        if (duration != null && duration.durationMinutes > 0) {
                            durations.add(duration);
                        }
                    }
                }
            }
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

    /**
     * Determines if content is relevant to query using LLM.
     * Uses English for internal processing, but preserves original language in query and content.
     */
    private boolean isRelevantByLLM(String content, String query) {
        if (content == null || content.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String contentSnippet = content.substring(0, Math.min(1000, content.length()));
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And the following meeting minutes content (may be in any language):
            "%s"
            
            Does this minutes document match all the conditions in the query?
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, contentSnippet);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in isRelevantByLLM, defaulting to false");
                return false;
            }
            
            // Use LLM to interpret boolean response
            return interpretBooleanResponse(result, "isRelevantByLLM");
        } catch (Exception e) {
            log().error("Error in isRelevantByLLM, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Extracts meeting duration from document with validation
     */
    private MeetingDuration extractMeetingDuration(Document doc) {
        if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
            log().debug("Cannot extract duration: document is null or empty");
            return null;
        }
        
        String content = doc.getContent();
        String date = extractDate(content);
        String startTime = extractTime(content, "start");
        String endTime = extractTime(content, "end");
        
        // Validate times
        if (startTime == null || startTime.trim().isEmpty()) {
            log().debug("Cannot extract duration: startTime is null or empty for document {}", doc.getId());
            return null;
        }
        
        if (endTime == null || endTime.trim().isEmpty()) {
            log().debug("Cannot extract duration: endTime is null or empty for document {}", doc.getId());
            return null;
        }
        
        int duration = calculateDuration(content);
        
        // Validate duration (should be between 1 minute and 24 hours)
        if (duration <= 0) {
            log().debug("Invalid duration: {} minutes (too short) for document {}", duration, doc.getId());
            return null;
        }
        if (duration > 24 * 60) {
            log().warn("Invalid duration: {} minutes (too long, >24h) for document {}", duration, doc.getId());
            return null;
        }
        
        log().debug("Extracted duration for document {}: {} minutes ({} - {})", 
                   doc.getId(), duration, startTime, endTime);
        
        return new MeetingDuration(date, startTime, endTime, duration);
    }

    /**
     * Generates final answer with found durations.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateFinalAnswer(String query, List<MeetingDuration> durations) {
        if (query == null || query.trim().isEmpty() || durations == null || durations.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        boolean isComparison = isComparisonQuery(query);
        if (isComparison) {
            MeetingDuration result = getComparisonResult(query, durations);
            if (result != null) {
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
                    """, query, durations.stream().map(MeetingDuration::toString).collect(Collectors.joining("\n")));
                
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
                    
                    // Apply formatResponse to clean and format the response
                    return formatResponse(response.strip(), query);
                } catch (Exception e) {
                    log().error("Error generating final answer (comparison), using fallback", e);
                    return generateFallbackAnswer(query, durations, result);
                }
            }
        }
        
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
            """, query, durations.stream().map(MeetingDuration::toString).collect(Collectors.joining("\n")));
        
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
            
            // Apply formatResponse to clean and format the response
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
            // Extract the first word
            String cleaned = normalized.split("\\s+")[0].trim();
            
            if (cleaned.contains("shortest") || cleaned.contains("corta") || cleaned.contains("menor")) {
                return durations.stream().min(Comparator.comparingInt(d -> d.durationMinutes)).orElse(null);
            } else {
                return durations.stream().max(Comparator.comparingInt(d -> d.durationMinutes)).orElse(null);
            }
        } catch (Exception e) {
            log().error("Error in getComparisonResult, defaulting to longest", e);
            return durations.stream().max(Comparator.comparingInt(d -> d.durationMinutes)).orElse(null);
        }
    }

    /**
     * Generates not found message.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundMessage(String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Write a short message indicating that no information was found related to the query, 
            in the same language as the query.
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                return generateFallbackNotFoundMessage(query);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating not found message, using fallback", e);
            return generateFallbackNotFoundMessage(query);
        }
    }
    
    /**
     * Interprets LLM response as boolean using another LLM call.
     */
    private boolean interpretBooleanResponse(String response, String context) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Context: %s
            
            The LLM generated this response: "%s"
            
            Task: Interpret this response as a boolean answer.
            - If it means YES/TRUE/POSITIVE, respond with: YES
            - If it means NO/FALSE/NEGATIVE, respond with: NO
            
            Consider semantic meaning, not just exact words.
            
            Respond with ONLY one word: YES or NO.
            """, context, response);
        
        try {
            String interpretation = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toUpperCase();
            
            return interpretation.contains("YES");
        } catch (Exception e) {
            log().warn("Error interpreting boolean response in {}, defaulting to false", context, e);
            return false;
        }
    }

    /**
     * Generates a fallback answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackAnswer(String query, List<MeetingDuration> durations, MeetingDuration comparisonResult) {
        String durationsText = durations.stream()
                .limit(5)
                .map(d -> String.format("- %s: %d minutes", d.date != null ? d.date : "unknown date", d.durationMinutes))
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
                comparisonResult.date != null ? comparisonResult.date : "unknown date",
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
                               comparisonResult.date != null ? comparisonResult.date : "unknown date",
                               comparisonResult.durationMinutes);
        } else {
            return "Durations found:\n" + durationsText;
        }
    }
    
    /**
     * Generates a fallback "not found" message when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackNotFoundMessage(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No information about meeting durations was found in the available documents for this query.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating that no duration information was found.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "");
        
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
            log().warn("Error generating fallback not found message with LLM", e);
        }
        
        // Ultimate fallback
        return "No information about meeting durations was found in the available documents for this query.";
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
