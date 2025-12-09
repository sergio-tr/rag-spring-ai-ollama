package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataCompareTool for comparing meeting minutes across different dimensions.
 */
public class MetadataCompareTool extends AbstractMetadataTool {

    public MetadataCompareTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing comparison query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "numberOfAttendees", "topics", "decisions", "summary"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().info("No documents found for comparison query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for comparison query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for comparison query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Infer comparison field with enhanced analysis
        ComparisonField fieldToCompare = inferComparisonFieldEnhanced(query, ner, relevantMinutes);
        if (fieldToCompare == null) {
            log().info("Could not infer comparison field for query: {}", query);
            return ToolResult.from(generateUnknownFieldMessage(query), getClass());
        }

        // Step 5: Extract comparison data in parallel
        Map<String, ComparisonValue> comparables = extractComparisonDataInParallel(relevantMinutes, fieldToCompare, ner);
        if (comparables.isEmpty()) {
            log().info("No comparison data found for field: {}", fieldToCompare.fieldName);
            return ToolResult.from(generateNoDataMessage(fieldToCompare.fieldName, query), getClass());
        }

        // Step 6: Perform statistical analysis
        ComparisonAnalysis analysis = performStatisticalAnalysis(comparables, fieldToCompare);

        // Step 7: Generate enhanced comparison answer
        String answer = generateEnhancedComparisonAnswer(query, fieldToCompare, comparables, analysis);
        log().info("Generated comparison answer for query: {} with {} data points", query, comparables.size());
        
        return ToolResult.from(answer, getClass());
    }


    /**
     * Enhanced field inference with context analysis
     */
    private ComparisonField inferComparisonFieldEnhanced(String query, JSONObject ner, List<Minute> minutes) {
        // First try rule-based inference for common cases
        ComparisonField ruleBasedField = inferFieldByRules(query);
        if (ruleBasedField != null) {
            log().info("Inferred field by rules: {}", ruleBasedField.fieldName);
            return ruleBasedField;
        }

        // If rule-based fails, pick the best available field based on data availability
        return inferFieldByAvailability(query, minutes);
    }

    /**
     * Rule-based field inference for common comparison patterns
     */
    private ComparisonField inferFieldByRules(String query) {
        String queryLower = query.toLowerCase();
        
        // Attendees patterns
        if (queryLower.contains("asistentes") || queryLower.contains("attendees") || 
            queryLower.contains("personas") || queryLower.contains("people")) {
            return new ComparisonField("numberOfAttendees", ComparisonType.NUMERIC);
        }
        
        // Duration patterns
        if (queryLower.contains("duración") || queryLower.contains("duration") || 
            queryLower.contains("tiempo") || queryLower.contains("time") ||
            queryLower.contains("horas") || queryLower.contains("hours")) {
            return new ComparisonField("duration", ComparisonType.NUMERIC);
        }
        
        // Date patterns
        if (queryLower.contains("fecha") || queryLower.contains("date") || 
            queryLower.contains("cuándo") || queryLower.contains("when")) {
            return new ComparisonField("date", ComparisonType.DATE);
        }
        
        // Place patterns
        if (queryLower.contains("lugar") || queryLower.contains("place") || 
            queryLower.contains("dónde") || queryLower.contains("where")) {
            return new ComparisonField("place", ComparisonType.TEXT);
        }
        
        // Topics patterns
        if (queryLower.contains("temas") || queryLower.contains("topics") || 
            queryLower.contains("asuntos") || queryLower.contains("subjects")) {
            return new ComparisonField("topics", ComparisonType.COUNT);
        }
        
        // Decisions patterns
        if (queryLower.contains("decisiones") || queryLower.contains("decisions") || 
            queryLower.contains("acuerdos") || queryLower.contains("agreements")) {
            return new ComparisonField("decisions", ComparisonType.COUNT);
        }
        
        return null;
    }

    /**
     * Field inference based on data availability (no LLM).
     * Chooses the field with highest availability among meaningful options.
     */
    private ComparisonField inferFieldByAvailability(String query, List<Minute> minutes) {
        if (query == null || query.trim().isEmpty() || minutes == null || minutes.isEmpty()) {
            return null;
        }

        Map<String, Integer> availability = analyzeFieldAvailability(minutes);

        // Choose the field with highest availability among meaningful options
        return availability.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(e -> switch (e.getKey()) {
                    case "numberOfAttendees" -> new ComparisonField("numberOfAttendees", ComparisonType.NUMERIC);
                    case "duration" -> new ComparisonField("duration", ComparisonType.NUMERIC);
                    case "date" -> new ComparisonField("date", ComparisonType.DATE);
                    case "place" -> new ComparisonField("place", ComparisonType.TEXT);
                    case "topics" -> new ComparisonField("topics", ComparisonType.COUNT);
                    case "decisions" -> new ComparisonField("decisions", ComparisonType.COUNT);
                    default -> null;
                })
                .orElse(null);
    }

    /**
     * Analyzes field availability in minutes
     */
    private Map<String, Integer> analyzeFieldAvailability(List<Minute> minutes) {
        Map<String, Integer> availability = new HashMap<>();
        String[] fields = {"numberOfAttendees", "duration", "date", "place", "topics", "decisions"};
        
        for (String field : fields) {
            int count = 0;
            for (Minute minute : minutes) {
                if (hasValidFieldData(minute, field)) {
                    count++;
                }
            }
            availability.put(field, count);
        }
        
        return availability;
    }

    /**
     * Checks if minute has valid data for a field
     */
    private boolean hasValidFieldData(Minute minute, String field) {
        return switch (field) {
            case "numberOfAttendees" -> minute.numberOfAttendees() > 0 ||
                    (minute.attendees() != null && !minute.attendees().isEmpty());
            case "duration" -> calculateDurationFromMinute(minute) > 0;
            case "date" -> minute.date() != null && !minute.date().isBlank();
            case "place" -> minute.place() != null && !minute.place().isBlank();
            case "topics" -> minute.topics() != null && !minute.topics().isEmpty();
            case "decisions" -> minute.decisions() != null && !minute.decisions().isEmpty();
            default -> false;
        };
    }

    /**
     * Extracts comparison data in parallel
     */
    private Map<String, ComparisonValue> extractComparisonDataInParallel(List<Minute> minutes, ComparisonField field, JSONObject ner) {
        List<CompletableFuture<Map.Entry<String, ComparisonValue>>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractComparisonValue(minute, field, ner)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (existing, replacement) -> existing, // Keep first occurrence
                    LinkedHashMap::new
                ));
    }

    /**
     * Extracts comparison value from a minute
     */
    private Map.Entry<String, ComparisonValue> extractComparisonValue(Minute minute, ComparisonField field, JSONObject ner) {
        String label = buildEnhancedLabel(minute);
        Object value = extractFieldValue(minute, field);
        
        if (label != null && value != null) {
            return new AbstractMap.SimpleEntry<>(label, new ComparisonValue(value, field.type));
        }
        
        return null;
    }

    /**
     * Builds enhanced label for comparison
     */
    private String buildEnhancedLabel(Minute minute) {
        StringBuilder label = new StringBuilder();
        
        if (minute.date() != null) {
            label.append(minute.date());
        }
        
        if (minute.place() != null) {
            if (label.length() > 0) label.append(" - ");
            label.append(minute.place());
        }
        
        if (minute.filename() != null) {
            if (label.length() > 0) label.append(" - ");
            label.append(minute.filename());
        }
        
        // Add president if available and relevant
        if (minute.president() != null && label.length() < 50) {
            if (label.length() > 0) label.append(" (");
            label.append(minute.president());
            label.append(")");
        }
        
        return label.length() > 0 ? label.toString() : minute.id();
    }

    /**
     * Extracts field value based on field type
     */
    private Object extractFieldValue(Minute minute, ComparisonField field) {
        return switch (field.fieldName) {
            case "numberOfAttendees" -> minute.numberOfAttendees() > 0
                    ? minute.numberOfAttendees()
                    : (minute.attendees() != null ? minute.attendees().size() : 0);
            case "duration" -> calculateDurationFromMinute(minute);
            case "date" -> minute.date();
            case "place" -> minute.place();
            case "topics" -> minute.topics() != null ? minute.topics().size() : 0;
            case "decisions" -> minute.decisions() != null ? minute.decisions().size() : 0;
            default -> null;
        };
    }

    /**
     * Performs statistical analysis on comparison data
     */
    private ComparisonAnalysis performStatisticalAnalysis(Map<String, ComparisonValue> comparables, ComparisonField field) {
        if (field.type != ComparisonType.NUMERIC && field.type != ComparisonType.COUNT) {
            return new ComparisonAnalysis(null, null, null);
        }

        List<Double> numericValues = comparables.values().stream()
                .map(cv -> {
                    if (cv.value instanceof Number) {
                        return ((Number) cv.value).doubleValue();
                    } else if (cv.value instanceof String) {
                        try {
                            return Double.parseDouble((String) cv.value);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (numericValues.isEmpty()) {
            return new ComparisonAnalysis(null, null, null);
        }

        double min = Collections.min(numericValues);
        double max = Collections.max(numericValues);
        double avg = numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return new ComparisonAnalysis(min, max, avg);
    }

    /**
     * Generates enhanced comparison answer with statistical insights.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedComparisonAnswer(String query, ComparisonField field, 
                                                   Map<String, ComparisonValue> comparables, 
                                                   ComparisonAnalysis analysis) {
        if (query == null || query.trim().isEmpty() || field == null || 
            comparables == null || comparables.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String comparisonData = formatComparisonData(comparables, field);
        String simpleStats = formatSimpleStats(analysis, field);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Comparison data:
            %s
            
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "statistical analysis", "análisis estadístico", "comparison data", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, comparisonData, simpleStats != null ? simpleStats : "");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedComparisonAnswer, using fallback");
                return generateFallbackComparisonAnswer(query, comparables, analysis);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced comparison answer, using fallback", e);
            return generateFallbackComparisonAnswer(query, comparables, analysis);
        }
    }
    
    /**
     * Generates a fallback comparison answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackComparisonAnswer(String query, Map<String, ComparisonValue> comparables, ComparisonAnalysis analysis) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            StringBuilder answer = new StringBuilder();
            answer.append("Comparación obtenida:\n");
            comparables.entrySet().stream().limit(5).forEach(entry -> 
                answer.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue().value)));
            if (analysis != null && analysis.min != null) {
                answer.append(String.format("\nEstadísticas: Min=%d, Max=%d, Promedio=%.1f", 
                    analysis.min.intValue(), analysis.max.intValue(), analysis.avg));
            }
            return answer.toString();
        } else {
            StringBuilder answer = new StringBuilder();
            answer.append("Comparison obtained:\n");
            comparables.entrySet().stream().limit(5).forEach(entry -> 
                answer.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue().value)));
            if (analysis != null && analysis.min != null) {
                answer.append(String.format("\nStatistics: Min=%d, Max=%d, Average=%.1f", 
                    analysis.min.intValue(), analysis.max.intValue(), analysis.avg));
            }
            return answer.toString();
        }
    }

    /**
     * Formats comparison data for LLM prompt
     */
    private String formatComparisonData(Map<String, ComparisonValue> comparables, ComparisonField field) {
        return comparables.entrySet().stream()
                .sorted(Map.Entry.comparingByValue((a, b) -> {
                    if (a.value instanceof Number && b.value instanceof Number) {
                        return Double.compare(((Number) b.value).doubleValue(), ((Number) a.value).doubleValue());
                    }
                    return 0;
                }))
                .map(entry -> String.format("- %s: %s", entry.getKey(), entry.getValue().value))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Formats simple statistics for LLM prompt (without technical terms)
     */
    private String formatSimpleStats(ComparisonAnalysis analysis, ComparisonField field) {
        if (analysis.min == null || field.type != ComparisonType.NUMERIC && field.type != ComparisonType.COUNT) {
            return "";
        }
        
        return String.format("""
            Resumen: Mínimo: %.2f, Máximo: %.2f, Promedio: %.2f
            """, analysis.min, analysis.max, analysis.avg);
    }

    /**
     * Generates unknown field message.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateUnknownFieldMessage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return generateFallbackUnknownFieldMessage("");
        }
        
        String prompt = String.format("""
            Given the following comparison query (in any language):
            "%s"
            Write a short message indicating that it was not possible to determine what to compare, 
            in the same language as the query.
            """, query);
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                return generateFallbackUnknownFieldMessage(query);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating unknown field message, using fallback", e);
            return generateFallbackUnknownFieldMessage(query);
        }
    }
    
    /**
     * Generates a fallback "unknown field" message when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackUnknownFieldMessage(String query) {
        String queryLower = query != null ? query.toLowerCase() : "";
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "No fue posible determinar qué campo comparar en esta consulta.";
        } else {
            return "It was not possible to determine what field to compare in this query.";
        }
    }

    /**
     * Generates no data message.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNoDataMessage(String field, String query) {
        if (query == null || query.trim().isEmpty()) {
            return generateFallbackNoDataMessage(field, "");
        }
        
        String prompt = String.format("""
            Given the following comparison query (in any language):
            "%s"
            Write a short message indicating that no data was found for the field '%s', 
            in the same language as the query.
            """, query, field);
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                return generateFallbackNoDataMessage(field, query);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating no data message, using fallback", e);
            return generateFallbackNoDataMessage(field, query);
        }
    }
    
    /**
     * Generates a fallback "no data" message when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackNoDataMessage(String field, String query) {
        String queryLower = query != null ? query.toLowerCase() : "";
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("No se encontraron datos para el campo '%s' en esta consulta.", field);
        } else {
            return String.format("No data was found for the field '%s' in this query.", field);
        }
    }

    /**
     * Represents a comparison field with its type and description
     */
    private static class ComparisonField {
        final String fieldName;
        final ComparisonType type;

        ComparisonField(String fieldName, ComparisonType type) {
            this.fieldName = fieldName;
            this.type = type;
        }
    }

    /**
     * Represents a comparison value with its type
     */
    private static class ComparisonValue {
        final Object value;
        final ComparisonType type;

        ComparisonValue(Object value, ComparisonType type) {
            this.value = value;
            this.type = type;
        }
        
        @Override
        public String toString() {
            return String.format("%s (%s)", value.toString(), type.name());
        }
    }

    /**
     * Represents statistical analysis results
     */
    private static class ComparisonAnalysis {
        final Double min;
        final Double max;
        final Double avg;

        ComparisonAnalysis(Double min, Double max, Double avg) {
            this.min = min;
            this.max = max;
            this.avg = avg;
        }
    }

    /**
     * Enum for comparison types
     */
    private enum ComparisonType {
        NUMERIC, TEXT, DATE, COUNT
    }
}
