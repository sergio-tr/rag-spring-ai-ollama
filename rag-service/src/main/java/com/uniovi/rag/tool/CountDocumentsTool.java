package com.uniovi.rag.tool;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enhanced CountDocumentsTool for counting meeting minutes based on specific criteria.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Semantic analysis instead of literal matching
 * - Support for all NER fields including temporalContext and answerType
 * - Multilingual support with adaptive prompts
 * - Decoupled from literal word matching
 */
public class CountDocumentsTool extends AbstractTool {

    public CountDocumentsTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing count documents query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
                
        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for count documents query", docs.size());
        if (docs == null || docs.isEmpty()) {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No documents found for count query: '{}' (execution time: {} ms)", query, totalTime);
            String response = generateFinalAnswerWithLLM(query, List.of());
            String formattedResponse = formatResponse(response, query);
            return ToolResult.from(formattedResponse, getClass());
        }

        // 1) Pre-filter with NER temporal context if available (cheap)
        List<Document> candidateDocs = (ner != null)
                ? nerHandler.filterDocumentsByTemporalContext(docs, ner)
                : docs;

        List<String> matchedIds = new java.util.ArrayList<>();

        for (Document doc : candidateDocs) {
            if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) continue;
            if (ner != null && !nerHandler.matchesDocumentWithNER(doc, ner)) continue;

            String id = extractMinuteIdentifier(doc);
            String fragment = extractor.extractRelevantFragment(doc.getText(), query);
            if (matchesQueryWithLLM(query, id, fragment)) {
                matchedIds.add(id);
            }
        }

        log().debug("Matched {} documents for count query", matchedIds.size());
        String response = generateFinalAnswerWithLLM(query, matchedIds);
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated count documents answer for query: '{}' (execution time: {} ms, matched: {})", 
                  query, totalTime, matchedIds.size());
        // Apply formatResponse to clean the response
        String formattedResponse = formatResponse(response, query);
        return ToolResult.from(formattedResponse, getClass());
    }

    /**
     * Generic per-document matcher.
     * No hardcoded schemas: the LLM decides if this minute satisfies the user's conditions.
     */
    private boolean matchesQueryWithLLM(String query, String minuteId, String fragment) {
        if (query == null || query.trim().isEmpty()) return false;
        if (fragment == null || fragment.trim().isEmpty()) return false;

        String safeId = minuteId == null ? "" : minuteId.trim();
        String safeFragment = fragment.length() > 1200 ? fragment.substring(0, 1200) : fragment;

        String prompt = String.format("""
            User question (respond in the same language as the question):
            "%s"

            Meeting minute identifier:
            "%s"

            Evidence fragment from that meeting minute:
            "%s"

            Task:
            Determine whether THIS meeting minute satisfies the user's requested conditions.
            Answer ONLY with: YES or NO
            """, query, safeId, safeFragment);

        try {
            String raw = chatClient.prompt().user(prompt).call().content();
            if (raw == null) return false;
            return interpretBooleanResponse(raw, "matchesQueryWithLLM");
        } catch (Exception e) {
            log().warn("LLM match check failed, defaulting to NO: {}", e.getMessage());
            return false;
        }
    }

    private String generateFinalAnswerWithLLM(String query, List<String> matchedIds) {
        List<String> uniqueIds = matchedIds == null
                ? List.of()
                : matchedIds.stream().filter(Objects::nonNull).distinct().limit(30).collect(Collectors.toList());

        String idsBlock = uniqueIds.isEmpty()
                ? "(none)"
                : uniqueIds.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));

        String prompt = String.format("""
            You are answering a user question about meeting minutes.
            Respond in the SAME language as the user's question.
            
            CRITICAL RULES:
            1. DO NOT repeat the user's question or any part of it at the beginning
            2. DO NOT start with phrases like "The user asked...", "La pregunta era...", etc.
            3. Start directly with the answer content
            4. Be concise and direct - maximum 2-3 sentences
            5. If there are no matching minutes, answer clearly that no meeting minutes match the conditions
            6. Otherwise, answer with the count
            7. If the user is asking "which minutes", include the list

            User question:
            "%s"

            Matching meeting minutes (identifiers):
            %s
            """, query, idsBlock);

        try {
            String raw = chatClient.prompt().user(prompt).call().content();
            return raw == null ? "" : raw.trim();
        } catch (Exception e) {
            log().warn("Failed to generate final answer with LLM: {}", e.getMessage());
            return generateFallbackAnswer(query, uniqueIds);
        }
    }

    private String extractMinuteIdentifier(Document doc) {
        if (doc == null) return null;
        Object filename = doc.getMetadata() != null ? doc.getMetadata().get("filename") : null;
        if (filename != null && !filename.toString().isBlank()) {
            return filename.toString();
        }

        String date = extractDateFromContent(doc.getText());
        if (date != null && !date.isBlank()) {
            return "Acta del " + date;
        }

        return doc.getId();
    }

    private String extractDateFromContent(String content) {
        if (content == null) return null;
        Matcher m = Pattern.compile("(?i)\\bFecha\\s*:\\s*(.+)$", Pattern.MULTILINE).matcher(content);
        if (m.find()) {
            String v = m.group(1).trim();
            // stop at line breaks or labels
            v = v.replaceAll("\\s{2,}", " ").trim();
            return v.length() > 60 ? v.substring(0, 60).trim() : v;
        }
        // fallback Spanish canonical used in PDFs
        Matcher m2 = Pattern.compile("(?i)(\\d{1,2}\\s+de\\s+[a-záéíóú]+\\s+de\\s+\\d{4})").matcher(content);
        if (m2.find()) return m2.group(1).trim();
        return null;
    }

    /**
     * Interprets LLM response as boolean using another LLM call.
     */
    private boolean interpretBooleanResponse(String response, String context) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Context: %s
            
            The LLM generated this response: "%s"
            
            Task: Interpret this response as a boolean answer.
            - If it means YES/TRUE/POSITIVE, respond with: YES
            - If it means NO/FALSE/NEGATIVE, respond with: NO
            
            Consider semantic meaning, not just exact words.
            
            Respond with ONLY one word: YES or NO.
            """, context, response);
        
        try {
            String interpretation = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toUpperCase();
            
            return interpretation.contains("YES");
        } catch (Exception e) {
            log().warn("Error interpreting boolean response in {}, defaulting to false", context, e);
            return false;
        }
    }

    /**
     * Generates a fallback answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackAnswer(String query, List<String> uniqueIds) {
        String idsText = uniqueIds.isEmpty() 
            ? "(none)" 
            : uniqueIds.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Matching meeting minutes (identifiers):
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question.
            If there are no matching minutes, state that clearly.
            Otherwise, provide the count.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", idsText);
        
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
            log().warn("Error generating fallback answer with LLM", e);
        }
        
        // Ultimate fallback
        if (uniqueIds.isEmpty()) {
            return "No meeting minutes were found that match the specified conditions.";
        }
        return String.format("Found %d meeting minutes that match the conditions.", uniqueIds.size());
    }
}