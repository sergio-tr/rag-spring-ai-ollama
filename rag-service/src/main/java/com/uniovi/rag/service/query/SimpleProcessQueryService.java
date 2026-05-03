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
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SimpleProcessQueryService implements QueryService, Loggable {

    private final ExecutionContextFactory executionContextFactory;
    private final RagExecutionOrchestrator ragExecutionOrchestrator;
    private final RuntimeTracePersistenceService runtimeTracePersistenceService;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;

    public SimpleProcessQueryService(
            ExecutionContextFactory executionContextFactory,
            RagExecutionOrchestrator ragExecutionOrchestrator,
            RuntimeTracePersistenceService runtimeTracePersistenceService,
            OllamaConnectivityChecker ollamaConnectivityChecker) {
        this.executionContextFactory = executionContextFactory;
        this.ragExecutionOrchestrator = ragExecutionOrchestrator;
        this.runtimeTracePersistenceService = runtimeTracePersistenceService;
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
    }

    @Override
    public QueryResponse generateResponse(String query, String chatModel) {
        try {
            ollamaConnectivityChecker.prepareForQuery(chatModel);
            ExecutionContext ctx = executionContextFactory.buildForLegacyHttp(query, chatModel);
            RagExecutionResult result = ragExecutionOrchestrator.execute(ctx);
            runtimeTracePersistenceService.persistBestEffort(ctx, result.executionTrace());
            return RagExecutionMapper.toQueryResponse(result);
        } catch (RagServiceException | ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            if (ConnectivityFailureDetector.isConnectivityFailure(e)) {
                throw RagServiceException.llmUnavailable(e);
            }
            if (ConnectivityFailureDetector.isOllamaModelMissingFailure(e)) {
                throw RagServiceException.ollamaModelNotInstalled(e);
            }
            log().error("SimpleProcessQueryService error", e);
            throw e;
        }
    }
}
