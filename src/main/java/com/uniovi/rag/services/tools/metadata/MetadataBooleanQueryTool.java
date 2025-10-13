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
 * Enhanced MetadataBooleanQueryTool for answering yes/no questions about meeting minutes.
 * 
 * Features:
 * - Intelligent NER-based filtering
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Adaptive prompts based on query context
 * - Comprehensive evidence extraction
 */
public class MetadataBooleanQueryTool extends AbstractMetadataTool {

    public MetadataBooleanQueryTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing boolean query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(query, new String[] {"date", "place", "decisions", "topics", "summary"});
        if (docs.isEmpty()) {
            log().debug("No documents found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Extract evidence in parallel
        List<String> evidence = extractEvidenceInParallel(query, relevantMinutes);
        if (evidence.isEmpty()) {
            log().debug("No evidence found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 5: Generate final answer
        String answer = generateBooleanAnswerWithLLM(query, evidence, relevantMinutes.size());
        log().debug("Generated answer for query: {} with {} evidence pieces", query, evidence.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Cached extraction of minute objects
     */
    @Cacheable(value = "minuteObjects", key = "#doc.id")
    public Minute getMinuteFromMetadataCached(Document doc) {
        return getMinuteFromMetadata(doc);
    }

    /**
     * Cached NER matching evaluation
     */
    @Cacheable(value = "nerMatching", key = "#minute.hashCode() + '_' + #ner.hashCode()")
    public boolean matchesMinuteWithNERCached(Minute minute, JSONObject ner) {
        return matchesMinuteWithNER(minute, ner);
    }

    /**
     * Extracts evidence from minutes in parallel
     */
    private List<String> extractEvidenceInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<String>> evidenceFutures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractEvidenceFromMinute(query, minute)))
                .collect(Collectors.toList());

        return evidenceFutures.stream()
                .map(CompletableFuture::join)
                .filter(evidence -> !evidence.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Extracts relevant evidence from a minute using LLM
     */
    private String extractEvidenceFromMinute(String query, Minute minute) {
        String prompt = generateEvidenceExtractionPrompt(query, minute);
        return getLLMResponseCached(prompt);
    }

    /**
     * Generates adaptive evidence extraction prompt
     */
    private String generateEvidenceExtractionPrompt(String query, Minute minute) {
        String queryType = analyzeQueryType(query);
        
        return String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            Meeting information:
            Date: %s
            Place: %s
            Decisions: %s
            Topics: %s
            Summary: %s
            
            Extract the most relevant evidence that helps answer the query.
            Format each piece of evidence with its type (Decision/Topic/Summary/Date/Place).
            If no evidence is relevant, return an empty string.
            """,
            query,
            queryType,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "none",
            minute.topics() != null ? String.join(", ", minute.topics()) : "none",
            minute.summary() != null ? minute.summary() : "none"
        );
    }

    /**
     * Generates final boolean answer with enhanced context
     */
    private String generateBooleanAnswerWithLLM(String query, List<String> evidence, int minuteCount) {
        String joined = String.join("\n\n", evidence);
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant meeting minutes with the following evidence:
            %s
            
            Write a clear and direct answer in the same language as the query, 
            indicating if the evidence allows to answer YES, NO, or PARTIALLY to the query.
            Be concise but informative. Include specific details from the evidence when relevant.
            """, query, minuteCount, joined);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Cached LLM response
     */
    @Cacheable(value = "llmResponses", key = "#prompt.hashCode()")
    public String getLLMResponseCached(String prompt) {
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
