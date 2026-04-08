package com.uniovi.rag.service.query;

import com.uniovi.rag.application.exception.RagServiceException;
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
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProcessQueryServiceTest {

    private ExecutionContextFactory executionContextFactory;
    private RagExecutionOrchestrator ragExecutionOrchestrator;
    private ChatClient chatClient;
    private OllamaConnectivityChecker ollamaConnectivityChecker;
    private ProcessQueryService service;

    @BeforeEach
    void setUp() {
        executionContextFactory = mock(ExecutionContextFactory.class);
        ragExecutionOrchestrator = mock(RagExecutionOrchestrator.class);
        chatClient = ChatClientTestSupport.clientWithUserPromptReturning("error-llm-message");
        ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        doNothing().when(ollamaConnectivityChecker).prepareForQuery(any());
        service = new ProcessQueryService(executionContextFactory, ragExecutionOrchestrator, chatClient, ollamaConnectivityChecker);
    }

    private static ResolvedRuntimeConfig minimalResolved(RagConfig rag) {
        CapabilitySet caps = CapabilitySet.fromRagConfig(rag);
        CompatibilityResult ok = CompatibilityResult.ok();
        return new ResolvedRuntimeConfig(
                rag,
                caps,
                ok,
                ReindexImpact.none(),
                new SystemPromptLayers("", "", "", ""),
                "system",
                new ConfigProvenance(null, null, null, List.of(), null, null),
                rag);
    }

    private static ExecutionContext legacyCtx() {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new com.uniovi.rag.configuration.RagFeatureConfiguration(),
                        10,
                        0.7,
                        "m",
                        "e",
                        "c",
                        "SIMPLE");
        return new ExecutionContext(
                null,
                null,
                null,
                "hello",
                RuntimeOperationKind.LEGACY_HTTP,
                minimalResolved(rag),
                "system",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "trace",
                List.of("all"),
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void generateResponse_delegatesToOrchestrator() {
        ExecutionContext ctx = legacyCtx();
        when(executionContextFactory.buildForLegacyHttp(any(), any())).thenReturn(ctx);
        RagExecutionResult result =
                RagExecutionResult.withPlaceholderTrace(
                        "answer",
                        "DirectLlmWorkflow",
                        false,
                        false,
                        List.of(),
                        null,
                        List.of());
        when(ragExecutionOrchestrator.execute(ctx)).thenReturn(result.withFinalTrace(ExecutionTrace.placeholder()));

        QueryResponse response = service.generateResponse("hello", null);
        assertEquals("answer", response.getAnswer());
        verify(ragExecutionOrchestrator).execute(ctx);
    }

    @Test
    void generateResponse_emptyQuery_returnsWithoutOrchestrator() {
        QueryResponse r = service.generateResponse("   ", null);
        assertNotNull(r.getAnswer());
        verify(ragExecutionOrchestrator, never()).execute(any());
    }

    @Test
    void generateResponse_ragServiceExceptionPropagates() {
        ExecutionContext ctx = legacyCtx();
        when(executionContextFactory.buildForLegacyHttp(any(), any())).thenReturn(ctx);
        when(ragExecutionOrchestrator.execute(ctx)).thenThrow(RagServiceException.unsupportedRuntimeConfiguration("x"));
        assertThrows(RagServiceException.class, () -> service.generateResponse("q", null));
    }

    @Test
    void generateResponse_connectivityFailure_throwsRagServiceException() {
        ExecutionContext ctx = legacyCtx();
        when(executionContextFactory.buildForLegacyHttp(any(), any())).thenReturn(ctx);
        when(ragExecutionOrchestrator.execute(ctx))
                .thenThrow(new ResourceAccessException("I/O error", new ConnectException()));
        assertThrows(RagServiceException.class, () -> service.generateResponse("question", null));
    }

    @Test
    void generateResponseForChat_buildsChatContext() {
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
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
                        true,
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
        ExecutionContext ctx =
                new ExecutionContext(
                        uid,
                        pid,
                        conv,
                        "q",
                        RuntimeOperationKind.CHAT_MESSAGE,
                        minimalResolved(rag),
                        "sys",
                        new KnowledgeSnapshotSelection(List.of(UUID.randomUUID()), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                        Optional.empty(),
                        Optional.empty(),
                        "c",
                        List.of("all"),
                        Optional.empty(),
                        Optional.empty());
        when(executionContextFactory.buildForChatMessage(eq(uid), eq(pid), eq(conv), eq("q"), any(), isNull()))
                .thenReturn(ctx);
        RagExecutionResult partial =
                RagExecutionResult.withPlaceholderTrace(
                        "a", "ChunkDenseRagWorkflow", true, false, ctx.knowledgeSnapshotSelection().orderedSnapshotIds(), null, List.of());
        when(ragExecutionOrchestrator.execute(ctx))
                .thenReturn(partial.withFinalTrace(ExecutionTrace.placeholder()));

        QueryResponse r = service.generateResponseForChat("q", null, uid, pid, conv, List.of());
        assertEquals("a", r.getAnswer());
    }
}
