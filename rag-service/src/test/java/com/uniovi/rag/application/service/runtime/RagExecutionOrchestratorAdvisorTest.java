package com.uniovi.rag.application.service.runtime;

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
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingExecutionResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.routing.RouteExecutionGate;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagExecutionOrchestratorAdvisorTest {

    @Test
    void deterministic_tool_short_circuit_skips_advisor() {
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT);
        ExecutionContext in = ctxWithoutPlan(ragDirectLlm(false), KnowledgeSnapshotSelection.empty());

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicyResolver = clarificationPolicyNoAsk();
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString()))
                .thenAnswer(inv -> new JudgeExecutionResult(false, JudgeOutcome.NOT_ATTEMPTED, false, false, false, inv.getArgument(5), false, List.of()));

        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenAnswer(inv -> withPlan(inv.getArgument(0), plan));

        ExecutionWorkflow wf = mock(ExecutionWorkflow.class);
        when(wf.workflowName()).thenReturn("DirectLlmWorkflow");
        when(wf.execute(any())).thenThrow(new AssertionError("workflow should not run"));
        when(workflowSelector.select(any())).thenReturn(wf);

        when(routingStrategy.execute(any(), eq(plan)))
                .thenReturn(
                        new AdaptiveRoutingExecutionResult(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                                true,
                                AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                                false,
                                Optional.empty(),
                                false,
                                new RouteExecutionGate(
                                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                                        false,
                                        true,
                                        false,
                                        false,
                                        true,
                                        Optional.of(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE),
                                        false),
                                List.of()));

        when(tools.tryExecute(any(), eq(plan)))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "tool-answer",
                                Map.of(),
                                List.of()));

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
                        clarificationPolicyResolver,
                        clarificationStrategy,
                        routingStrategy,
                        judgeStrategy,
                        mock(StructuredAnswerPlanService.class),
                        mock(AnswerVerificationService.class));

        RagExecutionResult out = orchestrator.execute(in);
        assertEquals("tool-answer", out.answerText());
        assertEquals(
                AdvisorOutcome.NOT_REACHED_BECAUSE_DETERMINISTIC_TOOL.name(),
                out.executionTrace().advisorOutcome());

        verify(advisorPolicy, never()).resolve(any(), any());
        verify(advisorStrategy, never()).execute(any(), any(), any(), any());
    }

    @Test
    void function_calling_short_circuit_skips_advisor() {
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT);
        ExecutionContext in = ctxWithoutPlan(ragDirectLlm(true), KnowledgeSnapshotSelection.empty());

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicyResolver = clarificationPolicyNoAsk();
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString()))
                .thenAnswer(inv -> new JudgeExecutionResult(false, JudgeOutcome.NOT_ATTEMPTED, false, false, false, inv.getArgument(5), false, List.of()));

        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenAnswer(inv -> withPlan(inv.getArgument(0), plan));

        ExecutionWorkflow wf = mock(ExecutionWorkflow.class);
        when(wf.workflowName()).thenReturn("DirectLlmWorkflow");
        when(wf.execute(any())).thenThrow(new AssertionError("workflow should not run"));
        when(workflowSelector.select(any())).thenReturn(wf);

        when(routingStrategy.execute(any(), eq(plan)))
                .thenReturn(
                        new AdaptiveRoutingExecutionResult(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                                true,
                                AdaptiveRouteKind.FUNCTION_CALLING_ROUTE,
                                false,
                                Optional.empty(),
                                false,
                                new RouteExecutionGate(
                                        AdaptiveRouteKind.FUNCTION_CALLING_ROUTE,
                                        false,
                                        false,
                                        true,
                                        false,
                                        true,
                                        Optional.of(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE),
                                        false),
                                List.of()));

        when(tools.tryExecute(any(), eq(plan)))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of(), Optional.empty()));

        FunctionCallingDecision fcDec =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        List.of(),
                        Optional.empty(),
                        "rewritten",
                        Map.of());
        when(fcPolicy.resolve(any(), eq(plan))).thenReturn(Optional.of(fcDec));

        FunctionCallingExecutionResult fcResult =
                new FunctionCallingExecutionResult(
                        FunctionCallingOutcome.EXECUTED_SUCCESS,
                        true,
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        "fc-answer",
                        Map.of(),
                        List.of(),
                        true,
                        List.of());
        when(fcStrategy.tryExecute(any(), eq(plan), eq(fcDec))).thenReturn(fcResult);

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
                        clarificationPolicyResolver,
                        clarificationStrategy,
                        routingStrategy,
                        judgeStrategy,
                        mock(StructuredAnswerPlanService.class),
                        mock(AnswerVerificationService.class));

        RagExecutionResult out = orchestrator.execute(in);
        assertEquals("fc-answer", out.answerText());
        assertEquals(
                AdvisorOutcome.NOT_REACHED_BECAUSE_FUNCTION_CALLING.name(),
                out.executionTrace().advisorOutcome());

        verify(advisorPolicy, never()).resolve(any(), any());
        verify(advisorStrategy, never()).execute(any(), any(), any(), any());
    }

    @Test
    void advisor_runs_before_workflow_and_attaches_packed_context() {
        UUID snap = UUID.randomUUID();
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT);
        ExecutionContext in =
                ctxWithoutPlan(ragChunkDenseAdvisor(), new KnowledgeSnapshotSelection(
                        List.of(snap), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicyResolver = clarificationPolicyNoAsk();
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString()))
                .thenAnswer(inv -> new JudgeExecutionResult(false, JudgeOutcome.NOT_ATTEMPTED, false, false, false, inv.getArgument(5), false, List.of()));

        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenAnswer(inv -> withPlan(inv.getArgument(0), plan));

        PackedContextSet packed =
                new PackedContextSet(List.of(), "pack", 0, 0, List.of(), "CTX");
        when(factory.attachAdvisorPackedContextSet(any(), eq(packed)))
                .thenAnswer(inv -> withAdvisorPack(inv.getArgument(0), inv.getArgument(1)));

        ExecutionWorkflow wf = mock(ExecutionWorkflow.class);
        when(wf.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(wf.execute(any()))
                .thenAnswer(
                        inv -> {
                            ExecutionContext wctx = inv.getArgument(0);
                            assertTrue(wctx.advisorPackedContextSet().isPresent());
                            assertEquals("CTX", wctx.advisorPackedContextSet().get().promptContextText());
                            return RagExecutionResult.withPlaceholderTrace(
                                    "wf-answer",
                                    "ChunkDenseRagWorkflow",
                                    true,
                                    false,
                                    List.of(snap),
                                    "dense",
                                    List.of());
                        });
        when(workflowSelector.select(any())).thenReturn(wf);

        when(routingStrategy.execute(any(), eq(plan)))
                .thenReturn(
                        new AdaptiveRoutingExecutionResult(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_CONTINUED_TO_WORKFLOW,
                                true,
                                AdaptiveRouteKind.ADVISOR_ROUTE,
                                false,
                                Optional.empty(),
                                false,
                                new RouteExecutionGate(
                                        AdaptiveRouteKind.ADVISOR_ROUTE,
                                        false,
                                        false,
                                        false,
                                        true,
                                        true,
                                        Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                                        false),
                                List.of()));

        when(tools.tryExecute(any(), eq(plan)))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of(), Optional.empty()));

        AdvisorDecision decision =
                new AdvisorDecision(
                        AdvisorMode.ENABLED,
                        true,
                        AdvisorDecision.EXECUTABLE_KINDS_5_2,
                        "rewritten",
                        List.of(),
                        Optional.empty());
        when(advisorPolicy.resolve(any(), eq(plan))).thenReturn(decision);
        when(advisorStrategy.execute(any(), eq(plan), eq("ChunkDenseRagWorkflow"), eq(decision)))
                .thenReturn(AdvisorExecutionResult.success(packed));

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
                        clarificationPolicyResolver,
                        clarificationStrategy,
                        routingStrategy,
                        judgeStrategy,
                        mock(StructuredAnswerPlanService.class),
                        mock(AnswerVerificationService.class));

        RagExecutionResult out = orchestrator.execute(in);
        assertEquals("wf-answer", out.answerText());
        assertEquals(AdvisorOutcome.EXECUTED_SUCCESS.name(), out.executionTrace().advisorOutcome());
        assertTrue(out.executionTrace().advisorAttempted());
        assertTrue(out.executionTrace().stages().stream().anyMatch(s -> "advisor_policy".equals(s.stageName())));
        assertTrue(out.executionTrace().stages().stream().anyMatch(s -> "advisor_retrieval".equals(s.stageName())));
        assertTrue(out.executionTrace().stages().stream().anyMatch(s -> "advisor_context_pack".equals(s.stageName())));

        verify(factory).attachAdvisorPackedContextSet(any(), eq(packed));
    }

    private static ClarificationPolicyResolver clarificationPolicyNoAsk() {
        ClarificationPolicyResolver m = mock(ClarificationPolicyResolver.class);
        when(m.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));
        return m;
    }

    private static RagConfig ragDirectLlm(boolean functionCallingEnabled) {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                functionCallingEnabled,
                false,
                false,
                false,
                false,
                true,
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

    private static RagConfig ragChunkDenseAdvisor() {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                true,
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

    private static ExecutionContext ctxWithoutPlan(RagConfig rag, KnowledgeSnapshotSelection snaps) {
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
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                snaps,
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "user q",
                "user q",
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

    private static ExecutionContext withPlan(ExecutionContext ctx, QueryPlan plan) {
        return new ExecutionContext(
                ctx.userId(),
                ctx.projectId(),
                ctx.conversationId(),
                ctx.userQuery(),
                ctx.operationKind(),
                ctx.resolved(),
                ctx.effectiveSystemPrompt(),
                ctx.knowledgeSnapshotSelection(),
                ctx.configHash(),
                ctx.pinnedResolvedConfigSnapshotId(),
                ctx.correlationId(),
                ctx.documentFilter(),
                ctx.chatModelOverride(),
                Optional.of(plan),
                Optional.empty(),
                ctx.structuredAnswerPlan(),
                ctx.preMemoryPlanningInputText(),
                ctx.effectivePlanningInputText(),
                ctx.memorySlice(),
                ctx.memoryOutcome(),
                ctx.memoryStageTraces(),
                ctx.memoryAttempted(),
                ctx.memoryHistoryLoaded(),
                ctx.memoryCondensationAttempted(),
                ctx.memoryCondensationUsed(),
                ctx.memoryFallbackApplied(),
                ctx.pendingClarificationLoadedForTrace(),
                ctx.validPendingExistedAtLoad(),
                ctx.invalidPendingRecoveredThisTurn(),
                ctx.clarificationDisableReason(),
                ctx.originatingUserMessageId(),
                ctx.routingAttempted(),
                ctx.routingOutcome(),
                ctx.routingRouteKind(),
                ctx.routingFallbackApplied(),
                ctx.routingFallbackRouteKind(),
                ctx.routingWorkflowSelectorInvoked(),
                ctx.routingStageTraces());
    }

    private static ExecutionContext withAdvisorPack(ExecutionContext ctx, PackedContextSet packed) {
        return new ExecutionContext(
                ctx.userId(),
                ctx.projectId(),
                ctx.conversationId(),
                ctx.userQuery(),
                ctx.operationKind(),
                ctx.resolved(),
                ctx.effectiveSystemPrompt(),
                ctx.knowledgeSnapshotSelection(),
                ctx.configHash(),
                ctx.pinnedResolvedConfigSnapshotId(),
                ctx.correlationId(),
                ctx.documentFilter(),
                ctx.chatModelOverride(),
                ctx.queryPlan(),
                Optional.of(packed),
                ctx.structuredAnswerPlan(),
                ctx.preMemoryPlanningInputText(),
                ctx.effectivePlanningInputText(),
                ctx.memorySlice(),
                ctx.memoryOutcome(),
                ctx.memoryStageTraces(),
                ctx.memoryAttempted(),
                ctx.memoryHistoryLoaded(),
                ctx.memoryCondensationAttempted(),
                ctx.memoryCondensationUsed(),
                ctx.memoryFallbackApplied(),
                ctx.pendingClarificationLoadedForTrace(),
                ctx.validPendingExistedAtLoad(),
                ctx.invalidPendingRecoveredThisTurn(),
                ctx.clarificationDisableReason(),
                ctx.originatingUserMessageId(),
                ctx.routingAttempted(),
                ctx.routingOutcome(),
                ctx.routingRouteKind(),
                ctx.routingFallbackApplied(),
                ctx.routingFallbackRouteKind(),
                ctx.routingWorkflowSelectorInvoked(),
                ctx.routingStageTraces());
    }

    private static QueryPlan plan(AmbiguityStatus amb) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rewritten",
                "lbl",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(amb, List.of(), List.of()),
                "cid",
                "",
                List.of());
    }
}
