package com.uniovi.rag.tool;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enhanced CountAndExplainTool for counting and explaining meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced explanation generation
 */
public class CountAndExplainTool extends AbstractTool {

    public CountAndExplainTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing count and explain query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
        
        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for count and explain query", docs.size());
        List<String> explanations = new ArrayList<>();
        List<String> matchedIds = new ArrayList<>();

        if (docs == null || docs.isEmpty()) {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No documents found for count and explain query: '{}' (execution time: {} ms)", query, totalTime);
            String response = generateFinalAnswerWithLLM(query, List.of(), List.of());
            String formattedResponse = formatResponse(response, query);
            return ToolResult.from(formattedResponse, getClass());
        }

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getText();
                    String id = extractMinuteIdentifier(doc);
                    String fragment = extractor.extractRelevantFragment(content, query);
                    if (matchesQueryWithLLM(query, id, fragment)) {
                        explanations.add(formatExplanationLine(id, fragment));
                        matchedIds.add(id);
                    }
                }
            }
        } else {
            // Fallback to query-based relevance
            for (Document doc : docs) {
                String content = doc.getText();
                String fragment = extractor.extractRelevantFragment(content, query);
                String id = extractMinuteIdentifier(doc);
                if (matchesQueryWithLLM(query, id, fragment)) {
                    explanations.add(formatExplanationLine(id, fragment));
                    matchedIds.add(id);
                }
            }
        }

        log().debug("Matched {} documents with {} explanations for count and explain query", 
                   matchedIds.size(), explanations.size());
        String response = generateFinalAnswerWithLLM(query, matchedIds, explanations);
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated count and explain answer for query: '{}' (execution time: {} ms, matched: {})", 
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

    private String formatExplanationLine(String id, String fragment) {
        String safeId = id != null ? id : "Acta";
        String safeFrag = fragment != null ? fragment : "";
        // Never include the raw user question; keep only snippet
        return safeFrag.isBlank() ? safeId : (safeId + ":\n" + safeFrag);
    }

    private String extractMinuteIdentifier(Document doc) {
        if (doc == null) return null;
        Object filename = doc.getMetadata() != null ? doc.getMetadata().get("filename") : null;
        if (filename != null && !filename.toString().isBlank()) {
            return filename.toString();
        }

        String content = doc.getText();
        String date = extractDateFromContent(content);
        if (date != null && !date.isBlank()) {
            return "Acta del " + date;
        }
        return doc.getId();
    }

    private String extractDateFromContent(String content) {
        if (content == null) return null;
        Matcher m = Pattern.compile("(?i)\\bFecha\\s*:\\s*(.+)$", Pattern.MULTILINE).matcher(content);
        if (m.find()) {
            String v = m.group(1).trim().replaceAll("\\s{2,}", " ").trim();
            return v.length() > 60 ? v.substring(0, 60).trim() : v;
        }
        Matcher m2 = Pattern.compile("(?i)(\\d{1,2}\\s+de\\s+[a-záéíóú]+\\s+de\\s+\\d{4})").matcher(content);
        if (m2.find()) return m2.group(1).trim();
        return null;
    }

    private String generateFinalAnswerWithLLM(String query, List<String> matchedIds, List<String> explanations) {
        List<String> uniqueIds = matchedIds == null
                ? List.of()
                : matchedIds.stream().filter(Objects::nonNull).distinct().limit(20).collect(Collectors.toList());

        String idsBlock = uniqueIds.isEmpty()
                ? "(none)"
                : uniqueIds.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));

        String explBlock = explanations == null
                ? ""
                : explanations.stream().filter(Objects::nonNull).limit(8).collect(Collectors.joining("\n\n"));

        String prompt = String.format("""
            You are answering a user question about meeting minutes.
            Respond in the SAME language as the user's question.
            
            CRITICAL RULES:
            1. DO NOT repeat the user's question or any part of it at the beginning
            2. DO NOT start with phrases like "The user asked...", "La pregunta era...", etc.
            3. Start directly with the answer content
            4. Be concise and direct - maximum 3-4 sentences for the count, then brief explanations
            5. If there are no matching minutes, answer clearly that no meeting minutes match the conditions
            6. Otherwise, answer with (1) the count and (2) a short explanation grounded in the snippets
            7. If the user is asking "which minutes", include the list
            8. Keep explanations brief - one sentence per minute maximum

            User question:
            "%s"

            Matching meeting minutes (identifiers):
            %s

            Relevant evidence snippets (may be empty):
            %s
            """, query, idsBlock, explBlock.isBlank() ? "(none)" : explBlock);

        try {
            String raw = chatClient.prompt().user(prompt).call().content();
            return raw == null ? "" : raw.trim();
        } catch (Exception e) {
            log().warn("Failed to generate final answer with LLM: {}", e.getMessage());
            return generateFallbackAnswer(query, uniqueIds, explanations);
        }
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
    private String generateFallbackAnswer(String query, List<String> uniqueIds, List<String> explanations) {
        String idsText = uniqueIds.isEmpty() 
            ? "(none)" 
            : uniqueIds.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
        
        String explText = explanations == null || explanations.isEmpty()
            ? "(none)"
            : explanations.stream().limit(8).collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Matching meeting minutes (identifiers):
            %s
            
            Relevant evidence snippets:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question.
            If there are no matching minutes, state that clearly.
            Otherwise, provide the count and a brief explanation.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", idsText, explText);
        
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
