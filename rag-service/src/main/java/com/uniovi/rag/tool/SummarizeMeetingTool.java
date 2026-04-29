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
 * Enhanced SummarizeMeetingTool for summarizing meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced summarization and analysis
 */
public class SummarizeMeetingTool extends AbstractTool {

    public SummarizeMeetingTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing summarize meeting query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
        
        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for summarize meeting query", docs.size());
        List<String> fragments = new ArrayList<>();

        if (ner != null) {
            collectSummarizeFragmentsWithNer(query, ner, docs, fragments);
        } else {
            collectSummarizeFragmentsWithoutNer(query, docs, fragments);
        }

        if (fragments.isEmpty()) {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No fragments found for summarize meeting query: '{}' (execution time: {} ms)", query, totalTime);
            String notFound = generateNotFoundMessage(query);
            // Apply formatResponse to clean the not found message
            String formattedNotFound = formatResponse(notFound, query);
            return ToolResult.from(formattedNotFound, getClass());
        }

        log().debug("Extracted {} fragments for summarize meeting query, limiting to 3 for conciseness", fragments.size());
        // Limit fragments to 3 maximum for conciseness
        List<String> limitedFragments = fragments.stream().limit(3).toList();
        
        String summary = generateSummaryWithLLM(query, limitedFragments);
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated summarize meeting answer for query: '{}' (execution time: {} ms, fragments used: {})", 
                  query, totalTime, limitedFragments.size());
        
        // Note: generateSummaryWithLLM already applies formatResponse, but we apply it again for consistency
        // (formatResponse is idempotent, so it's safe)
        String formattedSummary = formatResponse(summary, query);
        return ToolResult.from(formattedSummary, getClass());
    }

    private void collectSummarizeFragmentsWithNer(String query, JSONObject ner, List<Document> docs, List<String> fragments) {
        List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
        for (Document doc : filteredDocs) {
            if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                fragments.addAll(extractRelevantFragments(doc, query));
            }
            if (fragments.size() >= 3) {
                break;
            }
        }
    }

    private void collectSummarizeFragmentsWithoutNer(String query, List<Document> docs, List<String> fragments) {
        for (Document doc : docs) {
            if (isRelevantByLLM(doc.getText(), query)) {
                fragments.addAll(extractRelevantFragments(doc, query));
            }
            if (fragments.size() >= 3) {
                break;
            }
        }
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

    private List<String> extractRelevantFragments(Document doc, String query) {
        List<String> relevant = new ArrayList<>();
        String content = doc.getText();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        for (String p : paragraphs) {
            if (isParagraphRelevantByLLM(query, p)) {
                // Limit fragment length to 250 characters for conciseness
                String fragment = p.trim();
                if (fragment.length() > 250) {
                    fragment = fragment.substring(0, 250) + "...";
                }
                relevant.add(fragment);
            }
        }
        return relevant;
    }

    /**
     * Determines if a paragraph is relevant to the query using LLM.
     * Uses English for internal processing, but preserves original language in query and paragraph.
     */
    private boolean isParagraphRelevantByLLM(String query, String paragraph) {
        if (query == null || query.trim().isEmpty() || paragraph == null || paragraph.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            And this is a paragraph from the minutes (may be in any language):
            "%s"
            Does the paragraph clearly or partially answer the query?
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, paragraph);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in isParagraphRelevantByLLM, defaulting to false");
                return false;
            }
            
            // Use LLM to interpret boolean response
            return interpretBooleanResponse(result, "isParagraphRelevantByLLM");
        } catch (Exception e) {
            log().error("Error in isParagraphRelevantByLLM, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
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
            The following are relevant fragments from the minutes (may be in any language):
            "%s"
            
            CRITICAL RULES:
            1. Write in the EXACT SAME LANGUAGE as the user's question
            2. Be CONCISE - maximum 2-3 sentences TOTAL (not per fragment), focus on key points only
            3. DO NOT repeat the question or any part of it at the beginning
            4. DO NOT start with phrases like "Dame un resumen...", "Hazme un resumen...", "Resume la reunión...", etc.
            5. Start directly with the summary content
            6. Do NOT include redundant information - every word must add value
            7. Focus on what the user is asking for - if they ask about a specific topic, prioritize that
            8. Remove any technical details or internal processing information
            9. If multiple fragments, provide a unified summary of key points across all, not individual summaries
            10. Use the most important information first - prioritize relevance over completeness
            11. Base your answer ONLY on the fragments provided. If they do not contain enough to answer the query (e.g. specific date or topic), say so clearly in the user's language. Do NOT invent content.
            
            Examples of CORRECT responses:
            - Query: "Dame un resumen de la reunión del 25 de agosto"
              Correct: "En la reunión del 25 de agosto: [concise summary]"
              Wrong: "Dame un resumen de la reunión del 25 de agosto.\\n\\nEn la reunión: [summary]"
            
            Write a brief and clear summary in the same language as the query, 
            indicating the key points mentioned. Avoid literal repetition and organize the information clearly.
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
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback summary with LLM", e);
        }
        
        // Ultimate fallback
        return "Summary of relevant fragments found:\n\n" + fragmentsText;
    }

}
