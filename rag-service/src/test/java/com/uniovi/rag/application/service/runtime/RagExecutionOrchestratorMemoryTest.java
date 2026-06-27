package com.uniovi.rag.application.service.runtime;
import com.uniovi.rag.testsupport.ConversationRecallGuardTestSupport;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicRouteSafetyTestSupport;

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
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingStrategy;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.advisor.AdvisorDecision;
import com.uniovi.rag.domain.runtime.advisor.AdvisorExecutionResult;
import com.uniovi.rag.domain.runtime.advisor.AdvisorMode;
import com.uniovi.rag.domain.runtime.advisor.AdvisorOutcome;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingStrategy;

class RagExecutionOrchestratorMemoryTest {

    @Test
    void execute_runsQuOnce_andIncludesMemoryStagesBeforeQu_inTrace() {
        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicy = mock(ClarificationPolicyResolver.class);
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenAnswer(inv -> new JudgeExecutionResult(false, JudgeOutcome.NOT_ATTEMPTED, false, false, false, inv.getArgument(5), false, List.of()));

        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        mock(DirectLlmWorkflow.class),
                        qu,
                        factory,
                        tools,
                        fcPolicy,
                        fcStrategy,
                        advisorPolicy,
                        advisorStrategy,
                        clarificationPolicy,
                        clarificationStrategy,
                        routingStrategy,
                        MonotonicRouteSafetyTestSupport.deterministicToolRoutingStrategy(),
                        mock(FunctionCallingRoutingStrategy.class),
                        mock(AdvisorRoutingStrategy.class),
                        judgeStrategy,
                        mock(StructuredAnswerPlanService.class),
                        mock(AnswerVerificationService.class),
                        mock(ObjectProvider.class), MonotonicRouteSafetyTestSupport.permissiveSafety(), mock(ObjectProvider.class), mock(ObjectProvider.class), ConversationRecallGuardTestSupport.neverShortCircuit());

        ExecutionStageTrace mem1 =
                new ExecutionStageTrace(
                        "memory_history_load",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        "x");
        ExecutionStageTrace mem2 =
                new ExecutionStageTrace(
                        "memory_finalize_planning_input",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        "y");

        ExecutionContext in =
                ctxWithMemory(
                        List.of(mem1, mem2),
                        ConversationMemoryOutcome.NO_HISTORY_AVAILABLE,
                        true,
                        true,
                        false,
                        false,
                        false);

        QueryPlan plan = mock(QueryPlan.class);
        when(plan.pipelineNotes()).thenReturn(List.of());
        when(plan.ambiguityAssessment())
                .thenReturn(new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()));
        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenReturn(in);

        when(clarificationPolicy.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false,
                        ClarificationOutcome.NOT_NEEDED, null, ""));

        ExecutionWorkflow wf = mock(ExecutionWorkflow.class);
        when(wf.workflowName()).thenReturn("DirectLlmWorkflow");
        when(workflowSelector.select(any())).thenReturn(wf);
        when(wf.execute(any())).thenReturn(RagExecutionResult.withPlaceholderTrace("a", "DirectLlmWorkflow",
                false, false, List.of(), "none", List.of()));

        when(tools.tryExecute(any(), any())).thenReturn(
                DeterministicToolExecutionResult.skipped(DeterministicToolOutcome.NOT_ATTEMPTED, List.of(), Optional.empty()));
        when(fcPolicy.resolve(any(), any()))
                .thenReturn(Optional.of(new FunctionCallingDecision(
                        FunctionCallingMode.DISABLED,
                        FunctionCallingOutcome.DISABLED_BY_CONFIG,
                        false,
                        List.of(),
                        List.of(),
                        Optional.of("disabled"),
                        "",
                        Map.of())));
        when(advisorPolicy.resolve(any(), any()))
                .thenReturn(new AdvisorDecision(
                        AdvisorMode.DISABLED, false, List.of(), "", List.of(), Optional.empty()));
        when(advisorStrategy.execute(any(), any(), any(), any()))
                .thenReturn(new AdvisorExecutionResult(
                        AdvisorOutcome.SUPPRESSED_BY_POLICY, false, Optional.empty(), List.of()));

        RagExecutionResult out = orchestrator.execute(in);
        ExecutionTrace trace = out.executionTrace();

        verify(qu, times(1)).buildPlan(in);
        assertThat(trace.memoryAttempted()).isTrue();
        assertThat(trace.memoryOutcome()).isEqualTo(ConversationMemoryOutcome.NO_HISTORY_AVAILABLE.name());
        assertThat(trace.stages()).extracting(ExecutionStageTrace::stageName)
                .containsSequence("memory_history_load", "memory_finalize_planning_input");
    }

    private static ExecutionContext ctxWithMemory(
            List<ExecutionStageTrace> memoryStages,
            ConversationMemoryOutcome outcome,
            boolean memoryAttempted,
            boolean historyLoaded,
            boolean condensationAttempted,
            boolean condensationUsed,
            boolean fallbackApplied) {
        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        true,
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
        String q = "q";
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                q,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                q,
                q,
                Optional.empty(),
                outcome,
                memoryStages,
                memoryAttempted,
                historyLoaded,
                condensationAttempted,
                condensationUsed,
                fallbackApplied,
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

