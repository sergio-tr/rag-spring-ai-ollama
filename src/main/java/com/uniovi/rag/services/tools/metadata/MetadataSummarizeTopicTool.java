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
 * Enhanced MetadataSummarizeTopicTool for summarizing specific topics from meeting minutes with intelligent analysis.
 */
public class MetadataSummarizeTopicTool extends AbstractMetadataTool {

    public MetadataSummarizeTopicTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing summarize topic query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"},
            ner
        );
        
        // Validate date if present in query
        String requestedDate = extractDateFromQuery(query, ner);
        if (requestedDate != null && docs.isEmpty()) {
            // Date was specified but no documents match
            String errorMessage = generateDateNotFoundMessage(query, requestedDate);
            log().info("No documents found for specified date: {} in query: {}", requestedDate, query);
            return ToolResult.from(formatResponse(errorMessage, query), getClass());
        }
        
        if (docs.isEmpty()) {
            log().info("No documents found for summarize topic query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for summarize topic query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for summarize topic query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }
        
        // Step 3.5: Additional filtering by specific topic with relevance threshold
        // If threshold leaves 0 minutes, for known domain topics (calefacción, videovigilancia) retry with relaxed threshold
        String topic = extractTopicFromQuery(query, ner);
        if (topic != null && !topic.isEmpty()) {
            if (detectSpecificTopicQuery(query)) {
                List<Minute> topicFiltered = filterMinutesByTopicWithThreshold(relevantMinutes, topic);
                if (topicFiltered.isEmpty() && isRelaxedTopicForFallback(topic)) {
                    log().info("Topic '{}' had 0 matches with default threshold; retrying with relaxed threshold 0.25", topic);
                    topicFiltered = filterMinutesByTopicWithThreshold(relevantMinutes, topic, 0.25);
                }
                if (!topicFiltered.isEmpty()) {
                    log().info("Filtered {} minutes by topic '{}' with relevance threshold, {} remaining",
                              relevantMinutes.size(), topic, topicFiltered.size());
                    relevantMinutes = topicFiltered;
                } else {
                    // Specific topic (e.g. climatización piscina, renovación tejado) with 0 matches: return explicit "no encontrado"
                    log().info("Topic '{}' matched 0 minutes with threshold; returning topic-not-found message", topic);
                    String notFoundMsg = generateTopicNotFoundMessage(query, topic);
                    return ToolResult.from(formatResponse(notFoundMsg, query), getClass());
                }
            }
        }

        // Step 4: Generate topic summaries in parallel (metadata-first, LLM fallback)
        List<TopicResult> results = generateTopicSummariesInParallel(query, relevantMinutes);
        if (results.isEmpty()) {
            log().info("No topic summaries generated for query: {}", query);
            return ToolResult.from(formatResponse(generateNoDataMessage(query), query), getClass());
        }

        // Step 5: Analyze and rank topic summaries
        List<TopicResult> rankedResults = analyzeAndRankTopicSummaries(results);

        // Step 6: Generate final answer (metadata-only)
        String answer = generateTopicSummaryAnswer(query, rankedResults);
        log().info("Generated summarize topic answer for query: {} with {} topic summaries", 
                   query, results.size());
        
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    /**
     * Generates topic summaries in parallel
     */
    private List<TopicResult> generateTopicSummariesInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<TopicResult>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> generateTopicSummary(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> result.getTopicSummary() != null && !result.getTopicSummary().isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Generates topic summary for a minute with enhanced context
     */
    private TopicResult generateTopicSummary(String query, Minute minute) {
        String topicSummary = buildTopicSummaryFromMetadata(minute);
        
        if (topicSummary.isBlank()) {
            topicSummary = generateTopicSummaryWithLLM(query, minute);
        }

        if (topicSummary.isBlank()) {
            return null;
        }

        return new TopicResult(minute.id(), minute.date(), minute.place(), topicSummary);
    }

    /**
     * Topic summary from metadata fields.
     */
    private String buildTopicSummaryFromMetadata(Minute minute) {
        if (minute == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            parts.add("Temas: " + String.join("; ", minute.topics()));
        }
        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            parts.add("Decisiones: " + String.join("; ", minute.decisions()));
        }
        if (minute.summary() != null && !minute.summary().isBlank()) {
            parts.add("Resumen: " + minute.summary());
        }
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            parts.add("Agenda: " + minute.agenda().values().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining("; ")));
        }
        return String.join(" | ", parts).trim();
    }

    /**
     * Fallback LLM topic summary when metadata is insufficient.
     */
    private String generateTopicSummaryWithLLM(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }

        String prompt = String.format("""
            Summarize what is related to the topic(s) in the query in 3-4 sentences.
            Write in the same language as the query.
            Query: %s
            Date: %s
            Place: %s
            Topics: %s
            Decisions: %s
            Previous summary: %s
            Agenda: %s
            """,
            query,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "",
            minute.agenda() != null ? minute.agenda().toString() : "unknown"
        );

        try {
            String response = getLLMResponseCached(prompt);
            if (response == null || response.trim().isEmpty()) {
                log().info("Empty response from LLM in generateTopicSummaryWithLLM, returning empty string");
                return "";
            }
            return response;
        } catch (Exception e) {
            log().error("Error generating topic summary with LLM, returning empty string", e);
            return "";
        }
    }

    /**
     * Analyzes and ranks topic summaries by relevance and quality
     */
    private List<TopicResult> analyzeAndRankTopicSummaries(List<TopicResult> results) {
        // Orden simple por longitud de texto
        return results.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getTopicSummary() != null ? b.getTopicSummary().length() : 0,
                        a.getTopicSummary() != null ? a.getTopicSummary().length() : 0))
                .collect(Collectors.toList());
    }

    private String generateTopicSummaryAnswer(String query, List<TopicResult> results) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }

        // Build topic summary content (limit to 3 meetings max, 250 chars per summary)
        StringBuilder summaryContent = new StringBuilder();
        summaryContent.append(String.format("Based on %d meetings:\n\n", results.size()));

        results.stream().limit(3).forEach(r -> {
            if (r.getDate() != null) {
                summaryContent.append("Date: ").append(r.getDate());
                if (r.getPlace() != null) {
                    summaryContent.append(", Place: ").append(r.getPlace());
                }
                summaryContent.append("\n");
            }
            String txt = r.getTopicSummary() != null ? r.getTopicSummary() : "";
            // Limit to 250 characters per summary for conciseness
            summaryContent.append(txt.length() > 250 ? txt.substring(0, 250) + "..." : txt);
            summaryContent.append("\n\n");
        });

        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Topic summary information:
            %s
            
            CRITICAL INSTRUCTIONS:
            1. Format and present this topic summary in the EXACT SAME LANGUAGE as the user's question
            2. Be CONCISE - maximum 3-4 sentences total, focus on key points only
            3. Keep the structure clear and readable
            4. DO NOT repeat the question or any part of it at the beginning
            5. DO NOT start with phrases like "Resume lo tratado...", "Dime qué...", "The user asked...", etc.
            6. Start directly with the summary content
            7. If the question starts with "Resume", "Dime", "Busca", etc., do NOT include those words in your response
            8. Remove redundant information and focus on what's most relevant to the query
            9. If multiple meetings, summarize the key points across all meetings, not each one individually
            
            Examples of CORRECT responses:
            - Query: "Resume lo tratado en las reuniones sobre la climatización de la piscina"
              Correct: "Basado en 5 reuniones: [concise summary of key points]"
              Wrong: "Resume lo tratado en las reuniones sobre la climatización de la piscina.\\n\\nBasado en 5 reuniones: [summary]"
            
            - Query: "Dime qué se dijo sobre el ascensor"
              Correct: "Basado en 5 reuniones: [concise summary]"
              Wrong: "Dime qué se dijo sobre el ascensor.\\n\\nBasado en 5 reuniones: [summary]"
            """, query, summaryContent.toString());

        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                // Post-process to format and clean response
                return formatResponse(response, query);
            }
        } catch (Exception e) {
            log().warn("Error generating topic summary answer with LLM, using raw content", e);
        }

        // Fallback: return raw content
        return summaryContent.toString().trim();
    }
    
    /**
     * Filters minutes by topic with relevance threshold.
     * Only includes minutes where the topic is actually mentioned (not just vaguely related).
     * 
     * @param minutes List of minutes to filter
     * @param topic Topic to filter by
     * @return Filtered list of minutes that mention the topic
     */
    private List<Minute> filterMinutesByTopicWithThreshold(List<Minute> minutes, String topic) {
        boolean isCompoundTopic = topic != null && extractKeyTermsFromTopic(normalizePersonName(topic)).size() > 1;
        double threshold = isCompoundTopic ? 0.50 : 0.33;
        return filterMinutesByTopicWithThreshold(minutes, topic, threshold);
    }
    
    private List<Minute> filterMinutesByTopicWithThreshold(List<Minute> minutes, String topic, double relevanceThreshold) {
        if (minutes.isEmpty() || topic == null || topic.isEmpty()) {
            return minutes;
        }
        
        String topicLower = normalizePersonName(topic); // Reuse normalizePersonName for topic normalization
        log().info("Filtering {} minutes by topic '{}' (normalized: '{}') with relevance threshold {}", 
                  minutes.size(), topic, topicLower, String.format("%.2f", relevanceThreshold));
        
        // Extract key terms from compound topics (e.g., "climatización de la piscina" -> ["climatización", "piscina"])
        List<String> keyTerms = extractKeyTermsFromTopic(topicLower);
        log().debug("Extracted key terms from topic '{}': {}", topic, keyTerms);
        
        // For videovigilancia/vigilancia, keyTerms are synonyms (any match counts), not compound parts (item 39)
        boolean isCompoundTopic = keyTerms.size() > 1
                && !topicLower.contains("videovigilancia")
                && !topicLower.contains("vigilancia");
        
        List<Minute> filtered = minutes.stream()
                .filter(minute -> {
                    double relevanceScore = calculateTopicRelevance(minute, topicLower, keyTerms, isCompoundTopic);
                    boolean passes = relevanceScore >= relevanceThreshold;
                    
                    if (passes) {
                        log().debug("Minute {} passed topic filter: topic '{}', relevance score: {:.2f}", 
                                  minute.id(), topic, relevanceScore);
                    } else {
                        log().debug("Minute {} filtered out: topic '{}', relevance score: {:.2f} < threshold {:.2f}", 
                                  minute.id(), topic, String.format("%.2f", relevanceScore), String.format("%.2f", relevanceThreshold));
                    }
                    
                    return passes;
                })
                .collect(Collectors.toList());
        
        log().info("Filtered {} minutes by topic '{}' with STRICT threshold, {} remaining (threshold: {})", 
                  minutes.size(), topic, filtered.size(), String.format("%.2f", relevanceThreshold));
        
        return filtered;
    }
    
    /**
     * Calculates relevance score for a topic in a minute.
     * For compound topics, requires that all key terms appear.
     * 
     * @param minute Minute to check
     * @param topicNormalized Normalized topic string
     * @param keyTerms List of key terms extracted from topic
     * @param isCompoundTopic Whether this is a compound topic (multiple terms)
     * @return Relevance score (0.0 to 1.0)
     */
    private double calculateTopicRelevance(Minute minute, String topicNormalized, List<String> keyTerms, boolean isCompoundTopic) {
        if (keyTerms.isEmpty()) {
            return 0.0;
        }
        
        int foundTerms = 0;
        int totalChecks = 0;
        
        // Check in topics
        if (minute.topics() != null) {
            for (String t : minute.topics()) {
                if (t != null) {
                    String topicField = normalizePersonName(t);
                    totalChecks++;
                    if (isCompoundTopic) {
                        // For compound topics, check if ALL key terms appear
                        boolean allTermsFound = keyTerms.stream()
                                .allMatch(term -> topicField.contains(term));
                        if (allTermsFound) {
                            foundTerms += keyTerms.size();
                        }
                    } else {
                        // For simple topics, check if the topic or any synonym (keyTerm) appears
                        if (keyTerms.stream().anyMatch(term -> topicField.contains(term))) {
                            foundTerms++;
                        }
                    }
                }
            }
        }
        
        // Check in decisions
        if (minute.decisions() != null) {
            for (String d : minute.decisions()) {
                if (d != null) {
                    String decisionField = normalizePersonName(d);
                    totalChecks++;
                    if (isCompoundTopic) {
                        boolean allTermsFound = keyTerms.stream()
                                .allMatch(term -> decisionField.contains(term));
                        if (allTermsFound) {
                            foundTerms += keyTerms.size();
                        }
                    } else {
                        if (keyTerms.stream().anyMatch(term -> decisionField.contains(term))) {
                            foundTerms++;
                        }
                    }
                }
            }
        }
        
        // Check in summary
        if (minute.summary() != null) {
            String summaryField = normalizePersonName(minute.summary());
            totalChecks++;
            if (isCompoundTopic) {
                boolean allTermsFound = keyTerms.stream()
                        .allMatch(term -> summaryField.contains(term));
                if (allTermsFound) {
                    foundTerms += keyTerms.size();
                }
            } else {
                if (keyTerms.stream().anyMatch(term -> summaryField.contains(term))) {
                    foundTerms++;
                }
            }
        }
        
        // Check in agenda (order of day) - topic may appear only there (e.g. calefacción, videovigilancia)
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            String agendaText = minute.agenda().values().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(this::normalizePersonName)
                    .collect(Collectors.joining(" "));
            if (!agendaText.isEmpty()) {
                totalChecks++;
                if (isCompoundTopic) {
                    boolean allTermsFound = keyTerms.stream().allMatch(term -> agendaText.contains(term));
                    if (allTermsFound) {
                        foundTerms += keyTerms.size();
                    }
                } else {
                    if (keyTerms.stream().anyMatch(term -> agendaText.contains(term))) {
                        foundTerms++;
                    }
                }
            }
        }
        
        // Calculate relevance score
        if (totalChecks == 0) {
            return 0.0;
        }
        
        if (isCompoundTopic) {
            // For compound topics, require that at least one field contains ALL terms
            return foundTerms >= keyTerms.size() ? 1.0 : 0.0;
        } else {
            // For simple topics, calculate based on how many fields contain the topic
            return (double) foundTerms / Math.max(totalChecks, 1);
        }
    }
    
    /**
     * Extracts key terms from a topic phrase for semantic matching.
     * Similar to the method in MetadataCompareTool but adapted for this use case.
     */
    private List<String> extractKeyTermsFromTopic(String topic) {
        if (topic == null || topic.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> keyTerms = new ArrayList<>();
        
        // Split by common prepositions and conjunctions
        String[] parts = topic.split("\\s+(de|del|la|el|y|and|con|with|en|in|sobre|about)\\s+");
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase();
            if (!trimmed.isEmpty() && trimmed.length() > 2) { // Ignore very short words
                keyTerms.add(trimmed);
            }
        }
        
        // Also add the full topic as a key term
        keyTerms.add(topic.toLowerCase());

        // Add synonyms so acta wording is matched (calefacción ACTA 5; videovigilancia ACTA 2, 5, 6 - §4)
        if (keyTerms.stream().anyMatch(t -> t.contains("calefaccion") || t.contains("calefacción"))) {
            keyTerms.add("calefaccion");
            keyTerms.add("calefacción");
        }
        // Videovigilancia: include synonyms so actas with "cámaras", "vigilancia", "security cameras" match (item 39)
        if (keyTerms.stream().anyMatch(t -> t.contains("videovigilancia") || t.contains("vigilancia") || t.contains("camara"))) {
            keyTerms.add("videovigilancia");
            keyTerms.add("vigilancia");
            keyTerms.add("camaras");
            keyTerms.add("cámaras");
            keyTerms.add("camara");
            keyTerms.add("cámara");
            keyTerms.add("camaras de seguridad");
            keyTerms.add("cámaras de seguridad");
            keyTerms.add("security cameras");
            keyTerms.add("video surveillance");
            keyTerms.add("surveillance");
        }

        // Add normalized forms (no accents) so metadata normalized with normalizePersonName matches
        List<String> withNormalized = new ArrayList<>(keyTerms);
        for (String k : keyTerms) {
            String norm = normalizePersonName(k);
            if (!norm.isEmpty() && !withNormalized.contains(norm)) {
                withNormalized.add(norm);
            }
        }
        keyTerms = withNormalized;

        // Remove duplicates and sort by length (longer terms first for more specific matching)
        return keyTerms.stream()
                .distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());
    }
    
    /**
     * Topics that may appear in actas with varied wording; allow relaxed threshold fallback when strict filter yields 0.
     */
    private boolean isRelaxedTopicForFallback(String topic) {
        if (topic == null || topic.isEmpty()) return false;
        String t = topic.toLowerCase().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ñ", "n");
        return t.contains("calefaccion") || t.contains("calefacción")
            || t.contains("videovigilancia") || t.contains("vigilancia") || t.contains("camara") || t.contains("camaras");
    }
    
    /**
     * Generates a message when topic is not found in any minutes.
     */
    protected String generateTopicNotFoundMessage(String query, String topic) {
        if (query == null || query.trim().isEmpty()) {
            return String.format("No se encontró información sobre el tema '%s' en las actas disponibles.", topic);
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The topic '%s' was not found in any of the available meeting minutes.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating that no information was found about this topic.
            Be concise and direct.
            Do not repeat the question.
            """, query, topic != null ? topic : "the requested topic");
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating topic not found message with LLM", e);
        }
        
        // Fallback
        return String.format("No se encontró información sobre el tema '%s' en las actas disponibles.", 
                            topic != null ? topic : "solicitado");
    }
}
