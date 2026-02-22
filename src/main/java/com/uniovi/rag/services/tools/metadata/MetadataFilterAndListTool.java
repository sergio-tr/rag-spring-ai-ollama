package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import com.uniovi.rag.utils.InfoExtractor;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataFilterAndListTool for filtering and listing meeting minutes with intelligent analysis.
 */
public class MetadataFilterAndListTool extends AbstractMetadataTool {

    public MetadataFilterAndListTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
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
                    .collect(Collectors.toList());
            log().info("Filtered {} minutes by exact attendees count ({}), {} remaining", relevantMinutes.size(), exactAttendees, byCount.size());
            relevantMinutes = byCount;
        }

        // Step 3.7: Additional filtering by topic + person if query requires it (AND logic)
        if (requiresTopicAndPersonFilter(query)) {
            List<Minute> topicPersonFiltered = filterMinutesByTopicAndPerson(query, relevantMinutes, ner);
            log().info("Filtered {} minutes by topic + person (AND logic), {} remaining (applied filter even if empty)", 
                      relevantMinutes.size(), topicPersonFiltered.size());
            relevantMinutes = topicPersonFiltered;
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
        List<InfoExtractor.Cluster<FilterResult>> clusters = clusterResults(rankedResults);

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
                .map(minute -> CompletableFuture.supplyAsync(() -> generateSummary(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> result.getSummary() != null && !result.getSummary().isBlank())
                .collect(Collectors.toList());
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
    private List<InfoExtractor.Cluster<FilterResult>> clusterResults(List<FilterResult> results) {
        return InfoExtractor.clusterItems(
            results,
            result -> result.getSummary(),
            result -> result.getDate() != null ? result.getDate() : "unknown",
            0.3 // Similarity threshold
        );
    }

    /**
     * Generates enhanced filter answer with clustering and analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedFilterAnswer(String query, List<FilterResult> results, 
                                               List<InfoExtractor.Cluster<FilterResult>> clusters) {
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
                .map(r -> String.format("- %s: %s", 
                    r.getDate() != null ? r.getDate() : "unknown date",
                    r.getSummary() != null && r.getSummary().length() > 150 ? r.getSummary().substring(0, 150) + "..." : (r.getSummary() != null ? r.getSummary() : "")))
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
        return String.format("Found %d relevant meetings:\n%s",
                          results.size(), resultsText);
    }

    /**
     * Formats result summary for LLM prompt (without technical details)
     */
    private String formatResultSummary(List<FilterResult> results, List<InfoExtractor.Cluster<FilterResult>> clusters) {
        StringBuilder summary = new StringBuilder();
        
        // Format results naturally without mentioning clusters
        for (int i = 0; i < clusters.size(); i++) {
            InfoExtractor.Cluster<FilterResult> cluster = clusters.get(i);
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
     * True when the query asks for meetings where a specific person attended (e.g. "¿Cuándo asistió Alejandro Torres Rojas?")
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
                    }
                    return false;
                })
                .collect(Collectors.toList());
        log().info("Filtered to {} minutes where '{}' (normalized: '{}') is in attendees", out.size(), personName, normalized);
        return out;
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

    /**
     * Checks if query requires filtering by both topic and person (AND logic)
     */
    private boolean requiresTopicAndPersonFilter(String query) {
        return detectTopicAndPersonFilter(query);
    }
    
    /**
     * Filters minutes by topic AND person (both conditions must be met - AND logic)
     * Example: "Dime los asistentes de reuniones donde se habló de climatización y que fueran presididas por Natalia Vázquez Gutiérrez"
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
            log().warn("Could not extract person name from query for topic+person filtering. Query: '{}'", query);
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
        
        List<Minute> filtered = minutes.stream()
                .filter(minute -> {
                    // Check topic condition (in topics, decisions, or summary)
                    boolean topicMatches = false;
                    String topicLower = normalizedTopic;
                    
                    if (minute.topics() != null) {
                        topicMatches = minute.topics().stream()
                                .anyMatch(t -> t != null && normalizePersonName(t).contains(topicLower));
                    }
                    if (!topicMatches && minute.decisions() != null) {
                        topicMatches = minute.decisions().stream()
                                .anyMatch(d -> d != null && normalizePersonName(d).contains(topicLower));
                    }
                    if (!topicMatches && minute.summary() != null) {
                        topicMatches = normalizePersonName(minute.summary()).contains(topicLower);
                    }
                    
                    if (!topicMatches) {
                        log().debug("Minute {} filtered out: topic '{}' not found in topics/decisions/summary", 
                                  minute.id(), topic);
                        return false; // Topic doesn't match - AND logic requires both
                    }
                    
                    // Check person condition (president or secretary) - BOTH topic AND person must match (AND logic)
                    boolean personMatches = false;
                    if (filterByPresident && minute.president() != null) {
                        String presidentNormalized = normalizePersonName(minute.president());
                        // More robust matching: check if normalized names contain each other or are equal
                        personMatches = presidentNormalized.equals(normalizedPersonName) ||
                                       presidentNormalized.contains(normalizedPersonName) ||
                                       normalizedPersonName.contains(presidentNormalized);
                        if (personMatches) {
                            log().debug("Minute {} person match (president): '{}' matches '{}'", 
                                      minute.id(), minute.president(), personName);
                        }
                    }
                    if (!personMatches && filterBySecretary && minute.secretary() != null) {
                        String secretaryNormalized = normalizePersonName(minute.secretary());
                        personMatches = secretaryNormalized.equals(normalizedPersonName) ||
                                       secretaryNormalized.contains(normalizedPersonName) ||
                                       normalizedPersonName.contains(secretaryNormalized);
                        if (personMatches) {
                            log().debug("Minute {} person match (secretary): '{}' matches '{}'", 
                                      minute.id(), minute.secretary(), personName);
                        }
                    }
                    
                    if (!personMatches) {
                        log().debug("Minute {} filtered out: person '{}' not found as president/secretary. " +
                                  "President: '{}', Secretary: '{}'", 
                                  minute.id(), personName, 
                                  minute.president() != null ? minute.president() : "null",
                                  minute.secretary() != null ? minute.secretary() : "null");
                        return false; // Person doesn't match
                    }
                    
                    // Both topic AND person match
                    log().debug("Minute {} passed both filters: topic '{}' AND person '{}'", 
                              minute.id(), topic, personName);
                    return true;
                })
                .collect(Collectors.toList());
        
        log().info("Topic+person filtering result: {} minutes passed (out of {}). Topic: '{}', Person: '{}'", 
                  filtered.size(), minutes.size(), topic, personName);
        
        return filtered;
    }

}
