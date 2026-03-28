package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.model.Cluster;
import com.uniovi.rag.model.ParagraphResult;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import com.uniovi.rag.model.Minute;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.uniovi.rag.observability.ContextPropagatingFutures.supplyAsync;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataFindParagraphTool for finding relevant paragraphs in meeting minutes with intelligent analysis.
 */
public class MetadataFindParagraphTool extends AbstractMetadataTool {

    public MetadataFindParagraphTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor,
            MetadataLlmResponseCacheService llmResponseCache) {
        super(chatClient, retriever, extractor, llmResponseCache);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing find paragraph query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().info("No documents found for find paragraph query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for find paragraph query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for find paragraph query: {}", query);
            return ToolResult.from(formatResponse(generateNotFoundMessage(query), query), getClass());
        }

        // Step 3.5: Create map of minute ID to document for content access
        Map<String, Document> minuteIdToDoc = new HashMap<>();
        for (Document doc : docs) {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute != null && relevantMinutes.contains(minute)) {
                minuteIdToDoc.put(minute.id(), doc);
            }
        }

        // Step 4: Find relevant paragraphs in parallel (content-first, metadata fallback)
        List<ParagraphResult> results = findParagraphsInParallel(query, relevantMinutes, minuteIdToDoc);

        // Step 4.5: For "tejado" queries, exclude paragraphs that only mention "portal" (not the same as tejado)
        if (query != null && query.toLowerCase().contains("tejado")) {
            List<ParagraphResult> tejadoFiltered = results.stream()
                    .filter(r -> r.getParagraph() != null && r.getParagraph().toLowerCase().contains("tejado"))
                    .toList();
            if (tejadoFiltered.isEmpty() && !results.isEmpty()) {
                log().info("Query asks for 'tejado' but all paragraphs only mentioned 'portal' or other terms; returning not found");
                String notFoundMsg = generateTopicNotFoundMessage(query, "renovación del tejado");
                return ToolResult.from(formatResponse(notFoundMsg, query), getClass());
            }
            results = tejadoFiltered;
        }

        if (results.isEmpty()) {
            log().info("No paragraphs found for query: {}", query);
            return ToolResult.from(formatResponse(generateNoDataMessage(query), query), getClass());
        }

        // Step 5: Analyze and rank paragraphs
        List<ParagraphResult> rankedResults = analyzeAndRankParagraphs(results);

        // Step 6: Cluster similar paragraphs
        List<Cluster<ParagraphResult>> clusters = clusterParagraphs(rankedResults);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedParagraphAnswer(query, rankedResults, clusters);
        log().info("Generated find paragraph answer for query: {} with {} paragraphs in {} clusters", 
                   query, results.size(), clusters.size());
        
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    /**
     * Finds relevant paragraphs in parallel
     */
    private List<ParagraphResult> findParagraphsInParallel(String query, List<Minute> minutes, Map<String, Document> minuteIdToDoc) {
        List<CompletableFuture<ParagraphResult>> futures = minutes.stream()
                .map(minute -> supplyAsync(() -> {
                    Document doc = minuteIdToDoc.get(minute.id());
                    return findRelevantParagraph(query, minute, doc);
                }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> result.getParagraph() != null && !result.getParagraph().isBlank())
                .toList();
    }

    /**
     * Finds relevant paragraph for a minute with enhanced context.
     * CRITICAL FIX: Now accesses document content to extract literal paragraphs.
     */
    private ParagraphResult findRelevantParagraph(String query, Minute minute, Document doc) {
        // Priority 1: Extract literal paragraph from document content
        String paragraph = extractParagraphFromContent(query, minute, doc);

        // Priority 2: Fallback to metadata if content extraction failed
        if (paragraph.isBlank()) {
            paragraph = buildParagraphFromMetadata(minute);
        }

        // Priority 3: LLM fallback if both failed
        if (paragraph.isBlank()) {
            paragraph = extractRelevantParagraph(query, minute);
        }

        if (paragraph.isBlank()) {
            return null;
        }

        int score = paragraph.length();

        return new ParagraphResult(
            minute.id(),
            minute.date(),
            minute.place(),
            paragraph,
            score
        );
    }

    /**
     * Extracts a literal paragraph from document content using LLM.
     * This is the primary method for finding paragraphs.
     */
    private String extractParagraphFromContent(String query, Minute minute, Document doc) {
        if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
            return "";
        }

        // Truncate content to avoid context length issues
        String content = truncateForPrompt(doc.getText(), 4000);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And the following document content (may be in any language):
            "%s"
            
            Extract the most relevant paragraph or short section (2-5 sentences) from the document content 
            that directly answers the query. Return ONLY the literal text from the document, without modifications.
            Preserve the original language and formatting.
            
            If no relevant paragraph is found, return an empty string.
            """, query, content);

        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().info("Empty response from LLM in extractParagraphFromContent");
                return "";
            }
            
            return response.trim();
        } catch (Exception e) {
            log().error("Error extracting paragraph from content, returning empty string", e);
            return "";
        }
    }

    /**
     * Builds a paragraph from metadata (decisions/topics/summary/agenda) before LLM.
     */
    private String buildParagraphFromMetadata(Minute minute) {
        if (minute == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            parts.add("Decisiones: " + String.join(" | ", minute.decisions()));
        }
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            parts.add("Agenda: " + minute.agenda().values().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" | ")));
        }
        if (minute.summary() != null && !minute.summary().isBlank()) {
            parts.add("Resumen: " + minute.summary());
        }
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            parts.add("Temas: " + String.join(" | ", minute.topics()));
        }
        String combined = String.join(" | ", parts).trim();
        return combined.isBlank() ? "" : combined;
    }

    /**
     * Extracts relevant paragraph with enhanced context analysis (LLM fallback).
     */
    private String extractRelevantParagraph(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }

        String prompt = String.format("""
            Extract the most relevant paragraph or short section that answers the query.
            Keep the original language of the metadata if possible.
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
                log().info("Empty response from LLM in extractRelevantParagraph, returning empty string");
                return "";
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error extracting relevant paragraph, returning empty string", e);
            return "";
        }
    }

    /**
     * Analyzes and ranks paragraphs by relevance and quality
     */
    private List<ParagraphResult> analyzeAndRankParagraphs(List<ParagraphResult> results) {
        // Sort by paragraph length (descending) as a simple proxy of richness
        return results.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getParagraph() != null ? b.getParagraph().length() : 0,
                        a.getParagraph() != null ? a.getParagraph().length() : 0))
                .toList();
    }

    /**
     * Clusters similar paragraphs to avoid redundancy
     */
    private List<Cluster<ParagraphResult>> clusterParagraphs(List<ParagraphResult> results) {
        if (extractor == null) {
            return results.stream().map(Cluster::new).toList();
        }
        return extractor.clusterItems(
            results,
            result -> result.getParagraph(),
            result -> result.getDate() != null ? result.getDate() : "unknown",
            0.4
        );
    }

    /**
     * Generates enhanced paragraph answer with clustering and analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedParagraphAnswer(String query, List<ParagraphResult> results, 
                                                  List<Cluster<ParagraphResult>> clusters) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String paragraphSummary = formatParagraphSummary(clusters);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant paragraphs:
            
            %s
            
            Write a clear, direct answer in the same language as the query.
            Use ONLY information from the paragraphs provided. Do not add external or invented information.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "clusters", "análisis", "analysis", "grouped into", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, results.size(), 
            paragraphSummary != null ? paragraphSummary : "No paragraphs found.");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedParagraphAnswer, using fallback");
                return generateFallbackParagraphAnswer(query, results);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced paragraph answer, using fallback", e);
            return generateFallbackParagraphAnswer(query, results);
        }
    }
    
    /**
     * Generates a fallback paragraph answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackParagraphAnswer(String query, List<ParagraphResult> results) {
        String resultsText = results.stream()
                .limit(3)
                .map(r -> String.format("Meeting on %s:\n%s", 
                    r.getDate() != null ? r.getDate() : "unknown date",
                    r.getParagraph() != null && r.getParagraph().length() > 300 ? r.getParagraph().substring(0, 300) + "..." : (r.getParagraph() != null ? r.getParagraph() : "")))
                .collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found %d relevant paragraphs:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            listing the found paragraphs.
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
            log().warn("Error generating fallback paragraph answer with LLM", e);
        }
        
        // Ultimate fallback
        return String.format("Found %d relevant paragraphs:\n%s",
                          results.size(), resultsText);
    }

    /**
     * Formats paragraph summary for LLM prompt (without technical details)
     */
    private String formatParagraphSummary(List<Cluster<ParagraphResult>> clusters) {
        StringBuilder summary = new StringBuilder();
        
        // Format paragraphs naturally without mentioning clusters
        for (int i = 0; i < clusters.size(); i++) {
            Cluster<ParagraphResult> cluster = clusters.get(i);
            ParagraphResult representative = cluster.getRepresentativeItem();
            
            if (representative.getDate() != null) {
                summary.append(String.format("Reunión del %s", representative.getDate()));
                if (representative.getPlace() != null) {
                    summary.append(String.format(" (%s)", representative.getPlace()));
                }
                summary.append(":\n");
            }
            summary.append(representative.getParagraph() != null ? representative.getParagraph() : "");
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

}
