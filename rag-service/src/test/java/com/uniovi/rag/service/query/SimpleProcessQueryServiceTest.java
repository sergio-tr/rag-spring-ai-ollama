package com.uniovi.rag.service.query;

import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SimpleProcessQueryServiceTest {

    private ExecutionContextFactory executionContextFactory;
    private RagExecutionOrchestrator ragExecutionOrchestrator;
    private OllamaConnectivityChecker ollamaConnectivityChecker;
    private SimpleProcessQueryService service;

    @BeforeEach
    void setUp() {
        executionContextFactory = mock(ExecutionContextFactory.class);
        ragExecutionOrchestrator = mock(RagExecutionOrchestrator.class);
        ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        doNothing().when(ollamaConnectivityChecker).prepareForQuery(any());
        service = new SimpleProcessQueryService(executionContextFactory, ragExecutionOrchestrator, ollamaConnectivityChecker);
    }

    private static ResolvedRuntimeConfig minimalResolved() {
        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        5,
                        0.2,
                        "llm",
                        "emb",
                        "cls",
                        "reason",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        return new ResolvedRuntimeConfig(
                rag,
                CapabilitySet.fromRagConfig(rag),
                CompatibilityResult.ok(),
                ReindexImpact.none(),
                new SystemPromptLayers("", "", "", ""),
                "sys",
                new ConfigProvenance(null, null, null, List.of(), null, null),
                rag);
    }

    @Test
    void generateResponse_delegatesToOrchestrator() {
        ExecutionContext ctx =
                new ExecutionContext(
                        null,
                        null,
                        null,
                        "q",
                        RuntimeOperationKind.LEGACY_HTTP,
                        minimalResolved(),
                        "sys",
                        KnowledgeSnapshotSelection.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        "t",
                        List.of("all"),
                        Optional.empty(),
                        Optional.empty());
        when(executionContextFactory.buildForLegacyHttp(any(), any())).thenReturn(ctx);
        RagExecutionResult partial =
                RagExecutionResult.withPlaceholderTrace(
                        "ans", "DirectLlmWorkflow", false, false, List.of(), null, List.of());
        when(ragExecutionOrchestrator.execute(ctx)).thenReturn(partial.withFinalTrace(ExecutionTrace.placeholder()));

        QueryResponse r = service.generateResponse("q", null);
        assertEquals("ans", r.getAnswer());
    }
}
