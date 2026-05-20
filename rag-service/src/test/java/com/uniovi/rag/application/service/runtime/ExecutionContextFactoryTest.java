package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationBootstrap;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStateResolver;
import com.uniovi.rag.application.service.runtime.memory.ConversationMemoryStrategy;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryExecutionResult;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.application.service.config.ChatScopedRagConfigResolver;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionContextFactoryTest {

    @Mock private RuntimeConfigResolutionService runtimeConfigResolutionService;
    @Mock private KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector;
    @Mock private ChatScopedRagConfigResolver chatScopedRagConfigResolver;
    @Mock private ModelCatalogPort modelCatalogPort;
    @Mock private ClarificationStateResolver clarificationStateResolver;
    @Mock private ConversationMemoryStrategy conversationMemoryStrategy;
    @Mock private ResolvedRuntimeConfig resolvedRuntimeConfig;
    @Mock private QueryPlan queryPlan;
    @Mock private PackedContextSet packedContextSet;

    private ExecutionContextFactory factory;

    @AfterEach
    void clearBenchmarkContext() {
        LabBenchmarkExecutionContext.clear();
    }

    @BeforeEach
    void setUp() {
        factory =
                new ExecutionContextFactory(
                        runtimeConfigResolutionService,
                        knowledgeRuntimeSnapshotSelector,
                        chatScopedRagConfigResolver,
                        modelCatalogPort,
                        clarificationStateResolver,
                        conversationMemoryStrategy,
                        null);
    }

    @Test
    void attachQueryPlan_rejectsNullContext() {
        assertThatThrownBy(() -> factory.attachQueryPlan(null, queryPlan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ctx");
    }

    @Test
    void attachQueryPlan_rejectsNullPlan() {
        ExecutionContext ctx = minimalContextWithoutPlan();
        assertThatThrownBy(() -> factory.attachQueryPlan(ctx, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan");
    }

    @Test
    void attachQueryPlan_rejectsWhenPlanAlreadyPresent() {
        ExecutionContext ctx = minimalContextWithoutPlan();
        ExecutionContext once = factory.attachQueryPlan(ctx, queryPlan);
        assertThatThrownBy(() -> factory.attachQueryPlan(once, queryPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already contains");
    }

    @Test
    void attachQueryPlan_attachesPlan() {
        ExecutionContext ctx = minimalContextWithoutPlan();
        ExecutionContext out = factory.attachQueryPlan(ctx, queryPlan);
        assertThat(out.queryPlan()).contains(queryPlan);
    }

    @Test
    void attachAdvisorPackedContextSet_requiresQueryPlanFirst() {
        ExecutionContext ctx = minimalContextWithoutPlan();
        assertThatThrownBy(() -> factory.attachAdvisorPackedContextSet(ctx, packedContextSet))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QueryPlan");
    }

    @Test
    void attachAdvisorPackedContextSet_attachesAfterPlan() {
        ExecutionContext ctx = minimalContextWithoutPlan();
        ExecutionContext withPlan = factory.attachQueryPlan(ctx, queryPlan);
        ExecutionContext out = factory.attachAdvisorPackedContextSet(withPlan, packedContextSet);
        assertThat(out.advisorPackedContextSet()).contains(packedContextSet);
    }

    @Test
    void buildForChatMessage_wiresResolutionSnapshotAndMemory() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        when(resolvedRuntimeConfig.toRagConfig()).thenReturn(rag);
        when(resolvedRuntimeConfig.effectiveSystemPrompt()).thenReturn("system-prompt");
        when(runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        any(), any(), any(), anyString()))
                .thenReturn(resolvedRuntimeConfig);
        when(knowledgeRuntimeSnapshotSelector.select(projectId, conversationId))
                .thenReturn(KnowledgeSnapshotSelection.empty());
        when(chatScopedRagConfigResolver.mergedConversationConfigAsJson(conversationId))
                .thenReturn(null);
        when(clarificationStateResolver.bootstrap(conversationId, "hello"))
                .thenReturn(new ClarificationBootstrap("hello", false, false, false));
        when(conversationMemoryStrategy.execute(any(ExecutionContext.class), anyString()))
                .thenReturn(
                        new ConversationMemoryExecutionResult(
                                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                                Optional.empty(),
                                false,
                                false,
                                false,
                                "hello",
                                List.of()));

        ExecutionContext ctx =
                factory.buildForChatMessage(
                        userId, projectId, conversationId, "hello", List.of(), null);

        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.projectId()).isEqualTo(projectId);
        assertThat(ctx.conversationId()).isEqualTo(conversationId);
        assertThat(ctx.userQuery()).isEqualTo("hello");
        assertThat(ctx.effectiveSystemPrompt()).isEqualTo("system-prompt");
        assertThat(ctx.resolved()).isSameAs(resolvedRuntimeConfig);
    }

    @Test
    void buildForHttpQuery_usesAllDocumentsFilter() {
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
                        false,
                        false,
                        false,
                        false,
                        5,
                        0.5,
                        "m",
                        "e",
                        "c",
                        "SIMPLE",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        when(resolvedRuntimeConfig.toRagConfig()).thenReturn(rag);
        when(resolvedRuntimeConfig.effectiveSystemPrompt()).thenReturn("sys");
        when(runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        isNull(), isNull(), isNull(), anyString()))
                .thenReturn(resolvedRuntimeConfig);
        when(knowledgeRuntimeSnapshotSelector.select(null, null))
                .thenReturn(KnowledgeSnapshotSelection.empty());
        when(clarificationStateResolver.bootstrap(null, "q"))
                .thenReturn(new ClarificationBootstrap("q", false, false, false));
        when(conversationMemoryStrategy.execute(any(ExecutionContext.class), anyString()))
                .thenReturn(
                        new ConversationMemoryExecutionResult(
                                ConversationMemoryOutcome.NO_CONVERSATION_SCOPE,
                                Optional.empty(),
                                false,
                                false,
                                false,
                                "q",
                                List.of()));

        ExecutionContext ctx = factory.buildForHttpQuery("q", null);
        assertThat(ctx.documentFilter()).containsExactly(RagExecutionContext.ALL_DOCUMENTS);
    }

    @Test
    void buildForHttpQuery_passes_benchmark_terminal_override_when_present() throws Exception {
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
                        false,
                        false,
                        false,
                        false,
                        5,
                        0.5,
                        "m",
                        "e",
                        "c",
                        "SIMPLE",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        when(resolvedRuntimeConfig.toRagConfig()).thenReturn(rag);
        when(resolvedRuntimeConfig.effectiveSystemPrompt()).thenReturn("sys");
        ObjectNode terminal = JsonNodeFactory.instance.objectNode();
        terminal.put("useRetrieval", false);
        try (AutoCloseable ignored = LabBenchmarkExecutionContext.open(terminal)) {
            when(runtimeConfigResolutionService.resolveForOrchestratedExecute(
                            isNull(), isNull(), eq(terminal), anyString()))
                    .thenReturn(resolvedRuntimeConfig);
            when(knowledgeRuntimeSnapshotSelector.select(null, null))
                    .thenReturn(KnowledgeSnapshotSelection.empty());
            when(clarificationStateResolver.bootstrap(null, "q"))
                    .thenReturn(new ClarificationBootstrap("q", false, false, false));
            when(conversationMemoryStrategy.execute(any(ExecutionContext.class), anyString()))
                    .thenReturn(
                            new ConversationMemoryExecutionResult(
                                    ConversationMemoryOutcome.NO_CONVERSATION_SCOPE,
                                    Optional.empty(),
                                    false,
                                    false,
                                    false,
                                    "q",
                                    List.of()));

            factory.buildForHttpQuery("q", null);
        }
    }

    @Test
    void buildForHttpQuery_labContext_forces_explicit_snapshot_ids_over_active_selection() throws Exception {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        when(resolvedRuntimeConfig.toRagConfig()).thenReturn(rag);
        when(resolvedRuntimeConfig.effectiveSystemPrompt()).thenReturn("sys");
        when(clarificationStateResolver.bootstrap(null, "q"))
                .thenReturn(new ClarificationBootstrap("q", false, false, false));
        when(conversationMemoryStrategy.execute(any(ExecutionContext.class), anyString()))
                .thenReturn(
                        new ConversationMemoryExecutionResult(
                                ConversationMemoryOutcome.NO_CONVERSATION_SCOPE,
                                Optional.empty(),
                                false,
                                false,
                                false,
                                "q",
                                List.of()));

        UUID projectId = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        when(knowledgeRuntimeSnapshotSelector.selectExplicit(projectId, List.of(s1)))
                .thenReturn(new KnowledgeSnapshotSelection(List.of(s1), Optional.of(s1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

        when(runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        isNull(), eq(projectId), isNull(), anyString()))
                .thenReturn(resolvedRuntimeConfig);

        try (AutoCloseable ignored =
                LabBenchmarkExecutionContext.openLab(
                        null, UUID.randomUUID(), projectId, List.of(s1), "HYBRID_METADATA", "P8", true)) {
            ExecutionContext ctx = factory.buildForHttpQuery("q", null);
            assertThat(ctx.projectId()).isEqualTo(projectId);
            assertThat(ctx.knowledgeSnapshotSelection().orderedSnapshotIds()).containsExactly(s1);
        }
    }

    @Test
    void buildForChatMessage_invalidModelOverride_throwsBadRequest() {
        when(modelCatalogPort.allowedLlmNamesInGovernance()).thenReturn(Set.of("allowed-only"));
        UUID conversationId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                factory.buildForChatMessage(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        conversationId,
                                        "x",
                                        List.of(),
                                        "unknown-model"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private ExecutionContext minimalContextWithoutPlan() {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.5, "m", "e", "c", "SIMPLE");
        lenient().when(resolvedRuntimeConfig.toRagConfig()).thenReturn(rag);
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolvedRuntimeConfig,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                "",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }
}
