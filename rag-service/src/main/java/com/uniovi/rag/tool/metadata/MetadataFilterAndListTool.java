package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.domain.model.Cluster;
import com.uniovi.rag.domain.model.FilterResult;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.uniovi.rag.infrastructure.observability.ContextPropagatingFutures.supplyAsync;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataFilterAndListTool for filtering and listing meeting minutes with intelligent analysis.
 */
public class MetadataFilterAndListTool extends AbstractMetadataTool {

    public MetadataFilterAndListTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor,
            MetadataLlmResponseCacheService llmResponseCache) {
        super(chatClient, retriever, extractor, llmResponseCache);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing filter and list query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().info("No documents found for filter and list query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for filter and list query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for filter and list query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
        
        // Step 3.5: Filter by attendee name when query asks "when/where did [person] attend" (e.g. Alejandro Torres Rojas)
        if (isAttendeeListQuery(query)) {
            List<Minute> byAttendee = filterMinutesByAttendeeName(query, relevantMinutes, ner);
            log().info("Filtered {} minutes by attendee name, {} remaining", relevantMinutes.size(), byAttendee.size());
            relevantMinutes = byAttendee;
        }

        // Step 3.6: Filter by exact attendees count when query asks "reuniones con exactamente 21 asistentes"
        Integer exactAttendees = extractExactAttendeesCount(query);
        if (exactAttendees != null) {
            List<Minute> byCount = relevantMinutes.stream()
                    .filter(m -> (m.numberOfAttendees() > 0 ? m.numberOfAttendees() : (m.attendees() != null ? m.attendees().size() : 0)) == exactAttendees)
                    .toList();
            log().info("Filtered {} minutes by exact attendees count ({}), {} remaining", relevantMinutes.size(), exactAttendees, byCount.size());
            relevantMinutes = byCount;
        }

        // Step 3.6a: Filter by requested month when query says "reuniones celebradas en agosto" (exclude e.g. febrero)
        Integer requestedMonth = extractRequestedMonthFromQuery(query);
        if (requestedMonth != null) {
            List<Minute> byMonth = filterMinutesByMonth(relevantMinutes, requestedMonth);
            log().info("Filtered {} minutes by month ({}), {} remaining", relevantMinutes.size(), requestedMonth, byMonth.size());
            relevantMinutes = byMonth;
        }

        // Step 3.6b: Filter by minimum attendees when query says "más de 18 asistentes"
        Integer minAttendees = extractMinAttendeesFromQuery(query);
        if (minAttendees != null) {
            List<Minute> byMinCount = relevantMinutes.stream()
                    .filter(m -> (m.numberOfAttendees() > 0 ? m.numberOfAttendees() : (m.attendees() != null ? m.attendees().size() : 0)) > minAttendees)
                    .toList();
            log().info("Filtered {} minutes by min attendees (>{}), {} remaining", relevantMinutes.size(), minAttendees, byMinCount.size());
            relevantMinutes = byMinCount;
        }

        // Step 3.6c: Filter by topic when query mentions a topic (e.g. August + video surveillance + >18 attendees → only ACTA 6 §4)
        String topicForFilter = extractTopicFromQuery(query, ner);
        if (topicForFilter != null && !topicForFilter.isBlank() && !requiresTopicAndPersonFilter(query)) {
            List<Minute> byTopic = filterMinutesByTopicOnly(relevantMinutes, topicForFilter);
            log().info("Filtered {} minutes by topic '{}', {} remaining", relevantMinutes.size(), topicForFilter, byTopic.size());
            relevantMinutes = byTopic;
        }

        // Step 3.7: Additional filtering by topic + person if query requires it (AND logic)
        // Do NOT apply when query is only "when/where did [person] attend" (e.g. Alejandro Torres) — would zero out valid results
        if (requiresTopicAndPersonFilter(query) && !isAttendeeListQuery(query)) {
            List<Minute> topicPersonFiltered = filterMinutesByTopicAndPerson(query, relevantMinutes, ner);
            log().info("Filtered {} minutes by topic + person (AND logic), {} remaining (applied filter even if empty)", 
                      relevantMinutes.size(), topicPersonFiltered.size());
            if (topicPersonFiltered.isEmpty()) {
                // Fallback: return actas that match topic only (useful when president name or topic wording differs from metadata)
                String topic = extractTopicFromQuery(query, ner);
                List<Minute> byTopicOnly = topic != null ? filterMinutesByTopicOnly(relevantMinutes, topic) : Collections.emptyList();
                if (!byTopicOnly.isEmpty()) {
                    relevantMinutes = byTopicOnly;
                    log().info("Topic+person yielded 0; using topic-only fallback with {} actas for topic '{}'", byTopicOnly.size(), topic);
                } else {
                    relevantMinutes = topicPersonFiltered;
                }
            } else {
                relevantMinutes = topicPersonFiltered;
            }
        }

        // When topic+person filter (and fallback) yielded 0 results, return explicit message with criteria (item 33)
        if (relevantMinutes.isEmpty() && requiresTopicAndPersonFilter(query) && !isAttendeeListQuery(query)) {
            String topic = extractTopicFromQuery(query, ner);
            String person = extractPersonNameFromQuery(query, ner);
            String msg = generateNoDataMessageForTopicAndPresident(query, topic, person);
            log().info("Topic+person and fallback yielded 0; returning explicit no-data message for topic '{}' and person '{}'", topic, person);
            return ToolResult.from(formatResponse(msg, query), getClass());
        }

        // Step 4: Generate summaries in parallel (metadata-first, LLM fallback)
        List<FilterResult> results = generateSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().info("No summaries generated for query: {}", query);
            return ToolResult.from(formatResponse(generateNoDataMessage(query), query), getClass());
        }

        // Step 5: Analyze and rank results
        List<FilterResult> rankedResults = analyzeAndRankResults(results);

        // Step 6: Cluster similar results
        List<Cluster<FilterResult>> clusters = clusterResults(rankedResults);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedFilterAnswer(query, rankedResults, clusters);
        log().info("Generated filter and list answer for query: {} with {} results in {} clusters", 
                   query, results.size(), clusters.size());
        
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    /**
     * Generates summaries in parallel
     */
    private List<FilterResult> generateSummariesInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<FilterResult>> futures = minutes.stream()
                .map(minute -> supplyAsync(() -> generateSummary(query, minute)))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> result.getSummary() != null && !result.getSummary().isBlank())
                .toList();
    }

    /**
     * Generates summary for a minute with enhanced context
     */
    private FilterResult generateSummary(String query, Minute minute) {
        // Metadata-first summary
        String summary = buildSummaryFromMetadata(minute);

        if (summary.isBlank()) {
            // Fallback to LLM summary
            summary = buildSummaryExplanation(query, minute);
        }
        
        if (summary.isBlank()) {
            return null;
        }
        
        int score = summary.length();
        
        return new FilterResult(
            minute.id(),
            minute.date(),
            minute.place(),
            summary,
            score
        );
    }

    /**
     * Metadata-first summary builder; uses existing summary, decisions, topics, agenda.
     */
    private String buildSummaryFromMetadata(Minute minute) {
        if (minute == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (minute.summary() != null && !minute.summary().isBlank()) {
            parts.add(minute.summary());
        }
        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            parts.add("Decisiones: " + String.join("; ", minute.decisions()));
        }
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            parts.add("Temas: " + String.join("; ", minute.topics()));
        }
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            parts.add("Agenda: " + minute.agenda().toString());
        }
        return String.join(" | ", parts).trim();
    }

    /**
     * LLM fallback summary when metadata is insufficient.
     */
    private String buildSummaryExplanation(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }
        
        String prompt = String.format("""
            Summarize the meeting in 2-3 sentences focusing on what is most relevant to the query:
            Query: %s
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            Agenda: %s
            """,
            query,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown",
            minute.agenda() != null ? minute.agenda().toString() : "unknown"
        );
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response == null || response.trim().isEmpty()) {
                log().info("Empty response from LLM in buildSummaryExplanation, returning empty string");
                return "";
            }
            return response.trim();
        } catch (Exception e) {
            log().error("Error building summary explanation, returning empty string", e);
            return "";
        }
    }

    /**
     * Analyzes and ranks results by relevance and quality
     */
    private List<FilterResult> analyzeAndRankResults(List<FilterResult> results) {
        // Sort by summary length (descending) as a simple signal of content richness
        return results.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getSummary() != null ? b.getSummary().length() : 0,
                        a.getSummary() != null ? a.getSummary().length() : 0))
                .collect(Collectors.toList());
    }

    /**
     * Clusters similar results to avoid redundancy
     */
    private List<Cluster<FilterResult>> clusterResults(List<FilterResult> results) {
        if (extractor == null) {
            return results.stream().map(Cluster::new).toList();
        }
        return extractor.clusterItems(
            results,
            result -> result.getSummary(),
            result -> result.getDate() != null ? result.getDate() : "unknown",
            0.3
        );
    }

    /**
     * Generates enhanced filter answer with clustering and analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedFilterAnswer(String query, List<FilterResult> results, 
                                               List<Cluster<FilterResult>> clusters) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String resultSummary = formatResultSummary(results, clusters);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant meeting minutes:
            
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "clusters", "análisis", "analysis", "grouped into", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, results.size(), 
            resultSummary != null ? resultSummary : "No results found.");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedFilterAnswer, using fallback");
                return generateFallbackFilterAnswer(query, results);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced filter answer, using fallback", e);
            return generateFallbackFilterAnswer(query, results);
        }
    }
    
    /**
     * Generates a fallback filter answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackFilterAnswer(String query, List<FilterResult> results) {
        String resultsText = results.stream()
                .limit(5)
                .map(r -> {
                    String datePart = r.getDate() != null ? r.getDate() : "unknown date";
                    String sum = r.getSummary();
                    String summaryPart;
                    if (sum != null && sum.length() > 150) {
                        summaryPart = sum.substring(0, 150) + "...";
                    } else {
                        summaryPart = sum != null ? sum : "";
                    }
                    return String.format("- %s: %s", datePart, summaryPart);
                })
                .collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found %d relevant meetings:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            listing the found meetings.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", results.size(), resultsText);
        
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
            log().warn("Error generating fallback filter answer with LLM", e);
        }
        
        // Ultimate fallback
        return String.format("Found %d relevant meetings:%n%s",
                          results.size(), resultsText);
    }

    /**
     * Formats result summary for LLM prompt (without technical details)
     */
    private String formatResultSummary(List<FilterResult> results, List<Cluster<FilterResult>> clusters) {
        StringBuilder summary = new StringBuilder();
        
        // Format results naturally without mentioning clusters
        for (int i = 0; i < clusters.size(); i++) {
            Cluster<FilterResult> cluster = clusters.get(i);
            FilterResult representative = cluster.getRepresentativeItem();
            
            if (representative.getDate() != null) {
                summary.append(String.format("Reunión del %s", representative.getDate()));
                if (representative.getPlace() != null) {
                    summary.append(String.format(" (%s)", representative.getPlace()));
                }
                summary.append(":\n");
            }
            summary.append(representative.getSummary() != null ? representative.getSummary() : "");
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /**
     * True when the query asks for meetings where a specific person attended (when/which meetings someone attended).
     */
    private boolean isAttendeeListQuery(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        return (q.contains("asistió") || q.contains("asistieron") || q.contains("attended") || q.contains("participó") || q.contains("participaron"))
                && (q.contains("reuniones") || q.contains("actas") || q.contains("meetings"));
    }

    /**
     * Filters minutes to those where the named person appears in the attendees list (normalized name matching).
     */
    private List<Minute> filterMinutesByAttendeeName(String query, List<Minute> minutes, JSONObject ner) {
        if (minutes.isEmpty() || query == null) return minutes;
        String personName = extractPersonNameFromQuery(query, ner);
        // Fallback: "¿Cuándo (y en qué reuniones) asistió Alejandro Torres Rojas?" (§4 Alejandro → ACTA 1, 3, 6)
        if ((personName == null || personName.trim().isEmpty()) && query != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?:asistió|asistieron|participó|participaron)\\s+([\\p{L}]{1,128}(?:\\s+[\\p{L}]{1,128}){1,24})\\s*\\??",
                java.util.regex.Pattern.CASE_INSENSITIVE
                        | java.util.regex.Pattern.UNICODE_CASE
                        | java.util.regex.Pattern.UNICODE_CHARACTER_CLASS
            ).matcher(query);
            if (m.find()) {
                personName = m.group(1).trim();
                log().debug("Extracted attendee name via fallback: {}", personName);
            }
        }
        if (personName == null || personName.trim().isEmpty()) return minutes;
        final String normalized = normalizePersonName(personName);
        List<Minute> out = minutes.stream()
                .filter(m -> {
                    if (m.attendees() == null || m.attendees().isEmpty()) return false;
                    for (String a : m.attendees()) {
                        if (a == null) continue;
                        String an = normalizePersonName(a);
                        if (an.equals(normalized) || an.contains(normalized) || normalized.contains(an)) {
                            return true;
                        }
                        // Token-based match: same set of name tokens (handles "Alejandro Torres Rojas" vs "Torres Rojas, Alejandro")
                        if (nameTokensMatch(normalized, an)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
        log().info("Filtered to {} minutes where '{}' (normalized: '{}') is in attendees", out.size(), personName, normalized);
        return out;
    }

    /** Returns true when both strings contain the same set of tokens (ignoring order), for flexible name matching. */
    private static boolean nameTokensMatch(String normalized1, String normalized2) {
        if (normalized1 == null || normalized2 == null) return false;
        Set<String> t1 = Arrays.stream(normalized1.split("\\s+")).filter(s -> s.length() > 1).collect(Collectors.toSet());
        Set<String> t2 = Arrays.stream(normalized2.split("\\s+")).filter(s -> s.length() > 1).collect(Collectors.toSet());
        return t1.size() >= 2 && t2.size() >= 2 && t1.equals(t2);
    }

    /**
     * Extracts exact attendees count from query (e.g. "exactamente 21 asistentes" -> 21). Returns null if not found.
     */
    private Integer extractExactAttendeesCount(String query) {
        if (query == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:exactamente|con)\\s+(\\d+)\\s+asistentes", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(query);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Extracts minimum attendees from query (e.g. Spanish "more than 18 attendees" patterns -> 18). Returns null if not found. */
    private Integer extractMinAttendeesFromQuery(String query) {
        if (query == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:más de|más que)\\s+(\\d+)\\s+asistentes",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE | java.util.regex.Pattern.CANON_EQ);
        java.util.regex.Matcher m = p.matcher(query);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static final java.util.Map<String, Integer> MONTH_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("enero", 1), java.util.Map.entry("febrero", 2), java.util.Map.entry("marzo", 3),
            java.util.Map.entry("abril", 4), java.util.Map.entry("mayo", 5), java.util.Map.entry("junio", 6),
            java.util.Map.entry("julio", 7), java.util.Map.entry("agosto", 8), java.util.Map.entry("septiembre", 9),
            java.util.Map.entry("octubre", 10), java.util.Map.entry("noviembre", 11), java.util.Map.entry("diciembre", 12)
    );

    /** Extracts requested month (1-12) when query says "reuniones celebradas en agosto" / "en agosto". Returns null if not a single-month filter. */
    private Integer extractRequestedMonthFromQuery(String query) {
        if (query == null) return null;
        String q = query.toLowerCase();
        for (java.util.Map.Entry<String, Integer> e : MONTH_NAMES.entrySet()) {
            String month = e.getKey();
            if (q.contains("en " + month) || q.contains("celebradas en " + month) || q.contains("del mes de " + month)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Keeps only minutes whose date falls in the given month (1-12). */
    private List<Minute> filterMinutesByMonth(List<Minute> minutes, int requestedMonth) {
        if (minutes == null || requestedMonth < 1 || requestedMonth > 12) return minutes;
        return minutes.stream()
                .filter(m -> {
                    if (m.date() == null || m.date().isBlank()) return false;
                    java.time.LocalDate d = parseDateFlexible(m.date());
                    return d != null && d.getMonthValue() == requestedMonth;
                })
                .collect(Collectors.toList());
    }

    /**
     * Checks if query requires filtering by both topic and person (AND logic)
     */
    private boolean requiresTopicAndPersonFilter(String query) {
        return detectTopicAndPersonFilter(query);
    }
    
    /**
     * Filters minutes by topic AND person (both conditions must be met — AND logic).
     * Example: attendees at meetings that discussed a topic and were chaired by a named person.
     */
    private List<Minute> filterMinutesByTopicAndPerson(String query, List<Minute> minutes, JSONObject ner) {
        if (minutes.isEmpty() || query == null) {
            return minutes;
        }
        
        if (!requiresTopicAndPersonFilter(query)) {
            return minutes; // No filtering needed
        }
        
        String queryLower = query.toLowerCase();
        
        // Extract topic from query
        String topic = extractTopicFromQuery(query, ner);
        if (topic == null || topic.isEmpty()) {
            log().debug("Could not extract topic from query for filtering: {}", query);
            return minutes; // Can't filter by topic
        }
        
        // Extract person from query or NER using improved extraction
        String personName = extractPersonNameFromQuery(query, ner);
        
        if (personName == null || personName.isEmpty()) {
            if (queryRequiresPerson(query)) {
                log().warn("Could not extract person name from query for topic+person filtering. Query: '{}'", query);
            } else {
                log().debug("Could not extract person name from query for topic+person filtering (query may not require person). Query: '{}'", query);
            }
            return minutes; // Can't filter by person - return all to avoid false negatives
        }
        
        // Normalize names for comparison
        final String normalizedTopic = normalizePersonName(topic);
        final String normalizedPersonName = normalizePersonName(personName);
        
        // Determine which role to check (president, secretary, or both)
        final boolean filterByPresident = queryLower.contains("presididas") || queryLower.contains("presidida") ||
                                         queryLower.contains("presidió") || queryLower.contains("president") ||
                                         queryLower.contains("presid");
        final boolean filterBySecretary = queryLower.contains("secretario") || queryLower.contains("secretary") ||
                                         queryLower.contains("secretaria");
        
        log().info("Filtering {} minutes by topic '{}' (normalized: '{}') AND person '{}' (normalized: '{}'). " +
                  "Checking president: {}, secretary: {}", 
                  minutes.size(), topic, normalizedTopic, personName, normalizedPersonName, 
                  filterByPresident, filterBySecretary);
        
        // Topic synonyms so minute wording matches (e.g. ascensor <-> elevator for minute 24 Feb 2025)
        List<String> topicTerms = topicTermsForMatch(normalizedTopic);
        
        List<Minute> filtered =
                minutes.stream()
                        .filter(
                                minute ->
                                        minutePassesTopicAndPersonFilters(
                                                minute,
                                                topic,
                                                personName,
                                                topicTerms,
                                                normalizedPersonName,
                                                filterByPresident,
                                                filterBySecretary))
                        .collect(Collectors.toList());
        
        log().info("Topic+person filtering result: {} minutes passed (out of {}). Topic: '{}', Person: '{}'", 
                  filtered.size(), minutes.size(), topic, personName);
        
        return filtered;
    }

    private boolean minutePassesTopicAndPersonFilters(
            Minute minute,
            String topicLabel,
            String personName,
            List<String> topicTerms,
            String normalizedPersonName,
            boolean filterByPresident,
            boolean filterBySecretary) {
        if (!minuteTopicMatchesTerms(minute, topicTerms)) {
            log().debug(
                    "Minute {} filtered out: topic '{}' not found in topics/decisions/summary",
                    minute.id(),
                    topicLabel);
            return false;
        }
        if (!minutePersonMatchesRoles(
                minute, filterByPresident, filterBySecretary, normalizedPersonName, personName)) {
            log().debug(
                    "Minute {} filtered out: person '{}' not found as president/secretary. President: '{}', Secretary: '{}'",
                    minute.id(),
                    personName,
                    minute.president() != null ? minute.president() : "null",
                    minute.secretary() != null ? minute.secretary() : "null");
            return false;
        }
        log().debug("Minute {} passed both filters: topic '{}' AND person '{}'", minute.id(), topicLabel, personName);
        return true;
    }

    private boolean minuteTopicMatchesTerms(Minute minute, List<String> topicTerms) {
        if (minute.topics() != null
                && minute.topics().stream()
                        .anyMatch(
                                t ->
                                        t != null
                                                && topicTerms.stream()
                                                        .anyMatch(term -> normalizePersonName(t).contains(term)))) {
            return true;
        }
        if (minute.decisions() != null
                && minute.decisions().stream()
                        .anyMatch(
                                d ->
                                        d != null
                                                && topicTerms.stream()
                                                        .anyMatch(term -> normalizePersonName(d).contains(term)))) {
            return true;
        }
        if (minute.summary() != null) {
            String summaryNorm = normalizePersonName(minute.summary());
            return topicTerms.stream().anyMatch(summaryNorm::contains);
        }
        return false;
    }

    private boolean minutePersonMatchesRoles(
            Minute minute,
            boolean filterByPresident,
            boolean filterBySecretary,
            String normalizedPersonName,
            String personNameForLog) {
        boolean personMatches = false;
        if (filterByPresident && minute.president() != null) {
            String presidentNormalized = normalizePersonName(minute.president());
            personMatches =
                    presidentNormalized.equals(normalizedPersonName)
                            || presidentNormalized.contains(normalizedPersonName)
                            || normalizedPersonName.contains(presidentNormalized);
            if (personMatches) {
                log().debug(
                        "Minute {} person match (president): '{}' matches '{}'",
                        minute.id(),
                        minute.president(),
                        personNameForLog);
            }
        }
        if (!personMatches && filterBySecretary && minute.secretary() != null) {
            String secretaryNormalized = normalizePersonName(minute.secretary());
            personMatches =
                    secretaryNormalized.equals(normalizedPersonName)
                            || secretaryNormalized.contains(normalizedPersonName)
                            || normalizedPersonName.contains(secretaryNormalized);
            if (personMatches) {
                log().debug(
                        "Minute {} person match (secretary): '{}' matches '{}'",
                        minute.id(),
                        minute.secretary(),
                        personNameForLog);
            }
        }
        return personMatches;
    }
    
    /** Topic key terms including synonyms for matching (e.g. ascensor / elevator). */
    private List<String> topicTermsForMatch(String normalizedTopic) {
        List<String> terms = new ArrayList<>();
        terms.add(normalizedTopic);
        if (normalizedTopic != null) {
            if (normalizedTopic.contains("ascensor")) {
                terms.add("elevator");
            }
            if (normalizedTopic.contains("elevator")) {
                terms.add("ascensor");
            }
        }
        return terms;
    }

    /**
     * Filters minutes to those that mention the given topic (in topics, decisions, summary, or agenda).
     * Uses synonyms for common topics (e.g. video surveillance → surveillance, cameras). §4 August + video surveillance + >18 attendees → ACTA 6 only.
     */
    private List<Minute> filterMinutesByTopicOnly(List<Minute> minutes, String topic) {
        if (minutes.isEmpty() || topic == null || topic.isBlank()) return minutes;
        String topicNorm = normalizePersonName(topic);
        List<String> terms = new ArrayList<>();
        terms.add(topicNorm);
        if (topicNorm.contains("videovigilancia") || topicNorm.contains("vigilancia")) {
            terms.add("videovigilancia"); terms.add("vigilancia"); terms.add("camaras"); terms.add("cámaras");
        }
        if (topicNorm.contains("calefaccion") || topicNorm.contains("calefacción")) {
            terms.add("calefaccion"); terms.add("calefacción");
        }
        List<Minute> out = minutes.stream()
                .filter(m -> {
                    String ts = (m.topics() != null ? String.join(" ", m.topics()) : "") + " "
                            + (m.decisions() != null ? String.join(" ", m.decisions()) : "") + " "
                            + (m.summary() != null ? m.summary() : "") + " "
                            + (m.agenda() != null ? m.agenda().values().stream().filter(Objects::nonNull).reduce("", (a, b) -> a + " " + b) : "");
                    String combined = normalizePersonName(ts);
                    return terms.stream().anyMatch(combined::contains);
                })
                .toList();
        return out;
    }

    /**
     * Generates an explicit no-data message when topic+person filter yields 0 results (item 33).
     * Message includes the search criteria (topic and person) so the user knows why nothing was found.
     */
    private String generateNoDataMessageForTopicAndPresident(String query, String topic, String person) {
        String t = topic != null && !topic.isBlank() ? topic : "el tema indicado";
        String p = person != null && !person.isBlank() ? person : "la persona indicada";
        boolean likelySpanish = query != null && (query.contains("reunión") || query.contains("reuniones")
                || query.contains("presidida") || query.contains("presididas") || query.contains("hablara") || query.contains("asistentes"));
        if (likelySpanish) {
            return String.format("No se ha encontrado ninguna reunión en la que se hablara de %s y que además fuera presidida por %s.", t, p);
        }
        return String.format("No meeting was found that discussed %s and was chaired by %s.", t, p);
    }

}
