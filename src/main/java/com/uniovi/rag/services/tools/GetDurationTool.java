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
        
        log().info("Executing get duration query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
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
            answer = generateFinalAnswer(query, durations);
        } else {
            answer = generateNotFoundMessage(query);
        }
        return ToolResult.from(answer, getClass());
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
            
            String normalized = result.strip().toLowerCase();
            // Check for positive responses in multiple languages
            return normalized.startsWith("yes") || normalized.startsWith("sí") || normalized.startsWith("si") || 
                   normalized.startsWith("oui") || normalized.startsWith("ja") || normalized.startsWith("da");
        } catch (Exception e) {
            log().error("Error in isRelevantByLLM, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Extracts meeting duration from document
     */
    private MeetingDuration extractMeetingDuration(Document doc) {
        String content = doc.getContent();
        String date = extractDate(content);
        String startTime = extractTime(content, "start");
        String endTime = extractTime(content, "end");
        int duration = calculateDuration(content);
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
                    """, query, durations.stream().map(MeetingDuration::toString).collect(Collectors.joining("\n")));
                
                try {
                    String response = chatClient
                            .prompt()
                            .user(prompt)
                            .call()
                            .content();
                    
                    if (response == null || response.trim().isEmpty()) {
                        log().warn("Empty response from LLM in generateFinalAnswer (comparison), using fallback");
                        return generateFallbackAnswer(query, durations, result);
                    }
                    
                    return response.strip();
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
            
            Write a brief and clear answer, in the same language as the query, 
            indicating the duration and details of each meeting found.
            """, query, durations.stream().map(MeetingDuration::toString).collect(Collectors.joining("\n")));
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateFinalAnswer, using fallback");
                return generateFallbackAnswer(query, durations, null);
            }
            
            return response.strip();
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
            
            String normalized = result.strip().toLowerCase();
            // Check for positive responses in multiple languages
            return normalized.startsWith("yes") || normalized.startsWith("sí") || normalized.startsWith("si") || 
                   normalized.startsWith("oui") || normalized.startsWith("ja") || normalized.startsWith("da");
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
     * Generates a fallback answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackAnswer(String query, List<MeetingDuration> durations, MeetingDuration comparisonResult) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (comparisonResult != null) {
            if (isSpanish) {
                return String.format("La reunión con mayor/menor duración fue la del %s, con una duración de %d minutos.",
                                   comparisonResult.date != null ? comparisonResult.date : "fecha desconocida",
                                   comparisonResult.durationMinutes);
            } else {
                return String.format("The meeting with longest/shortest duration was on %s, with a duration of %d minutes.",
                                   comparisonResult.date != null ? comparisonResult.date : "unknown date",
                                   comparisonResult.durationMinutes);
            }
        } else {
            if (isSpanish) {
                return "Duraciones encontradas:\n" + 
                       durations.stream()
                               .limit(5)
                               .map(d -> String.format("- %s: %d minutos", d.date != null ? d.date : "fecha desconocida", d.durationMinutes))
                               .collect(Collectors.joining("\n"));
            } else {
                return "Durations found:\n" + 
                       durations.stream()
                               .limit(5)
                               .map(d -> String.format("- %s: %d minutes", d.date != null ? d.date : "unknown date", d.durationMinutes))
                               .collect(Collectors.joining("\n"));
            }
        }
    }
    
    /**
     * Generates a fallback "not found" message when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackNotFoundMessage(String query) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "No se encontró información sobre la duración de las reuniones en los documentos disponibles para esta consulta.";
        } else {
            return "No information about meeting durations was found in the available documents for this query.";
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
