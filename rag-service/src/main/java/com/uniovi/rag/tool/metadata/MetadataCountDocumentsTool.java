package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.domain.model.CountingAnalysis;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

/**
 * Enhanced MetadataCountDocumentsTool for counting meeting minutes with intelligent analysis.
 * P12: Count is by unique canonical meeting (date-normalized dedupe); attendees filter is strict
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

    private record PersonActaCountQuery(String personName) {
    }

    private record StartTimeCountQuery(String targetTime) {
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
        
        Optional<ToolResult> futureExit = exitWhenFutureOrUnavailableDate(query, ner, getClass());
        if (futureExit.isPresent()) {
            return futureExit.get();
        }

        boolean topicCountQuery =
                (isTopicMeetingCountQuery(query) || isTopicPresenceCountQuery(query))
                        && extractTopicFromQuery(query, ner) != null;

        PersonActaCountQuery personActaQueryEarly = detectPersonActaCountQuery(query, ner);

        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        long startTime = System.currentTimeMillis();
        List<Document> docs =
                topicCountQuery || personActaQueryEarly != null
                        ? retrieveWithSmallCorpusFullScanIfEligible(query, COUNT_RETRIEVAL_FIELDS, ner)
                        : retrieveDocumentsWithFallback(query, COUNT_RETRIEVAL_FIELDS, ner);

        ToolResult early = exitWhenDateSpecifiedButNoDocuments(query, ner, docs);
        if (early != null) {
            return early;
        }

        StartTimeCountQuery startTimeCountQuery = detectStartTimeCountQuery(query, ner);
        if (startTimeCountQuery != null) {
            return countMeetingsStartingAtTime(query, docs, startTimeCountQuery);
        }

        String requestedYear = extractYearFromQuery(query, ner);
        if (requestedYear != null && isYearMeetingCountQuery(query)) {
            return countMeetingsInYear(query, docs, requestedYear);
        }

        if (requestedYear != null && !docs.isEmpty()) {
            docs = filterDocumentsByYear(docs, requestedYear);
            if (docs.isEmpty()) {
                String zeroMsg =
                        querySeemsSpanish(query)
                                ? (isYearOnlyActaCountQuery(query)
                                        ? yearOnlyActaCorpusAbsenceMessage(requestedYear)
                                        : "No hay reuniones registradas en el año "
                                                + requestedYear
                                                + ".")
                                : "No meetings were found in year " + requestedYear + ".";
                return ToolResult.from(formatResponse(zeroMsg, query), getClass());
            }
        }

        PersonActaCountQuery personActaQuery = detectPersonActaCountQuery(query, ner);
        if (personActaQuery != null) {
            return countActasContainingPerson(query, ner, docs, personActaQuery);
        }

        TopicFilterOutcome topicOutcome = filterDocumentsByExtractedTopic(query, ner, docs);
        if (topicOutcome.earlyExit() != null) {
            return topicOutcome.earlyExit();
        }
        docs = topicOutcome.docs();
        String topic = topicOutcome.topic();

        if (topic != null && (isTopicMeetingCountQuery(query) || isTopicPresenceCountQuery(query))) {
            return countMeetingsMentioningTopic(query, docs, topic);
        }

        AttendeesFilterResult attendeesResult = applyAttendeesCountFilterIfNeeded(query, docs);
        if (attendeesResult.earlyExit() != null) {
            return attendeesResult.earlyExit();
        }
        docs = attendeesResult.docs();

        ToolResult missing = notFoundIfEmptyDocuments(query, docs, QUERY_STAGE_COUNT);
        if (missing != null) {
            return missing;
        }

        // Step 1.8: Canonical meeting projection (scope filter + HYBRID dedupe)
        int docsBeforeCanonical = docs.size();
        List<Minute> minutes = canonicalizeMeetingsFromDocuments(docs);
        if (minutes.size() < docsBeforeCanonical) {
            log().info(
                    "Canonicalized retrieval rows: {} chunks -> {} meetings",
                    docsBeforeCanonical,
                    minutes.size());
        }

        missing = notFoundIfEmptyMinutes(query, minutes, QUERY_STAGE_COUNT);
        if (missing != null) {
            return missing;
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (requestedYear != null) {
            List<Minute> byYear =
                    relevantMinutes.stream().filter(minute -> minuteMatchesYear(minute, requestedYear)).toList();
            log().info(
                    "Filtered {} relevant minutes to {} by calendar year {}",
                    relevantMinutes.size(),
                    byYear.size(),
                    requestedYear);
            relevantMinutes = byYear;
        }
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
        if (!relevantMinutes.isEmpty()) {
            publishMatchedMinutesContext(relevantMinutes, true);
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated count answer for query: '{}' with {} documents (total execution time: {} ms)", 
                  query, analysis.getTotalCount(), totalTime);
        
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private PersonActaCountQuery detectPersonActaCountQuery(String query, JSONObject ner) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String q = query.toLowerCase(Locale.ROOT);
        boolean personActaCount =
                q.contains("en cuántas actas aparece")
                        || q.contains("en cuantas actas aparece")
                        || q.contains("en cuántas actas particip")
                        || q.contains("en cuantas actas particip")
                        || (q.contains("cuántas actas") && q.contains("aparece"))
                        || (q.contains("cuantas actas") && q.contains("aparece"));
        if (!personActaCount) {
            return null;
        }
        String personName = extractPersonNameFromQuery(query, ner);
        if (personName == null || personName.isBlank()) {
            return null;
        }
        return new PersonActaCountQuery(personName);
    }

    private boolean isYearMeetingCountQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q =
                Normalizer.normalize(query.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
        boolean countCue =
                q.contains("cuantas reuniones")
                        || q.contains("cuántas reuniones")
                        || q.contains("cuantas actas")
                        || q.contains("cuántas actas")
                        || q.contains("how many meetings")
                        || q.contains("how many actas");
        boolean meetingCue = q.contains("reuniones") || q.contains("reunion") || q.contains("actas");
        boolean notTopic = !q.contains("mencion") && !q.contains("habl");
        boolean notStartTime =
                !q.contains("comenzaron")
                        && !q.contains("iniciaron")
                        && !q.contains("hora de inicio")
                        && !q.contains("started at");
        return countCue && meetingCue && notTopic && notStartTime;
    }

    private ToolResult countMeetingsInYear(String query, List<Document> docs, String year) {
        if (docs == null || docs.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
        List<Minute> minutes = canonicalizeMeetingsFromDocuments(docs);
        List<Minute> matching =
                minutes.stream()
                        .filter(minute -> minuteMatchesYear(minute, year))
                        .sorted(
                                Comparator.comparing(
                                        StructuredMinuteMetadataSupport::formatDateSlash,
                                        Comparator.nullsLast(String::compareTo)))
                        .toList();
        if (matching.isEmpty()) {
            String zeroMsg =
                    querySeemsSpanish(query)
                            ? (isYearOnlyActaCountQuery(query)
                                    ? yearOnlyActaCorpusAbsenceMessage(year)
                                    : "No hay reuniones registradas en el año " + year + ".")
                            : "No meetings were found in year " + year + ".";
            return ToolResult.from(formatResponse(zeroMsg, query), getClass());
        }
        String answer = StructuredMinuteMetadataSupport.formatYearMeetingCountAnswer(query, matching, year);
        log().info("Year meeting count for {}: {} actas", year, matching.size());
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private StartTimeCountQuery detectStartTimeCountQuery(String query, JSONObject ner) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String q =
                Normalizer.normalize(query.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
        boolean countCue =
                q.contains("cuantas reuniones")
                        || q.contains("cuántas reuniones")
                        || q.contains("cuantas actas")
                        || q.contains("cuántas actas");
        boolean startCue =
                q.contains("comenzaron")
                        || q.contains("comenzo")
                        || q.contains("iniciaron")
                        || q.contains("started at")
                        || q.contains("start at");
        if (!countCue || !startCue) {
            return null;
        }
        String targetTime = extractStartTimeFromQuery(query, ner);
        if (targetTime == null) {
            return null;
        }
        return new StartTimeCountQuery(targetTime);
    }

    private ToolResult countMeetingsStartingAtTime(String query, List<Document> docs, StartTimeCountQuery timeQuery) {
        if (docs == null || docs.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
        List<Minute> minutes = canonicalizeMeetingsFromDocuments(docs);
        if (minutes.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
        List<Minute> matching =
                minutes.stream().filter(m -> minuteStartsAtTime(m, timeQuery.targetTime())).toList();
        int count = matching.size();
        List<String> dates =
                matching.stream()
                        .map(Minute::date)
                        .filter(Objects::nonNull)
                        .filter(d -> !d.isBlank())
                        .distinct()
                        .sorted()
                        .toList();
        String answer = formatStartTimeCountAnswer(query, count, dates, timeQuery.targetTime());
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private String formatStartTimeCountAnswer(String query, int count, List<String> dates, String targetTime) {
        if (querySeemsSpanish(query)) {
            if (count == 0) {
                return "Ninguna reunión comenzó a las " + targetTime + " horas.";
            }
            if (count == 1) {
                return "Una reunión comenzó a las " + targetTime + " horas" + formatDatesSuffix(dates) + ".";
            }
            return count + " reuniones comenzaron a las " + targetTime + " horas" + formatDatesSuffix(dates) + ".";
        }
        if (count == 0) {
            return "No meetings started at " + targetTime + ".";
        }
        if (count == 1) {
            return "One meeting started at " + targetTime + formatDatesSuffix(dates) + ".";
        }
        return count + " meetings started at " + targetTime + formatDatesSuffix(dates) + ".";
    }

    private ToolResult countActasContainingPerson(
            String query, JSONObject ner, List<Document> docs, PersonActaCountQuery personQuery) {
        if (docs.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
        List<Minute> minutes = canonicalizeMeetingsFromDocuments(docs);
        if (minutes.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
        final String normalizedPerson = normalizePersonName(personQuery.personName());
        List<Minute> matching =
                minutes.stream().filter(m -> minuteContainsPerson(m, normalizedPerson, personQuery.personName())).toList();
        int count = matching.size();
        List<String> dates =
                matching.stream()
                        .map(Minute::date)
                        .filter(Objects::nonNull)
                        .filter(d -> !d.isBlank())
                        .distinct()
                        .sorted()
                        .toList();
        String answer;
        if (querySeemsSpanish(query)) {
            if (count == 0) {
                answer = personQuery.personName() + " no aparece en ninguna acta.";
            } else if (count == 1) {
                answer = personQuery.personName() + " aparece en 1 acta" + formatDatesSuffix(dates) + ".";
            } else {
                answer = personQuery.personName() + " aparece en " + count + " actas" + formatDatesSuffix(dates) + ".";
            }
        } else if (count == 0) {
            answer = personQuery.personName() + " does not appear in any meeting minutes.";
        } else {
            answer = personQuery.personName() + " appears in " + count + " meeting minute(s)" + formatDatesSuffix(dates) + ".";
        }
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private boolean minuteContainsPerson(Minute minute, String normalizedPerson, String displayName) {
        if (minute == null || normalizedPerson.isBlank()) {
            return false;
        }
        if (nameMatches(minute.president(), normalizedPerson, displayName)) {
            return true;
        }
        if (nameMatches(minute.secretary(), normalizedPerson, displayName)) {
            return true;
        }
        if (minute.attendees() != null) {
            for (String attendee : minute.attendees()) {
                if (nameMatches(attendee, normalizedPerson, displayName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean nameMatches(String candidate, String normalizedPerson, String displayName) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String normalizedCandidate = normalizePersonName(candidate);
        return normalizedCandidate.equals(normalizedPerson)
                || normalizedCandidate.contains(normalizedPerson)
                || normalizedPerson.contains(normalizedCandidate)
                || candidate.equalsIgnoreCase(displayName);
    }

    private static String formatDatesSuffix(List<String> dates) {
        if (dates == null || dates.isEmpty()) {
            return "";
        }
        return " (" + String.join(", ", dates) + ")";
    }

    private boolean isTopicMeetingCountQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        boolean countCue =
                q.contains("cuántas")
                        || q.contains("cuantas")
                        || q.contains("en cuántas")
                        || q.contains("en cuantas")
                        || q.contains("how many");
        boolean topicMention =
                q.contains("se habló")
                        || q.contains("se hablo")
                        || q.contains("mencion")
                        || q.contains("mention")
                        || q.contains("discuss");
        return countCue && topicMention;
    }

    private boolean isTopicPresenceCountQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        boolean spokeCue = q.contains("se habló") || q.contains("se hablo");
        boolean meetingCue =
                q.contains("alguna reunión")
                        || q.contains("alguna reunion")
                        || q.contains("en alguna")
                        || q.contains("en las actas");
        return spokeCue && meetingCue;
    }

    private ToolResult countMeetingsMentioningTopic(String query, List<Document> docs, String topic) {
        if (docs == null || docs.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
        Map<String, String> evidenceByKey = buildMeetingEvidenceTextByKey(docs);
        List<Document> merged = mergeChunksWithRichestMetadata(collectInScopeCorpusRows(docs));
        List<Minute> minutes = extractMeetingShellsForTopicMatch(merged);
        if (minutes.isEmpty()) {
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
        List<Minute> matching =
                minutes.stream().filter(m -> minuteMentionsTopicEvidence(m, topic, evidenceByKey)).toList();
        if (!matching.isEmpty()) {
            publishMatchedMinutesContext(matching, true);
        }
        String answer = StructuredMinuteMetadataSupport.formatTopicMeetingCountAnswer(query, matching, topic);
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    // Topic evidence matching delegated to AbstractMetadataTool.minuteMentionsTopicEvidence

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
        if (isTopicMeetingCountQuery(query) || isTopicPresenceCountQuery(query)) {
            log().info(
                    "Topic count query for '{}': scanning full corpus at minute metadata level (skip chunk pre-filter)",
                    topic);
            return new TopicFilterOutcome(docs, topic, null);
        }
        log().info("Filtering documents by topic: {}", topic);
        List<Document> filteredDocs = filterDocumentsByTopic(docs, topic);
        if (filteredDocs.isEmpty()) {
            if (isTopicPresenceCountQuery(query)) {
                log().info(
                        "Topic '{}' not found in chunk filter; scanning full corpus metadata for presence query",
                        topic);
                return new TopicFilterOutcome(docs, topic, null);
            }
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

    // dedupeDocsByDocumentId and dedupeMinutesByDocumentId inherited from AbstractMetadataTool

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
