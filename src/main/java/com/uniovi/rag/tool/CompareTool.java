package com.uniovi.rag.tool;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;

import java.util.*;
import java.util.stream.Collectors;

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

    public CompareTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing compare query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
        
        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for compare query", docs.size());
        
        if (docs.isEmpty()) {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No documents found for compare query: '{}' (execution time: {} ms)", query, totalTime);
            String notFound = generateNotFoundMessage(query);
            String formattedNotFound = formatResponse(notFound, query);
            return ToolResult.from(formattedNotFound, getClass());
        }

        Map<String, MinuteInfo> summary = new LinkedHashMap<>();
        
        if (ner != null) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getText();
                    String date = extractor.extractDate(content);
                    summary.put(date, buildMinuteInfo(content, date));
                }
            }
        } else {
            // Baseline: group and compare by heuristic
            for (Document doc : docs) {
                String content = doc.getText();
                String date = extractor.extractDate(content);
                summary.put(date, buildMinuteInfo(content, date));
            }
        }

        if (summary.isEmpty()) {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No summary data for compare query: '{}' (execution time: {} ms)", query, totalTime);
            String notFound = generateNotFoundMessage(query);
            String formattedNotFound = formatResponse(notFound, query);
            return ToolResult.from(formattedNotFound, getClass());
        }

        log().debug("Built summary with {} entries for compare query", summary.size());
        String comparisonType = nerHandler.determineComparisonType(query, ner);
        String comparison = compareValues(summary, comparisonType, query);
        String response = generateResponseWithLLM(query, comparison);
        
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated compare answer for query: '{}' (execution time: {} ms, comparison type: {})", 
                  query, totalTime, comparisonType);
        // Apply formatResponse to clean the response
        String formattedResponse = formatResponse(response, query);
        return ToolResult.from(formattedResponse, getClass());
    }

    /**
     * Builds minute information from content
     */
    private MinuteInfo buildMinuteInfo(String content, String date) {
        if (extractor == null) {
            return new MinuteInfo(date, 0, 0, 0, 0, 0, null);
        }
        return new MinuteInfo(
                date,
                extractor.extractAttendeeCount(content),
                extractor.calculateDuration(content),
                extractor.countProposals(content),
                extractor.countAgendaItems(content),
                extractor.countQuestions(content),
                extractor.extractLiteralField("place", content)
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
            
            // Use LLM to interpret boolean response
            return interpretBooleanResponse(result, "shouldGroupByTemporalPeriod");
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
        switch (comparisonType) {
            case "attendees":
                return info.attendeeCount();
            case "duration":
                return info.duration();
            case "proposals":
                return info.proposalCount();
            case "agenda":
                return info.agendaItemCount();
            case "questions":
                return info.questionCount();
            default:
                return info.attendeeCount(); // Default to attendees
        }
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
            
            Comparison data:
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "comparison obtained", "análisis", "analysis", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            DO NOT repeat the question or any part of it at the beginning.
            DO NOT start with phrases like "Dime qué...", "The user asked...", etc.
            Start directly with the answer content.
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            Be concise and direct - maximum 3-4 sentences.
            """, query, comparison);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateResponseWithLLM for query: '{}', using fallback", query);
                return generateFallbackResponse(query, comparison);
            }
            
            // Apply formatResponse to clean and format the response
            return formatResponse(response.strip(), query);
        } catch (Exception e) {
            log().error("Error generating response with LLM, using fallback", e);
            return generateFallbackResponse(query, comparison);
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
     * Generates a fallback response when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackResponse(String query, String comparison) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Comparison obtained:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            presenting the comparison results.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", comparison != null ? comparison : "");
        
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
            log().warn("Error generating fallback response with LLM", e);
        }
        
        // Ultimate fallback
        return "Comparison obtained:\n" + (comparison != null ? comparison : "");
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