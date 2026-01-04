package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataCountDocumentsTool for counting meeting minutes with intelligent analysis.
 */
public class MetadataCountDocumentsTool extends AbstractMetadataTool {

    public MetadataCountDocumentsTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing count documents query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query,
            new String[] {"date", "place", "topics", "decisions", "summary"},
            ner
        );
        
        // Step 1.5: Validate date if present in query
        String requestedDate = extractDateFromQuery(query, ner);
        if (requestedDate != null && docs.isEmpty()) {
            // Date was specified but no documents match
            String errorMessage = generateDateNotFoundMessage(query, requestedDate);
            log().info("No documents found for specified date: {} in query: {}", requestedDate, query);
            return ToolResult.from(errorMessage, getClass());
        }
        
        // Step 1.6: Filter by topic/keyword if present in query
        String topic = extractTopicFromQuery(query, ner);
        if (topic != null && !docs.isEmpty()) {
            log().info("Filtering documents by topic: {}", topic);
            List<Document> filteredDocs = filterDocumentsByTopic(docs, topic);
            if (filteredDocs.isEmpty()) {
                log().info("No documents found for topic '{}' in query: {}", topic, query);
                String errorMessage = generateSpecificErrorMessage(query, "topic", topic, docs.size(), "No documents mention this topic");
                return ToolResult.from(errorMessage, getClass());
            }
            docs = filteredDocs;
            log().info("Filtered to {} documents that mention topic '{}'", docs.size(), topic);
        }
        
        // Step 1.7: Filter by attendeesCount if query asks about number of attendees
        // Example: "En cuántas actas participaron menos de diez personas"
        AttendeesCountQueryInfo attendeesQueryInfo = detectAttendeesCountQuery(query);
        if (attendeesQueryInfo != null) {
            log().info("Query asks about number of attendees (operator={}, threshold={}), filtering documents", 
                      attendeesQueryInfo.operator, attendeesQueryInfo.threshold);
            List<Document> filteredByAttendees = filterDocumentsByAttendeesCount(attendeesQueryInfo, docs);
            log().info("Filtered {} documents by attendees count criteria, {} remaining (applied filter even if empty)", 
                      docs.size(), filteredByAttendees.size());
            docs = filteredByAttendees; // Apply filter even if empty - this indicates no matches
        }
        
        if (docs.isEmpty()) {
            log().info("No documents found for count query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for count query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for count query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Perform comprehensive counting analysis
        CountingAnalysis analysis = performCountingAnalysis(query, relevantMinutes);

        // Step 5: Generate enhanced count answer
        String answer = generateEnhancedCountAnswer(query, analysis);
        log().info("Generated count answer for query: {} with {} documents", query, analysis.getTotalCount());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Performs comprehensive counting analysis
     */
    private CountingAnalysis performCountingAnalysis(String query, List<Minute> minutes) {
        int totalCount = minutes.size();
        
        // Extract and analyze dates
        List<String> dates = extractAndAnalyzeDates(minutes);
        
        // Extract and analyze places
        List<String> places = extractAndAnalyzePlaces(minutes);
        
        // Extract and analyze topics
        List<String> topics = extractAndAnalyzeTopics(minutes);
        
        // Extract and analyze attendeesCount if query asks about attendees
        List<Integer> attendeesCounts = null;
        if (query != null && (query.toLowerCase().contains("asistente") || 
                              query.toLowerCase().contains("attendee") ||
                              query.toLowerCase().contains("participaron") ||
                              query.toLowerCase().contains("personas"))) {
            attendeesCounts = extractAndAnalyzeAttendeesCounts(minutes);
        }
        
        return new CountingAnalysis(
            totalCount,
            dates,
            places,
            topics,
            attendeesCounts
        );
    }
    
    /**
     * Extracts and analyzes attendeesCount from minutes
     */
    private List<Integer> extractAndAnalyzeAttendeesCounts(List<Minute> minutes) {
        return minutes.stream()
                .map(minute -> {
                    if (minute.numberOfAttendees() > 0) {
                        return minute.numberOfAttendees();
                    }
                    if (minute.attendees() != null && !minute.attendees().isEmpty()) {
                        return minute.attendees().size();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Extracts and analyzes dates from minutes
     */
    private List<String> extractAndAnalyzeDates(List<Minute> minutes) {
        return minutes.stream()
                .map(Minute::date)
                .filter(Objects::nonNull)
                .filter(date -> !date.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Extracts and analyzes places from minutes
     */
    private List<String> extractAndAnalyzePlaces(List<Minute> minutes) {
        return minutes.stream()
                .map(Minute::place)
                .filter(Objects::nonNull)
                .filter(place -> !place.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Extracts and analyzes topics from minutes
     */
    private List<String> extractAndAnalyzeTopics(List<Minute> minutes) {
        return minutes.stream()
                .map(Minute::topics)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Generates enhanced count answer with comprehensive analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedCountAnswer(String query, CountingAnalysis analysis) {
        if (query == null || query.trim().isEmpty() || analysis == null) {
            return generateNotFoundMessage(query);
        }
        
        String simpleData = formatSimpleData(analysis);
        
        String prompt = String.format("""
            You need to answer a question about meeting minutes. The question asked was about counting meeting minutes that meet certain criteria.
            
            Found %d relevant meeting minutes.
            
            Information:
            %s
            
            Write a clear, direct answer in the same language as the user's question (detect from context).
            CRITICAL RULES:
            1. DO NOT repeat or echo the user's question in your response.
            2. DO NOT start your answer with the question.
            3. Answer directly with the count and relevant information.
            4. Example if the query is in English: "Found 5 meeting minutes" (NOT "The question was... Found 5 meeting minutes")
            5. Example if the query is in Spanish: "Se encontraron 5 actas" (NOT "La pregunta era... Se encontraron 5 actas")
            
            Provide only the information requested.
            DO NOT mention any technical details like "análisis temporal", "análisis de distribución", "temporal analysis", "distribution analysis", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering naturally and concisely, as if you were a helpful assistant.
            """, 
            analysis.getTotalCount(),
            simpleData != null ? simpleData : "No additional information available."
        );
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedCountAnswer, using fallback");
                return generateFallbackCountAnswer(query, analysis);
            }
            
            String trimmed = response.trim();
            if (trimmed.length() < 10 || trimmed.matches("^\\d+$")) {
                log().warn("Response too short or just a number (length: {}), reformatting automatically", trimmed.length());
                return generateFallbackCountAnswer(query, analysis);
            }
            
            String cleaned = removeQuestionEcho(trimmed, query);
            return cleaned;
        } catch (Exception e) {
            log().error("Error generating enhanced count answer, using fallback", e);
            return generateFallbackCountAnswer(query, analysis);
        }
    }
    
    /**
     * Generates a fallback count answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackCountAnswer(String query, CountingAnalysis analysis) {
        if (query == null || query.trim().isEmpty()) {
            return String.format("Found %d relevant meeting minutes.", analysis.getTotalCount());
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found %d relevant meeting minutes.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating how many meeting minutes were found.
            Be concise and direct.
            Do not repeat the question.
            """, query, analysis.getTotalCount());
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback count answer with LLM", e);
        }
        
        // Ultimate fallback
        return String.format("Found %d relevant meeting minutes.", analysis.getTotalCount());
    }

    /**
     * Removes question echo from response using LLM.
     */
    private String removeQuestionEcho(String response, String query) {
        if (response == null || query == null || response.trim().isEmpty()) {
            return response;
        }
        
        // If response is very short, likely no echo
        if (response.length() < 20) {
            return response;
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The system generated this response: "%s"
            
            Task: If the response repeats or echoes the question, extract ONLY the actual answer part.
            Remove any phrases like "the question was", "la pregunta era", "the user asked", etc.
            Remove the question itself if it appears at the beginning.
            
            Return ONLY the cleaned answer, without any explanation or additional text.
            If the response doesn't echo the question, return it as-is.
            """, query, response);
        
        try {
            String cleaned = getLLMResponseCached(prompt);
            if (cleaned != null && !cleaned.trim().isEmpty()) {
                return cleaned.trim();
            }
        } catch (Exception e) {
            log().warn("Error removing question echo with LLM, returning original response", e);
        }
        
        // Fallback: return original response
        return response;
    }

    /**
     * Formats simple data for LLM prompt (without technical analysis terms)
     */
    private String formatSimpleData(CountingAnalysis analysis) {
        StringBuilder data = new StringBuilder();
        
        if (analysis.getDates() != null && !analysis.getDates().isEmpty()) {
            data.append("Fechas: ").append(String.join(", ", analysis.getDates())).append("\n");
        }
        
        if (analysis.getPlaces() != null && !analysis.getPlaces().isEmpty()) {
            data.append("Lugares: ").append(String.join(", ", analysis.getPlaces())).append("\n");
        }
        
        if (analysis.getTopics() != null && !analysis.getTopics().isEmpty()) {
            data.append("Temas principales: ").append(String.join(", ", analysis.getTopics().stream().limit(10).collect(Collectors.toList()))).append("\n");
        }
        
        if (analysis.getAttendeesCounts() != null && !analysis.getAttendeesCounts().isEmpty()) {
            String countsStr = analysis.getAttendeesCounts().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            data.append("Número de asistentes por acta: ").append(countsStr).append("\n");
        }
        
        return data.toString();
    }
    
    /**
     * Filters documents by attendeesCount based on query criteria.
     * Uses structured information from LLM detection instead of hardcoded string checks.
     */
    private List<Document> filterDocumentsByAttendeesCount(AttendeesCountQueryInfo queryInfo, List<Document> docs) {
        if (docs.isEmpty() || queryInfo == null) {
            return docs;
        }
        
        final int threshold = queryInfo.threshold;
        final String operator = queryInfo.operator;
        
        List<Document> filtered = docs.stream()
                .filter(doc -> {
                    Integer count = getAttendeesCount(doc);
                    if (count == null) {
                        return false; // Exclude documents without attendeesCount
                    }
                    
                    return switch (operator) {
                        case "less_than" -> count < threshold;
                        case "more_than" -> count > threshold;
                        case "equal" -> count == threshold;
                        default -> {
                            log().warn("Unknown operator: {}, defaulting to less_than", operator);
                            yield count < threshold;
                        }
                    };
                })
                .collect(Collectors.toList());
        
        log().info("Filtered {} documents by attendeesCount (operator: {}, threshold: {}), {} remaining", 
                  docs.size(), operator, threshold, filtered.size());
        
        return filtered;
    }

}
