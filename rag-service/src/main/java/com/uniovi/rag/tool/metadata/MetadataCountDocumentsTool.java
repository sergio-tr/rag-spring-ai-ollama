package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.domain.model.CountingAnalysis;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

/**
 * Enhanced MetadataCountDocumentsTool for counting meeting minutes with intelligent analysis.
 * P12: Count is by unique document_id (dedupeMinutesByDocumentId); attendees filter is strict
 * (e.g. "menos de diez" uses count &lt; 10); date-existence guard for non-existent dates runs in {@link com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator}.
 */
public class MetadataCountDocumentsTool extends AbstractMetadataTool {

    private static final String QUERY_STAGE_COUNT = "count";

    private static final String PLACEHOLDER_UNKNOWN_MONTH = "unknown";

    private static final String[] COUNT_RETRIEVAL_FIELDS = {
            "date", "place", "topics", "decisions", "summary"
    };

    private record TopicFilterOutcome(List<Document> docs, String topic, ToolResult earlyExit) {
    }

    private record AttendeesFilterResult(List<Document> docs, ToolResult earlyExit) {
    }

    private static final String[] SPANISH_MONTH_NAMES_LOWER = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    };
    private static final String[] ENGLISH_MONTH_NAMES_LOWER = {
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
    };

    public MetadataCountDocumentsTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor,
            MetadataLlmResponseCacheService llmResponseCache) {
        super(chatClient, retriever, extractor, llmResponseCache);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing count documents query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        long startTime = System.currentTimeMillis();
        List<Document> docs = retrieveDocumentsWithFallback(query, COUNT_RETRIEVAL_FIELDS, ner);

        ToolResult early = exitWhenDateSpecifiedButNoDocuments(query, ner, docs);
        if (early != null) {
            return early;
        }

        TopicFilterOutcome topicOutcome = filterDocumentsByExtractedTopic(query, ner, docs);
        if (topicOutcome.earlyExit() != null) {
            return topicOutcome.earlyExit();
        }
        docs = topicOutcome.docs();
        String topic = topicOutcome.topic();

        AttendeesFilterResult attendeesResult = applyAttendeesCountFilterIfNeeded(query, docs);
        if (attendeesResult.earlyExit() != null) {
            return attendeesResult.earlyExit();
        }
        docs = attendeesResult.docs();

        ToolResult missing = notFoundIfEmptyDocuments(query, docs, QUERY_STAGE_COUNT);
        if (missing != null) {
            return missing;
        }

        // Step 1.8: Dedupe documents by document_id so we have at most one doc per minute (avoids overcounting e.g. elevator)
        int docsBeforeDedupe = docs.size();
        docs = dedupeDocsByDocumentId(docs);
        if (docs.size() < docsBeforeDedupe) {
            log().info("Deduped documents by document_id: {} -> {} unique minutes", docsBeforeDedupe, docs.size());
        }

        // Step 2: Extract minutes in parallel (chunks may repeat same document_id; count by unique minute)
        List<Minute> minutes = extractMinutesInParallel(docs);
        minutes = dedupeMinutesByDocumentId(minutes);
        missing = notFoundIfEmptyMinutes(query, minutes, QUERY_STAGE_COUNT);
        if (missing != null) {
            return missing;
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        ToolResult missingRelevant = notFoundIfEmptyRelevantMinutes(query, relevantMinutes, QUERY_STAGE_COUNT);
        if (missingRelevant != null) {
            log().info("No relevant minutes found for count query: {}", query);
            String zeroMsg = messageWhenNoRelevantMinutes(topic, query);
            return ToolResult.from(formatResponse(zeroMsg, query), getClass());
        }

        // Step 3.5: Check if query asks for month comparison
        // Example: which month had more registered meetings (February vs April, etc.)
        if (detectMonthComparisonQuery(query)) {
            log().info("Query asks for month comparison, processing accordingly");
            String answer = generateMonthComparisonAnswer(query, relevantMinutes);
            return ToolResult.from(formatResponse(answer, query), getClass());
        }
        
        // Step 4: Perform comprehensive counting analysis
        CountingAnalysis analysis = performCountingAnalysis(query, relevantMinutes);
        
        // Step 5: Generate enhanced count answer
        String answer = generateEnhancedCountAnswer(query, analysis);
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated count answer for query: '{}' with {} documents (total execution time: {} ms)", 
                  query, analysis.getTotalCount(), totalTime);
        
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private ToolResult exitWhenDateSpecifiedButNoDocuments(String query, JSONObject ner, List<Document> docs) {
        String requestedDate = extractDateFromQuery(query, ner);
        if (requestedDate != null && docs.isEmpty()) {
            String errorMessage = generateDateNotFoundMessage(query, requestedDate);
            log().info("No documents found for specified date: {} in query: {}", requestedDate, query);
            return ToolResult.from(formatResponse(errorMessage, query), getClass());
        }
        return null;
    }

    private TopicFilterOutcome filterDocumentsByExtractedTopic(String query, JSONObject ner, List<Document> docs) {
        String topic = extractTopicFromQuery(query, ner);
        if (topic == null || docs.isEmpty()) {
            return new TopicFilterOutcome(docs, topic, null);
        }
        log().info("Filtering documents by topic: {}", topic);
        List<Document> filteredDocs = filterDocumentsByTopic(docs, topic);
        if (filteredDocs.isEmpty()) {
            log().info("No documents found for topic '{}' in query: {}", topic, query);
            String errorMessage = generateSpecificErrorMessage(query, "topic", topic, docs.size(),
                    "No documents mention this topic");
            return new TopicFilterOutcome(docs, topic,
                    ToolResult.from(formatResponse(errorMessage, query), getClass()));
        }
        log().info("Filtered to {} documents that mention topic '{}'", filteredDocs.size(), topic);
        return new TopicFilterOutcome(filteredDocs, topic, null);
    }

    private AttendeesFilterResult applyAttendeesCountFilterIfNeeded(String query, List<Document> docs) {
        AttendeesCountQueryInfo attendeesQueryInfo = detectAttendeesCountQuery(query);
        if (attendeesQueryInfo == null) {
            return new AttendeesFilterResult(docs, null);
        }
        log().info("Query asks about number of attendees (operator={}, threshold={}), filtering documents",
                attendeesQueryInfo.operator, attendeesQueryInfo.threshold);
        List<Document> filteredByAttendees = filterDocumentsByAttendeesCount(attendeesQueryInfo, docs);
        log().info("Filtered {} documents by attendees count criteria, {} remaining (applied filter even if empty)",
                docs.size(), filteredByAttendees.size());
        if (filteredByAttendees.isEmpty()) {
            String zeroAnswer = generateCountZeroMessage(query, attendeesQueryInfo);
            return new AttendeesFilterResult(filteredByAttendees,
                    ToolResult.from(formatResponse(zeroAnswer, query), getClass()));
        }
        return new AttendeesFilterResult(filteredByAttendees, null);
    }

    private String messageWhenNoRelevantMinutes(String topic, String query) {
        if (topic == null) {
            return generateNotFoundMessage(query);
        }
        if (querySeemsSpanish(query)) {
            return "No se encontraron actas que cumplan con ese criterio.";
        }
        return "No meeting minutes match the specified criteria.";
    }

    /**
     * Heuristic used for bilingual response templates.
     *
     * <p>Implemented without regex to avoid super-linear backtracking on adversarial long inputs.
     */
    static boolean querySeemsSpanish(String query) {
        if (query == null || query.isBlank()) return false;
        boolean hasAlphabetic = false;
        boolean hasSpanishDiacritic = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (!hasAlphabetic && Character.isAlphabetic(c)) hasAlphabetic = true;
            if (!hasSpanishDiacritic && isSpanishDiacritic(c)) hasSpanishDiacritic = true;
            if (hasAlphabetic && hasSpanishDiacritic) return true;
        }
        return false;
    }

    private static boolean isSpanishDiacritic(char c) {
        // include ü as it's common in Spanish loanwords (e.g. pingüino)
        return c == 'á' || c == 'é' || c == 'í' || c == 'ó' || c == 'ú' || c == 'ü' || c == 'ñ'
                || c == 'Á' || c == 'É' || c == 'Í' || c == 'Ó' || c == 'Ú' || c == 'Ü' || c == 'Ñ';
    }

    /**
     * Deduplicates documents by document_id so we pass at most one doc per minute to extractMinutesInParallel.
     */
    private List<Document> dedupeDocsByDocumentId(List<Document> docs) {
        if (docs == null || docs.isEmpty()) return docs;
        Set<String> seen = new LinkedHashSet<>();
        List<Document> out = new ArrayList<>();
        for (Document doc : docs) {
            String id = getDocumentIdFromDoc(doc);
            if (id == null) id = "";
            if (seen.add(id)) {
                out.add(doc);
            }
        }
        return out;
    }

    /**
     * Deduplicates minutes by document_id (id) so count is by unique actas, not chunks.
     */
    private List<Minute> dedupeMinutesByDocumentId(List<Minute> minutes) {
        if (minutes == null || minutes.isEmpty()) return minutes;
        Set<String> seen = new LinkedHashSet<>();
        List<Minute> out = new ArrayList<>();
        for (Minute m : minutes) {
            String id = m.id() != null ? m.id() : "";
            if (seen.add(id)) {
                out.add(m);
            }
        }
        if (out.size() < minutes.size()) {
            log().info("Deduped minutes by document_id: {} -> {} unique actas", minutes.size(), out.size());
        }
        return out;
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
        if (queryIndicatesAttendeeCountInterest(query)) {
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

    private static boolean queryIndicatesAttendeeCountInterest(String query) {
        if (query == null) {
            return false;
        }
        String q = query.toLowerCase();
        return q.contains("asistente")
                || q.contains("attendee")
                || q.contains("participaron")
                || q.contains("personas");
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
        
        String extraInstructions = "";
        String qLower = query.toLowerCase();
        if (qLower.contains("contexto") || qLower.contains("en qué contexto")) {
            extraInstructions = " If the question asks for context (e.g. 'en qué contexto fue tratada'), include the date(s) from the information and a brief context (e.g. improvements, budget request, specific purpose).";
        }
        if (qLower.contains("cuántas actas") || qLower.contains("cuántas reuniones") || qLower.contains("qué actas") || qLower.contains("cuáles")) {
            extraInstructions += " If the question asks which actas or which dates, list the dates from the 'Fechas' field in the information (e.g. '24 de febrero de 2025 y 25 de agosto de 2026').";
        }
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
            6.%s
            
            Provide only the information requested.
            DO NOT mention any technical details like "análisis temporal", "análisis de distribución", "temporal analysis", "distribution analysis", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering naturally and concisely, as if you were a helpful assistant.
            """, 
            analysis.getTotalCount(),
            simpleData != null ? simpleData : "No additional information available.",
            extraInstructions
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
            
            return removeQuestionEcho(trimmed, query);
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
            data.append("Fechas: ").append(String.join(", ", analysis.getDates())).append(System.lineSeparator());
        }

        if (analysis.getPlaces() != null && !analysis.getPlaces().isEmpty()) {
            data.append("Lugares: ").append(String.join(", ", analysis.getPlaces())).append(System.lineSeparator());
        }

        if (analysis.getTopics() != null && !analysis.getTopics().isEmpty()) {
            data.append("Temas principales: ")
                    .append(String.join(", ", analysis.getTopics().stream().limit(10).collect(Collectors.toList())))
                    .append(System.lineSeparator());
        }

        if (analysis.getAttendeesCounts() != null && !analysis.getAttendeesCounts().isEmpty()) {
            String countsStr = analysis.getAttendeesCounts().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            data.append("Número de asistentes por acta: ").append(countsStr).append(System.lineSeparator());
        }
        
        return data.toString();
    }
    
    /**
     * When count-by-attendees filter yields 0 documents (e.g. "fewer than ten" and no minute has &lt;10 attendees).
     */
    private String generateCountZeroMessage(String query, AttendeesCountQueryInfo queryInfo) {
        if (query == null || queryInfo == null) return "0 actas.";
        String lang = querySeemsSpanish(query) ? "es" : "en";
        if ("less_than".equals(queryInfo.operator)) {
            return lang.equals("es")
                ? String.format("Ninguna acta cumple el criterio (menos de %d personas).", queryInfo.threshold)
                : String.format("No meeting minutes have fewer than %d attendees.", queryInfo.threshold);
        }
        if ("more_than".equals(queryInfo.operator)) {
            return lang.equals("es")
                ? String.format("Ninguna acta cumple el criterio (más de %d personas).", queryInfo.threshold)
                : String.format("No meeting minutes have more than %d attendees.", queryInfo.threshold);
        }
        return lang.equals("es") ? "Ninguna acta cumple el criterio." : "No meeting minutes match the criteria.";
    }

    /**
     * Filters documents by attendeesCount based on query criteria (P12: strict &lt;10 for "menos de diez").
     * Uses structured information from LLM detection instead of hardcoded string checks.
     */
    private List<Document> filterDocumentsByAttendeesCount(AttendeesCountQueryInfo queryInfo, List<Document> docs) {
        if (docs.isEmpty() || queryInfo == null) {
            return docs;
        }
        
        final int threshold = queryInfo.threshold;
        final String operator = queryInfo.operator;
        
        log().info("Filtering {} documents by attendeesCount (operator: {}, threshold: {})", 
                  docs.size(), operator, threshold);
        
        List<Document> filtered = docs.stream()
                .filter(doc -> {
                    Integer count = getAttendeesCount(doc);
                    if (count == null) {
                        log().debug("Document {} has no attendeesCount, excluding from filter", doc.getId());
                        return false; // Exclude documents without attendeesCount
                    }
                    
                    // less_than: strictly fewer than threshold (e.g. "menos de diez" -> count < 10, so 10 is excluded)
                    boolean matches;
                    switch (operator) {
                        case "less_than":
                            matches = count < threshold;
                            break;
                        case "more_than":
                            matches = count > threshold;
                            break;
                        case "equal":
                            matches = count == threshold;
                            break;
                        default:
                            log().warn("Unknown operator: {}, defaulting to less_than", operator);
                            matches = count < threshold;
                            break;
                    }
                    
                    if (matches) {
                        log().debug("Document {} passed filter: count={} {} threshold={}", 
                                  doc.getId(), count, operator, threshold);
                    } else {
                        log().debug("Document {} filtered out: count={} does NOT {} threshold={}", 
                                  doc.getId(), count, operator, threshold);
                    }
                    
                    return matches;
                })
                .toList();

        log().info("Filtered {} documents by attendeesCount (operator: {}, threshold: {}), {} remaining. " +
                  "This filter was applied even if it results in 0 documents.", 
                  docs.size(), operator, threshold, filtered.size());
        
        return filtered;
    }
    
    /**
     * Detects if the query asks for month comparison (which month had more meetings, etc.).
     */
    private boolean detectMonthComparisonQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String queryLower = query.toLowerCase();
        boolean hasMonthComparison = (queryLower.contains("qué mes") || queryLower.contains("which month") ||
                                     queryLower.contains("qué mes tuvo") || queryLower.contains("which month had")) &&
                                    (queryLower.contains("más") || queryLower.contains("more") ||
                                     queryLower.contains("menos") || queryLower.contains("less")) &&
                                    (queryLower.contains(" o ") || queryLower.contains(" or "));
        
        // Also check for explicit month names (Spanish + English)
        int monthCount = 0;
        for (String month : SPANISH_MONTH_NAMES_LOWER) {
            if (queryLower.contains(month)) {
                monthCount++;
            }
        }
        for (String month : ENGLISH_MONTH_NAMES_LOWER) {
            if (queryLower.contains(month)) {
                monthCount++;
            }
        }
        
        boolean hasMultipleMonths = monthCount >= 2;
        
        log().debug("Month comparison detection: hasMonthComparison={}, hasMultipleMonths={}, monthCount={}", 
                   hasMonthComparison, hasMultipleMonths, monthCount);
        
        return hasMonthComparison || hasMultipleMonths;
    }
    
    /**
     * Generates the answer for month comparison queries.
     */
    private String generateMonthComparisonAnswer(String query, List<Minute> minutes) {
        if (minutes == null || minutes.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        // Extract months from query
        List<String> requestedMonths = extractMonthsFromQuery(query);
        log().info("Extracted months from query: {}", requestedMonths);
        
        // Count meetings by month
        Map<String, Integer> meetingsByMonth = countMeetingsByMonth(minutes, requestedMonths);
        log().info("Meetings by month: {}", meetingsByMonth);
        
        // Build comparison data
        StringBuilder comparisonData = new StringBuilder();
        for (Map.Entry<String, Integer> entry : meetingsByMonth.entrySet()) {
            comparisonData.append(String.format("- %s: %d reuniones%n", entry.getKey(), entry.getValue()));
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Meeting counts by month:
            %s
            
            CRITICAL INSTRUCTIONS:
            1. Respond in the EXACT SAME LANGUAGE as the question
            2. Compare the months and clearly state which month has MORE meetings
            3. If months have the same count, state that clearly
            4. If a requested month has no meetings, state that clearly
            5. Be concise and direct
            6. Do NOT repeat the question
            7. Start directly with the comparison answer
            
            Examples:
            - If febrero has 2 and abril has 0: "Febrero tiene varias reuniones registradas en diferentes años. No se encuentran actas correspondientes al mes de abril."
            - If both have the same count: "Ambos meses tienen el mismo número de reuniones: X reuniones cada uno."
            """, query, comparisonData.toString());
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating month comparison answer with LLM", e);
        }
        
        // Fallback
        return formatMonthComparisonFallback(meetingsByMonth);
    }
    
    /**
     * Extracts month names from query.
     */
    private List<String> extractMonthsFromQuery(String query) {
        List<String> months = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return months;
        }
        
        String queryLower = query.toLowerCase();

        for (String month : SPANISH_MONTH_NAMES_LOWER) {
            if (queryLower.contains(month)) {
                months.add(month);
            }
        }
        
        return months;
    }
    
    /**
     * Counts meetings by month.
     */
    private Map<String, Integer> countMeetingsByMonth(List<Minute> minutes, List<String> requestedMonths) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        
        // Initialize counts for requested months
        for (String month : requestedMonths) {
            counts.put(month, 0);
        }
        
        // Count meetings by month
        for (Minute minute : minutes) {
            String rawDate = minute.date();
            if (rawDate == null) {
                continue;
            }
            try {
                LocalDate parsedDate = parseDateFlexible(rawDate);
                if (parsedDate == null) {
                    log().debug("Could not parse date '{}' for minute {}", rawDate, minute.id());
                } else {
                    int monthValue = parsedDate.getMonthValue();
                    String monthName = getMonthName(monthValue);
                    if (requestedMonths.isEmpty() || requestedMonths.contains(monthName)) {
                        counts.put(monthName, counts.getOrDefault(monthName, 0) + 1);
                        log().debug("Counted meeting for month '{}' (minute {}, date: {})",
                                monthName, minute.id(), rawDate);
                    }
                }
            } catch (Exception e) {
                log().warn("Error extracting month from date '{}' for minute {}: {}",
                        rawDate, minute.id(), e.getMessage());
            }
        }
        
        return counts;
    }
    
    /**
     * Gets Spanish month name from month number (1-12).
     */
    private String getMonthName(int month) {
        if (month >= 1 && month <= 12) {
            return SPANISH_MONTH_NAMES_LOWER[month - 1];
        }
        return PLACEHOLDER_UNKNOWN_MONTH;
    }
    
    /**
     * Formats fallback month comparison answer.
     */
    private String formatMonthComparisonFallback(Map<String, Integer> meetingsByMonth) {
        if (meetingsByMonth.isEmpty()) {
            return "No meetings were found for the requested months.";
        }
        
        StringBuilder answer = new StringBuilder();
        for (Map.Entry<String, Integer> entry : meetingsByMonth.entrySet()) {
            answer.append(String.format("%s: %d reuniones%n", entry.getKey(), entry.getValue()));
        }
        
        return answer.toString().trim();
    }

}
