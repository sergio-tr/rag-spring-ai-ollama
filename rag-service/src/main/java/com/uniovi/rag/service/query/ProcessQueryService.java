package com.uniovi.rag.service.query;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionMapper;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.interfaces.rest.support.ConnectivityFailureDetector;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProcessQueryService implements QueryService, Loggable {

    private static final String LOG_STACK_TRACE = "Stack trace:";

    private final ExecutionContextFactory executionContextFactory;
    private final RagExecutionOrchestrator ragExecutionOrchestrator;
    private final RuntimeTracePersistenceService runtimeTracePersistenceService;
    private final ChatClient chatClient;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;

    public ProcessQueryService(
            ExecutionContextFactory executionContextFactory,
            RagExecutionOrchestrator ragExecutionOrchestrator,
            RuntimeTracePersistenceService runtimeTracePersistenceService,
            ChatClient chatClient,
            OllamaConnectivityChecker ollamaConnectivityChecker) {
        this.executionContextFactory = executionContextFactory;
        this.ragExecutionOrchestrator = ragExecutionOrchestrator;
        this.runtimeTracePersistenceService = runtimeTracePersistenceService;
        this.chatClient = chatClient;
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
    }

    @Override
    public QueryResponse generateResponse(String query, String chatModel) {
        try {
            ollamaConnectivityChecker.prepareForQuery(chatModel);
            return executeOrchestrated(executionContextFactory.buildForLegacyHttp(query, chatModel));
        } catch (RagServiceException | ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return handleUnexpected(query, e);
        }
    }

    @Transactional(readOnly = true)
    public QueryResponse generateResponseForChat(
            String query,
            String chatModel,
            UUID userId,
            UUID projectId,
            UUID conversationId,
            List<String> documentFilter) {
        return generateResponseForChat(
                query, chatModel, userId, projectId, conversationId, documentFilter, null);
    }

    /**
     * @param userMessageId optional id of the persisted user message for this turn (used for clarification state).
     */
    @Transactional(readOnly = true)
    public QueryResponse generateResponseForChat(
            String query,
            String chatModel,
            UUID userId,
            UUID projectId,
            UUID conversationId,
            List<String> documentFilter,
            UUID userMessageId) {
        try {
            ollamaConnectivityChecker.prepareForQuery(chatModel);
            ExecutionContext ctx =
                    executionContextFactory.buildForChatMessage(
                            userId,
                            projectId,
                            conversationId,
                            query,
                            documentFilter,
                            chatModel,
                            Optional.ofNullable(userMessageId));
            return executeOrchestrated(ctx);
        } catch (RagServiceException | ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return handleUnexpected(query, e);
        }
    }

    private QueryResponse executeOrchestrated(ExecutionContext ctx) {
        if (ctx.userQuery() == null || ctx.userQuery().trim().isEmpty()) {
            log().warn("Empty query received");
            String errorResponse =
                    generateErrorResponse(ctx.userQuery() != null ? ctx.userQuery() : "", new IllegalArgumentException("empty query"));
            return QueryResponse.fromLLM(errorResponse);
        }
        RagExecutionResult result = ragExecutionOrchestrator.execute(ctx);
        runtimeTracePersistenceService.persistBestEffort(ctx, result.executionTrace());
        log()
                .info(
                        "Runtime workflow={} snapshots={} correlationId={}",
                        result.workflowName(),
                        result.usedKnowledgeSnapshotIds(),
                        ctx.correlationId());
        return RagExecutionMapper.toQueryResponse(result);
    }

    private QueryResponse handleUnexpected(String query, Exception e) {
        if (ConnectivityFailureDetector.isConnectivityFailure(e)) {
            log().warn("Inference backend unreachable: {}", e.getMessage());
            throw RagServiceException.llmUnavailable(e);
        }
        if (ConnectivityFailureDetector.isOllamaModelMissingFailure(e)) {
            log().warn("Required Ollama model missing: {}", e.getMessage());
            throw RagServiceException.ollamaModelNotInstalled(e);
        }
        log().error("Unexpected error processing query: {}", query, e);
        log().error(LOG_STACK_TRACE, e);
        String errorResponse = generateErrorResponse(query, e);
        return QueryResponse.fromLLM(errorResponse);
    }

    private String generateErrorResponse(String query, Throwable cause) {
        if (cause != null && ConnectivityFailureDetector.isConnectivityFailure(cause)) {
            return "The AI inference service is unavailable. Please try again once Ollama is running and reachable.";
        }
        if (cause != null && ConnectivityFailureDetector.isOllamaModelMissingFailure(cause)) {
            return "A required Ollama model is not installed. Pull the chat and embedding models on the Ollama host "
                    + "(ollama pull …) or wait for automatic pull at startup.";
        }

        String prompt = String.format(
                """
                The user asked (in any language): "%s"

                An error occurred while processing this query.

                Respond with a short message in the EXACT SAME LANGUAGE as the question,
                apologizing for the error and asking the user to try again.
                Be concise and polite.
                Do not repeat the question.
                """,
                query != null ? query : "");

        try {
            String response =
                    chatClient
                            .prompt()
                            .user(prompt)
                            .call()
                            .content();

            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            if (ConnectivityFailureDetector.isConnectivityFailure(e)) {
                log().warn("Skipping LLM error message: inference backend unreachable");
                return "The AI inference service is unavailable. Please try again once Ollama is running and reachable.";
            }
            if (ConnectivityFailureDetector.isOllamaModelMissingFailure(e)) {
                log().warn("Skipping LLM error message: Ollama model not installed");
                return "A required Ollama model is not installed. Pull the chat and embedding models on the Ollama host.";
            }
            log().warn("Error generating error response with LLM", e);
        }

        return "I'm sorry, an error occurred while processing your query. Please try again.";
    }
}
