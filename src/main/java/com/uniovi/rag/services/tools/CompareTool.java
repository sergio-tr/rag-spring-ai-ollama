package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.*;

/**
 * Enhanced CompareTool for comparing meeting minutes across different dimensions.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Semantic analysis instead of literal matching
 * - Support for all NER fields including comparisonType and temporalContext
 * - Multilingual support with adaptive prompts
 * - Decoupled from literal word matching
 */
public class CompareTool extends AbstractTool {

    public CompareTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        
        if (docs.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        Map<String, MinuteInfo> summary = new LinkedHashMap<>();
        
        if (ner != null) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    summary.put(date, buildMinuteInfo(content, date));
                }
            }
        } else {
            // Baseline: group and compare by heuristic
            for (Document doc : docs) {
                String content = doc.getContent();
                String date = extractDate(content);
                summary.put(date, buildMinuteInfo(content, date));
            }
        }

        if (summary.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        String comparisonType = nerHandler.determineComparisonType(query, ner);
        String comparison = compareValues(summary, comparisonType, query);
        String response = generateResponseWithLLM(query, comparison);
        
        return ToolResult.from(response, getClass());
    }

    /**
     * Builds minute information from content
     */
    private MinuteInfo buildMinuteInfo(String content, String date) {
        return new MinuteInfo(
                date,
                extractAttendeeCount(content),
                calculateDuration(content),
                countProposals(content),
                countAgendaItems(content),
                countQuestions(content),
                extractLiteralField("place", content)
        );
    }

    /**
     * Compares values based on comparison type and temporal context
     */
    private String compareValues(Map<String, MinuteInfo> summary, String comparisonType, String query) {
        // Check if we should group by temporal periods
        if (shouldGroupByTemporalPeriod(query, summary)) {
            return compareByTemporalPeriod(summary, comparisonType);
        } else {
            return compareDirectly(summary, comparisonType);
        }
    }

    /**
     * Determines if we should group by temporal periods.
     * Uses English for internal processing, but preserves original language in query.
     */
    private boolean shouldGroupByTemporalPeriod(String query, Map<String, MinuteInfo> summary) {
        if (query == null || query.trim().isEmpty() || summary == null || summary.isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And these available meeting dates:
            %s
            
            Should the comparison be grouped by temporal periods (months, quarters, years)?
            Consider if the query mentions time periods, months, or temporal groupings.
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, String.join(", ", summary.keySet()));
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in shouldGroupByTemporalPeriod, defaulting to false");
                return false;
            }
            
            String normalized = result.strip().toLowerCase();
            return normalized.contains("yes") || normalized.contains("sí");
        } catch (Exception e) {
            log().error("Error in shouldGroupByTemporalPeriod, defaulting to false", e);
            return false;
        }
    }

    /**
     * Compares values grouped by temporal periods
     */
    private String compareByTemporalPeriod(Map<String, MinuteInfo> summary, String comparisonType) {
        Map<String, List<MinuteInfo>> byPeriod = new HashMap<>();
        
        for (MinuteInfo info : summary.values()) {
            String period = extractTemporalPeriod(info.date());
            byPeriod.computeIfAbsent(period, k -> new ArrayList<>()).add(info);
        }
        
        // Summarize by period
        Map<String, Integer> valuesByPeriod = new HashMap<>();
        for (Map.Entry<String, List<MinuteInfo>> entry : byPeriod.entrySet()) {
            int value = entry.getValue().stream()
                    .mapToInt(info -> getValue(info, comparisonType))
                    .sum();
            valuesByPeriod.put(entry.getKey(), value);
        }
        
        // Generate comparative text
        return valuesByPeriod.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> String.format("In %s: %d", e.getKey(), e.getValue()))
                .collect(Collectors.joining(". "));
    }

    /**
     * Compares values directly between meetings
     */
    private String compareDirectly(Map<String, MinuteInfo> summary, String comparisonType) {
        return summary.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> String.format("In the meeting of %s: %d", e.getKey(), getValue(e.getValue(), comparisonType)))
                .collect(Collectors.joining(". "));
    }

    /**
     * Extracts temporal period from date
     */
    private String extractTemporalPeriod(String date) {
        try {
            String[] parts = date.split(" de ");
            if (parts.length >= 2) {
                return parts[1].toLowerCase();
            }
        } catch (Exception ignored) {}
        return date;
    }

    /**
     * Gets value for comparison type
     */
    private int getValue(MinuteInfo info, String comparisonType) {
        return switch (comparisonType) {
            case "attendees" -> info.attendeeCount();
            case "duration" -> info.duration();
            case "proposals" -> info.proposalCount();
            case "agenda" -> info.agendaItemCount();
            case "questions" -> info.questionCount();
            default -> info.attendeeCount(); // Default to attendees
        };
    }

    /**
     * Generates response using LLM.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateResponseWithLLM(String query, String comparison) {
        if (query == null || query.trim().isEmpty() || comparison == null || comparison.trim().isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            This is the comparison obtained:
            %s
            
            Write a clear, concise response in the same language as the query, 
            comparing the values and explaining which is greater or if there's a tie.
            """, query, comparison);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateResponseWithLLM, using fallback");
                return generateFallbackResponse(query, comparison);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating response with LLM, using fallback", e);
            return generateFallbackResponse(query, comparison);
        }
    }
    
    /**
     * Generates a fallback response when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackResponse(String query, String comparison) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "Comparación obtenida:\n" + comparison;
        } else {
            return "Comparison obtained:\n" + comparison;
        }
    }

    /**
     * Generates not found message using LLM.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundMessage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return generateFallbackNotFoundMessage("");
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Write a short message indicating that no relevant meeting minutes were found for comparison, 
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
     * Generates a fallback "not found" message when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackNotFoundMessage(String query) {
        String queryLower = query != null ? query.toLowerCase() : "";
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "No se encontraron actas de reunión relevantes para realizar la comparación solicitada.";
        } else {
            return "No relevant meeting minutes were found for the requested comparison.";
        }
    }

    /**
     * Represents minute information for comparison
     */
    private record MinuteInfo(
            String date,
            int attendeeCount,
            int duration,
            int proposalCount,
            int agendaItemCount,
            int questionCount,
            String place
    ) {
        @Override
        public String toString() {
            return String.format("Date: %s, Attendees: %d, Duration: %d min, Proposals: %d, Agenda: %d, Questions: %d, Place: %s",
                    date, attendeeCount, duration, proposalCount, agendaItemCount, questionCount, place);
        }
    }
}