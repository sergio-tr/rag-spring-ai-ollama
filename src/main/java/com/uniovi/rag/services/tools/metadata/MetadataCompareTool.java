package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataCompareTool for comparing meeting minutes across different dimensions.
 * 
 * Features:
 * - Intelligent field inference with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Multi-dimensional comparisons (numeric, text, dates)
 * - Statistical analysis and trend detection
 * - Advanced NER-based filtering
 */
public class MetadataCompareTool extends AbstractMetadataTool {

    public MetadataCompareTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing comparison query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query, 
            new String[] {"date", "place", "numberOfAttendees", "topics", "decisions", "summary"}
        );
        if (docs.isEmpty()) {
            log().debug("No documents found for comparison query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for comparison query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for comparison query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Infer comparison field with enhanced analysis
        ComparisonField fieldToCompare = inferComparisonFieldEnhanced(query, ner, relevantMinutes);
        if (fieldToCompare == null) {
            log().debug("Could not infer comparison field for query: {}", query);
            return ToolResult.from(generateUnknownFieldMessage(query), getClass());
        }

        // Step 5: Extract comparison data in parallel
        Map<String, ComparisonValue> comparables = extractComparisonDataInParallel(relevantMinutes, fieldToCompare, ner);
        if (comparables.isEmpty()) {
            log().debug("No comparison data found for field: {}", fieldToCompare.fieldName);
            return ToolResult.from(generateNoDataMessage(fieldToCompare.fieldName, query), getClass());
        }

        // Step 6: Perform statistical analysis
        ComparisonAnalysis analysis = performStatisticalAnalysis(comparables, fieldToCompare);

        // Step 7: Generate enhanced comparison answer
        String answer = generateEnhancedComparisonAnswer(query, fieldToCompare, comparables, analysis);
        log().debug("Generated comparison answer for query: {} with {} data points", query, comparables.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Cached NER matching evaluation
     */
    @Cacheable(value = "nerMatching", key = "#minute.hashCode() + '_' + #ner.hashCode()")
    public boolean matchesMinuteWithNERCached(Minute minute, JSONObject ner) {
        return matchesMinuteWithNER(minute, ner);
    }

    /**
     * Cached query relevance evaluation for comparison queries
     */
    @Cacheable(value = "comparisonQueryRelevance", key = "#query.hashCode() + '_' + #minute.hashCode()")
    public boolean isRelevantToComparisonQueryCached(String query, Minute minute) {
        return isRelevantToComparisonQueryByLLM(query, minute);
    }

    /**
     * Determines if a minute is relevant to the comparison query using LLM
     */
    private boolean isRelevantToComparisonQueryByLLM(String query, Minute minute) {
        String prompt = generateComparisonRelevancePrompt(query, minute);
        String result = getLLMResponseCached(prompt);
        return result.toLowerCase().contains("yes") || result.toLowerCase().contains("sí");
    }

    /**
     * Generates adaptive relevance prompt for comparison queries
     */
    private String generateComparisonRelevancePrompt(String query, Minute minute) {
        return String.format("""
            Given the following comparison query (in any language):
            "%s"
            
            Meeting metadata:
            Date: %s
            Place: %s
            Number of Attendees: %d
            Topics: %s
            Decisions: %s
            Summary: %s
            
            Does this meeting contain data that could be useful for the comparison requested?
            Consider that the query asks for comparing meetings or their attributes.
            Answer only with YES or NO.
            """,
            query,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.numberOfAttendees(),
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown"
        );
    }

    /**
     * Enhanced field inference with context analysis
     */
    private ComparisonField inferComparisonFieldEnhanced(String query, JSONObject ner, List<Minute> minutes) {
        // First try rule-based inference for common cases
        ComparisonField ruleBasedField = inferFieldByRules(query);
        if (ruleBasedField != null) {
            log().debug("Inferred field by rules: {}", ruleBasedField.fieldName);
            return ruleBasedField;
        }

        // If rule-based fails, use LLM with enhanced context
        return inferFieldByLLMWithContext(query, ner, minutes);
    }

    /**
     * Rule-based field inference for common comparison patterns
     */
    private ComparisonField inferFieldByRules(String query) {
        String queryLower = query.toLowerCase();
        
        // Attendees patterns
        if (queryLower.contains("asistentes") || queryLower.contains("attendees") || 
            queryLower.contains("personas") || queryLower.contains("people")) {
            return new ComparisonField("numberOfAttendees", ComparisonType.NUMERIC, "Number of attendees");
        }
        
        // Duration patterns
        if (queryLower.contains("duración") || queryLower.contains("duration") || 
            queryLower.contains("tiempo") || queryLower.contains("time") ||
            queryLower.contains("horas") || queryLower.contains("hours")) {
            return new ComparisonField("duration", ComparisonType.NUMERIC, "Meeting duration in minutes");
        }
        
        // Date patterns
        if (queryLower.contains("fecha") || queryLower.contains("date") || 
            queryLower.contains("cuándo") || queryLower.contains("when")) {
            return new ComparisonField("date", ComparisonType.DATE, "Meeting date");
        }
        
        // Place patterns
        if (queryLower.contains("lugar") || queryLower.contains("place") || 
            queryLower.contains("dónde") || queryLower.contains("where")) {
            return new ComparisonField("place", ComparisonType.TEXT, "Meeting place");
        }
        
        // Topics patterns
        if (queryLower.contains("temas") || queryLower.contains("topics") || 
            queryLower.contains("asuntos") || queryLower.contains("subjects")) {
            return new ComparisonField("topics", ComparisonType.COUNT, "Number of topics");
        }
        
        // Decisions patterns
        if (queryLower.contains("decisiones") || queryLower.contains("decisions") || 
            queryLower.contains("acuerdos") || queryLower.contains("agreements")) {
            return new ComparisonField("decisions", ComparisonType.COUNT, "Number of decisions");
        }
        
        return null;
    }

    /**
     * LLM-based field inference with enhanced context
     */
    private ComparisonField inferFieldByLLMWithContext(String query, JSONObject ner, List<Minute> minutes) {
        // Analyze available data in minutes
        Map<String, Integer> fieldAvailability = analyzeFieldAvailability(minutes);
        
        String prompt = String.format("""
            Given the following comparison query (in any language):
            "%s"
            
            Available fields for comparison (with data availability):
            %s
            
        Which field does the user want to compare? 
            Consider the semantic meaning and context of the query.
            Respond with one of: numberOfAttendees, duration, date, place, topics, decisions.
            If unclear, respond only: unknown
            """, query, formatFieldAvailability(fieldAvailability));
        
        String result = getLLMResponseCached(prompt).strip().toLowerCase();
        
        return switch (result) {
            case "numberofattendees" -> new ComparisonField("numberOfAttendees", ComparisonType.NUMERIC, "Number of attendees");
            case "duration" -> new ComparisonField("duration", ComparisonType.NUMERIC, "Meeting duration in minutes");
            case "date" -> new ComparisonField("date", ComparisonType.DATE, "Meeting date");
            case "place" -> new ComparisonField("place", ComparisonType.TEXT, "Meeting place");
            case "topics" -> new ComparisonField("topics", ComparisonType.COUNT, "Number of topics");
            case "decisions" -> new ComparisonField("decisions", ComparisonType.COUNT, "Number of decisions");
            default -> null;
        };
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
            case "numberOfAttendees" -> minute.numberOfAttendees() > 0;
            case "duration" -> calculateDurationFromMinute(minute) > 0;
            case "date" -> minute.date() != null && !minute.date().isBlank();
            case "place" -> minute.place() != null && !minute.place().isBlank();
            case "topics" -> minute.topics() != null && !minute.topics().isEmpty();
            case "decisions" -> minute.decisions() != null && !minute.decisions().isEmpty();
            default -> false;
        };
    }

    /**
     * Formats field availability for LLM prompt
     */
    private String formatFieldAvailability(Map<String, Integer> availability) {
        return availability.entrySet().stream()
                .map(entry -> String.format("- %s: %d meetings with data", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n"));
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
        String label = buildEnhancedLabel(minute, ner);
        Object value = extractFieldValue(minute, field);
        
        if (label != null && value != null) {
            return new AbstractMap.SimpleEntry<>(label, new ComparisonValue(value, field.type));
        }
        
        return null;
    }

    /**
     * Builds enhanced label for comparison
     */
    private String buildEnhancedLabel(Minute minute, JSONObject ner) {
        StringBuilder label = new StringBuilder();
        
        if (minute.date() != null) {
            label.append(minute.date());
        }
        
        if (minute.place() != null) {
            if (label.length() > 0) label.append(" - ");
            label.append(minute.place());
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
            case "numberOfAttendees" -> minute.numberOfAttendees();
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
            return new ComparisonAnalysis(null, null, null, null, null);
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
            return new ComparisonAnalysis(null, null, null, null, null);
        }

        double min = Collections.min(numericValues);
        double max = Collections.max(numericValues);
        double avg = numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double median = calculateMedian(numericValues);
        double stdDev = calculateStandardDeviation(numericValues, avg);

        return new ComparisonAnalysis(min, max, avg, median, stdDev);
    }

    /**
     * Calculates median value
     */
    private double calculateMedian(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    /**
     * Calculates standard deviation
     */
    private double calculateStandardDeviation(List<Double> values, double mean) {
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    /**
     * Generates enhanced comparison answer with statistical insights
     */
    private String generateEnhancedComparisonAnswer(String query, ComparisonField field, 
                                                   Map<String, ComparisonValue> comparables, 
                                                   ComparisonAnalysis analysis) {
        String comparisonData = formatComparisonData(comparables, field);
        String statisticalInsights = formatStatisticalInsights(analysis, field);
        
        String prompt = String.format("""
            Given the following comparison query (in any language):
            "%s"
            
            Field being compared: %s (%s)
            
            Comparison data:
            %s
            
            Statistical analysis:
            %s
            
            Write a clear, comprehensive answer in the same language as the query, 
            comparing the values and explaining trends, patterns, and insights.
            Include specific details from the data and statistical analysis when relevant.
            """, query, field.description, field.fieldName, comparisonData, statisticalInsights);
        
        return getLLMResponseCached(prompt);
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
     * Formats statistical insights for LLM prompt
     */
    private String formatStatisticalInsights(ComparisonAnalysis analysis, ComparisonField field) {
        if (analysis.min == null) {
            return "No statistical analysis available for this field type.";
        }
        
        return String.format("""
            - Minimum: %.2f
            - Maximum: %.2f
            - Average: %.2f
            - Median: %.2f
            - Standard Deviation: %.2f
            """, analysis.min, analysis.max, analysis.avg, analysis.median, analysis.stdDev);
    }

    /**
     * Generates unknown field message
     */
    private String generateUnknownFieldMessage(String query) {
        String prompt = String.format("""
            Given the following comparison query (in any language):
            "%s"
            Write a short message indicating that it was not possible to determine what to compare, 
            in the same language as the query.
            """, query);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Generates no data message
     */
    private String generateNoDataMessage(String field, String query) {
        String prompt = String.format("""
            Given the following comparison query (in any language):
            "%s"
            Write a short message indicating that no data was found for the field '%s', 
            in the same language as the query.
            """, query, field);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Cached LLM response
     */
    @Cacheable(value = "llmResponses", key = "#prompt.hashCode()")
    public String getLLMResponseCached(String prompt) {
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    /**
     * Represents a comparison field with its type and description
     */
    private static class ComparisonField {
        final String fieldName;
        final ComparisonType type;
        final String description;

        ComparisonField(String fieldName, ComparisonType type, String description) {
            this.fieldName = fieldName;
            this.type = type;
            this.description = description;
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
        final Double median;
        final Double stdDev;

        ComparisonAnalysis(Double min, Double max, Double avg, Double median, Double stdDev) {
            this.min = min;
            this.max = max;
            this.avg = avg;
            this.median = median;
            this.stdDev = stdDev;
        }
    }

    /**
     * Enum for comparison types
     */
    private enum ComparisonType {
        NUMERIC, TEXT, DATE, COUNT
    }
}
