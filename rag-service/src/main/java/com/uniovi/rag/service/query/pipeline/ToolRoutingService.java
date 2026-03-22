package com.uniovi.rag.service.query.pipeline;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Tool routing: two flags in {@link RagFeatureConfiguration} —
 * {@code function-calling} (Spring AI {@code .tools(adapter)}) and {@code tools} (manual execution).
 * <p>Precedence: when function-calling is enabled, {@code .tools()} is used and the {@code tools} flag
 * does not gate that path. When function-calling is off and {@code tools} is on, the deterministic
 * adapter runs, then {@link #tryToolRoute}.</p>
 */
public final class ToolRoutingService {

    private static final Logger log = LoggerFactory.getLogger(ToolRoutingService.class);
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;

    private final RagFeatureConfiguration featureConfig;
    private final RagToolsConfiguration toolsConfig;
    private final MeetingMinutesToolsAdapter meetingMinutesToolsAdapter;
    private final ResponseValidator responseValidator;
    private final ChatRequestSpecFactory chatRequestSpecFactory;

    public ToolRoutingService(
            RagFeatureConfiguration featureConfig,
            RagToolsConfiguration toolsConfig,
            MeetingMinutesToolsAdapter meetingMinutesToolsAdapter,
            ResponseValidator responseValidator,
            ChatRequestSpecFactory chatRequestSpecFactory) {
        this.featureConfig = featureConfig;
        this.toolsConfig = toolsConfig;
        this.meetingMinutesToolsAdapter = meetingMinutesToolsAdapter;
        this.responseValidator = responseValidator;
        this.chatRequestSpecFactory = chatRequestSpecFactory;
    }

    private boolean adapterPathEnabled() {
        return featureConfig.isFunctionCallingEnabled() || featureConfig.isToolsEnabled();
    }

    /**
     * Prefer tool response for date-scoped DECISION_EXTRACTION / GET_FIELD before reasoning.
     * Function-calling first; otherwise manual tools via the adapter.
     */
    public Optional<QueryResponse> tryPreferToolForDate(QueryType queryType, JSONObject nerEntities, String expandedQuery) {
        if (meetingMinutesToolsAdapter == null || !adapterPathEnabled() || !shouldPreferToolResponse(queryType, nerEntities)) {
            return Optional.empty();
        }
        QueryType toolQueryType = queryType;
        try {
            if (featureConfig.isFunctionCallingEnabled()) {
                Optional<QueryResponse> fc = tryFunctionCallingResponse(expandedQuery, toolQueryType, "prefer-tool");
                if (fc.isPresent()) {
                    return fc;
                }
            }
            if (featureConfig.isToolsEnabled() && toolQueryType != null) {
                ToolResult adapterResult = meetingMinutesToolsAdapter.execute(toolQueryType, expandedQuery);
                if (adapterResult != null && adapterResult.result() != null && !adapterResult.result().trim().isEmpty()) {
                    String validated = responseValidator.validateAndClean(adapterResult.result(), "Tool-" + queryType);
                    if (validated != null && !validated.trim().isEmpty()) {
                        log.info("Response generated via tool (prefer-tool path for date-scoped query)");
                        return Optional.of(QueryResponse.fromTool(validated, adapterResult.source() != null ? adapterResult.source() : "tool", toolQueryType));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Prefer-tool path failed, continuing to reasoning: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Main block: {@code .tools(adapter)} first when function-calling is enabled; if there is no result and
     * {@code tools} is enabled, deterministic {@link MeetingMinutesToolsAdapter#execute}.
     */
    public Optional<QueryResponse> tryMainToolsBlock(QueryType queryType, String expandedQuery) {
        if (meetingMinutesToolsAdapter == null) {
            return Optional.empty();
        }
        QueryType toolQueryType = queryType;
        if (featureConfig.isFunctionCallingEnabled()) {
            Optional<QueryResponse> fc = tryFunctionCallingResponse(expandedQuery, toolQueryType, "main-tools");
            if (fc.isPresent()) {
                return fc;
            }
        }
        // Without QueryType the adapter cannot choose a tool; execute(null) only returns an error message
        // and blocks the fallback to LLM+RAG (see evaluation: "No tool available for query type: null").
        if (featureConfig.isToolsEnabled() && toolQueryType != null) {
            try {
                ToolResult adapterResult = meetingMinutesToolsAdapter.execute(toolQueryType, expandedQuery);
                if (adapterResult != null && adapterResult.result() != null && !adapterResult.result().trim().isEmpty()) {
                    String validated = responseValidator.validateAndClean(adapterResult.result(), "Tool-" + queryType);
                    if (validated != null && !validated.trim().isEmpty()) {
                        log.info("Response generated via tool adapter (deterministic path)");
                        return Optional.of(QueryResponse.fromTool(validated, adapterResult.source() != null ? adapterResult.source() : "tool", toolQueryType));
                    }
                }
            } catch (Exception e) {
                log.warn("Tool adapter execution failed: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    private Optional<QueryResponse> tryFunctionCallingResponse(String expandedQuery, QueryType toolQueryType, String logTag) {
        try {
            String content = chatRequestSpecFactory.spec()
                    .user(expandedQuery)
                    .tools(meetingMinutesToolsAdapter)
                    .call()
                    .content();
            if (content != null && !content.trim().isEmpty()) {
                String toolResponse = responseValidator.validateAndClean(content, "Tool-function-calling");
                if (toolResponse != null && !toolResponse.trim().isEmpty()) {
                    log.info("Response generated via ChatClient.tools(adapter) (function-calling path, {})", logTag);
                    return Optional.of(QueryResponse.fromTool(toolResponse, "function-calling", toolQueryType));
                }
            }
        } catch (Exception e) {
            log.warn("ChatClient.tools(adapter) failed ({}): {}", logTag, e.getMessage());
        }
        return Optional.empty();
    }

    public ToolResult tryToolRoute(String query, JSONObject nerEntities, QueryType queryType) {
        if (!featureConfig.isToolsEnabled()) {
            log.debug("Tools (manual registry) disabled, skipping tool routing");
            return null;
        }
        if (queryType == null) {
            log.info("fallback_reason=query_type_null. Cannot route to tool.");
            return null;
        }
        Tool tool = toolsConfig.getTool(queryType);
        if (tool == null) {
            log.info("fallback_reason=no_tool_for_type, queryType={}. This may indicate a configuration issue.", queryType);
            return null;
        }
        log.info("Routing query to tool: {} for query type: {}. Query: '{}'",
                tool.getClass().getSimpleName(), queryType, query.length() > 100 ? query.substring(0, 100) + "..." : query);

        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log.warn("Retry attempt {} for tool: {} (query: '{}')",
                            attempt, queryType, query.length() > 50 ? query.substring(0, 50) + "..." : query);
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }
                log.info("Executing tool: {} (attempt {} of {}) for query type: {}",
                        queryType, attempt + 1, MAX_RETRIES + 1, queryType);
                long startTime = System.currentTimeMillis();
                ToolResult result = featureConfig.isNerEnabled()
                        ? tool.execute(ToolExecutionContext.of(query, queryType, nerEntities))
                        : tool.execute(ToolExecutionContext.of(query, queryType));
                long executionTime = System.currentTimeMillis() - startTime;
                log.debug("Tool {} execution completed in {} ms", queryType, executionTime);

                if (result != null && result.result() != null && !result.result().trim().isEmpty()) {
                    String originalResult = result.result();
                    log.debug("Tool {} returned result (length: {} chars) on attempt {}. Preview: {}",
                            queryType, originalResult.length(), attempt + 1,
                            originalResult.length() > 100 ? originalResult.substring(0, 100) + "..." : originalResult);

                    String validatedResult = responseValidator.validateAndClean(originalResult, "Tool-" + queryType);
                    if (validatedResult != null && !validatedResult.trim().isEmpty()) {
                        log.info("Successfully executed tool {} on attempt {} (execution time: {} ms). " +
                                        "Result length: {} chars, validated length: {} chars",
                                queryType, attempt + 1, executionTime,
                                originalResult.length(), validatedResult.length());
                        return new ToolResult(validatedResult, result.source());
                    } else {
                        log.warn("Tool {} returned result that failed validation on attempt {}. " +
                                        "Original result length: {} chars, Validated result: null or empty. " +
                                        "Original preview: {}. This may indicate ResponseValidator rejected the response.",
                                queryType, attempt + 1, originalResult.length(),
                                originalResult.length() > 200 ? originalResult.substring(0, 200) + "..." : originalResult);
                        if (attempt < MAX_RETRIES) {
                            continue;
                        }
                    }
                } else {
                    String resultInfo = result == null ? "null" :
                            (result.result() == null ? "result() is null" :
                                    (result.result().trim().isEmpty() ? "result() is empty" : "unknown"));
                    log.warn("Tool {} returned {} on attempt {} of {}. Tool class: {}. Query: '{}'",
                            queryType, resultInfo, attempt + 1, MAX_RETRIES + 1,
                            tool.getClass().getSimpleName(),
                            query.length() > 50 ? query.substring(0, 50) + "..." : query);
                    if (attempt < MAX_RETRIES) {
                        continue;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted during tool retry: {}", queryType, e);
                break;
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

                if (errorMsg.contains("duplicate element")) {
                    log.warn("Duplicate element error detected for tool {} on attempt {}: {}. Skipping retries as this won't resolve with retry.",
                            queryType, attempt + 1, e.getMessage());
                    break;
                }

                log.warn("Error executing tool {} on attempt {}: {}", queryType, attempt + 1, e.getMessage());
                if (e instanceof IllegalArgumentException || "DECISION_EXTRACTION".equals(String.valueOf(queryType))) {
                    log.error("Full stack trace for tool {} (queryType={}):", tool.getClass().getSimpleName(), queryType, e);
                }
                if (attempt < MAX_RETRIES) {
                    continue;
                }
            }
        }

        if (lastException != null) {
            log.error("Failed to execute tool {} after {} attempts: {}", queryType, MAX_RETRIES + 1, lastException.getMessage());
            log.info("fallback_reason=exception, queryType={}, exception={}", queryType, lastException.getClass().getSimpleName());
            if (lastException instanceof IllegalArgumentException || "DECISION_EXTRACTION".equals(String.valueOf(queryType))) {
                log.error("Stack trace for fallback diagnostics:", lastException);
            }
        } else {
            log.error("Tool {} failed to return valid result after {} attempts", queryType, MAX_RETRIES + 1);
            log.info("fallback_reason=validation_failed, queryType={}", queryType);
        }

        return null;
    }

    private static boolean shouldPreferToolResponse(QueryType queryType, JSONObject nerEntities) {
        if (queryType == null) {
            return false;
        }
        boolean dateScoped = hasDateInNer(nerEntities);
        return dateScoped && (queryType == QueryType.DECISION_EXTRACTION || queryType == QueryType.GET_FIELD);
    }

    private static boolean hasDateInNer(JSONObject ner) {
        if (ner == null || ner.isEmpty()) {
            return false;
        }
        for (String key : List.of("date", "fecha", "dates", "fechas", "date_iso")) {
            if (ner.has(key) && ner.get(key) != null && !ner.optString(key).isBlank()) {
                return true;
            }
        }
        return false;
    }
}
