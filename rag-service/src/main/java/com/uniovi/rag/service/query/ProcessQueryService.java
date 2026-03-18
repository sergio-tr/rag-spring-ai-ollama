package com.uniovi.rag.service.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.model.CandidateResponse;
import com.uniovi.rag.model.DraftAndContext;
import com.uniovi.rag.model.PostStepOutput;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.model.RankerResult;
import com.uniovi.rag.model.ReasoningPreOutput;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.ranker.ResponseRanker;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.retriever.AbstractContextRetriever;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.ToolRagService;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        4. NEVER invent names, dates, places, actas, or any other information that is not explicitly in the context. Do not invent acta dates or mix information from different actas. Only use information that appears in the context.
        5. NEVER provide lists of names or details if they are not in the context
        6. If you cannot find the requested information, say so clearly in the same language as the question
        7. Be concise but complete - provide all relevant information from the context
        8. Do not add information that is not in the context
        9. Do not include headers, introductions, or explanatory text - just provide the direct answer
        10. If multiple fragments contain relevant information, synthesize them into a coherent answer
        11. When relevant, mention or cite the source (e.g. document, date) from the context.
        
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
    private final NERQueryEnricher nerQueryEnricher;
    private final QueryClassifier classifier;
    private final ContextRetriever retriever;
    private final RagToolsConfiguration toolsConfig;
    private final DateExistenceGuard dateExistenceGuard;
    private final MeetingMinutesToolsAdapter meetingMinutesToolsAdapter;
    private final ReasoningStrategy reasoningStrategy;
    private final ResponseRanker responseRanker;
    private final PostRetrievalProcessor postRetrievalProcessor;
    private final ToolRagService toolRagService;
    private final ResponseValidator responseValidator;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;

    public ProcessQueryService(RagFeatureConfiguration featureConfig,
                               RagToolsConfiguration toolsConfig,
                               QueryExpander expander,
                               QueryAnalyser analyser,
                               NERQueryEnricher nerQueryEnricher,
                               QueryClassifier classifier,
                               ContextRetriever retriever,
                               ChatClient chatClient,
                               DateExistenceGuard dateExistenceGuard,
                               MeetingMinutesToolsAdapter meetingMinutesToolsAdapter,
                               ReasoningStrategy reasoningStrategy,
                               ResponseRanker responseRanker,
                               PostRetrievalProcessor postRetrievalProcessor,
                               ToolRagService toolRagService,
                               ResponseValidator responseValidator,
                               QuestionAnswerAdvisor questionAnswerAdvisor) {
        this.featureConfig = featureConfig;
        this.chatClient = chatClient;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.expander = expander;
        this.analyser = analyser;
        this.nerQueryEnricher = nerQueryEnricher;
        this.classifier = classifier;
        this.retriever = retriever;
        this.toolsConfig = toolsConfig;
        this.dateExistenceGuard = dateExistenceGuard;
        this.meetingMinutesToolsAdapter = meetingMinutesToolsAdapter;
        this.reasoningStrategy = reasoningStrategy;
        this.responseRanker = responseRanker;
        this.postRetrievalProcessor = postRetrievalProcessor;
        this.toolRagService = toolRagService;
        this.responseValidator = responseValidator;
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
            queryType = applyClassifierOverrides(expandedQuery, queryType);

            log().info("Query expanded: {}", expandedQuery);
            log().info("NER: {}", nerEntities);
            log().info("Query Type : {}", queryType);

            // Date existence guard - if no acta exists for requested date, return standard response without calling tool
            if (featureConfig.isMetadataEnabled() && queryType != null) {
                var guardResult = dateExistenceGuard.checkNoActaForDate(expandedQuery, queryType, nerEntities);
                if (guardResult.isPresent()) {
                    log().info("DateExistenceGuard: returning no-acta response for date-dependent query (queryType={})", queryType);
                    log().info("Response generated with tool {}: {}", guardResult.get().source(), guardResult.get().result());
                    return QueryResponse.fromTool(guardResult.get().result(), guardResult.get().source(), queryType);
                }
            }

            // A.4: Prefer tool response for date-scoped queries (DECISION_EXTRACTION, GET_FIELD) so tools answer before reasoning
            if (featureConfig.isToolsEnabled() && meetingMinutesToolsAdapter != null && shouldPreferToolResponse(queryType, nerEntities)) {
                QueryType toolQueryType = (featureConfig.isToolRagEnabled() && toolRagService != null)
                        ? toolRagService.findBestQueryType(expandedQuery)
                        : queryType;
                if (toolQueryType != null) {
                    try {
                        ToolResult adapterResult = meetingMinutesToolsAdapter.execute(toolQueryType, expandedQuery);
                        if (adapterResult != null && adapterResult.result() != null && !adapterResult.result().trim().isEmpty()) {
                            String validated = responseValidator.validateAndClean(adapterResult.result(), "Tool-" + queryType);
                            if (validated != null && !validated.trim().isEmpty()) {
                                log().info("Response generated via tool (prefer-tool path for date-scoped query)");
                                return QueryResponse.fromTool(validated, adapterResult.source() != null ? adapterResult.source() : "tool", toolQueryType);
                            }
                        }
                    } catch (Exception e) {
                        log().warn("Prefer-tool path failed, continuing to reasoning: {}", e.getMessage());
                    }
                }
            }

            if (featureConfig.isReasoningEnabled() && reasoningStrategy != null) {
                ReasoningPreOutput preOutput = reasoningStrategy.runPreStep(query, queryType, nerEntities, expandedQuery);
                DraftAndContext draftAndContext = getDraftForReasoning(expandedQuery, nerEntities, queryType, preOutput);
                if (draftAndContext != null && draftAndContext.draft() != null && !draftAndContext.draft().trim().isEmpty()) {
                    if (!isDraftAcceptable(draftAndContext)) {
                        log().info("Draft not acceptable (A.5 fallback), using askModel");
                        String answer = askModel(expandedQuery, nerEntities, queryType);
                        return QueryResponse.fromLLM(answer != null ? answer : draftAndContext.draft(), queryType);
                    }
                    PostStepOutput postOutput = reasoningStrategy.runPostStep(query, draftAndContext.context(), draftAndContext.draft());
                    String candidate = (postOutput != null && postOutput.verifiedOrRefinedText() != null)
                            ? postOutput.verifiedOrRefinedText()
                            : draftAndContext.draft();
                    List<CandidateResponse> candidates = new ArrayList<>();
                    candidates.add(CandidateResponse.of(candidate, "reasoning"));
                    if (featureConfig.isRankerEnabled() && responseRanker != null) {
                        RankerResult rankerResult = responseRanker.selectBest(query, draftAndContext.context(), candidates);
                        if (rankerResult != null && rankerResult.chosenText() != null) {
                            return QueryResponse.fromTool(rankerResult.chosenText(), "reasoning+ranker", queryType);
                        }
                    }
                    return QueryResponse.fromTool(candidate, "reasoning", queryType);
                }
            }

            if (featureConfig.isToolsEnabled() && meetingMinutesToolsAdapter != null) {
                QueryType toolQueryType = (featureConfig.isToolRagEnabled() && toolRagService != null)
                        ? toolRagService.findBestQueryType(expandedQuery)
                        : queryType;
                if (toolQueryType != null) {
                    String toolResponse = null;
                    if (featureConfig.isFunctionCallingEnabled()) {
                        try {
                            String content = (meetingMinutesToolsAdapter != null)
                                    ? chatClient.prompt().user(expandedQuery).tools(meetingMinutesToolsAdapter).call().content()
                                    : chatClient.prompt().user(expandedQuery).call().content();
                            if (content != null && !content.trim().isEmpty()) {
                                toolResponse = responseValidator.validateAndClean(content, "Tool-function-calling");
                                if (toolResponse != null && !toolResponse.trim().isEmpty()) {
                                    log().info("Response generated via ChatClient.tools(adapter) (function-calling path)");
                                    return QueryResponse.fromTool(toolResponse, "function-calling", toolQueryType);
                                }
                            }
                        } catch (Exception e) {
                            log().warn("ChatClient.tools(adapter) failed, falling back to deterministic execute: {}", e.getMessage());
                        }
                    }
                    if (toolResponse == null || toolResponse.trim().isEmpty()) {
                        try {
                            ToolResult adapterResult = meetingMinutesToolsAdapter.execute(toolQueryType, expandedQuery);
                            if (adapterResult != null && adapterResult.result() != null && !adapterResult.result().trim().isEmpty()) {
                                String validated = responseValidator.validateAndClean(adapterResult.result(), "Tool-" + queryType);
                                if (validated != null && !validated.trim().isEmpty()) {
                                    log().info("Response generated via tool adapter (deterministic path)");
                                    return QueryResponse.fromTool(validated, adapterResult.source() != null ? adapterResult.source() : "tool", toolQueryType);
                                }
                            }
                        } catch (Exception e) {
                            log().warn("Tool adapter execution failed: {}", e.getMessage());
                        }
                    }
                }
            }

            ToolResult response = tryToolRoute(expandedQuery, nerEntities, queryType);

            if (response == null) {
                log().info("Response generated with model directly (fallback; see fallback_reason in log above if tools were enabled)");
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
     * Applies rule-based overrides to the classified query type so that questions
     * like "confirm if X appears", "compare quantity of...", "agenda", "how long did it last"
     * are routed to the correct tool (BOOLEAN_QUERY, COMPARE, GET_FIELD, GET_DURATION).
     */
    private QueryType applyClassifierOverrides(String query, QueryType classifiedType) {
        if (query == null || query.trim().isEmpty()) {
            return classifiedType;
        }
        String q = query.toLowerCase().trim();
        // presence check phrases (Spanish/English) -> BOOLEAN_QUERY
        if (q.contains("confirma si") || q.contains("aparece en el acta") || q.contains("figura como")) {
            log().debug("Classifier override: query matches presence check -> BOOLEAN_QUERY");
            return QueryType.BOOLEAN_QUERY;
        }
        // compare quantity phrases -> COMPARE
        if (q.contains("compara la cantidad de") || q.contains("comparar propuestas") || q.contains("más propuestas en febrero y agosto")) {
            log().debug("Classifier override: query matches compare quantity -> COMPARE");
            return QueryType.COMPARE;
        }
        // agenda-related phrases -> GET_FIELD
        if (q.contains("orden del día") || q.contains("qué contiene el orden") || q.contains("puntos del día") || q.contains("contenido del orden")) {
            log().debug("Classifier override: query matches agenda -> GET_FIELD");
            return QueryType.GET_FIELD;
        }
        // duration-related phrases -> GET_DURATION
        if (q.contains("cuánto tiempo duró") || q.contains("duración de la reunión") || q.contains("cuánto duró") || q.contains("cuanto tiempo duro") || q.contains("duracion de la reunion")) {
            log().debug("Classifier override: query matches duration -> GET_DURATION");
            return QueryType.GET_DURATION;
        }
        // "acuerdos tomados [fecha]", "dime los acuerdos", "acuerdos del/de la reunión" -> DECISION_EXTRACTION (C.1)
        if (q.contains("acuerdos") && (q.contains("tomados") || q.contains("tomadas") || q.contains("de la reunión") || q.contains("del acta") || q.contains("en el acta") || q.contains("dime los acuerdos") || q.contains("lista de acuerdos"))) {
            log().debug("Classifier override: query matches agreements by date -> DECISION_EXTRACTION");
            return QueryType.DECISION_EXTRACTION;
        }
        return classifiedType;
    }

    /** True when we should try the tool path before reasoning (e.g. date-scoped DECISION_EXTRACTION, GET_FIELD). */
    private boolean shouldPreferToolResponse(QueryType queryType, JSONObject nerEntities) {
        if (queryType == null) return false;
        boolean dateScoped = hasDateInNer(nerEntities);
        return dateScoped && (queryType == QueryType.DECISION_EXTRACTION || queryType == QueryType.GET_FIELD);
    }

    private boolean hasDateInNer(JSONObject ner) {
        if (ner == null || ner.isEmpty()) return false;
        for (String key : List.of("date", "fecha", "dates", "fechas", "date_iso")) {
            if (ner.has(key) && ner.get(key) != null && !ner.optString(key).isBlank()) return true;
        }
        return false;
    }

    /** A.5: Heuristic for unacceptable draft (empty, no encontrado, too short). */
    private boolean isDraftAcceptable(DraftAndContext draftAndContext) {
        String draft = draftAndContext.draft();
        if (draft == null || draft.trim().isEmpty()) return false;
        if (draft.trim().length() < 30) return false;
        String lower = draft.toLowerCase();
        if (lower.contains("no puedo encontrar") || lower.contains("no encontrado") || lower.contains("no se encontró") || lower.contains("no hay información")) return false;
        return true;
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
            log().info("fallback_reason=query_type_null. Cannot route to tool.");
            return null;
        }

        Tool tool = toolsConfig.getTool(queryType);
        if (tool == null) {
            log().info("fallback_reason=no_tool_for_type, queryType={}. This may indicate a configuration issue.", queryType);
            return null;
        }
        
        log().info("Routing query to tool: {} for query type: {}. Query: '{}'", 
                  tool.getClass().getSimpleName(), queryType, query.length() > 100 ? query.substring(0, 100) + "..." : query);

        // Retry logic for tool execution
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log().warn("Retry attempt {} for tool: {} (query: '{}')", 
                              attempt, queryType, query.length() > 50 ? query.substring(0, 50) + "..." : query);
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }
                
                log().info("Executing tool: {} (attempt {} of {}) for query type: {}", 
                          queryType, attempt + 1, MAX_RETRIES + 1, queryType);
                long startTime = System.currentTimeMillis();
                ToolResult result = featureConfig.isNerEnabled() ?
                        tool.execute(ToolExecutionContext.of(query, queryType, nerEntities)) :
                        tool.execute(ToolExecutionContext.of(query, queryType));
                long executionTime = System.currentTimeMillis() - startTime;
                log().debug("Tool {} execution completed in {} ms", queryType, executionTime);
                
                if (result != null && result.result() != null && !result.result().trim().isEmpty()) {
                    String originalResult = result.result();
                    log().debug("Tool {} returned result (length: {} chars) on attempt {}. Preview: {}", 
                              queryType, originalResult.length(), attempt + 1,
                              originalResult.length() > 100 ? originalResult.substring(0, 100) + "..." : originalResult);
                    
                    // Validate tool result
                    String validatedResult = responseValidator.validateAndClean(originalResult, "Tool-" + queryType);
                    if (validatedResult != null && !validatedResult.trim().isEmpty()) {
                        log().info("Successfully executed tool {} on attempt {} (execution time: {} ms). " +
                                  "Result length: {} chars, validated length: {} chars", 
                                  queryType, attempt + 1, executionTime, 
                                  originalResult.length(), validatedResult.length());
                        // Create new ToolResult with validated result, preserving original source
                        // Note: Informative messages (like "no documents found") are considered valid responses
                        return new ToolResult(validatedResult, result.source());
                    } else {
                        log().warn("Tool {} returned result that failed validation on attempt {}. " +
                                  "Original result length: {} chars, Validated result: null or empty. " +
                                  "Original preview: {}. This may indicate ResponseValidator rejected the response.",
                                  queryType, attempt + 1, originalResult.length(),
                                  originalResult.length() > 200 ? originalResult.substring(0, 200) + "..." : originalResult);
                        if (attempt < MAX_RETRIES) {
                            continue; // Retry
                        }
                    }
                } else {
                    String resultInfo = result == null ? "null" : 
                                      (result.result() == null ? "result() is null" : 
                                      (result.result().trim().isEmpty() ? "result() is empty" : "unknown"));
                    log().warn("Tool {} returned {} on attempt {} of {}. Tool class: {}. Query: '{}'", 
                              queryType, resultInfo, attempt + 1, MAX_RETRIES + 1, 
                              tool.getClass().getSimpleName(),
                              query.length() > 50 ? query.substring(0, 50) + "..." : query);
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
                if (e instanceof IllegalArgumentException || "DECISION_EXTRACTION".equals(String.valueOf(queryType))) {
                    log().error("Full stack trace for tool {} (queryType={}):", tool.getClass().getSimpleName(), queryType, e);
                }
                if (attempt < MAX_RETRIES) {
                    continue; // Retry
                }
            }
        }
        
        // All retries failed - log structured fallback reason for diagnostics
        if (lastException != null) {
            log().error("Failed to execute tool {} after {} attempts: {}", queryType, MAX_RETRIES + 1, lastException.getMessage());
            log().info("fallback_reason=exception, queryType={}, exception={}", queryType, lastException.getClass().getSimpleName());
            if (lastException instanceof IllegalArgumentException || "DECISION_EXTRACTION".equals(String.valueOf(queryType))) {
                log().error("Stack trace for fallback diagnostics:", lastException);
            }
        } else {
            log().error("Tool {} failed to return valid result after {} attempts", queryType, MAX_RETRIES + 1);
            log().info("fallback_reason=validation_failed, queryType={}", queryType);
        }
        
        return null; // Fall back to direct model query
    }

    private DraftAndContext getDraftForReasoning(String query, JSONObject nerEntities, QueryType queryType, ReasoningPreOutput preOutput) {
        // C.2: When tool-rag is enabled, use tool-rag to choose the tool for the draft (override classifier)
        QueryType draftQueryType = queryType;
        if (featureConfig.isToolRagEnabled() && toolRagService != null) {
            QueryType toolRagType = toolRagService.findBestQueryType(query);
            if (toolRagType != null) {
                draftQueryType = toolRagType;
                log().debug("Tool-rag selected queryType {} for reasoning draft (classifier was {})", draftQueryType, queryType);
            }
        }
        ToolResult toolResult = null;
        if (featureConfig.isToolsEnabled() && draftQueryType != null && meetingMinutesToolsAdapter != null) {
            try {
                toolResult = meetingMinutesToolsAdapter.execute(draftQueryType, query);
            } catch (Exception ignored) {
            }
        }
        if (toolResult == null) {
            toolResult = tryToolRoute(query, nerEntities, queryType);
        }
        if (toolResult != null && toolResult.result() != null && !toolResult.result().trim().isEmpty()) {
            String validated = responseValidator.validateAndClean(toolResult.result(), "Tool-" + draftQueryType);
            if (validated != null && !validated.trim().isEmpty()) {
                return new DraftAndContext(validated, validated);
            }
        }
        return askModelWithPreStep(query, nerEntities, draftQueryType != null ? draftQueryType : queryType, preOutput != null ? preOutput.thoughtOrPlan() : null);
    }

    private DraftAndContext askModelWithPreStep(String query, JSONObject nerEntities, QueryType queryType, String preStepThought) {
        if (!featureConfig.isUseRetrieval()) {
            try {
                String response = chatClient.prompt().user(query).call().content();
                String validated = response != null ? responseValidator.validateAndClean(response, "ProcessQueryService-NoContext") : null;
                return new DraftAndContext(validated != null ? validated : generateNoContextResponse(query), "");
            } catch (Exception e) {
                log().warn("LLM call without context failed in reasoning path: {}", e.getMessage());
                return new DraftAndContext(generateNoContextResponse(query), "");
            }
        }
        String retrievalQuery = (featureConfig.isNerEnabled() && nerQueryEnricher != null && nerEntities != null && !nerEntities.isEmpty())
                ? nerQueryEnricher.buildEnrichedQueryForRetrieval(query, nerEntities)
                : query;
        List<Document> docs;
        try {
            if (retriever instanceof AbstractContextRetriever && nerEntities != null && !nerEntities.isEmpty()) {
                docs = ((AbstractContextRetriever) retriever).retrieveWithMetadataFilters(retrievalQuery, nerEntities);
            } else {
                docs = retriever.retrieve(retrievalQuery);
            }
        } catch (Exception e) {
            log().warn("Retrieval failed in reasoning path: {}", e.getMessage());
            return null;
        }
        if (featureConfig.isPostRetrievalEnabled() && postRetrievalProcessor != null) {
            docs = postRetrievalProcessor.process(docs, query);
        }
        String context = retriever.createContext(docs, query, nerEntities);
        if (context == null || context.trim().isEmpty()) {
            String fallback = generateNoContextResponse(query);
            return new DraftAndContext(fallback, "");
        }
        String contextWithReasoning = context;
        if (preStepThought != null && !preStepThought.trim().isEmpty()) {
            contextWithReasoning = "<Reasoning>\n" + preStepThought.trim() + "\n</Reasoning>\n\n" + context;
        }
        String prompt = String.format(DEFAULT_PROMPT_TEMPLATE,
                queryType != null ? queryType.name() : "UNKNOWN",
                query,
                contextWithReasoning);
        try {
            String response = chatClient.prompt().user(prompt).call().content();
            String validated = responseValidator.validateAndClean(response, "ProcessQueryService");
            if (validated != null) {
                return new DraftAndContext(validated, context);
            }
        } catch (Exception e) {
            log().warn("LLM call failed in reasoning path: {}", e.getMessage());
        }
        return new DraftAndContext(generateNoContextResponse(query), context);
    }

    /**
     * Asks the LLM model with retry logic and response validation.
     * Retries up to MAX_RETRIES times if the response is invalid or an error occurs.
     */
    private String askModel(String query, JSONObject nerEntities, QueryType queryType) {
        // Baseline real (TFG): no retrieval = LLM with question only, no context
        if (!featureConfig.isUseRetrieval()) {
            try {
                String rawContent = chatClient.prompt().user(query).call().content();
                String validated = rawContent != null ? responseValidator.validateAndClean(rawContent, "ProcessQueryService-NoContext") : null;
                if (validated != null && !validated.trim().isEmpty()) {
                    log().info("Response generated without context (use-retrieval=false)");
                    return validated;
                }
            } catch (Exception e) {
                log().warn("LLM call without context failed: {}", e.getMessage());
            }
            return generateNoContextResponse(query);
        }

        // Use QuestionAnswerAdvisor path when useAdvisor and no NER or post-retrieval (advisor injects context from vector store)
        if (featureConfig.isUseAdvisor() && questionAnswerAdvisor != null && !featureConfig.isNerEnabled() && !featureConfig.isPostRetrievalEnabled()) {
            try {
                String rawContent = chatClient.prompt()
                        .user(query)
                        .advisors(questionAnswerAdvisor)
                        .call()
                        .content();
                String validated = rawContent != null ? responseValidator.validateAndClean(rawContent, "ProcessQueryService-Advisor") : null;
                if (validated != null && !validated.trim().isEmpty()) {
                    log().info("Response generated via QuestionAnswerAdvisor (askModel path)");
                    return validated;
                }
            } catch (Exception e) {
                log().warn("QuestionAnswerAdvisor path failed, falling back to manual retrieve+createContext: {}", e.getMessage());
            }
        }

        boolean isProblematicConfig = featureConfig.isMetadataEnabled() &&
                                      featureConfig.isNerEnabled() &&
                                      !featureConfig.isToolsEnabled();

        String retrievalQuery = (featureConfig.isNerEnabled() && nerQueryEnricher != null && nerEntities != null && !nerEntities.isEmpty())
                ? nerQueryEnricher.buildEnrichedQueryForRetrieval(query, nerEntities)
                : query;
        if (!retrievalQuery.equals(query)) {
            log().debug("Using NER-enriched query for retrieval (length {} vs {} chars)", retrievalQuery.length(), query.length());
        }
        List<Document> docs;
        try {
            if (retriever instanceof AbstractContextRetriever && nerEntities != null && !nerEntities.isEmpty()) {
                if (isProblematicConfig) {
                    log().debug("Attempting retrieval with metadata filters and NER entities");
                }
                docs = ((AbstractContextRetriever) retriever).retrieveWithMetadataFilters(retrievalQuery, nerEntities);
                log().info("Using optimized retrieval with metadata filters, retrieved {} documents", docs.size());
            } else {
                if (isProblematicConfig) {
                    log().debug("Using standard retrieval (no metadata filters or NER)");
                }
                docs = retriever.retrieve(retrievalQuery);
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

        if (featureConfig.isPostRetrievalEnabled() && postRetrievalProcessor != null) {
            docs = postRetrievalProcessor.process(docs, query);
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
                String validatedResponse = responseValidator.validateAndClean(response, "ProcessQueryService");
                
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

