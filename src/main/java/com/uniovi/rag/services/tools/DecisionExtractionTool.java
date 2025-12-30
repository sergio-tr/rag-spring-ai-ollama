package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;

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

    public DecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing decision extraction query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> decisions = new ArrayList<>();

        // Try with NER filtering if available
        if (ner != null && !docs.isEmpty()) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    List<String> fragments = extractDecisions(content, query);
                    for (String fragment : fragments) {
                        if (fragment != null && !fragment.trim().isEmpty()) {
                            decisions.add("Meeting minutes from " + (date != null ? date : "unknown date") + ":\n" + fragment);
                        }
                    }
                }
            }
        }
        
        if (decisions.isEmpty() && !docs.isEmpty()) {
            // Fallback to query-based relevance
            for (Document doc : docs) {
                if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                    continue;
                }
                
                String content = doc.getContent();
                String date = extractDate(content);
                List<String> fragments = extractDecisions(content, query);
                for (String fragment : fragments) {
                    if (fragment != null && !fragment.trim().isEmpty() && isDecisionRelevantToQuery(fragment, query)) {
                        decisions.add("Meeting minutes from " + (date != null ? date : "unknown date") + ":\n" + fragment);
                    }
                }
            }
        }
        
        if (decisions.isEmpty()) {
            docs = retrieveAllDocuments(query);
            if (!docs.isEmpty()) {
                for (Document doc : docs) {
                    if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                        continue;
                    }
                    
                    String content = doc.getContent();
                    String date = extractDate(content);
                    List<String> fragments = extractDecisions(content, query);
                    for (String fragment : fragments) {
                        if (fragment != null && !fragment.trim().isEmpty() && isDecisionRelevantToQuery(fragment, query)) {
                            decisions.add("Meeting minutes from " + (date != null ? date : "unknown date") + ":\n" + fragment);
                        }
                    }
                }
            }
        }

        String response;
        if (!decisions.isEmpty()) {
            response = generateResponseWithLLM(query, decisions);
        } else {
            response = generateNotFoundResponse(query);
        }
        return ToolResult.from(response, getClass());
    }

    /**
     * Extracts decisions from content using intelligent fragment analysis
     */
    private List<String> extractDecisions(String content, String query) {
        // Split content into fragments and use LLM to determine if it's a relevant decision
        return Stream.of(content.split("(?<=[.:?])\\s*([\\n\\r])+"))
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
            """, query, joined);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateResponseWithLLM, using fallback");
                return generateFallbackResponse(query, decisions);
            }
            
            return response.strip();
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

    /**
     * Generates a fallback "not found" message when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackNotFoundMessage(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No relevant decisions were found in the available documents for this query.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating that no relevant decisions were found.
            Be concise and direct.
            Do not repeat the question.
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip();
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback not found message with LLM", e);
        }
        
        // Ultimate fallback
        return "No relevant decisions were found in the available documents for this query.";
    }
}
