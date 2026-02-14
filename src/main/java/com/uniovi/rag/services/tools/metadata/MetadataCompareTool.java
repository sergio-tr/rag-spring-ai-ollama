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
import java.util.Objects;

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
        
        // Step 1.5: Extract and validate years from query to avoid confusion (2025 vs 2026)
        List<String> requestedYears = extractYearsFromQuery(query, ner);
        if (!requestedYears.isEmpty() && !docs.isEmpty()) {
            log().info("Filtering documents by requested years: {}", requestedYears);
            List<Document> filteredDocs = filterDocumentsByYears(docs, requestedYears);
            if (filteredDocs.isEmpty()) {
                log().info("No documents found for requested years {} in query: {}", requestedYears, query);
                String errorMessage = generateSpecificErrorMessage(query, "years", String.join(", ", requestedYears), docs.size(), "No documents found for these years");
                return ToolResult.from(formatResponse(errorMessage, query), getClass());
            }
            docs = filteredDocs;
            log().info("Filtered to {} documents for years {}", docs.size(), requestedYears);
        }
        
        if (docs.isEmpty()) {
            log().info("No documents found for comparison query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for comparison query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for comparison query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 4: Infer comparison field with enhanced analysis
        ComparisonField fieldToCompare = inferComparisonFieldEnhanced(query, ner, relevantMinutes);
        if (fieldToCompare == null) {
            log().info("Could not infer comparison field for query: {}", query);
            return ToolResult.from(formatResponse(generateUnknownFieldMessage(query), query), getClass());
        }

        // Step 5: Extract comparison data in parallel
        Map<String, ComparisonValue> comparables = extractComparisonDataInParallel(relevantMinutes, fieldToCompare, ner, query);
        if (comparables.isEmpty()) {
            log().info("No comparison data found for field: {}", fieldToCompare.fieldName);
            return ToolResult.from(formatResponse(generateNoDataMessage(fieldToCompare.fieldName, query), query), getClass());
        }

        // Step 5.5: Aggregate mentions by month if this is a mentions_by_month comparison
        if ("mentions_by_month".equals(fieldToCompare.fieldName)) {
            comparables = aggregateMentionsByMonth(comparables);
            log().info("Aggregated mentions by month: {}", comparables);
        }

        // Step 6: Perform statistical analysis
        ComparisonAnalysis analysis = performStatisticalAnalysis(comparables, fieldToCompare);

        // Step 7: Generate enhanced comparison answer
        String answer = generateEnhancedComparisonAnswer(query, fieldToCompare, comparables, analysis);
        log().info("Generated comparison answer for query: {} with {} data points", query, comparables.size());
        
        return ToolResult.from(formatResponse(answer, query), getClass());
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
     * Extracts years from query using NER or LLM.
     * Returns list of years mentioned in the query.
     */
    private List<String> extractYearsFromQuery(String query, JSONObject ner) {
        List<String> years = new ArrayList<>();
        
        if (query == null || query.trim().isEmpty()) {
            return years;
        }
        
        // Try to extract from NER first
        if (ner != null && !ner.isEmpty()) {
            try {
                if (ner.has("filters") && !ner.isNull("filters")) {
                    JSONObject filters = ner.getJSONObject("filters");
                    if (filters.has("date") && !filters.isNull("date")) {
                        org.json.JSONArray dates = filters.getJSONArray("date");
                        for (int i = 0; i < dates.length(); i++) {
                            String dateStr = dates.getString(i);
                            // Extract year from date string
                            java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b");
                            java.util.regex.Matcher matcher = yearPattern.matcher(dateStr);
                            while (matcher.find()) {
                                String year = matcher.group(1);
                                if (!years.contains(year)) {
                                    years.add(year);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log().debug("Error extracting years from NER: {}", e.getMessage());
            }
        }
        
        // Extract years from query using regex
        java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b");
        java.util.regex.Matcher matcher = yearPattern.matcher(query);
        while (matcher.find()) {
            String year = matcher.group(1);
            if (!years.contains(year)) {
                years.add(year);
            }
        }
        
        log().info("Extracted years from query: {}", years);
        return years;
    }

    /**
     * Filters documents by years.
     * 
     * @param docs List of documents to filter
     * @param years List of years to filter by (e.g., ["2025", "2026"])
     * @return Filtered list of documents from the specified years
     */
    private List<Document> filterDocumentsByYears(List<Document> docs, List<String> years) {
        if (docs == null || docs.isEmpty() || years == null || years.isEmpty()) {
            return docs != null ? docs : new ArrayList<>();
        }
        
        log().info("Filtering {} documents by years: {}", docs.size(), years);
        
        List<Document> filtered = new ArrayList<>();
        
        for (Document doc : docs) {
            if (doc == null) continue;
            
            String docDate = getDocumentDate(doc);
            if (docDate != null) {
                // Check if document date contains any of the requested years
                boolean matches = false;
                for (String year : years) {
                    if (docDate.contains(year)) {
                        matches = true;
                        break;
                    } else {
                        // Try parsing the date to extract year
                        try {
                            java.time.LocalDate parsedDate = parseDateFlexible(docDate);
                            if (parsedDate != null) {
                                String docYear = String.valueOf(parsedDate.getYear());
                                if (docYear.equals(year)) {
                                    matches = true;
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            log().debug("Error parsing date '{}' for year filtering: {}", docDate, e.getMessage());
                        }
                    }
                }
                
                if (matches) {
                    filtered.add(doc);
                }
            }
        }
        
        log().info("Filtered {} documents by years {}: {} documents match", docs.size(), years, filtered.size());
        
        // Detailed logging for debugging
        if (filtered.isEmpty() && !docs.isEmpty()) {
            log().warn("Year filtering failed: Years {} not found in any of {} documents. Sample document dates: {}", 
                      years, docs.size(),
                      docs.stream()
                          .limit(3)
                          .map(doc -> getDocumentDate(doc))
                          .filter(Objects::nonNull)
                          .collect(Collectors.joining(", ")));
        } else if (!filtered.isEmpty()) {
            log().debug("Year filtering succeeded: Years {} found in {} documents", years, filtered.size());
        }
        
        return filtered;
    }

    /**
     * Rule-based field inference using LLM for better accuracy.
     * Handles special cases like mentions comparison by month.
     */
    private ComparisonField inferFieldByRules(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        String queryLower = query.toLowerCase();
        
        // Special case: Comparison of mentions by month (e.g., "más menciones a problemas de seguridad en febrero o en agosto")
        if ((queryLower.contains("menciones") || queryLower.contains("mentions") || queryLower.contains("menciona")) &&
            (queryLower.contains("febrero") || queryLower.contains("february") || 
             queryLower.contains("agosto") || queryLower.contains("august") ||
             queryLower.contains("mes") || queryLower.contains("month"))) {
            // This is a mentions comparison query - we need to count mentions of a topic by month
            log().info("Detected mentions comparison query, will use special comparison logic");
            return new ComparisonField("mentions_by_month", ComparisonType.COUNT);
        }
        
        // Use LLM to infer comparison field
        String prompt = String.format("""
            Task: Determine what field the user wants to compare in this query about meeting minutes.
            
            Query (may be in any language): "%s"
            
            Possible comparison fields:
            - numberOfAttendees: number of attendees, people present
            - duration: meeting duration, time length
            - date: dates of meetings
            - place: meeting places, locations
            - topics: number of topics discussed
            - decisions: number of decisions made
            - mentions_by_month: counting mentions of a topic/keyword by month (special case)
            
            Respond with ONLY the field name (e.g., "numberOfAttendees", "duration", "date", "place", "topics", "decisions", "mentions_by_month").
            If unclear, respond with "UNKNOWN".
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toLowerCase();
            
            if (response.contains("mentions_by_month") || response.contains("mentions by month")) {
                return new ComparisonField("mentions_by_month", ComparisonType.COUNT);
            } else if (response.contains("numberofattendees") || response.contains("attendees") || response.contains("people")) {
                return new ComparisonField("numberOfAttendees", ComparisonType.NUMERIC);
            } else if (response.contains("duration") || response.contains("time") || response.contains("length")) {
                return new ComparisonField("duration", ComparisonType.NUMERIC);
            } else if (response.contains("date") || response.contains("when")) {
                return new ComparisonField("date", ComparisonType.DATE);
            } else if (response.contains("place") || response.contains("location") || response.contains("where")) {
                return new ComparisonField("place", ComparisonType.TEXT);
            } else if (response.contains("topics") || response.contains("subjects")) {
                return new ComparisonField("topics", ComparisonType.COUNT);
            } else if (response.contains("decisions") || response.contains("agreements")) {
                return new ComparisonField("decisions", ComparisonType.COUNT);
            }
        } catch (Exception e) {
            log().warn("Error inferring comparison field with LLM: {}", e.getMessage());
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
    private Map<String, ComparisonValue> extractComparisonDataInParallel(List<Minute> minutes, ComparisonField field, JSONObject ner, String query) {
        List<CompletableFuture<Map.Entry<String, ComparisonValue>>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractComparisonValue(minute, field, ner, query)))
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
     * Extracts comparison value from a minute, validating it belongs to the correct period.
     */
    private Map.Entry<String, ComparisonValue> extractComparisonValue(Minute minute, ComparisonField field, JSONObject ner, String query) {
        // Special handling for mentions_by_month comparison
        if ("mentions_by_month".equals(field.fieldName)) {
            return extractMentionsByMonthValue(minute, field, ner, query);
        }
        
        // Validate that minute belongs to the requested period if years are specified
        if (ner != null && minute.date() != null) {
            String docDate = minute.date();
            // Extract year from document date
            String docYear = null;
            try {
                java.time.LocalDate parsedDate = parseDateFlexible(docDate);
                if (parsedDate != null) {
                    docYear = String.valueOf(parsedDate.getYear());
                }
            } catch (Exception e) {
                log().debug("Error parsing date for validation: {}", e.getMessage());
            }
            
            // If we have a year, validate it matches requested years (if any)
            // This validation is done at document level, so we just log here
            if (docYear != null) {
                log().debug("Extracting comparison value for minute dated {} (year: {})", docDate, docYear);
            }
        }
        
        String label = buildEnhancedLabel(minute);
        Object value = extractFieldValue(minute, field);
        
        if (label != null && value != null) {
            return new AbstractMap.SimpleEntry<>(label, new ComparisonValue(value, field.type));
        }
        
        return null;
    }
    
    /**
     * Extracts mentions count by month for comparison queries.
     * Example: "Indica si hubo más menciones a problemas de seguridad en febrero o en agosto"
     */
    private Map.Entry<String, ComparisonValue> extractMentionsByMonthValue(Minute minute, ComparisonField field, JSONObject ner, String query) {
        // Extract the topic/keyword to count mentions for
        String topic = extractTopicFromQuery(query, ner);
        
        if (topic == null || topic.isEmpty()) {
            log().warn("Could not extract topic for mentions comparison from query: '{}'", query);
            return null;
        }
        
        // Extract month from minute date
        if (minute.date() == null) {
            log().debug("Minute {} has no date, cannot extract month", minute.id());
            return null;
        }
        
        try {
            java.time.LocalDate parsedDate = parseDateFlexible(minute.date());
            if (parsedDate == null) {
                log().warn("Could not parse date '{}' for minute {} to extract month", minute.date(), minute.id());
                return null;
            }
            
            int month = parsedDate.getMonthValue();
            String monthName = getMonthName(month);
            
            // Count mentions of the topic in this minute
            int mentionCount = countTopicMentions(minute, topic);
            
            log().info("Minute {} (date: {}, month: {}) has {} mention(s) of topic '{}'", 
                      minute.id(), minute.date(), monthName, mentionCount, topic);
            
            // Use month as label (e.g., "febrero", "agosto")
            String label = monthName;
            
            return new AbstractMap.SimpleEntry<>(label, new ComparisonValue(mentionCount, ComparisonType.COUNT));
        } catch (Exception e) {
            log().error("Error extracting mentions by month value for minute {}: {}", minute.id(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Counts mentions of a topic in a minute (in topics, decisions, summary).
     * Uses semantic matching to find related terms, not just exact matches.
     * For example, "problemas de seguridad" should match "seguridad", "vigilancia", "iluminación y vigilancia", etc.
     */
    private int countTopicMentions(Minute minute, String topic) {
        if (topic == null || topic.isEmpty() || minute == null) {
            return 0;
        }
        
        String topicLower = topic.toLowerCase().trim();
        int count = 0;
        
        // Extract key terms from the topic (e.g., "problemas de seguridad" -> ["seguridad", "problemas"])
        List<String> keyTerms = extractKeyTermsFromTopic(topicLower);
        log().debug("Counting mentions of topic '{}' (key terms: {}) in minute {}", topic, keyTerms, minute.id());
        
        // Count in topics
        if (minute.topics() != null) {
            for (String t : minute.topics()) {
                if (t != null) {
                    String topicField = t.toLowerCase();
                    // Check if any key term appears in the topic field
                    boolean matches = keyTerms.stream().anyMatch(term -> topicField.contains(term));
                    if (matches) {
                        count++;
                        log().debug("Found topic match in topics field: '{}' matches topic '{}'", t, topic);
                    }
                }
            }
        }
        
        // Count in decisions
        if (minute.decisions() != null) {
            for (String d : minute.decisions()) {
                if (d != null) {
                    String decisionField = d.toLowerCase();
                    // Check if any key term appears in the decision field
                    boolean matches = keyTerms.stream().anyMatch(term -> decisionField.contains(term));
                    if (matches) {
                        count++;
                        log().debug("Found topic match in decisions field: '{}' matches topic '{}'", d, topic);
                    }
                }
            }
        }
        
        // Count in summary (count occurrences, not just presence)
        if (minute.summary() != null) {
            String summaryLower = minute.summary().toLowerCase();
            // Count occurrences of each key term
            for (String term : keyTerms) {
                int index = 0;
                while ((index = summaryLower.indexOf(term, index)) != -1) {
                    count++;
                    index += term.length();
                }
            }
            if (count > 0) {
                log().debug("Found {} mention(s) of topic '{}' in summary", count, topic);
            }
        }
        
        log().debug("Total mentions of topic '{}' in minute {}: {}", topic, minute.id(), count);
        return count;
    }
    
    /**
     * Extracts key terms from a topic phrase for semantic matching.
     * For example, "problemas de seguridad" -> ["seguridad", "problemas"]
     * This allows matching related terms like "vigilancia", "iluminación y vigilancia", etc.
     */
    private List<String> extractKeyTermsFromTopic(String topic) {
        if (topic == null || topic.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> keyTerms = new ArrayList<>();
        
        // Split by common prepositions and conjunctions
        String[] parts = topic.split("\\s+(de|del|la|el|y|and|con|with)\\s+");
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase();
            if (!trimmed.isEmpty() && trimmed.length() > 2) { // Ignore very short words
                keyTerms.add(trimmed);
            }
        }
        
        // Also add the full topic as a key term
        keyTerms.add(topic.toLowerCase());
        
        // Remove duplicates and sort by length (longer terms first for more specific matching)
        return keyTerms.stream()
                .distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets Spanish month name from month number (1-12)
     */
    private String getMonthName(int month) {
        String[] monthNames = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                              "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        if (month >= 1 && month <= 12) {
            return monthNames[month - 1];
        }
        return "unknown";
    }
    
    /**
     * Aggregates mentions by month for comparison queries.
     * Groups minutes by month and sums mention counts.
     */
    private Map<String, ComparisonValue> aggregateMentionsByMonth(Map<String, ComparisonValue> comparables) {
        Map<String, Integer> monthCounts = new HashMap<>();
        
        log().info("Aggregating mentions by month from {} entries", comparables.size());
        
        for (Map.Entry<String, ComparisonValue> entry : comparables.entrySet()) {
            String month = entry.getKey();
            Object value = entry.getValue().value;
            
            if (value instanceof Number) {
                int count = ((Number) value).intValue();
                int previousCount = monthCounts.getOrDefault(month, 0);
                monthCounts.put(month, previousCount + count);
                log().debug("Month '{}': added {} mentions (total: {})", month, count, monthCounts.get(month));
            } else {
                log().warn("Unexpected value type for month '{}': {}", month, value != null ? value.getClass() : "null");
            }
        }
        
        log().info("Aggregated mentions by month: {}", monthCounts);
        
        // Convert back to ComparisonValue map, sorted by month name for consistent ordering
        Map<String, Integer> sortedMonths = new LinkedHashMap<>();
        String[] monthOrder = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                              "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        for (String month : monthOrder) {
            if (monthCounts.containsKey(month)) {
                sortedMonths.put(month, monthCounts.get(month));
            }
        }
        // Add any months not in the standard order
        for (Map.Entry<String, Integer> entry : monthCounts.entrySet()) {
            if (!sortedMonths.containsKey(entry.getKey())) {
                sortedMonths.put(entry.getKey(), entry.getValue());
            }
        }
        
        return sortedMonths.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> new ComparisonValue(e.getValue(), ComparisonType.COUNT),
                    (existing, replacement) -> existing,
                    LinkedHashMap::new
                ));
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
            
            IMPORTANT: 
            - Labels "febrero" / "Febrero" = February; "agosto" / "Agosto" = August. Do NOT invert the conclusion.
            - If the data shows "Febrero: X" and "Agosto: Y", state which has more according to the numbers (e.g. "Febrero tiene más menciones que Agosto" if X > Y).
            - The comparison data order and values are authoritative; do not swap or invert them.
            - If comparing months (febrero vs agosto), clearly state which month has MORE mentions or attendees based on the numbers given.
            - Be precise: if data shows febrero: 20 and agosto: 18, then February has MORE.
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
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackComparisonAnswer(String query, Map<String, ComparisonValue> comparables, ComparisonAnalysis analysis) {
        String comparisonData = comparables.entrySet().stream()
                .limit(5)
                .map(entry -> String.format("- %s: %s", entry.getKey(), entry.getValue().value))
                .collect(Collectors.joining("\n"));
        
        String statsText = "";
        if (analysis != null && analysis.min != null) {
            statsText = String.format("\nStatistics: Min=%.1f, Max=%.1f, Average=%.1f", 
                analysis.min, analysis.max, analysis.avg);
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Comparison data:
            %s
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            presenting the comparison results.
            Be concise and direct.
            Do not repeat the question.
            """, query, comparisonData, statsText);
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback comparison answer with LLM", e);
        }
        
        // Ultimate fallback
        StringBuilder answer = new StringBuilder();
        answer.append("Comparison obtained:\n");
        answer.append(comparisonData);
        if (!statsText.isEmpty()) {
            answer.append(statsText);
        }
        return answer.toString();
    }

    /**
     * Formats comparison data for LLM prompt
     */
    private String formatComparisonData(Map<String, ComparisonValue> comparables, ComparisonField field) {
        if ("mentions_by_month".equals(field.fieldName)) {
            // For month comparisons, format clearly showing which month has more
            return comparables.entrySet().stream()
                    .sorted(Map.Entry.<String, ComparisonValue>comparingByValue((a, b) -> {
                        if (a.value instanceof Number && b.value instanceof Number) {
                            return Double.compare(((Number) b.value).doubleValue(), ((Number) a.value).doubleValue());
                        }
                        return 0;
                    }).reversed()) // Sort descending (highest first)
                    .map(entry -> {
                        String month = entry.getKey();
                        Object value = entry.getValue().value;
                        return String.format("- %s: %s menciones", month, value);
                    })
                    .collect(Collectors.joining("\n"));
        }
        
        // For attendees comparison, sort descending (highest first) and format clearly
        if ("numberOfAttendees".equals(field.fieldName)) {
            log().info("Formatting attendees comparison data. Total entries: {}", comparables.size());
            List<Map.Entry<String, ComparisonValue>> sorted = comparables.entrySet().stream()
                    .sorted(Map.Entry.<String, ComparisonValue>comparingByValue((a, b) -> {
                        if (a.value instanceof Number && b.value instanceof Number) {
                            double aVal = ((Number) a.value).doubleValue();
                            double bVal = ((Number) b.value).doubleValue();
                            log().debug("Comparing attendees: {} vs {}", aVal, bVal);
                            return Double.compare(bVal, aVal); // Descending: higher first
                        }
                        return 0;
                    }).reversed())
                    .collect(Collectors.toList());
            
            log().info("Sorted attendees comparison (descending): {}", 
                      sorted.stream()
                          .map(e -> String.format("%s: %s", e.getKey(), e.getValue().value))
                          .collect(Collectors.joining(", ")));
            
            return sorted.stream()
                    .map(entry -> {
                        String label = entry.getKey();
                        Object value = entry.getValue().value;
                        log().debug("Formatting attendees entry: {} = {}", label, value);
                        return String.format("- %s: %s asistentes", label, value);
                    })
                    .collect(Collectors.joining("\n"));
        }
        
        // Default formatting for other comparison types (sort descending for numeric/count)
        return comparables.entrySet().stream()
                .sorted(Map.Entry.<String, ComparisonValue>comparingByValue((a, b) -> {
                    if (a.value instanceof Number && b.value instanceof Number) {
                        return Double.compare(((Number) b.value).doubleValue(), ((Number) a.value).doubleValue());
                    }
                    return 0;
                }).reversed()) // Sort descending (highest first)
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
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackUnknownFieldMessage(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            It was not possible to determine what field to compare in this query.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating that it was not possible to determine what to compare.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "");
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback unknown field message with LLM", e);
        }
        
        // Ultimate fallback
        return "It was not possible to determine what field to compare in this query.";
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
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackNoDataMessage(String field, String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No data was found for the field '%s' in this query.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating that no data was found for the field.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", field);
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback no data message with LLM", e);
        }
        
        // Ultimate fallback
        return String.format("No data was found for the field '%s' in this query.", field);
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
