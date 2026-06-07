package com.uniovi.rag.tool;

import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

/**
 * Enhanced SummarizeTopicTool for summarizing specific topics from meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced topic summarization and analysis
 */
public class SummarizeTopicTool extends AbstractTool {

    public SummarizeTopicTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        log().info("Executing summarize topic query: '{}' with NER: {}",
                query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();

        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for summarize topic query", docs.size());

        List<String> fragments = collectFragments(query, ner, docs);

        if (fragments.isEmpty()) {
            return buildNotFoundResult(query, startTime);
        }

        log().debug("Extracted {} fragments for summarize topic query, limiting to 3 for conciseness", fragments.size());
        List<String> limitedFragments = fragments.stream().limit(3).toList();

        String summary = generateSummaryWithLLM(query, limitedFragments);
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated summarize topic answer for query: '{}' (execution time: {} ms, fragments used: {})",
                query, totalTime, limitedFragments.size());

        String formattedSummary = formatResponse(summary, query);
        return ToolResult.from(formattedSummary, getClass());
    }

    private List<String> collectFragments(String query, JSONObject ner, List<Document> docs) {
        List<String> fragments = new ArrayList<>();
        if (ner != null && !docs.isEmpty()) {
            collectFragmentsWithNer(query, ner, docs, fragments);
        }
        if (fragments.isEmpty() && !docs.isEmpty()) {
            collectFragmentsFromDocsWithLlm(query, docs, fragments);
        }
        if (fragments.isEmpty()) {
            List<Document> allDocs = retrieveAllDocuments(query, ner);
            collectFragmentsFromDocsWithLlm(query, allDocs, fragments);
        }
        return fragments;
    }

    private void collectFragmentsWithNer(String query, JSONObject ner, List<Document> docs, List<String> fragments) {
        List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
        log().debug("Filtered {} documents by temporal context, {} remaining", docs.size(), filteredDocs.size());

        int matchedCount = 0;
        for (Document doc : filteredDocs) {
            if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
                log().debug("Skipping document {}: null or empty content", doc != null ? doc.getId() : "null");
            } else if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                matchedCount++;
                fragments.addAll(extractRelevantFragments(doc, query));
                if (fragments.size() >= 3) {
                    break;
                }
            }
        }
        log().debug("NER filtering: {} documents matched NER conditions, extracted {} fragments", matchedCount, fragments.size());
    }

    private void collectFragmentsFromDocsWithLlm(String query, List<Document> docs, List<String> fragments) {
        for (Document doc : docs) {
            if (doc != null && doc.getText() != null && !doc.getText().trim().isEmpty()
                    && isRelevantByLLM(doc.getText(), query)) {
                fragments.addAll(extractRelevantFragments(doc, query));
            }
            if (fragments.size() >= 3) {
                break;
            }
        }
    }

    private ToolResult buildNotFoundResult(String query, long startTime) {
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("No fragments found for summarize topic query: '{}' (execution time: {} ms)", query, totalTime);
        return buildFormattedNotFoundToolResult(query);
    }

    private List<String> extractRelevantFragments(Document doc, String query) {
        return new ArrayList<>(extractRelevantParagraphFragments(doc, query));
    }

    /**
     * Generates summary using LLM.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateSummaryWithLLM(String query, List<String> fragments) {
        if (query == null || query.trim().isEmpty() || fragments == null || fragments.isEmpty()) {
            return generateNotFoundMessage(query);
        }

        String joined = fragments.stream()
                .filter(f -> f != null && !f.trim().isEmpty())
                .collect(Collectors.joining("\n\n"));

        if (joined.trim().isEmpty()) {
            return generateNotFoundMessage(query);
        }

        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            The following are relevant fragments from the minutes:
            %s
            
            CRITICAL RULES:
            1. Write in the EXACT SAME LANGUAGE as the user's question
            2. Be CONCISE - maximum 2-3 sentences TOTAL (not per fragment), focus on key points only
            3. DO NOT repeat the question or any part of it at the beginning
            4. DO NOT start with phrases like "Resume lo tratado...", "Dime qué...", "The user asked...", etc.
            5. Start directly with the summary content
            6. Do NOT include redundant information - every word must add value
            7. Focus ONLY on what the user is asking about the topic
            8. Remove any technical details or internal processing information
            9. If multiple fragments, provide a unified summary of key points across all, not individual summaries
            10. Use the most important information first - prioritize relevance over completeness
            11. If the fragments do NOT actually discuss the requested topic, respond clearly that no information was found on that topic in the available minutes. Do NOT invent or extrapolate content.
            
            Examples of CORRECT responses:
            - Query: "Resume lo tratado en las reuniones sobre la climatización de la piscina"
              Correct: "Basado en las reuniones: [concise summary of key points]"
              Wrong: "Resume lo tratado en las reuniones sobre la climatización de la piscina.\\n\\nBasado en las reuniones: [summary]"
            
            Write a brief and clear summary in the same language as the query, 
            indicating the key points mentioned about the topic. 
            Avoid literal repetition and organize the information clearly.
            """, query, joined);

        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateSummaryWithLLM for query: '{}', using fallback", query);
                return generateFallbackSummary(query, fragments);
            }

            // Apply formatResponse to clean and format the response
            return formatResponse(response.strip(), query);
        } catch (Exception e) {
            log().error("Error generating summary with LLM, using fallback", e);
            return generateFallbackSummary(query, fragments);
        }
    }

    /**
     * Generates a fallback summary when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackSummary(String query, List<String> fragments) {
        String fragmentsText = fragments.stream()
                .limit(5)
                .collect(Collectors.joining("\n\n"));

        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found the following relevant fragments:
            %s
            
            Respond with a short summary in the EXACT SAME LANGUAGE as the question,
            summarizing the found fragments.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", fragmentsText);

        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response != null && !response.trim().isEmpty()) {
                // Apply formatResponse to clean the response
                return formatResponse(response.trim(), query);
            }
        } catch (Exception e) {
            log().warn("Error generating fallback summary with LLM", e);
        }

        // Ultimate fallback
        String fallback = "Summary of relevant fragments found:\n\n" + fragmentsText;
        return formatResponse(fallback, query);
    }
}
