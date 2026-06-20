package com.uniovi.rag.application.service.runtime;
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
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingPolicy;
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingPolicy;
import com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.RouteExecutionGateBuilder;
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
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
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
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.ObjectProvider;

class RagExecutionOrchestratorAdaptiveRoutingTest {

    private final DeterministicToolRoutingStrategy deterministicToolRoutingStrategy =
            MonotonicRouteSafetyTestSupport.deterministicToolRoutingStrategy();
    private final FunctionCallingRoutingStrategy functionCallingRoutingStrategy =
            MonotonicRouteSafetyTestSupport.functionCallingRoutingStrategy();
    private final com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingStrategy advisorRoutingStrategy =
            MonotonicRouteSafetyTestSupport.advisorRoutingStrategy();

    @Test
    void selectedDeterministicToolRoute_executesOnlyTools_andSkipsWorkflowSelector() {
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT);
        ExecutionContext in = ctx(rag(true, true));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenReturn(in);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));

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

        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "workflow-answer",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                in.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));
        when(workflowSelector.select(any())).thenReturn(workflow);

        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString()))
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
                        clarificationPolicyResolver,
                        clarificationStrategy,
                        routingStrategy,
                        deterministicToolRoutingStrategy,
                        functionCallingRoutingStrategy,
                        advisorRoutingStrategy,
                        judgeStrategy,
                        MonotonicRouteSafetyTestSupport.structuredAnswerPlanNoOp(),
                        mock(AnswerVerificationService.class),
                        mock(ObjectProvider.class), MonotonicRouteSafetyTestSupport.permissiveSafety(), mock(ObjectProvider.class), mock(ObjectProvider.class));

        var out = orchestrator.execute(in);
        assertEquals("tool-answer", out.answerText());
        verify(workflowSelector, atLeastOnce()).select(any());
        verify(fcPolicy, never()).resolve(any(), any());
        verify(advisorPolicy, never()).resolve(any(), any());
    }

    private static RagConfig rag(boolean adaptiveRoutingEnabled, boolean useRetrieval) {
        return new RagConfig(
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                useRetrieval,
                false,
                false,
                false,
                adaptiveRoutingEnabled,
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

    private static ExecutionContext ctx(RagConfig rag) {
        UUID id = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
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
        KnowledgeSnapshotSelection snapshots =
                new KnowledgeSnapshotSelection(
                        List.of(snapshotId),
                        Optional.of(snapshotId),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
        return new ExecutionContext(
                id,
                id,
                id,
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                snapshots,
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "q",
                "q",
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
                Optional.empty());
    }

    private static QueryPlan plan(AmbiguityStatus status) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rw",
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
                new AmbiguityAssessment(status, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }
}

