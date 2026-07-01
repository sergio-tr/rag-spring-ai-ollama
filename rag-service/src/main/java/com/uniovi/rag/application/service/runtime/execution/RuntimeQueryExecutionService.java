package com.uniovi.rag.application.service.runtime.execution;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.application.exception.llm.LlmProviderException;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmException;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionMapper;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.interfaces.rest.support.ConnectivityFailureDetector;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.application.service.llm.LlmErrorComposer;
import com.uniovi.rag.application.service.llm.LlmFallbackErrorComposer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical query/chat execution entry point: builds {@link ExecutionContext} and delegates to
 * {@link RagExecutionOrchestrator} (single runtime center).
 */
@Service
public class RuntimeQueryExecutionService implements QueryExecutionService, Loggable {

    private static final String LOG_STACK_TRACE = "Stack trace:";
    private static final String LOG_EMPTY_QUERY_RECEIVED = "Empty query received";
    private static final String ERR_EMPTY_QUERY = "empty query";

    private static final String LOG_REQUIRED_OLLAMA_MODEL_MISSING = "Required Ollama model missing: {}";

    private final ExecutionContextFactory executionContextFactory;
    private final RagExecutionOrchestrator ragExecutionOrchestrator;
    private final RuntimeTracePersistenceService runtimeTracePersistenceService;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ObjectProvider<RuntimeQueryExecutionService> selfProvider;
    private final ObjectProvider<RuntimeObservability> runtimeObservability;
    private final ChatGenerationModelSelector chatGenerationModelSelector;
    private final LlmErrorComposer llmErrorComposer;

    public RuntimeQueryExecutionService(
            ExecutionContextFactory executionContextFactory,
            RagExecutionOrchestrator ragExecutionOrchestrator,
            RuntimeTracePersistenceService runtimeTracePersistenceService,
            OllamaConnectivityChecker ollamaConnectivityChecker,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            ChatGenerationModelSelector chatGenerationModelSelector,
            LlmErrorComposer llmErrorComposer) {
        this(
                executionContextFactory,
                ragExecutionOrchestrator,
                runtimeTracePersistenceService,
                ollamaConnectivityChecker,
                knowledgeDocumentRepository,
                chatGenerationModelSelector,
                llmErrorComposer,
                null,
                null);
    }

    @Autowired
    public RuntimeQueryExecutionService(
            ExecutionContextFactory executionContextFactory,
            RagExecutionOrchestrator ragExecutionOrchestrator,
            RuntimeTracePersistenceService runtimeTracePersistenceService,
            OllamaConnectivityChecker ollamaConnectivityChecker,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            ChatGenerationModelSelector chatGenerationModelSelector,
            LlmErrorComposer llmErrorComposer,
            ObjectProvider<RuntimeQueryExecutionService> selfProvider,
            ObjectProvider<RuntimeObservability> runtimeObservability) {
        this.executionContextFactory = executionContextFactory;
        this.ragExecutionOrchestrator = ragExecutionOrchestrator;
        this.runtimeTracePersistenceService = runtimeTracePersistenceService;
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.chatGenerationModelSelector = chatGenerationModelSelector;
        this.llmErrorComposer = llmErrorComposer;
        this.runtimeObservability = runtimeObservability;
        this.selfProvider =
                selfProvider != null
                        ? selfProvider
                        : new ObjectProvider<>() {
                            @Override
                            public RuntimeQueryExecutionService getObject() {
                                return RuntimeQueryExecutionService.this;
                            }

                            @Override
                            public RuntimeQueryExecutionService getObject(Object... args) {
                                return RuntimeQueryExecutionService.this;
                            }

                            @Override
                            public RuntimeQueryExecutionService getIfAvailable() {
                                return RuntimeQueryExecutionService.this;
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
            ExecutionContext ctx = executionContextFactory.buildForHttpQuery(query, chatModel);
            try {
                prepareOllamaForContext(ctx);
                return executeOrchestrated(ctx);
            } finally {
                OrchestrationLlmConfigScope.clear();
            }
        } catch (RagServiceException | ResponseStatusException | LlmProviderException | OpenAiCompatibleLlmException e) {
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
        RuntimeQueryExecutionService self = selfProvider.getIfAvailable();
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
            try {
                prepareOllamaForContext(ctx);
                RuntimeObservability obs = runtimeObservability != null ? runtimeObservability.getIfAvailable() : null;
                if (obs != null) {
                    return obs.chatGenerate(ctx, () -> executeOrchestrated(ctx));
                }
                return executeOrchestrated(ctx);
            } finally {
                OrchestrationLlmConfigScope.clear();
            }
        } catch (RagServiceException | ResponseStatusException | LlmProviderException | OpenAiCompatibleLlmException e) {
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
        RuntimeObservability obs = runtimeObservability != null ? runtimeObservability.getIfAvailable() : null;
        if (obs != null && result.executionTrace() != null) {
            obs.sourcesAttribute(result.executionTrace());
        }
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

    private void prepareOllamaForContext(ExecutionContext ctx) {
        String chatModel = chatGenerationModelSelector.effectiveChatModelId(ctx).orElse(null);
        boolean requireOllamaChat =
                OrchestrationLlmConfigScope.current()
                        .map(ResolvedLlmConfig::requiresOllamaNativeChat)
                        .orElseGet(
                                () ->
                                        ollamaConnectivityChecker.requiresOllamaChatForDefaults());
        boolean requireOllamaEmbedding =
                OrchestrationLlmConfigScope.current()
                        .map(ResolvedLlmConfig::requiresOllamaNativeEmbedding)
                        .orElseGet(
                                () ->
                                        ollamaConnectivityChecker.requiresOllamaEmbeddingForDefaults());
        ollamaConnectivityChecker.prepareForQuery(chatModel, requireOllamaChat, requireOllamaEmbedding);
    }

    private String generateErrorResponse(String query, Exception e) {
        return llmErrorComposer != null
                ? llmErrorComposer.composeApologyForQueryFailure(query, e)
                : LlmFallbackErrorComposer.genericApology();
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
        String errorResponse =
                llmErrorComposer != null
                        ? llmErrorComposer.composeApologyForQueryFailure(query, e)
                        : LlmFallbackErrorComposer.genericApology();
        return QueryResponse.fromLLM(errorResponse);
    }
}
