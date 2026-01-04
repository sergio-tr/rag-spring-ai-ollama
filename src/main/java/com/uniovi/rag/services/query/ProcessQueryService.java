package com.uniovi.rag.services.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.tools.Tool;
import com.uniovi.rag.services.retriever.AbstractContextRetriever;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.services.tools.ToolResult;
import com.uniovi.rag.utils.LLMResponseValidator;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProcessQueryService implements QueryService {

    private static final String DEFAULT_PROMPT_TEMPLATE = """
        You are a helpful assistant that answers questions based on retrieved documents from a meeting minutes database.
        
        CRITICAL: The following context contains RAW DOCUMENT FRAGMENTS retrieved from the database. 
        These are NOT pre-extracted answers - they are document fragments that may or may not contain 
        the information needed to answer the question.
        
        Your task is to:
        1. ANALYZE and PROCESS the retrieved context fragments
        2. EXTRACT the specific information needed to answer the question
        3. SYNTHESIZE a clear, direct answer from the relevant information found
        4. Answer in the SAME LANGUAGE as the user's question (if Spanish, answer in Spanish; if English, answer in English, etc.)
        
        CRITICAL RULES - YOU MUST FOLLOW THESE:
        1. You must PROCESS the context - do not just copy or summarize it. Extract the specific answer.
        2. Base your answer ONLY on the information provided in the context
        3. If the context is empty or does not contain enough information to answer the question, you MUST clearly state that you cannot find the information in the available documents. DO NOT invent, guess, or make up information.
        4. NEVER invent names, dates, places, or any other information that is not explicitly in the context
        5. NEVER provide lists of names or details if they are not in the context
        6. If you cannot find the requested information, say so clearly in the same language as the question
        7. Be concise but complete - provide all relevant information from the context
        8. Do not add information that is not in the context
        9. Do not include headers, introductions, or explanatory text - just provide the direct answer
        10. If multiple fragments contain relevant information, synthesize them into a coherent answer
        
        <QueryType> %s </QueryType>
        <Question> %s </Question>
        <Context> %s </Context>
        
        Provide your direct answer now (in the same language as the question):
        """;

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;

    private final RagFeatureConfiguration featureConfig;
    private final ChatClient chatClient;
    private final QueryExpander expander;
    private final QueryAnalyser analyser;
    private final QueryClassifier classifier;
    private final ContextRetriever retriever;
    private final RagToolsConfiguration toolsConfig;

    public ProcessQueryService(RagFeatureConfiguration featureConfig,
                               RagToolsConfiguration toolsConfig,
                               QueryExpander expander,
                               QueryAnalyser analyser,
                               QueryClassifier classifier,
                               ContextRetriever retriever,
                               ChatClient chatClient) {
        this.featureConfig = featureConfig;
        this.chatClient = chatClient;
        this.expander = expander;
        this.analyser = analyser;
        this.classifier = classifier;
        this.retriever = retriever;
        this.toolsConfig = toolsConfig;
    }

    @Override
    public QueryResponse generateResponse(String query) {
        boolean isProblematicConfig = featureConfig.isMetadataEnabled() && 
                                      featureConfig.isNerEnabled() && 
                                      !featureConfig.isToolsEnabled();
        
        if (isProblematicConfig) {
            log().info("Processing query with problematic configuration: metadata=true, ner=true, tools=false");
            log().info("Configuration details: expansion={}, ner={}, tools={}, metadata={}", 
                      featureConfig.isExpansionEnabled(), 
                      featureConfig.isNerEnabled(), 
                      featureConfig.isToolsEnabled(), 
                      featureConfig.isMetadataEnabled());
        }
        
        try {
            if (query == null || query.trim().isEmpty()) {
                log().warn("Empty query received");
                String errorResponse = generateErrorResponse(query != null ? query : "");
                return QueryResponse.fromLLM(errorResponse);
            }
            
            String expandedQuery = expand(query);
            if (isProblematicConfig) {
                log().debug("Expanded query: {}", expandedQuery);
            }
            
            JSONObject nerEntities = analyse(expandedQuery);
            if (isProblematicConfig) {
                log().debug("NER entities extracted: {}", nerEntities != null ? nerEntities.toString() : "null");
                if (nerEntities != null) {
                    log().debug("NER keys: {}", nerEntities.keySet());
                }
            }
            
            QueryType queryType = classify(expandedQuery);

            log().info("Query expanded: {}", expandedQuery);
            log().info("NER: {}", nerEntities);
            log().info("Query Type : {}", queryType);

            ToolResult response = tryToolRoute(expandedQuery, nerEntities, queryType);

            if (response == null) {
                if (isProblematicConfig) {
                    log().debug("No tool route available, falling back to direct model query");
                }
                String answer = askModel(expandedQuery, nerEntities, queryType);
                log().info("Response generated with model directly: {}", answer);
                return QueryResponse.fromLLM(answer, queryType);
            }

            log().info("Response generated with tool {}: {}", response.source(), response.result());
            return QueryResponse.fromTool(response.result(), response.source(), queryType);
        } catch (NullPointerException e) {
            log().error("NullPointerException processing query (config: metadata={}, ner={}, tools={}): {}", 
                       featureConfig.isMetadataEnabled(), 
                       featureConfig.isNerEnabled(), 
                       featureConfig.isToolsEnabled(), 
                       query, e);
            log().error("Stack trace:", e);
            String errorResponse = generateErrorResponse(query);
            return QueryResponse.fromLLM(errorResponse);
        } catch (IllegalArgumentException e) {
            log().error("IllegalArgumentException processing query (config: metadata={}, ner={}, tools={}): {}", 
                       featureConfig.isMetadataEnabled(), 
                       featureConfig.isNerEnabled(), 
                       featureConfig.isToolsEnabled(), 
                       query, e);
            log().error("Stack trace:", e);
            String errorResponse = generateErrorResponse(query);
            return QueryResponse.fromLLM(errorResponse);
        } catch (Exception e) {
            log().error("Unexpected error processing query (config: metadata={}, ner={}, tools={}): {}", 
                       featureConfig.isMetadataEnabled(), 
                       featureConfig.isNerEnabled(), 
                       featureConfig.isToolsEnabled(), 
                       query, e);
            log().error("Exception type: {}, Message: {}", e.getClass().getName(), e.getMessage());
            log().error("Stack trace:", e);
            String errorResponse = generateErrorResponse(query);
            return QueryResponse.fromLLM(errorResponse);
        }
    }
    
    /**
     * Generates an error response in the same language as the query.
     * Uses LLM to generate message in correct language.
     */
    private String generateErrorResponse(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            An error occurred while processing this query.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            apologizing for the error and asking the user to try again.
            Be concise and polite.
            Do not repeat the question.
            """, query != null ? query : "");
        
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
            log().warn("Error generating error response with LLM", e);
        }
        
        // Ultimate fallback
        return "I'm sorry, an error occurred while processing your query. Please try again.";
    }

    private String expand(String query) {
        return featureConfig.isExpansionEnabled() ? expander.expand(query) : query;
    }

    private JSONObject analyse(String query) {
        return featureConfig.isNerEnabled() ? analyser.analyse(query) : null;
    }

    private QueryType classify(String query) {
        return featureConfig.isToolsEnabled() ? classifier.classify(query) : null;
    }

    /**
     * Attempts to route the query through a tool with retry logic.
     * Falls back to direct model query if tool execution fails.
     */
    private ToolResult tryToolRoute(String query, JSONObject nerEntities, QueryType queryType) {
        if (!featureConfig.isToolsEnabled()) {
            log().debug("Tools are disabled in configuration, skipping tool routing");
            return null;
        }
        
        if (queryType == null) {
            log().debug("Query type is null, cannot route to tool");
            return null;
        }

        Tool tool = toolsConfig.getTool(queryType);
        if (tool == null) {
            log().warn("No tool found for query type: {}. This may indicate a configuration issue.", queryType);
            return null;
        }
        
        log().debug("Routing query to tool: {} for query type: {}", tool.getClass().getSimpleName(), queryType);

        // Retry logic for tool execution
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log().info("Retry attempt {} for tool: {}", attempt, queryType);
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }
                
                log().info("Executing tool: {} (attempt {})", queryType, attempt + 1);
                ToolResult result = featureConfig.isNerEnabled() ?
                        tool.execute(ToolExecutionContext.of(query, queryType, nerEntities)) :
                        tool.execute(ToolExecutionContext.of(query, queryType));
                
                if (result != null && result.result() != null && !result.result().trim().isEmpty()) {
                    // Validate tool result
                    String validatedResult = LLMResponseValidator.validateAndClean(result.result(), "Tool-" + queryType);
                    if (validatedResult != null && !validatedResult.trim().isEmpty()) {
                        log().info("Successfully executed tool {} on attempt {}", queryType, attempt + 1);
                        // Create new ToolResult with validated result, preserving original source
                        // Note: Informative messages (like "no documents found") are considered valid responses
                        return new ToolResult(validatedResult, result.source());
                    } else {
                        log().warn("Tool {} returned result that failed validation on attempt {}. " +
                                  "Original result length: {}, Validated result: null or empty. " +
                                  "This may indicate LLMResponseValidator rejected the response.",
                                  queryType, attempt + 1, result.result().length());
                        if (attempt < MAX_RETRIES) {
                            continue; // Retry
                        }
                    }
                } else {
                    String resultInfo = result == null ? "null" : 
                                      (result.result() == null ? "result() is null" : 
                                      (result.result().trim().isEmpty() ? "result() is empty" : "unknown"));
                    log().warn("Tool {} returned {} on attempt {}. Tool class: {}", 
                              queryType, resultInfo, attempt + 1, tool.getClass().getSimpleName());
                    if (attempt < MAX_RETRIES) {
                        continue; // Retry
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log().error("Thread interrupted during tool retry: {}", queryType, e);
                break;
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                
                if (errorMsg.contains("duplicate element")) {
                    log().warn("Duplicate element error detected for tool {} on attempt {}: {}. Skipping retries as this won't resolve with retry.", 
                              queryType, attempt + 1, e.getMessage());
                    break; // Don't retry for duplicate element errors
                }
                
                log().warn("Error executing tool {} on attempt {}: {}", queryType, attempt + 1, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    continue; // Retry
                }
            }
        }
        
        // All retries failed
        if (lastException != null) {
            log().error("Failed to execute tool {} after {} attempts: {}", queryType, MAX_RETRIES + 1, lastException.getMessage());
        } else {
            log().error("Tool {} failed to return valid result after {} attempts", queryType, MAX_RETRIES + 1);
        }
        
        return null; // Fall back to direct model query
    }

    /**
     * Asks the LLM model with retry logic and response validation.
     * Retries up to MAX_RETRIES times if the response is invalid or an error occurs.
     */
    private String askModel(String query, JSONObject nerEntities, QueryType queryType) {
        boolean isProblematicConfig = featureConfig.isMetadataEnabled() && 
                                      featureConfig.isNerEnabled() && 
                                      !featureConfig.isToolsEnabled();
        
        List<Document> docs;
        try {
            if (retriever instanceof AbstractContextRetriever && nerEntities != null && !nerEntities.isEmpty()) {
                if (isProblematicConfig) {
                    log().debug("Attempting retrieval with metadata filters and NER entities");
                }
                docs = ((AbstractContextRetriever) retriever).retrieveWithMetadataFilters(query, nerEntities);
                log().info("Using optimized retrieval with metadata filters, retrieved {} documents", docs.size());
            } else {
                if (isProblematicConfig) {
                    log().debug("Using standard retrieval (no metadata filters or NER)");
                }
                docs = retriever.retrieve(query);
            }
        } catch (NullPointerException e) {
            log().error("NullPointerException during document retrieval (config: metadata={}, ner={}): {}", 
                       featureConfig.isMetadataEnabled(), 
                       featureConfig.isNerEnabled(), 
                       e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log().error("Exception during document retrieval (config: metadata={}, ner={}): {}", 
                       featureConfig.isMetadataEnabled(), 
                       featureConfig.isNerEnabled(), 
                       e.getMessage(), e);
            throw e;
        }

        String context = retriever.createContext(docs, query, nerEntities);

        log().info("Retrieved {} documents, context length: {}", docs.size(), context != null ? context.length() : 0);
        if (log().isDebugEnabled()) {
            log().info("Retrieved context:\n{}", context);
        }

        if (context == null || context.trim().isEmpty()) {
            log().warn("Empty context retrieved for query: {}", query);
            return generateNoContextResponse(query);
        }
        
        // Additional validation: if context is too short, it might not contain useful information
        if (context.trim().length() < 50) {
            log().warn("Context too short ({} chars) for query: {}", context.length(), query);
            // Still try to use it, but log the warning
        }

        String prompt = String.format(
                DEFAULT_PROMPT_TEMPLATE,
                queryType != null ? queryType.name() : "UNKNOWN",
                query,
                context
        );

        // Retry logic for LLM calls
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log().info("Retry attempt {} for query: {}", attempt, query);
                    Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                }
                
                String response = chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();
                
                // Validate and clean response
                String validatedResponse = LLMResponseValidator.validateAndClean(response, "ProcessQueryService");
                
                if (validatedResponse != null) {
                    log().info("Successfully generated response on attempt {}", attempt + 1);
                    return validatedResponse;
                } else {
                    log().warn("Invalid response from LLM on attempt {} for query: {}", attempt + 1, query);
                    if (attempt < MAX_RETRIES) {
                        continue; // Retry
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log().error("Thread interrupted during retry for query: {}", query, e);
                break;
            } catch (Exception e) {
                lastException = e;
                log().warn("Error generating response on attempt {} for query: {} - {}", 
                          attempt + 1, query, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    continue; // Retry
                }
            }
        }
        
        // All retries failed
        if (lastException != null) {
            log().error("Failed to generate response after {} attempts for query: {}", MAX_RETRIES + 1, query, lastException);
        } else {
            log().error("Failed to generate valid response after {} attempts for query: {}", MAX_RETRIES + 1, query);
        }
        
        return generateNoContextResponse(query);
    }
    
    /**
     * Generates a response when no context is available.
     * Uses LLM to generate message in correct language.
     */
    private String generateNoContextResponse(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No relevant information was found in the available documents to answer this question.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            apologizing and stating that no relevant information was found.
            Be concise and polite.
            Do not repeat the question.
            """, query != null ? query : "");
        
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
            // Fallback if LLM fails
        }
        
        // Ultimate fallback
        return "I'm sorry, I couldn't find relevant information in the available documents to answer your question.";
    }
}

