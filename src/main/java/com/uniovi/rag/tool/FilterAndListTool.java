package com.uniovi.rag.tool;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced FilterAndListTool for filtering and listing meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced summarization and listing
 */
public class FilterAndListTool extends AbstractTool {

    public FilterAndListTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing filter and list query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
        
        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for filter and list query", docs.size());
        List<String> results = new ArrayList<>();

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getText();
                    String date = extractor.extractDate(content);
                    String summary = extractAndSummarize(content, query);
                    results.add("Meeting minutes from " + date + ":\n" + summary);
                }
            }
        } else {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                String content = doc.getText();
                String date = extractor.extractDate(content);
                if (isRelevantByLLM(content, query)) {
                    String summary = extractAndSummarize(content, query);
                    results.add("Meeting minutes from " + date + ":\n" + summary);
                }
            }
        }

        String answer;
        if (!results.isEmpty()) {
            log().debug("Found {} results for filter and list query, limiting to 3 for conciseness", results.size());
            // Limit results to 3 maximum for conciseness
            List<String> limitedResults = results.stream().limit(3).collect(java.util.stream.Collectors.toList());
            answer = generateFinalAnswer(query, limitedResults);
        } else {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No results found for filter and list query: '{}' (execution time: {} ms)", query, totalTime);
            answer = generateNotFoundResponse(query);
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated filter and list answer for query: '{}' (execution time: {} ms)", query, totalTime);
        // Apply formatResponse to clean the response
        String formattedAnswer = formatResponse(answer, query);
        return ToolResult.from(formattedAnswer, getClass());
    }

    /**
     * Determines if content is relevant to query using LLM.
     * Uses English for internal processing, but preserves original language in query and content.
     */
    private boolean isRelevantByLLM(String content, String query) {
        if (content == null || content.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String contentSnippet = content.substring(0, Math.min(1000, content.length()));
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And the following meeting minutes content (may be in any language):
            "%s"
            
            Does this minutes document match all the conditions in the query?
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, contentSnippet);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in isRelevantByLLM, defaulting to false");
                return false;
            }
            
            // Use LLM to interpret boolean response
            return interpretBooleanResponse(result, "isRelevantByLLM");
        } catch (Exception e) {
            log().error("Error in isRelevantByLLM, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Extracts and summarizes relevant content.
     * Uses English for internal processing, but preserves original language in query and content.
     */
    private String extractAndSummarize(String content, String query) {
        if (content == null || content.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return "";
        }
        
        String fragment = extractor.extractRelevantFragment(content, query);
        if (fragment == null || fragment.trim().isEmpty()) {
            return "";
        }
        
        String prompt = String.format("""
            Summarize in at most two sentences the fragment of the following text that answers this query (in any language): "%s"
            
            Text (may be in any language):
            %s
            
            CRITICAL RULES:
            1. Write in the EXACT SAME LANGUAGE as the user's question
            2. Be CONCISE - maximum 2 sentences
            3. Focus ONLY on what answers the query
            4. Do NOT include redundant information
            5. Remove any technical details
            
            Write the summary in the same language as the query.
            """, query, fragment);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in extractAndSummarize, returning empty string");
                return "";
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error in extractAndSummarize, returning empty string", e);
            return "";
        }
    }

    /**
     * Generates final answer with found results.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateFinalAnswer(String query, List<String> results) {
        if (query == null || query.trim().isEmpty() || results == null || results.isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String joined = results.stream()
                .filter(r -> r != null && !r.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Relevant meeting minutes found:
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "matched the filters", "análisis", "analysis", or internal processing.
            DO NOT include phrases like "Basándonos en el análisis" or "Según los datos proporcionados".
            DO NOT repeat the question or any part of it at the beginning.
            DO NOT start with phrases like "Dime qué...", "The user asked...", etc.
            Start directly with the answer content.
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            Be concise and direct.
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
    private String generateFallbackAnswer(String query, List<String> results) {
        String resultsText = results.stream()
                .limit(5)
                .collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found the following meetings matching the filters:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            listing the found meetings.
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
        return "Meetings found matching the filters:\n\n" + resultsText;
    }
    
    /**
     * Generates not found response.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundResponse(String query) {
        if (query == null || query.trim().isEmpty()) {
            return generateFallbackNotFoundMessage("");
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No meeting minutes were found that match all the conditions specified in the query.
            
            Write a polite response in the same language as the query explaining that no matching minutes were found.
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
}
