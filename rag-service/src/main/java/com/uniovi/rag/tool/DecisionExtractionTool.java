package com.uniovi.rag.tool;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.util.RegexSafety;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enhanced DecisionExtractionTool for extracting decisions from meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic decision relevance evaluation
 * - Enhanced decision extraction and summarization
 */
public class DecisionExtractionTool extends AbstractTool {

    private static final String PLACEHOLDER_UNKNOWN_DATE = "unknown date";

    public DecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        super(chatClient, retriever, extractor);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing decision extraction query: '{}' with NER: {}", 
                  query, ner != null ? ner.toString() : "null");
        long startTime = System.currentTimeMillis();
        
        List<Document> docs = retrieveDocuments(query, ner);
        log().debug("Retrieved {} documents for decision extraction query", docs.size());
        List<String> decisions = new ArrayList<>();

        if (ner != null && !docs.isEmpty()) {
            collectDecisionsWithNerMatching(decisions, nerHandler.filterDocumentsByTemporalContext(docs, ner), query, ner);
        }

        if (decisions.isEmpty() && !docs.isEmpty()) {
            collectDecisionsWithRelevanceGate(decisions, docs, query);
        }

        if (decisions.isEmpty()) {
            List<Document> allDocs = retrieveAllDocuments(query, ner);
            if (!allDocs.isEmpty()) {
                collectDecisionsWithRelevanceGate(decisions, allDocs, query);
            }
        }

        String response;
        if (!decisions.isEmpty()) {
            log().debug("Extracted {} decisions for query, limiting to 5 for conciseness", decisions.size());
            // Limit decisions to 5 maximum for conciseness
            List<String> limitedDecisions = decisions.stream().limit(5).toList();
            response = generateResponseWithLLM(query, limitedDecisions);
        } else {
            long totalTime = System.currentTimeMillis() - startTime;
            log().info("No decisions found for query: '{}' (execution time: {} ms)", query, totalTime);
            response = generateNotFoundResponse(query);
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log().info("Generated decision extraction answer for query: '{}' (execution time: {} ms, decisions: {})", 
                  query, totalTime, decisions.size());
        // Apply formatResponse to clean the response
        String formattedResponse = formatResponse(response, query);
        return ToolResult.from(formattedResponse, getClass());
    }

    private static boolean isMissingDocText(Document doc) {
        return doc == null || doc.getText() == null || doc.getText().trim().isEmpty();
    }

    private String formatDecisionLine(String date, String fragment) {
        return "Meeting minutes from " + (date != null ? date : PLACEHOLDER_UNKNOWN_DATE) + ":\n" + fragment;
    }

    private void collectDecisionsWithNerMatching(List<String> decisions, List<Document> filteredDocs, String query, JSONObject ner) {
        for (Document doc : filteredDocs) {
            if (isMissingDocText(doc) || !nerHandler.matchesDocumentWithNER(doc, ner)) {
                continue;
            }
            appendDecisionFragments(decisions, doc, query, false);
        }
    }

    private void collectDecisionsWithRelevanceGate(List<String> decisions, List<Document> docs, String query) {
        for (Document doc : docs) {
            if (isMissingDocText(doc)) {
                continue;
            }
            appendDecisionFragments(decisions, doc, query, true);
        }
    }

    private void appendDecisionFragments(List<String> decisions, Document doc, String query, boolean requireExplicitRelevance) {
        String content = doc.getText();
        String date = extractor.extractDate(content);
        for (String fragment : extractDecisions(content, query)) {
            if (fragment == null || fragment.trim().isEmpty()) {
                continue;
            }
            if (requireExplicitRelevance && !isDecisionRelevantToQuery(fragment, query)) {
                continue;
            }
            decisions.add(formatDecisionLine(date, fragment));
        }
    }

    /**
     * Extracts decisions from content using intelligent fragment analysis
     */
    private List<String> extractDecisions(String content, String query) {
        String bounded = content == null ? "" : RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        // Split content into fragments and use LLM to determine if it's a relevant decision
        return Stream.of(bounded.split("(?<=[.:?])\\s*([\\n\\r])+"))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .filter(p -> isDecisionRelevantToQuery(p, query))
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Determines if a fragment contains a decision relevant to the query.
     * Uses English for internal processing, but preserves original language in query and fragment.
     */
    private boolean isDecisionRelevantToQuery(String fragment, String query) {
        if (fragment == null || fragment.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            This is the user's query (in any language):
            "%s"
            
            This is a fragment from meeting minutes (may be in any language):
            "%s"
            
            Does this fragment contain a decision relevant to the query?
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, fragment);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in isDecisionRelevantToQuery, defaulting to false");
                return false;
            }
            
            // Use LLM to interpret the response as yes/no
            return interpretBooleanResponse(result, "isDecisionRelevantToQuery");
        } catch (Exception e) {
            log().error("Error in isDecisionRelevantToQuery, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Generates response with LLM using found decisions.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateResponseWithLLM(String query, List<String> decisions) {
        if (query == null || query.trim().isEmpty() || decisions == null || decisions.isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String joined = decisions.stream()
                .filter(d -> d != null && !d.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining("\n\n"));
        
        if (joined.trim().isEmpty()) {
            return generateNotFoundResponse(query);
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found the following relevant decisions in the meeting minutes:
            %s
            
            Write a brief and clear response in the same language as the query, 
            summarizing the decisions found and their context.
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
                log().warn("Empty response from LLM in generateResponseWithLLM for query: '{}', using fallback", query);
                return generateFallbackResponse(query, decisions);
            }
            
            // Apply formatResponse to clean and format the response
            return formatResponse(response.strip(), query);
        } catch (Exception e) {
            log().error("Error generating response with LLM, using fallback", e);
            return generateFallbackResponse(query, decisions);
        }
    }
    
    /**
     * Generates not found response.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateNotFoundResponse(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No relevant decisions were found for this query in the available meeting minutes.
            
            Write a polite response in the same language as the query explaining that no relevant decisions were found.
            Be concise and direct.
            DO NOT repeat the question or any part of it.
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
     * Generates a fallback response when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackResponse(String query, List<String> decisions) {
        String decisionsText = decisions.stream()
                .limit(5)
                .collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found the following relevant decisions:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            listing the found decisions.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "", decisionsText);
        
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
            log().warn("Error generating fallback response with LLM", e);
        }
        
        // Ultimate fallback
        return "Found the following relevant decisions:\n\n" + decisionsText;
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
}
