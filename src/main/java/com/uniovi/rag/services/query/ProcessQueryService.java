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
    public String generateResponse(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                log().warn("Empty query received");
                return generateErrorResponse(query != null ? query : "");
            }
            
            String expandedQuery = expand(query);
            JSONObject nerEntities = analyse(expandedQuery);
            QueryType queryType = classify(expandedQuery);

            log().debug("Query expanded: {}", expandedQuery);
            log().debug("NER: {}", nerEntities);
            log().debug("Query Type : {}", queryType);

            ToolResult response = tryToolRoute(expandedQuery, nerEntities, queryType);

            if (response == null) {
                String answer = askModel(expandedQuery, nerEntities, queryType);
                log().info("Response generated with model directly: {}", answer);
                return answer;
            }

            log().info("Response generated with tool {}: {}", response.source(), response.result());
            return response.result();
        } catch (Exception e) {
            log().error("Unexpected error processing query : {}", query, e);
            return generateErrorResponse(query);
        }
    }
    
    /**
     * Generates an error response in the same language as the query.
     */
    private String generateErrorResponse(String query) {
        // Detect query language (simple heuristic)
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*") || 
                           queryLower.contains("quién") || queryLower.contains("qué") || 
                           queryLower.contains("cuándo") || queryLower.contains("dónde") ||
                           queryLower.contains("cuántos") || queryLower.contains("cómo");
        
        if (isSpanish) {
            return "Lo siento, ocurrió un error al procesar tu consulta. Por favor, inténtalo de nuevo.";
        } else {
            return "I'm sorry, an error occurred while processing your query. Please try again.";
        }
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
        if (!featureConfig.isToolsEnabled() || queryType == null) return null;

        Tool tool = toolsConfig.getTool(queryType);
        if (tool == null) {
            log().debug("No tool found for query type: {}", queryType);
            return null;
        }

        // Retry logic for tool execution
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log().debug("Retry attempt {} for tool: {}", attempt, queryType);
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }
                
                log().debug("Executing tool: {} (attempt {})", queryType, attempt + 1);
                ToolResult result = featureConfig.isNerEnabled() ?
                        tool.execute(ToolExecutionContext.of(query, queryType, nerEntities)) :
                        tool.execute(ToolExecutionContext.of(query, queryType));
                
                if (result != null && result.result() != null) {
                    // Validate tool result
                    String validatedResult = LLMResponseValidator.validateAndClean(result.result(), "Tool-" + queryType);
                    if (validatedResult != null) {
                        log().debug("Successfully executed tool {} on attempt {}", queryType, attempt + 1);
                        // Create new ToolResult with validated result, preserving original source
                        return new ToolResult(validatedResult, result.source());
                    } else {
                        log().warn("Tool {} returned invalid result on attempt {}", queryType, attempt + 1);
                        if (attempt < MAX_RETRIES) {
                            continue; // Retry
                        }
                    }
                } else {
                    log().warn("Tool {} returned null result on attempt {}", queryType, attempt + 1);
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
        List<Document> docs;
        if (retriever instanceof AbstractContextRetriever && nerEntities != null && !nerEntities.isEmpty()) {
            docs = ((AbstractContextRetriever) retriever).retrieveWithMetadataFilters(query, nerEntities);
            log().debug("Using optimized retrieval with metadata filters, retrieved {} documents", docs.size());
        } else {
            docs = retriever.retrieve(query);
        }

        String context = retriever.createContext(docs, query, nerEntities);

        log().debug("Retrieved {} documents, context length: {}", docs.size(), context != null ? context.length() : 0);
        if (log().isDebugEnabled()) {
            log().debug("Retrieved context:\n{}", context);
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
                    log().debug("Retry attempt {} for query: {}", attempt, query);
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
                    log().debug("Successfully generated response on attempt {}", attempt + 1);
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
     * The response language matches the query language.
     */
    private String generateNoContextResponse(String query) {
        // Detect query language (simple heuristic)
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*") || 
                           queryLower.contains("quién") || queryLower.contains("qué") || 
                           queryLower.contains("cuándo") || queryLower.contains("dónde") ||
                           queryLower.contains("cuántos") || queryLower.contains("cómo");
        
        if (isSpanish) {
            return "Lo siento, no se encontró información relevante en los documentos disponibles para responder a tu pregunta.";
        } else {
            return "I'm sorry, I couldn't find relevant information in the available documents to answer your question.";
        }
    }
}

