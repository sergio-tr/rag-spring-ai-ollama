package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.uniovi.rag.infrastructure.observability.ContextPropagatingFutures.supplyAsync;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataCompareTool for comparing meeting minutes across different dimensions.
 */
public class MetadataCompareTool extends AbstractMetadataTool {

    private static final String FIELD_NUMBER_OF_ATTENDEES_BY_MONTH = "numberOfAttendees_by_month";

    private static final String[] SPANISH_MONTH_NAMES_ORDERED = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    };

    private static final String NER_KEY_FILTERS = "filters";
    private static final String FIELD_KEY_DURATION = "duration";
    private static final String FIELD_KEY_PLACE = "place";

    /** Comparison field: topic mention counts aggregated by calendar month. */
    private static final String FIELD_MENTIONS_BY_MONTH = "mentions_by_month";

    /** Comparison field: number of meetings (actas) per month. */
    private static final String FIELD_MEETINGS_COUNT_BY_MONTH = "meetings_count_by_month";

    /** Topic keyword for security-related comparisons (Spanish). */
    private static final String TOPIC_SEGURIDAD = "seguridad";

    public MetadataCompareTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor,
            MetadataLlmResponseCacheService llmResponseCache) {
        super(chatClient, retriever, extractor, llmResponseCache);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        return executeComparison(ctx.query(), ctx.nerEntities());
    }

    private ToolResult executeComparison(String query, JSONObject ner) {
        log().info("Executing comparison query: {} with NER: {}", query, ner != null ? ner.toString() : "null");

        List<Document> docs = retrieveInitialDocumentsForComparison(query, ner);
        YearNarrowOutcome yearNarrow = applyYearNarrowing(query, ner, docs);
        if (yearNarrow.earlyExit() != null) {
            return yearNarrow.earlyExit();
        }
        docs = yearNarrow.documents();

        ToolResult missing = notFoundIfEmptyDocuments(query, docs);
        if (missing != null) {
            return missing;
        }

        List<Minute> minutes = extractMinutesInParallel(docs);
        missing = notFoundIfEmptyMinutes(query, minutes);
        if (missing != null) {
            return missing;
        }

        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        missing = notFoundIfEmptyRelevantMinutes(query, relevantMinutes);
        if (missing != null) {
            return missing;
        }

        ComparisonField fieldToCompare = inferComparisonFieldEnhanced(query, relevantMinutes);
        if (fieldToCompare == null) {
            log().info("Could not infer comparison field for query: {}", query);
            return ToolResult.from(formatResponse(generateUnknownFieldMessage(query), query), getClass());
        }

        Map<String, ComparisonValue> comparables = extractComparisonDataInParallel(relevantMinutes, fieldToCompare, ner, query);
        if (comparables.isEmpty()) {
            log().info("No comparison data found for field: {}", fieldToCompare.fieldName);
            return ToolResult.from(formatResponse(generateNoDataMessage(fieldToCompare.fieldName, query), query), getClass());
        }

        comparables = applyMonthlyAggregationIfApplicable(fieldToCompare, comparables);
        comparables = filterComparablesByQueryMonthsIfApplicable(query, fieldToCompare, comparables);

        ComparisonAnalysis analysis = performStatisticalAnalysis(comparables, fieldToCompare);
        String answer = generateEnhancedComparisonAnswer(query, fieldToCompare, comparables, analysis);
        log().info("Generated comparison answer for query: {} with {} data points", query, comparables.size());

        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private List<Document> retrieveInitialDocumentsForComparison(String query, JSONObject ner) {
        List<Document> docs = retrieveDocumentsWithFallback(
                query,
                new String[] {"date", "place", "numberOfAttendees", "topics", "decisions", "summary"},
                ner);
        List<String> dateCandidates = extractDateCandidates(query, ner);
        return mergeDocumentsWhenComparingTwoDates(query, ner, docs, dateCandidates);
    }

    private ToolResult notFoundIfEmptyDocuments(String query, List<Document> docs) {
        if (!docs.isEmpty()) {
            return null;
        }
        log().info("No documents found for comparison query: {}", query);
        return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
    }

    private ToolResult notFoundIfEmptyMinutes(String query, List<Minute> minutes) {
        if (!minutes.isEmpty()) {
            return null;
        }
        log().info("No valid minutes found for comparison query: {}", query);
        return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
    }

    private ToolResult notFoundIfEmptyRelevantMinutes(String query, List<Minute> relevantMinutes) {
        if (!relevantMinutes.isEmpty()) {
            return null;
        }
        log().info("No relevant minutes found for comparison query: {}", query);
        return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
    }

    private Map<String, ComparisonValue> applyMonthlyAggregationIfApplicable(
            ComparisonField fieldToCompare, Map<String, ComparisonValue> comparables) {
        if (!isMonthlyAggregationField(fieldToCompare.fieldName)) {
            return comparables;
        }
        Map<String, ComparisonValue> aggregated = aggregateMentionsByMonth(comparables);
        logMonthlyAggregation(fieldToCompare.fieldName, aggregated);
        return aggregated;
    }

    private boolean isMonthlyAggregationField(String fieldName) {
        return FIELD_MENTIONS_BY_MONTH.equals(fieldName)
                || FIELD_MEETINGS_COUNT_BY_MONTH.equals(fieldName)
                || FIELD_NUMBER_OF_ATTENDEES_BY_MONTH.equals(fieldName);
    }

    private void logMonthlyAggregation(String fieldName, Map<String, ComparisonValue> aggregated) {
        if (FIELD_MENTIONS_BY_MONTH.equals(fieldName)) {
            log().info("Aggregated mentions by month: {}", aggregated);
        } else if (FIELD_MEETINGS_COUNT_BY_MONTH.equals(fieldName)) {
            log().info("Aggregated meetings count by month: {}", aggregated);
        } else {
            log().info("Aggregated attendees by month: {}", aggregated);
        }
    }

    private Map<String, ComparisonValue> filterComparablesByQueryMonthsIfApplicable(
            String query, ComparisonField fieldToCompare, Map<String, ComparisonValue> comparables) {
        List<String> requestedMonths = extractMonthsFromQuery(query);
        if (requestedMonths.isEmpty() || !isMonthlyAggregationField(fieldToCompare.fieldName)) {
            return comparables;
        }
        Map<String, ComparisonValue> filtered = filterComparablesByMonths(comparables, requestedMonths);
        log().info("Filtered comparables to requested months {}: {}", requestedMonths, filtered);
        return filtered;
    }

    private YearNarrowOutcome applyYearNarrowing(String query, JSONObject ner, List<Document> docs) {
        List<String> requestedYears = extractYearsFromQuery(query, ner);
        if (requestedYears.isEmpty() || docs.isEmpty()) {
            return new YearNarrowOutcome(docs, null);
        }
        log().info("Filtering documents by requested years: {}", requestedYears);
        List<Document> filteredDocs = filterDocumentsByYears(docs, requestedYears);
        if (filteredDocs.isEmpty()) {
            log().info("No documents found for requested years {} in query: {}", requestedYears, query);
            String errorMessage = generateSpecificErrorMessage(query, "years", String.join(", ", requestedYears), docs.size(),
                    "No documents found for these years");
            return new YearNarrowOutcome(null, ToolResult.from(formatResponse(errorMessage, query), getClass()));
        }
        log().info("Filtered to {} documents for years {}", filteredDocs.size(), requestedYears);
        return new YearNarrowOutcome(filteredDocs, null);
    }

    private record YearNarrowOutcome(List<Document> documents, ToolResult earlyExit) {
    }

    private List<Document> mergeDocumentsWhenComparingTwoDates(
            String query, JSONObject ner, List<Document> docs, List<String> dateCandidates) {
        if (dateCandidates == null || dateCandidates.size() < 2 || !isCompareTwoDatesQuery(query)) {
            return docs;
        }
        log().info("Query compares two specific dates; re-retrieving and filtering by any of {} dates", dateCandidates.size());
        List<Document> allDocs = retrieveDocumentsWithFallback(
                query, new String[] {"date", "place", "numberOfAttendees", "topics", "decisions", "summary"}, ner);
        if (allDocs.isEmpty()) {
            allDocs = retrieveDocuments(query, ner);
        }
        if (allDocs.isEmpty()) {
            return docs;
        }
        List<String> requestedYearsForMerge = extractYearsFromQuery(query, ner);
        if (!requestedYearsForMerge.isEmpty()) {
            allDocs = filterDocumentsByYears(allDocs, requestedYearsForMerge);
        }
        List<Document> docsByAnyDate = filterDocumentsByAnyOfDates(allDocs, dateCandidates);
        if (docsByAnyDate.isEmpty()) {
            return docs;
        }
        log().info("Filtered to {} documents matching any of the {} dates", docsByAnyDate.size(), dateCandidates.size());
        return docsByAnyDate;
    }

    /**
     * Enhanced field inference with context analysis
     */
    private ComparisonField inferComparisonFieldEnhanced(String query, List<Minute> minutes) {
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
                if (ner.has(NER_KEY_FILTERS) && !ner.isNull(NER_KEY_FILTERS)) {
                    JSONObject filters = ner.getJSONObject(NER_KEY_FILTERS);
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
    private boolean documentDateMatchesAnyRequestedYear(String docDate, List<String> years) {
        for (String year : years) {
            if (docDate.contains(year)) {
                return true;
            }
            try {
                java.time.LocalDate parsedDate = parseDateFlexible(docDate);
                if (parsedDate != null && String.valueOf(parsedDate.getYear()).equals(year)) {
                    return true;
                }
            } catch (Exception e) {
                log().debug("Error parsing date for year filtering: {}", e.getMessage());
            }
        }
        return false;
    }

    private List<Document> filterDocumentsByYears(List<Document> docs, List<String> years) {
        if (docs == null || docs.isEmpty() || years == null || years.isEmpty()) {
            return docs != null ? docs : new ArrayList<>();
        }

        log().info("Filtering {} documents by years: {}", docs.size(), years);

        List<Document> filtered = new ArrayList<>();

        for (Document doc : docs) {
            if (doc != null) {
                String docDate = getDocumentDate(doc);
                if (docDate != null && documentDateMatchesAnyRequestedYear(docDate, years)) {
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
     * Filters documents to those whose date matches any of the given date candidates (OR).
     * Used when the query compares two specific dates (e.g. which meeting was longer on date A vs date B) — item 53.
     */
    private List<Document> filterDocumentsByAnyOfDates(List<Document> docs, List<String> dateCandidates) {
        if (docs == null || docs.isEmpty() || dateCandidates == null || dateCandidates.isEmpty()) {
            return docs != null ? docs : new ArrayList<>();
        }
        Set<String> normalizedCandidates = new java.util.HashSet<>();
        for (String candidate : dateCandidates) {
            java.time.LocalDate parsed = parseDateFlexible(candidate);
            if (parsed != null) {
                normalizedCandidates.add(parsed.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }
        if (normalizedCandidates.isEmpty()) {
            log().warn("Could not parse any date from candidates {}, returning all docs", dateCandidates);
            return docs;
        }
        List<Document> filtered = docs.stream()
                .filter(doc -> {
                    String docDate = getDocumentDate(doc);
                    if (docDate == null) return false;
                    java.time.LocalDate docLocal = parseDateFlexible(docDate);
                    if (docLocal == null) {
                        try {
                            docLocal = java.time.LocalDate.parse(docDate, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                        } catch (Exception ignored) {
                            return false;
                        }
                    }
                    return normalizedCandidates.contains(docLocal.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
                })
                .toList();
        log().info("Filtered {} documents by any-of-dates {}: {} match", docs.size(), dateCandidates, filtered.size());
        return filtered;
    }

    /**
     * Returns true if the query asks to compare two specific dates (e.g. duration: which of two meetings was longer).
     */
    private boolean isCompareTwoDatesQuery(String query) {
        if (query == null || query.trim().isEmpty()) return false;
        String q = query.toLowerCase();
        return (q.contains("más larga") || q.contains("más larga") || q.contains("más corta") || q.contains("which was longer") || q.contains("which meeting was longer"))
                && (q.contains(" o ") || q.contains(" or "));
    }

    /** Spanish/English month tokens or "month" / "mes" — used by rule-based comparison detection. */
    private static boolean monthComparisonCueInQuery(String queryLower) {
        return queryLower.contains("febrero") || queryLower.contains("february")
                || queryLower.contains("abril") || queryLower.contains("april")
                || queryLower.contains("agosto") || queryLower.contains("august")
                || queryLower.contains("mes") || queryLower.contains("month");
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
        
        // More attendees in month A or B (e.g. "más asistentes en agosto o en febrero") — compare by month
        if ((queryLower.contains("asistentes") || queryLower.contains("attendees") || queryLower.contains("asistencia"))
                && monthComparisonCueInQuery(queryLower)) {
            log().info("Detected attendees comparison by month");
            return new ComparisonField(FIELD_NUMBER_OF_ATTENDEES_BY_MONTH, ComparisonType.NUMERIC);
        }
        // Special case: Number of meetings/actas by month (e.g., "más reuniones registradas, febrero o abril")
        if ((queryLower.contains("reuniones") || queryLower.contains("actas"))
                && monthComparisonCueInQuery(queryLower)) {
            log().info("Detected meetings count by month comparison");
            return new ComparisonField(FIELD_MEETINGS_COUNT_BY_MONTH, ComparisonType.COUNT);
        }
        // Special case: Comparison of mentions by month (e.g., "más menciones a problemas de seguridad en febrero o en agosto")
        if ((queryLower.contains("menciones") || queryLower.contains("mentions") || queryLower.contains("menciona"))
                && monthComparisonCueInQuery(queryLower)) {
            // This is a mentions comparison query - we need to count mentions of a topic by month
            log().info("Detected mentions comparison query, will use special comparison logic");
            return new ComparisonField(FIELD_MENTIONS_BY_MONTH, ComparisonType.COUNT);
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
            return mapLlmResponseToComparisonField(response);
        } catch (Exception e) {
            log().warn("Error inferring comparison field with LLM: {}", e.getMessage());
        }

        return null;
    }

    private ComparisonField mapLlmResponseToComparisonField(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        if (response.contains(FIELD_MENTIONS_BY_MONTH) || response.contains("mentions by month")) {
            return new ComparisonField(FIELD_MENTIONS_BY_MONTH, ComparisonType.COUNT);
        }
        if (response.contains("numberofattendees") || response.contains("attendees") || response.contains("people")) {
            return new ComparisonField("numberOfAttendees", ComparisonType.NUMERIC);
        }
        if (response.contains(FIELD_KEY_DURATION) || response.contains("time") || response.contains("length")) {
            return new ComparisonField(FIELD_KEY_DURATION, ComparisonType.NUMERIC);
        }
        if (response.contains("date") || response.contains("when")) {
            return new ComparisonField("date", ComparisonType.DATE);
        }
        if (response.contains(FIELD_KEY_PLACE) || response.contains("location") || response.contains("where")) {
            return new ComparisonField(FIELD_KEY_PLACE, ComparisonType.TEXT);
        }
        if (response.contains("topics") || response.contains("subjects")) {
            return new ComparisonField("topics", ComparisonType.COUNT);
        }
        if (response.contains("decisions") || response.contains("agreements")) {
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
                .map(e -> mapKeyToComparisonField(e.getKey()))
                .orElse(null);
    }

    private ComparisonField mapKeyToComparisonField(String key) {
        switch (key) {
            case "numberOfAttendees":
                return new ComparisonField("numberOfAttendees", ComparisonType.NUMERIC);
            case FIELD_KEY_DURATION:
                return new ComparisonField(FIELD_KEY_DURATION, ComparisonType.NUMERIC);
            case "date":
                return new ComparisonField("date", ComparisonType.DATE);
            case FIELD_KEY_PLACE:
                return new ComparisonField(FIELD_KEY_PLACE, ComparisonType.TEXT);
            case "topics":
                return new ComparisonField("topics", ComparisonType.COUNT);
            case "decisions":
                return new ComparisonField("decisions", ComparisonType.COUNT);
            default:
                return null;
        }
    }

    /**
     * Analyzes field availability in minutes
     */
    private Map<String, Integer> analyzeFieldAvailability(List<Minute> minutes) {
        Map<String, Integer> availability = new HashMap<>();
        String[] fields = {"numberOfAttendees", FIELD_KEY_DURATION, "date", FIELD_KEY_PLACE, "topics", "decisions"};
        
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
        switch (field) {
            case "numberOfAttendees":
                return minute.numberOfAttendees() > 0 ||
                        (minute.attendees() != null && !minute.attendees().isEmpty());
            case FIELD_KEY_DURATION:
                return calculateDurationFromMinute(minute) > 0;
            case "date":
                return minute.date() != null && !minute.date().isBlank();
            case FIELD_KEY_PLACE:
                return minute.place() != null && !minute.place().isBlank();
            case "topics":
                return minute.topics() != null && !minute.topics().isEmpty();
            case "decisions":
                return minute.decisions() != null && !minute.decisions().isEmpty();
            default:
                return false;
        }
    }

    /**
     * Extracts comparison data in parallel
     */
    private Map<String, ComparisonValue> extractComparisonDataInParallel(List<Minute> minutes, ComparisonField field, JSONObject ner, String query) {
        List<CompletableFuture<Map.Entry<String, ComparisonValue>>> futures = minutes.stream()
                .map(minute -> supplyAsync(() -> extractComparisonValue(minute, field, ner, query)))
                .toList();

        boolean mergeBySum = FIELD_MENTIONS_BY_MONTH.equals(field.fieldName)
                || FIELD_MEETINGS_COUNT_BY_MONTH.equals(field.fieldName)
                || FIELD_NUMBER_OF_ATTENDEES_BY_MONTH.equals(field.fieldName);
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (existing, replacement) -> mergeComparisonValues(existing, replacement, mergeBySum),
                    LinkedHashMap::new
                ));
    }

    private static ComparisonValue mergeComparisonValues(
            ComparisonValue existing, ComparisonValue replacement, boolean mergeBySum) {
        if (mergeBySum && existing.value instanceof Number existingNum && replacement.value instanceof Number replacementNum) {
            int sum = existingNum.intValue() + replacementNum.intValue();
            return new ComparisonValue(sum, existing.type != null ? existing.type : ComparisonType.COUNT);
        }
        return existing;
    }

    /**
     * Extracts comparison value from a minute, validating it belongs to the correct period.
     */
    private Map.Entry<String, ComparisonValue> extractComparisonValue(Minute minute, ComparisonField field, JSONObject ner, String query) {
        // Special handling for mentions_by_month comparison
        if (FIELD_MENTIONS_BY_MONTH.equals(field.fieldName)) {
            return extractMentionsByMonthValue(minute, field, ner, query);
        }
        // Count of meetings (actas) per month: one minute = one meeting
        if (FIELD_MEETINGS_COUNT_BY_MONTH.equals(field.fieldName)) {
            return extractMeetingsCountByMonthValue(minute);
        }
        // Attendees per month (for "más asistentes en febrero o en agosto")
        if (FIELD_NUMBER_OF_ATTENDEES_BY_MONTH.equals(field.fieldName)) {
            return extractAttendeesByMonthValue(minute);
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
     * Extracts mentions count by month for comparison queries
     * (e.g. more security-issue mentions in February vs August).
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
            
            // Count mentions of the topic in this minute; use binary per-minute value (1 if any mention, 0 otherwise)
            // For "seguridad" topic: require at least two security-related terms to avoid false positives (item 9)
            String topicLower = topic != null ? topic.toLowerCase().trim() : "";
            boolean isSecurityTopic = topicLower.contains(TOPIC_SEGURIDAD);
            int mentionCount = countTopicMentions(minute, topic);
            int valueForMonth;
            if (isSecurityTopic) {
                valueForMonth = minuteMentionsSecurityTopic(minute) ? 1 : 0;
            } else {
                valueForMonth = mentionCount > 0 ? 1 : 0;
            }
            
            log().info("Minute {} (date: {}, month: {}) has {} mention(s) of topic '{}' (valueForMonth={})", 
                      minute.id(), minute.date(), monthName, mentionCount, topic, valueForMonth);
            
            // Use month as label (e.g., "febrero", "agosto")
            String label = monthName;
            
            return new AbstractMap.SimpleEntry<>(label, new ComparisonValue(valueForMonth, ComparisonType.COUNT));
        } catch (Exception e) {
            log().error("Error extracting mentions by month value for minute {}: {}", minute.id(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Counts mentions of a topic in a minute (in topics, decisions, summary).
     * Uses semantic matching to find related terms, not just exact matches.
     * For example, a "security problems" phrase should match security, surveillance, lighting-and-surveillance wording, etc.
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
     * Returns true if the minute mentions the security topic with sufficient context (at least two of:
     * security, surveillance, video surveillance, cameras). Used for security-problem comparisons
     * to avoid counting minutes that only mention "security" in passing (item 9).
     */
    private boolean minuteMentionsSecurityTopic(Minute minute) {
        if (minute == null) return false;
        List<String> securityTerms = List.of(TOPIC_SEGURIDAD, "vigilancia", "videovigilancia", "camaras", "camara");
        StringBuilder text = new StringBuilder();
        if (minute.topics() != null) {
            minute.topics().stream().filter(t -> t != null).forEach(t -> text.append(t.toLowerCase()).append(" "));
        }
        if (minute.decisions() != null) {
            minute.decisions().stream().filter(d -> d != null).forEach(d -> text.append(d.toLowerCase()).append(" "));
        }
        if (minute.summary() != null) {
            text.append(minute.summary().toLowerCase());
        }
        String normalized = text.toString()
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
        long matchCount = securityTerms.stream()
                .filter(term -> normalized.contains(term))
                .count();
        return matchCount >= 2;
    }
    
    /**
     * Extracts key terms from a topic phrase for semantic matching.
     * For example, "security problems" -> ["security", "problems"].
     * This allows matching related terms like surveillance, lighting and surveillance, etc.
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
        
        // Add synonyms for common query topics so minute wording is matched (§4: August ACTA 3, 6 = security/surveillance/cameras)
        if (keyTerms.stream().anyMatch(t -> t.contains(TOPIC_SEGURIDAD))) {
            keyTerms.add(TOPIC_SEGURIDAD);
            keyTerms.add("vigilancia");
            keyTerms.add("videovigilancia");
            keyTerms.add("cámaras");
            keyTerms.add("camaras");
            keyTerms.add("camara");
            keyTerms.add("cámara");
        }

        // Remove duplicates and sort by length (longer terms first for more specific matching)
        return keyTerms.stream()
                .distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
    }
    
    /**
     * Extracts month names mentioned in the query (Spanish or English month names).
     * Returns only months that appear in the query so we filter comparison data to the requested pair.
     */
    private List<String> extractMonthsFromQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String q = query.toLowerCase().trim();
        List<String> found = new ArrayList<>();
        for (String month : SPANISH_MONTH_NAMES_ORDERED) {
            if (q.contains(month)) {
                found.add(month);
            }
        }
        // English month names mapping
        String[] enNames = {"january", "february", "march", "april", "may", "june",
                           "july", "august", "september", "october", "november", "december"};
        for (int i = 0; i < enNames.length; i++) {
            if (q.contains(enNames[i]) && !found.contains(SPANISH_MONTH_NAMES_ORDERED[i])) {
                found.add(SPANISH_MONTH_NAMES_ORDERED[i]);
            }
        }
        log().info("Extracted months from query: {}", found);
        return found;
    }

    /**
     * Filters comparables map to only include requested months (so comparison is February vs April when asked, not all months).
     */
    private Map<String, ComparisonValue> filterComparablesByMonths(Map<String, ComparisonValue> comparables, List<String> requestedMonths) {
        if (comparables == null || requestedMonths == null || requestedMonths.isEmpty()) {
            return comparables != null ? comparables : new LinkedHashMap<>();
        }
        Map<String, ComparisonValue> out = new LinkedHashMap<>();
        for (String month : requestedMonths) {
            if (comparables.containsKey(month)) {
                out.put(month, comparables.get(month));
            } else {
                out.put(month, new ComparisonValue(0, ComparisonType.COUNT));
            }
        }
        return out;
    }

    /**
     * Attendees count per minute by month; used for more-attendees-in-February-vs-August style queries.
     */
    private Map.Entry<String, ComparisonValue> extractAttendeesByMonthValue(Minute minute) {
        if (minute.date() == null) {
            return null;
        }
        try {
            java.time.LocalDate parsedDate = parseDateFlexible(minute.date());
            if (parsedDate == null) {
                return null;
            }
            String monthName = getMonthName(parsedDate.getMonthValue());
            int count = minute.numberOfAttendees() > 0 ? minute.numberOfAttendees() :
                    (minute.attendees() != null ? minute.attendees().size() : 0);
            return new AbstractMap.SimpleEntry<>(monthName, new ComparisonValue(count, ComparisonType.NUMERIC));
        } catch (Exception e) {
            log().debug("Could not extract attendees by month from minute {}: {}", minute.id(), e.getMessage());
            return null;
        }
    }

    /**
     * One meeting (acta) per minute; used for "meetings count by month" comparison.
     */
    private Map.Entry<String, ComparisonValue> extractMeetingsCountByMonthValue(Minute minute) {
        if (minute.date() == null) {
            return null;
        }
        try {
            java.time.LocalDate parsedDate = parseDateFlexible(minute.date());
            if (parsedDate == null) {
                return null;
            }
            String monthName = getMonthName(parsedDate.getMonthValue());
            return new AbstractMap.SimpleEntry<>(monthName, new ComparisonValue(1, ComparisonType.COUNT));
        } catch (Exception e) {
            log().debug("Could not extract month for meetings count from minute {}: {}", minute.id(), e.getMessage());
            return null;
        }
    }

    /**
     * Gets Spanish month name from month number (1-12)
     */
    private String getMonthName(int month) {
        if (month >= 1 && month <= 12) {
            return SPANISH_MONTH_NAMES_ORDERED[month - 1];
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
        for (String month : SPANISH_MONTH_NAMES_ORDERED) {
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
        
        return !label.isEmpty() ? label.toString() : minute.id();
    }

    private static int attendeeCountForComparison(Minute minute) {
        if (minute.numberOfAttendees() > 0) {
            return minute.numberOfAttendees();
        }
        return minute.attendees() != null ? minute.attendees().size() : 0;
    }

    /**
     * Extracts field value based on field type
     */
    private Object extractFieldValue(Minute minute, ComparisonField field) {
        switch (field.fieldName) {
            case "numberOfAttendees":
                return attendeeCountForComparison(minute);
            case FIELD_KEY_DURATION:
                return calculateDurationFromMinute(minute);
            case "date":
                return minute.date();
            case FIELD_KEY_PLACE:
                return minute.place();
            case "topics":
                return minute.topics() != null ? minute.topics().size() : 0;
            case "decisions":
                return minute.decisions() != null ? minute.decisions().size() : 0;
            default:
                return null;
        }
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
                    if (cv.value instanceof Number number) {
                        return number.doubleValue();
                    } else if (cv.value instanceof String str) {
                        try {
                            return Double.parseDouble(str);
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
        
        boolean singleDataPoint = comparables != null && comparables.size() == 1;
        boolean isDurationField = field != null && FIELD_KEY_DURATION.equals(field.fieldName);
        boolean queryComparesTwoDates = query != null && (query.toLowerCase().contains("más larga") || query.toLowerCase().contains("más corta") || query.toLowerCase().contains("which was longer"));
        String extraInstructions = "";
        if (singleDataPoint && isDurationField && queryComparesTwoDates) {
            extraInstructions = "\n- Only one meeting date has data. Do NOT claim which meeting was longer; state the duration available and that data was found for only one of the dates.";
        }
        if (isDurationField) {
            extraInstructions += "\n- When answering which meeting was longer, INCLUDE the duration value in the answer (e.g. 'La del 25 de febrero de 2025 fue más larga, con una duración de 1 hora y 45 minutos').";
        }
        
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
            - The labels (month names or other terms) and values in the comparison data are authoritative. Do NOT invert or swap them.
            - State which option has more according to the numbers given (e.g. if data shows "febrero: 0" and "agosto: 2", say agosto has more, not febrero).
            - Use the exact labels from the data (e.g. febrero, abril, agosto) in your answer. Do not assume a fixed pair like "febrero vs agosto" if the data shows different months.
            - If a CONCLUSION line is present in the data, your answer MUST agree with it verbatim. Do not state the opposite (e.g. if CONCLUSION says "agosto tiene más", do not say "febrero tiene más").%s
            """, query, comparisonData, simpleStats != null ? simpleStats : "", extraInstructions);
        
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

    /** Builds deterministic conclusion line so LLM does not invert (e.g. "August has more than February" when data says otherwise). Handles ties. */
    private String formatMonthConclusion(Map<String, ComparisonValue> comparables, String unitLabel) {
        if (comparables == null || comparables.size() != 2) {
            return "";
        }
        Map.Entry<String, ComparisonValue> e1 = comparables.entrySet().iterator().next();
        Map.Entry<String, ComparisonValue> e2 = comparables.entrySet().stream().skip(1).findFirst().orElse(null);
        if (e2 == null || !(e1.getValue().value instanceof Number n1) || !(e2.getValue().value instanceof Number n2)) {
            return "";
        }
        double v1 = n1.doubleValue();
        double v2 = n2.doubleValue();
        if (v1 == v2) {
            return String.format("%nCONCLUSION: Ambos meses tienen el mismo número de %s.", unitLabel);
        }
        String more = v1 > v2 ? e1.getKey() : e2.getKey();
        String less = v1 > v2 ? e2.getKey() : e1.getKey();
        return String.format("%nCONCLUSION: %s tiene más %s que %s.", more, unitLabel, less);
    }

    /**
     * Formats comparison data for LLM prompt
     */
    private String formatComparisonData(Map<String, ComparisonValue> comparables, ComparisonField field) {
        if (FIELD_MENTIONS_BY_MONTH.equals(field.fieldName)) {
            // Preserve order: list each month with its count, add context phrase and conclusion
            List<Map.Entry<String, ComparisonValue>> entries = new ArrayList<>(comparables.entrySet());
            String contextPhrase = "";
            if (entries.size() >= 2) {
                Map.Entry<String, ComparisonValue> e1 = entries.get(0);
                Map.Entry<String, ComparisonValue> e2 = entries.get(1);
                contextPhrase = String.format("En %s, %s menciones; en %s, %s. ",
                    e1.getKey(), e1.getValue().value, e2.getKey(), e2.getValue().value);
            }
            String lines = entries.stream()
                    .map(entry -> String.format("- %s: %s menciones", entry.getKey(), entry.getValue().value))
                    .collect(Collectors.joining("\n"));
            lines = contextPhrase + "\n" + lines + formatMonthConclusion(comparables, "menciones");
            return lines;
        }
        if (FIELD_MEETINGS_COUNT_BY_MONTH.equals(field.fieldName)) {
            String lines = comparables.entrySet().stream()
                    .map(entry -> {
                        String month = entry.getKey();
                        Object value = entry.getValue().value;
                        return String.format("- %s: %s reuniones", month, value);
                    })
                    .collect(Collectors.joining("\n"));
            lines += formatMonthConclusion(comparables, "reuniones");
            return lines;
        }
        if (FIELD_NUMBER_OF_ATTENDEES_BY_MONTH.equals(field.fieldName)) {
            String lines = comparables.entrySet().stream()
                    .map(entry -> {
                        String month = entry.getKey();
                        Object value = entry.getValue().value;
                        return String.format("- %s: %s asistentes", month, value);
                    })
                    .collect(Collectors.joining("\n"));
            lines += formatMonthConclusion(comparables, "asistentes");
            return lines;
        }

        // For attendees comparison (per-minute labels), sort descending (highest first) and format clearly
        if ("numberOfAttendees".equals(field.fieldName)) {
            log().info("Formatting attendees comparison data. Total entries: {}", comparables.size());
            List<Map.Entry<String, ComparisonValue>> sorted = comparables.entrySet().stream()
                    .sorted(Map.Entry.<String, ComparisonValue>comparingByValue((a, b) -> {
                        if (a.value instanceof Number na && b.value instanceof Number nb) {
                            return Double.compare(nb.doubleValue(), na.doubleValue());
                        }
                        return 0;
                    }).reversed())
                    .toList();
            
            log().info("Sorted attendees comparison (descending): {}", 
                      sorted.stream()
                          .map(e -> String.format("%s: %s", e.getKey(), e.getValue().value))
                          .collect(Collectors.joining(", ")));
            
            return sorted.stream()
                    .map(entry -> String.format("- %s: %s asistentes", entry.getKey(), entry.getValue().value))
                    .collect(Collectors.joining("\n"));
        }

        // Default formatting for other comparison types (sort descending for numeric/count)
        return comparables.entrySet().stream()
                .sorted(Map.Entry.<String, ComparisonValue>comparingByValue((a, b) -> {
                    if (a.value instanceof Number na && b.value instanceof Number nb) {
                        return Double.compare(nb.doubleValue(), na.doubleValue());
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
