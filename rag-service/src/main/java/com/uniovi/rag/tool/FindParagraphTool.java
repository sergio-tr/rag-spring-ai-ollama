package com.uniovi.rag.tool;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

/**
 * Enhanced FindParagraphTool for finding relevant paragraphs in meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic paragraph relevance evaluation
 * - Enhanced paragraph extraction and summarization
 */
public class FindParagraphTool extends AbstractTool {

    public FindParagraphTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing find paragraph query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
        
        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for find paragraph query", docs.size());
        List<String> results = collectParagraphResults(query, ner, docs);

        String answer;
        if (!results.isEmpty()) {
            log().debug("Found {} paragraphs for query, limiting to 3 for conciseness", results.size());
            // Limit paragraphs to 3 maximum for conciseness
            List<String> limitedResults = results.stream().limit(3).toList();
            answer = generateFinalAnswer(query, limitedResults);
        } else {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No paragraphs found for query: '{}' (execution time: {} ms)", query, totalTime);
            answer = generateNotFoundResponse(query);
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated find paragraph answer for query: '{}' (execution time: {} ms, paragraphs: {})", 
                  query, totalTime, results.size());
        // Apply formatResponse to clean the response
        String formattedAnswer = formatResponse(answer, query);
        return ToolResult.from(formattedAnswer, getClass());
    }

    private List<String> collectParagraphResults(String query, JSONObject ner, List<Document> docs) {
        List<String> results = new ArrayList<>();
        if (ner != null && !docs.isEmpty()) {
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            for (Document doc : filteredDocs) {
                if (doc != null && doc.getText() != null && !doc.getText().trim().isEmpty()
                        && nerHandler.matchesDocumentWithNER(doc, ner)) {
                    results.addAll(findRelevantParagraphs(doc, query));
                }
            }
        }
        if (results.isEmpty() && !docs.isEmpty()) {
            appendLlmParagraphsForDocuments(docs, query, results);
        }
        if (results.isEmpty()) {
            List<Document> allDocs = retrieveAllDocuments(query, ner);
            if (!allDocs.isEmpty()) {
                appendLlmParagraphsForDocuments(allDocs, query, results);
            }
        }
        return results;
    }

    private void appendLlmParagraphsForDocuments(List<Document> docs, String query, List<String> results) {
        for (Document doc : docs) {
            if (doc != null && doc.getText() != null && !doc.getText().trim().isEmpty()) {
                results.addAll(findRelevantParagraphsByLLM(doc, query));
            }
        }
    }

    /**
     * Finds relevant paragraphs in a document.
     * Uses English for internal processing, but preserves original language in content.
     */
    private List<String> findRelevantParagraphs(Document doc, String query) {
        if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> relevant = new ArrayList<>();
        String content = doc.getText();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        String date = extractor.extractDate(content);
        
        for (String paragraph : paragraphs) {
            if (paragraph != null && !paragraph.trim().isEmpty() && isParagraphRelevantByLLM(query, paragraph)) {
                relevant.add("Meeting minutes from " + (date != null ? date : "unknown date") + ":\n" + paragraph.trim());
            }
        }
        return relevant;
    }

    /**
     * Finds relevant paragraphs using LLM-based relevance.
     * Uses English for internal processing, but preserves original language in content.
     */
    private List<String> findRelevantParagraphsByLLM(Document doc, String query) {
        // Same paragraph scan and LLM relevance as findRelevantParagraphs; kept for call-site clarity.
        return findRelevantParagraphs(doc, query);
    }

    /**
     * Determines if a paragraph is relevant to the query using LLM.
     * Uses English for internal processing, but preserves original language in query and paragraph.
     */
    @Override
    protected boolean isParagraphRelevantByLLM(String query, String paragraph) {
        if (query == null || query.trim().isEmpty() || paragraph == null || paragraph.trim().isEmpty()) {
            return false;
        }

        String prompt = String.format("""
            This is the user's query (in any language):
            "%s"

            And this is a paragraph from the meeting minutes (may be in any language):
            "%s"

            Does the paragraph clearly or partially answer the query?

            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, paragraph);

        try {
            String result =
                    chatClient
                            .prompt()
                            .user(prompt)
                            .call()
                            .content();

            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in isParagraphRelevantByLLM, defaulting to false");
                return false;
            }

            // Use LLM to interpret the response as yes/no
            return interpretBooleanResponse(result, "isParagraphRelevantByLLM");
        } catch (Exception e) {
            log().error("Error in isParagraphRelevantByLLM, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Generates final answer with found paragraphs.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateFinalAnswer(String query, List<String> results) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String joined = results.stream()
                .filter(r -> r != null && !r.trim().isEmpty())
                .collect(Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The following paragraphs from the meeting minutes are relevant:
            %s
            
            Write a brief and clear answer in the same language as the query, 
            summarizing the relevant information from all the paragraphs found.
            Use ONLY information from the paragraphs provided. Do NOT add external or invented information.
            DO NOT repeat the question or any part of it at the beginning.
            DO NOT start with phrases like "Dime qué...", "The user asked...", etc.
            Start directly with the answer content.
            Be concise - maximum 3-4 sentences.
            """, query, joined);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateFinalAnswer for query: '{}', using fallback", query);
                return generateFallbackAnswer(query, results);
            }
            
            // Apply formatResponse to clean and format the response
            return formatResponse(response.strip(), query);
        } catch (Exception e) {
            log().error("Error generating final answer, using fallback", e);
            return generateFallbackAnswer(query, results);
        }
    }
    
    /**
     * Generates not found response.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundResponse(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No relevant paragraphs were found for this query in the available meeting minutes.
            
            Write a polite response in the EXACT SAME LANGUAGE as the query, stating clearly that no relevant paragraphs were found. Be concise and direct. DO NOT repeat the question or any part of it.
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                return generateFallbackNotFoundMessage(query);
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error generating not found response, using fallback", e);
            return generateFallbackNotFoundMessage(query);
        }
    }
    
    /**
     * Generates a fallback answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackAnswer(String query, List<String> results) {
        String resultsText = results.stream()
                .limit(5)
                .collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found the following relevant paragraphs:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            listing the found paragraphs.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", resultsText);
        
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
        return "Relevant paragraphs found:\n\n" + resultsText;
    }
}
