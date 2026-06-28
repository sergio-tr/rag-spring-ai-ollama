package com.uniovi.rag.application.service.runtime.memory;
import com.uniovi.rag.testsupport.ConversationRecallGuardTestSupport;

import com.uniovi.rag.application.service.runtime.DirectLlmWorkflow;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.WorkflowSelector;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorPolicyResolver;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorStrategy;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStrategy;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingPolicyResolver;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingStrategy;
import com.uniovi.rag.application.service.runtime.judge.JudgeStrategy;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.reasoning.AnswerVerificationService;
import com.uniovi.rag.application.service.runtime.reasoning.StructuredAnswerPlanService;
import com.uniovi.rag.application.service.runtime.routing.AdaptiveRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicRouteSafetyTestSupport;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrossConversationIsolationTest {

    @Test
    void newConversation_doesNotRetrieveCorpus_forPriorConversationRecall() {
        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        ClarificationPolicyResolver clarificationPolicy = mock(ClarificationPolicyResolver.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenAnswer(
                        inv ->
                                new JudgeExecutionResult(
                                        false,
                                        JudgeOutcome.NOT_ATTEMPTED,
                                        false,
                                        false,
                                        false,
                                        inv.getArgument(5),
                                        false,
                                        List.of()));

        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        mock(DirectLlmWorkflow.class),
                        qu,
                        factory,
                        mock(DeterministicToolStrategy.class),
                        mock(FunctionCallingPolicyResolver.class),
                        mock(FunctionCallingStrategy.class),
                        mock(AdvisorPolicyResolver.class),
                        mock(AdvisorStrategy.class),
                        clarificationPolicy,
                        mock(ClarificationStrategy.class),
                        routingStrategy,
                        MonotonicRouteSafetyTestSupport.deterministicToolRoutingStrategy(),
                        mock(FunctionCallingRoutingStrategy.class),
                        mock(AdvisorRoutingStrategy.class),
                        judgeStrategy,
                        mock(StructuredAnswerPlanService.class),
                        mock(AnswerVerificationService.class),
                        mock(ObjectProvider.class),
                        MonotonicRouteSafetyTestSupport.permissiveSafety(),
                        mock(ObjectProvider.class),
                        mock(ObjectProvider.class),
                        ConversationRecallGuardTestSupport.withEmptyHistory());

        ExecutionContext in = ctx("¿De qué hablamos antes?");
        QueryPlan plan = mock(QueryPlan.class);
        when(plan.pipelineNotes()).thenReturn(List.of());
        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenReturn(in);
        when(clarificationPolicy.resolve(any(), any()))
                .thenReturn(
                        new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));

        var out = orchestrator.execute(in);
        ExecutionTrace trace = out.executionTrace();

        assertThat(out.answerText())
                .containsIgnoringCase("no hemos")
                .containsIgnoringCase("primera");
        assertThat(trace.stages()).extracting(ExecutionStageTrace::stageName).contains("memory_recall_guard");
        verify(workflowSelector, never()).select(any());
        verify(routingStrategy, never()).execute(any(), any());
    }

    @Test
    void newConversation_memoryDisabled_doesNotRetrieveCorpus_forPriorConversationRecall() {
        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        ClarificationPolicyResolver clarificationPolicy = mock(ClarificationPolicyResolver.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenAnswer(
                        inv ->
                                new JudgeExecutionResult(
                                        false,
                                        JudgeOutcome.NOT_ATTEMPTED,
                                        false,
                                        false,
                                        false,
                                        inv.getArgument(5),
                                        false,
                                        List.of()));

        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        mock(DirectLlmWorkflow.class),
                        qu,
                        factory,
                        mock(DeterministicToolStrategy.class),
                        mock(FunctionCallingPolicyResolver.class),
                        mock(FunctionCallingStrategy.class),
                        mock(AdvisorPolicyResolver.class),
                        mock(AdvisorStrategy.class),
                        clarificationPolicy,
                        mock(ClarificationStrategy.class),
                        routingStrategy,
                        MonotonicRouteSafetyTestSupport.deterministicToolRoutingStrategy(),
                        mock(FunctionCallingRoutingStrategy.class),
                        mock(AdvisorRoutingStrategy.class),
                        judgeStrategy,
                        mock(StructuredAnswerPlanService.class),
                        mock(AnswerVerificationService.class),
                        mock(ObjectProvider.class),
                        MonotonicRouteSafetyTestSupport.permissiveSafety(),
                        mock(ObjectProvider.class),
                        mock(ObjectProvider.class),
                        ConversationRecallGuardTestSupport.withEmptyHistory());

        ExecutionContext in = ctxMemoryDisabled("¿De qué hablamos antes?");
        QueryPlan plan = mock(QueryPlan.class);
        when(plan.pipelineNotes()).thenReturn(List.of());
        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenReturn(in);
        when(clarificationPolicy.resolve(any(), any()))
                .thenReturn(
                        new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));

        var out = orchestrator.execute(in);

        assertThat(out.answerText()).containsIgnoringCase("no hemos");
        assertThat(out.executionTrace().stages()).extracting(ExecutionStageTrace::stageName).contains("memory_recall_guard");
        verify(workflowSelector, never()).select(any());
    }

    @Test
    void isolatedConversation_ambiguousParticipants_asksForActaDate_notCorpus() {
        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        ClarificationPolicyResolver clarificationPolicy = mock(ClarificationPolicyResolver.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenAnswer(
                        inv ->
                                new JudgeExecutionResult(
                                        false,
                                        JudgeOutcome.NOT_ATTEMPTED,
                                        false,
                                        false,
                                        false,
                                        inv.getArgument(5),
                                        false,
                                        List.of()));

        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        mock(DirectLlmWorkflow.class),
                        qu,
                        factory,
                        mock(DeterministicToolStrategy.class),
                        mock(FunctionCallingPolicyResolver.class),
                        mock(FunctionCallingStrategy.class),
                        mock(AdvisorPolicyResolver.class),
                        mock(AdvisorStrategy.class),
                        clarificationPolicy,
                        mock(ClarificationStrategy.class),
                        routingStrategy,
                        MonotonicRouteSafetyTestSupport.deterministicToolRoutingStrategy(),
                        mock(FunctionCallingRoutingStrategy.class),
                        mock(AdvisorRoutingStrategy.class),
                        judgeStrategy,
                        mock(StructuredAnswerPlanService.class),
                        mock(AnswerVerificationService.class),
                        mock(ObjectProvider.class),
                        MonotonicRouteSafetyTestSupport.permissiveSafety(),
                        mock(ObjectProvider.class),
                        mock(ObjectProvider.class),
                        ConversationRecallGuardTestSupport.withEmptyHistory());

        ExecutionContext in = ctx("¿Cuántos participantes asistieron?");
        QueryPlan plan = mock(QueryPlan.class);
        when(plan.pipelineNotes()).thenReturn(List.of());
        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenReturn(in);
        when(clarificationPolicy.resolve(any(), any()))
                .thenReturn(
                        new ClarificationDecision(false, ClarificationOutcome.DISABLED_BY_CONFIG, null, ""));

        var out = orchestrator.execute(in);
        ExecutionTrace trace = out.executionTrace();

        assertThat(out.answerText())
                .isNotBlank()
                .containsIgnoringCase("acta")
                .containsIgnoringCase("fecha")
                .doesNotContain("17");
        assertThat(trace.stages()).extracting(ExecutionStageTrace::stageName).contains("memory_ambiguous_acta_guard");
        verify(workflowSelector, never()).select(any());
        verify(routingStrategy, never()).execute(any(), any());
    }

    private static RagConfig ragMemoryOn() {
        return new RagConfig(
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
                true,
                false,
                false,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static ExecutionContext ctx(String query) {
        RagConfig rag = ragMemoryOn();
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        UUID id = UUID.randomUUID();
        return new ExecutionContext(
                id,
                id,
                id,
                query,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                query,
                query,
                Optional.empty(),
                ConversationMemoryOutcome.NO_HISTORY_AVAILABLE,
                List.of(
                        new ExecutionStageTrace(
                                "memory_history_load",
                                0L,
                                ExecutionStageOutcome.SUCCESS,
                                "eligible_count=0")),
                true,
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

    private static ExecutionContext ctxMemoryDisabled(String query) {
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
                        0.2,
                        "l",
                        "e",
                        "c",
                        "r",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        UUID id = UUID.randomUUID();
        return new ExecutionContext(
                id,
                id,
                id,
                query,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                query,
                query,
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
