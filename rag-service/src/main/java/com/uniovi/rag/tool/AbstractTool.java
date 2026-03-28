package com.uniovi.rag.tool;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;

import java.util.List;

public abstract class AbstractTool implements Tool {

    protected final ChatClient chatClient;
    protected final ContextRetriever retriever;
    protected final EnhancedNERHandler nerHandler;
    protected final DocumentContentExtractor extractor;

    protected AbstractTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
        this.chatClient = chatClient;
        this.retriever = retriever;
        this.nerHandler = new EnhancedNERHandler(chatClient);
        this.extractor = extractor;
    }

    /**
     * Performs retrieval; when NER entities are provided and the retriever supports it,
     * uses metadata filters (e.g. date) for better relevance.
     */
    protected List<Document> doRetrieve(String query, JSONObject nerEntities) {
        if (nerEntities != null && !nerEntities.isEmpty()) {
            return retriever.retrieveWithMetadataFilters(query, nerEntities);
        }
        return retriever.retrieve(query);
    }

    /**
     * Retrieves all documents matching the query with maximum recall.
     * When NER is enabled and context provides nerEntities, retrieval uses metadata filters (e.g. date).
     */
    protected List<Document> retrieveAllDocuments(String query) {
        return retrieveAllDocuments(query, null);
    }

    protected List<Document> retrieveAllDocuments(String query, JSONObject nerEntities) {
        retriever.setTopK(100);  // Reduced from 1000 to improve performance
        retriever.setSimilarityThreshold(0);
        return doRetrieve(query, nerEntities);
    }

    /**
     * Retrieves documents intelligently with a configurable limit.
     * When NER is enabled, uses metadata filters for retrieval.
     */
    protected List<Document> retrieveDocumentsIntelligently(String query, int maxDocuments) {
        return retrieveDocumentsIntelligently(query, maxDocuments, null);
    }

    protected List<Document> retrieveDocumentsIntelligently(String query, int maxDocuments, JSONObject nerEntities) {
        retriever.setTopK(Math.min(maxDocuments, 200));  // Maximum 200 to avoid overload
        retriever.setSimilarityThreshold(0);
        return doRetrieve(query, nerEntities);
    }

    /**
     * Retrieves documents with default settings.
     * When NER is enabled and context provides nerEntities, uses metadata filters (e.g. date) for retrieval.
     */
    protected List<Document> retrieveDocuments(String query) {
        return retrieveDocuments(query, null);
    }

    protected List<Document> retrieveDocuments(String query, JSONObject nerEntities) {
        retriever.restoreDefaultSettings();
        return doRetrieve(query, nerEntities);
    }

    protected List<Document> retrieveDocumentsWithTopK(String query, int topK) {
        return retrieveDocumentsWithTopK(query, topK, null);
    }

    protected List<Document> retrieveDocumentsWithTopK(String query, int topK, JSONObject nerEntities) {
        retriever.restoreDefaultSettings();
        retriever.setTopK(topK);
        return doRetrieve(query, nerEntities);
    }

    /**
     * Removes question repetition from LLM-generated responses.
     * Detects and removes instances where the LLM repeats part or all of the user's question.
     * 
     * @param response The LLM-generated response
     * @param query The original user query
     * @return Cleaned response without question repetition
     */
    protected String removeQuestionRepetition(String response, String query) {
        if (response == null || query == null || response.trim().isEmpty() || query.trim().isEmpty()) {
            return response;
        }
        
        String responseLower = response.trim().toLowerCase();
        String queryLower = query.trim().toLowerCase();
        
        // Check if response starts with the full question
        if (responseLower.startsWith(queryLower)) {
            String cleaned = response.substring(query.length()).trim();
            // Remove common separators that might follow the question (e.g. ": - ", ": ", " - ")
            cleaned = cleaned.replaceFirst("^[:\\s\\-]+", "");
            cleaned = cleaned.replaceFirst("^[.\\n\\r\\-\\s]+", "");
            log().debug("Removed full question repetition from response. Original length: {}, Cleaned length: {}", 
                      response.length(), cleaned.length());
            return cleaned.isEmpty() ? response : cleaned; // Return original if cleaning removes everything
        }
        
        // Check if response starts with common question prefixes followed by part of the question
        String[] questionPrefixes = {
            "dime que", "dime qué", "dime", "tell me", "the user asked", "la pregunta era",
            "resume lo tratado", "resume la reunión", "resume", "summarize",
            "dame un resumen", "hazme un resumen", "haz un resumen", "give me a summary",
            "busca lo comentado", "busca", "search", "find",
            "proporciona la fecha", "proporciona", "provide"
        };
        
        for (String prefix : questionPrefixes) {
            if (responseLower.startsWith(prefix.toLowerCase())) {
                // Check if what follows is part of the query
                String afterPrefix = response.substring(prefix.length()).trim();
                String afterPrefixLower = afterPrefix.toLowerCase();
                
                // If the next part matches a significant portion of the query, remove it
                if (afterPrefixLower.length() > 10 && queryLower.contains(afterPrefixLower.substring(0, Math.min(20, afterPrefixLower.length())))) {
                    // Find where the actual answer starts (usually after a newline or period)
                    String[] separators = {"\n\n", "\n", ". ", ".\n"};
                    for (String sep : separators) {
                        int sepIndex = afterPrefix.indexOf(sep);
                        if (sepIndex > 0 && sepIndex < afterPrefix.length() - 5) {
                            String cleaned = afterPrefix.substring(sepIndex + sep.length()).trim();
                            if (!cleaned.isEmpty()) {
                                log().debug("Removed question prefix and repetition from response");
                                return cleaned;
                            }
                        }
                    }
                }
            }
        }
        
        // Check for common patterns where question is repeated at the start
        // Pattern: "Question text.\n\nAnswer text"
        int newlineIndex = response.indexOf("\n\n");
        if (newlineIndex > 0 && newlineIndex < response.length() / 2) {
            String beforeNewline = response.substring(0, newlineIndex).trim().toLowerCase();
            // If the part before newline is similar to the query, it's likely a repetition
            if (beforeNewline.length() > 10 && 
                (queryLower.contains(beforeNewline.substring(0, Math.min(30, beforeNewline.length()))) ||
                 beforeNewline.contains(queryLower.substring(0, Math.min(30, queryLower.length()))))) {
                String cleaned = response.substring(newlineIndex + 2).trim();
                if (!cleaned.isEmpty()) {
                    log().debug("Removed question repetition before double newline");
                    return cleaned;
                }
            }
        }
        
        // No repetition detected, return original
        return response;
    }
    
    /**
     * Formats and cleans LLM response for better presentation.
     * Applies multiple formatting improvements:
     * - Removes question repetition
     * - Normalizes whitespace
     * - Removes trailing punctuation issues
     * - Ensures proper sentence endings
     * 
     * @param response Raw LLM response
     * @param query Original user query (for removing repetition)
     * @return Formatted and cleaned response
     */
    protected String formatResponse(String response, String query) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }
        
        // Step 1: Remove question repetition
        String cleaned = removeQuestionRepetition(response, query);
        
        // Step 2: Normalize whitespace (multiple spaces/newlines to single)
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("\\n\\s*\\n+", "\n\n"); // Normalize multiple newlines to double newline max
        
        // Step 3: Remove trailing punctuation issues (multiple periods, etc.)
        cleaned = cleaned.replaceAll("\\.{3,}", "...");
        cleaned = cleaned.replaceAll("\\?{2,}", "?");
        cleaned = cleaned.replaceAll("!{2,}", "!");
        
        // Step 4: Ensure proper sentence structure (capitalize first letter if needed)
        if (cleaned.length() > 0 && Character.isLowerCase(cleaned.charAt(0))) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        }
        
        // Step 5: Remove common formatting artifacts
        cleaned = cleaned.replaceAll("^\\s*[-•*]\\s+", ""); // Remove leading bullets
        cleaned = cleaned.replaceAll("\\s*[-•*]\\s*$", ""); // Remove trailing bullets
        
        log().debug("Formatted response: original length={}, cleaned length={}", 
                   response.length(), cleaned.length());
        
        return cleaned.trim();
    }

    /**
     * Generates a "not found" message using the LLM so the response language matches the query.
     */
    protected String generateNotFoundMessage(String query) {
        String prompt = String.format("""
            You are a helpful assistant. The user asked the following question (in any language):
            "%s"
            
            IMPORTANT: You must respond in the EXACT SAME LANGUAGE as the user's question.
            Write a short, polite message indicating that no relevant information was found for this query.
            Be concise and helpful.
            """, query != null ? query : "");
        try {
            String response = chatClient.prompt().user(prompt).call().content();
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating not found message, using fallback", e);
        }
        return generateFallbackNotFoundMessage(query);
    }

    /**
     * Fallback "not found" message when LLM fails or query is empty.
     */
    protected String generateFallbackNotFoundMessage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "No relevant information was found for this query.";
        }
        String prompt = String.format("""
            The user asked (in any language): "%s"
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            indicating that no relevant information was found. Be concise and polite.
            """, query);
        try {
            String response = chatClient.prompt().user(prompt).call().content();
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback not found message with LLM", e);
        }
        return "No relevant information was found for this query.";
    }

    /**
     * Interprets LLM response as YES/NO/UNKNOWN using another LLM call.
     * @return true for YES, false for NO, null for UNKNOWN or on error
     */
    protected Boolean interpretLLMYesNoResponse(String response, String context) {
        if (response == null || response.trim().isEmpty()) {
            log().warn("Empty LLM response in {}", context);
            return null;
        }
        String prompt = String.format("""
            Context: %s
            
            The LLM generated this response: "%s"
            
            Task: Interpret this response as a boolean answer.
            - If it means YES/TRUE/POSITIVE/MATCH/RELEVANT, respond with: YES
            - If it means NO/FALSE/NEGATIVE/NO_MATCH/IRRELEVANT, respond with: NO
            - If it's UNCLEAR/AMBIGUOUS/UNCERTAIN, respond with: UNKNOWN
            
            Consider semantic meaning, not just exact words.
            Respond with ONLY one word: YES, NO, or UNKNOWN.
            """, context, response);
        try {
            String interpretation = chatClient.prompt().user(prompt).call().content();
            if (interpretation == null) return null;
            String upper = interpretation.strip().toUpperCase();
            if (upper.contains("YES")) return true;
            if (upper.contains("NO")) return false;
            log().debug("LLM interpreted response in {} as UNKNOWN: '{}'", context, response);
            return null;
        } catch (Exception e) {
            log().warn("Error interpreting LLM yes/no response in {}", context, e);
            return null;
        }
    }

}
