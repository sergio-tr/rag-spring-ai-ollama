package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
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
 * Enhanced MetadataBooleanQueryTool for answering yes/no questions about meeting minutes.
 */
public class MetadataBooleanQueryTool extends AbstractMetadataTool {

    public MetadataBooleanQueryTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing boolean query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(query, new String[] {"date", "place", "decisions", "topics", "summary"}, ner);
        
        if (docs.isEmpty()) {
            log().info("No documents found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Extract evidence in parallel (metadata-first, LLM as fallback per minute)
        List<String> evidence = extractEvidenceInParallel(query, relevantMinutes);
        
        if (evidence.isEmpty()) {
            log().info("No evidence found for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 5: Generate final answer
        String answer = generateBooleanAnswerWithLLM(query, evidence, relevantMinutes.size());
        log().info("Generated answer for query: {} with {} evidence pieces", query, evidence.size());
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Extracts evidence from minutes in parallel
     */
    private List<String> extractEvidenceInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<String>> evidenceFutures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractEvidencePreferMetadata(query, minute)))
                .collect(Collectors.toList());

        return evidenceFutures.stream()
                .map(CompletableFuture::join)
                .filter(evidence -> !evidence.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Prefers metadata-based evidence; falls back to LLM only if metadata has no usable signal.
     */
    private String extractEvidencePreferMetadata(String query, Minute minute) {
        String metadataEvidence = buildEvidenceFromMetadata(minute, query);
        if (metadataEvidence != null && !metadataEvidence.isBlank()) {
            return metadataEvidence;
        }
        return extractEvidenceFromMinute(query, minute);
    }

    /**
     * Extracts relevant evidence from a minute using LLM.
     */
    private String extractEvidenceFromMinute(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return "";
        }
        
        String prompt = generateEvidenceExtractionPrompt(query, minute);
        String response = getLLMResponseCached(prompt);
        
        if (response == null || response.trim().isEmpty()) {
            log().info("Empty evidence extracted from minute: {}", minute.id());
            return "";
        }
        
        return response;
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
     * Generates final boolean answer with enhanced context.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateBooleanAnswerWithLLM(String query, List<String> evidence, int minuteCount) {
        if (query == null || query.trim().isEmpty() || evidence == null || evidence.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String joined = evidence.stream()
                .filter(e -> e != null && !e.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Found %d relevant meeting minutes with the following evidence:
            %s
            
            Write a clear and direct answer in the same language as the query, 
            indicating if the evidence allows to answer YES, NO, or PARTIALLY to the query.
            Be concise but informative. Include specific details from the evidence when relevant.
            """, query, minuteCount, joined);
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateBooleanAnswerWithLLM, using fallback");
                return generateFallbackAnswer(query, evidence);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating boolean answer with LLM, using fallback", e);
            return generateFallbackAnswer(query, evidence);
        }
    }
    
    /**
     * Builds evidence directly from minute metadata when LLM extraction fails.
     * This is a fallback to avoid false negatives.
     */
    private String buildEvidenceFromMetadata(Minute minute, String query) {
        if (minute == null || query == null || query.trim().isEmpty()) {
            return "";
        }
        
        StringBuilder evidence = new StringBuilder();
        
        // Extract relevant fields based on query keywords
        String queryLower = query.toLowerCase();
        
        if (queryLower.contains("decisión") || queryLower.contains("decision") || 
            queryLower.contains("acuerdo") || queryLower.contains("acord")) {
            if (minute.decisions() != null && !minute.decisions().isEmpty()) {
                evidence.append("Decisiones: ").append(String.join(", ", minute.decisions())).append("\n");
            }
        }
        
        if (queryLower.contains("tema") || queryLower.contains("topic") || 
            queryLower.contains("asunto") || queryLower.contains("subject")) {
            if (minute.topics() != null && !minute.topics().isEmpty()) {
                evidence.append("Temas: ").append(String.join(", ", minute.topics())).append("\n");
            }
        }
        
        if (queryLower.contains("resumen") || queryLower.contains("summary")) {
            if (minute.summary() != null && !minute.summary().trim().isEmpty()) {
                evidence.append("Resumen: ").append(minute.summary()).append("\n");
            }
        }
        
        // Always include date and place for context
        if (minute.date() != null) {
            evidence.append("Fecha: ").append(minute.date()).append("\n");
        }
        if (minute.place() != null) {
            evidence.append("Lugar: ").append(minute.place()).append("\n");
        }
        
        return evidence.toString().trim();
    }
    
    /**
     * Generates a fallback answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackAnswer(String query, List<String> evidence) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Basándome en la evidencia encontrada (%d piezas), la respuesta es: SÍ/NO/PARCIALMENTE. Evidencia: %s",
                              evidence.size(), 
                              evidence.stream().limit(3).collect(Collectors.joining("; ")));
        } else {
            return String.format("Based on the evidence found (%d pieces), the answer is: YES/NO/PARTIALLY. Evidence: %s",
                              evidence.size(),
                              evidence.stream().limit(3).collect(Collectors.joining("; ")));
        }
    }
}
