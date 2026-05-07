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
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.interfaces.rest.support.ConnectivityFailureDetector;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProcessQueryService implements QueryService, Loggable {

    private static final String LOG_STACK_TRACE = "Stack trace:";
    private static final String LOG_EMPTY_QUERY_RECEIVED = "Empty query received";
    private static final String ERR_EMPTY_QUERY = "empty query";

    private static final String LOG_REQUIRED_OLLAMA_MODEL_MISSING = "Required Ollama model missing: {}";

    private final ExecutionContextFactory executionContextFactory;
    private final RagExecutionOrchestrator ragExecutionOrchestrator;
    private final RuntimeTracePersistenceService runtimeTracePersistenceService;
    private final ChatClient chatClient;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ObjectProvider<ProcessQueryService> selfProvider;

    public ProcessQueryService(
            ExecutionContextFactory executionContextFactory,
            RagExecutionOrchestrator ragExecutionOrchestrator,
            RuntimeTracePersistenceService runtimeTracePersistenceService,
            ChatClient chatClient,
            OllamaConnectivityChecker ollamaConnectivityChecker,
            KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this(
                executionContextFactory,
                ragExecutionOrchestrator,
                runtimeTracePersistenceService,
                chatClient,
                ollamaConnectivityChecker,
                knowledgeDocumentRepository,
                null);
    }

    @Autowired
    public ProcessQueryService(
            ExecutionContextFactory executionContextFactory,
            RagExecutionOrchestrator ragExecutionOrchestrator,
            RuntimeTracePersistenceService runtimeTracePersistenceService,
            ChatClient chatClient,
            OllamaConnectivityChecker ollamaConnectivityChecker,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            ObjectProvider<ProcessQueryService> selfProvider) {
        this.executionContextFactory = executionContextFactory;
        this.ragExecutionOrchestrator = ragExecutionOrchestrator;
        this.runtimeTracePersistenceService = runtimeTracePersistenceService;
        this.chatClient = chatClient;
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.selfProvider =
                selfProvider != null
                        ? selfProvider
                        : new ObjectProvider<>() {
                            @Override
                            public ProcessQueryService getObject() {
                                return ProcessQueryService.this;
                            }

                            @Override
                            public ProcessQueryService getObject(Object... args) {
                                return ProcessQueryService.this;
                            }

                            @Override
                            public ProcessQueryService getIfAvailable() {
                                return ProcessQueryService.this;
                            }
                        };
    }

    @Override
    public QueryResponse generateResponse(String query, String chatModel) {
        try {
            if (query == null || query.trim().isEmpty()) {
                log().warn(LOG_EMPTY_QUERY_RECEIVED);
                String errorResponse = generateErrorResponse("", new IllegalArgumentException(ERR_EMPTY_QUERY));
                return QueryResponse.fromLLM(errorResponse);
            }
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
        ProcessQueryService self = selfProvider.getIfAvailable();
        return self.generateResponseForChat(
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
            if (query == null || query.trim().isEmpty()) {
                log().warn(LOG_EMPTY_QUERY_RECEIVED);
                String errorResponse = generateErrorResponse("", new IllegalArgumentException(ERR_EMPTY_QUERY));
                return QueryResponse.fromLLM(errorResponse);
            }
            ollamaConnectivityChecker.prepareForQuery(chatModel);
            validateChatDocumentFilter(projectId, documentFilter);
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

    /**
     * When the UI limits retrieval to explicit document ids, ensure those ids exist in the project before running RAG.
     */
    private void validateChatDocumentFilter(UUID projectId, List<String> documentFilter) {
        if (documentFilter == null || documentFilter.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(documentFilter.size());
        for (String raw : documentFilter) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ex) {
                throw RagServiceException.chatDocumentFilterInvalid();
            }
        }
        if (ids.isEmpty()) {
            throw RagServiceException.chatDocumentScopeEmpty();
        }
        if (knowledgeDocumentRepository == null) {
            return;
        }
        long matches = knowledgeDocumentRepository.countByProject_IdAndIdIn(projectId, ids);
        if (matches == 0) {
            throw RagServiceException.chatDocumentScopeEmpty();
        }
    }

    private QueryResponse executeOrchestrated(ExecutionContext ctx) {
        if (ctx == null) {
            log().warn("ExecutionContext is null");
            String errorResponse = generateErrorResponse("", new IllegalArgumentException("missing execution context"));
            return QueryResponse.fromLLM(errorResponse);
        }
        if (ctx.userQuery() == null || ctx.userQuery().trim().isEmpty()) {
            log().warn(LOG_EMPTY_QUERY_RECEIVED);
            String errorResponse =
                    generateErrorResponse(
                            ctx.userQuery() != null ? ctx.userQuery() : "",
                            new IllegalArgumentException(ERR_EMPTY_QUERY));
            return QueryResponse.fromLLM(errorResponse);
        }
        RagExecutionResult result = ragExecutionOrchestrator.execute(ctx);
        runtimeTracePersistenceService.persistBestEffort(ctx, result.executionTrace());
        boolean useRetrieval = ctx.resolved() != null && ctx.resolved().toRagConfig().useRetrieval();
        int denseCandidateCount =
                result.retrievalDiagnostics().map(d -> d.denseCandidateCount()).orElse(0);
        int afterFilterCount =
                result.retrievalDiagnostics().map(d -> d.afterFilterCount()).orElse(0);
        log()
                .info(
                        "chat_runtime_context conversationId={} projectId={} correlationId={} useRetrieval={} documentFilterCount={} denseCandidateCount={} afterFilterCount={} retrievedChunks={} sourceCount={}",
                        ctx.conversationId(),
                        ctx.projectId(),
                        ctx.correlationId(),
                        useRetrieval,
                        ctx.documentFilter() != null ? ctx.documentFilter().size() : 0,
                        denseCandidateCount,
                        afterFilterCount,
                        result.retrievalDiagnostics().map(d -> d.afterCompressionCount()).orElse(0),
                        result.responseSources() != null ? result.responseSources().size() : 0);
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
        if (ConnectivityFailureDetector.isContextLimitFailure(e)) {
            log().warn("LLM context limit exceeded: {}", e.getMessage());
            throw RagServiceException.llmContextLimitExceeded(e);
        }
        if (ConnectivityFailureDetector.isOllamaModelMissingFailure(e)) {
            log().warn(LOG_REQUIRED_OLLAMA_MODEL_MISSING, e.getMessage());
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
