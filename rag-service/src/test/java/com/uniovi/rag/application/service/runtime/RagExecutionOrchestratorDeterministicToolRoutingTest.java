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
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
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
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingPolicy;
import com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingStrategy;

class RagExecutionOrchestratorDeterministicToolRoutingTest {

    private final DeterministicToolRoutingStrategy deterministicToolRoutingStrategy =
            new DeterministicToolRoutingStrategy(
                    new DeterministicToolRoutingPolicy(), new RouteExecutionGateBuilder());
    private final FunctionCallingRoutingStrategy functionCallingRoutingStrategy =
            new FunctionCallingRoutingStrategy(
                    new FunctionCallingRoutingPolicy(), new RouteExecutionGateBuilder());

    @Test
    void deterministicToolRoutingEnabled_executesToolWithoutAdaptiveRoutingOrFunctionCalling() {
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT);
        ExecutionContext in = ctx(p7Rag());
        RagExecutionOrchestratorHarness harness = orchestrator(in, plan, p7Rag());

        when(harness.tools().tryExecute(any(), eq(plan)))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "tool-answer",
                                Map.of(),
                                List.of()));

        var out = harness.orchestrator().execute(in);
        assertThat(out.answerText()).isEqualTo("tool-answer");
        verify(harness.adaptiveRoutingStrategy(), never()).execute(any(), any());
        verify(harness.functionCallingPolicy(), never()).resolve(any(), any());
        verify(harness.workflowSelector(), atLeastOnce()).select(any());

        ExecutionTrace trace = out.executionTrace();
        Map<String, Object> telemetry = ToolExecutionTelemetryMapper.fromTrace(trace);
        assertThat(telemetry.get("deterministicToolRoute")).isEqualTo(true);
        assertThat(telemetry.get("functionCallingUsed")).isEqualTo(false);
    }

    @Test
    void deterministicToolRoutingEnabled_fallsBackToWorkflowWhenToolNotApplicable() {
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT);
        ExecutionContext in = ctx(p7Rag());
        RagExecutionOrchestratorHarness harness = orchestrator(in, plan, p7Rag());

        when(harness.tools().tryExecute(any(), eq(plan)))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.empty(),
                                DeterministicToolOutcome.NOT_APPLICABLE,
                                false,
                                "",
                                Map.of(),
                                List.of("tool_not_applicable")));

        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(harness.workflowSelector().select(any())).thenReturn(workflow);
        when(workflow.workflowName()).thenReturn("ChunkDenseMetadataWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        new RagExecutionResult(
                                "workflow-answer",
                                "ChunkDenseMetadataWorkflow",
                                true,
                                false,
                                Optional.empty(),
                                Optional.empty(),
                                List.of(),
                                ExecutionTrace.placeholder(),
                                "",
                                QueryType.COUNT_DOCUMENTS,
                                false,
                                List.of(),
                                Optional.empty(),
                                List.of()));

        var out = harness.orchestrator().execute(in);
        assertThat(out.answerText()).isEqualTo("workflow-answer");
        verify(harness.workflowSelector()).select(any());
        verify(harness.adaptiveRoutingStrategy(), never()).execute(any(), any());

        Map<String, Object> telemetry = ToolExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("deterministicToolRoute")).isEqualTo(false);
        assertThat(telemetry.get("toolFallbackReason")).isEqualTo("tool_not_applicable");
        assertThat(out.executionTrace().routingFallbackApplied()).isTrue();
        assertThat(out.executionTrace().routingRouteKind())
                .isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name());
    }

    private RagExecutionOrchestratorHarness orchestrator(ExecutionContext in, QueryPlan plan, RagConfig rag) {
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
        AdaptiveRoutingStrategy adaptiveRoutingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);

        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenReturn(in);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));
        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString()))
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

        ExecutionWorkflow probeWorkflow = mock(ExecutionWorkflow.class);
        when(probeWorkflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(probeWorkflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "probe",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                in.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));
        when(workflowSelector.select(any())).thenReturn(probeWorkflow);

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
                        adaptiveRoutingStrategy,
                        deterministicToolRoutingStrategy,
                        functionCallingRoutingStrategy,
                        new AdvisorRoutingStrategy(
                                new AdvisorRoutingPolicy(),
                                new RouteExecutionGateBuilder()),
                        judgeStrategy,
                        MonotonicRouteSafetyTestSupport.structuredAnswerPlanNoOp(),
                        mock(AnswerVerificationService.class),
                        mock(ObjectProvider.class), MonotonicRouteSafetyTestSupport.permissiveSafety(), mock(ObjectProvider.class), mock(ObjectProvider.class));

        return new RagExecutionOrchestratorHarness(
                orchestrator,
                tools,
                workflowSelector,
                adaptiveRoutingStrategy,
                fcPolicy);
    }

    private record RagExecutionOrchestratorHarness(
            RagExecutionOrchestrator orchestrator,
            DeterministicToolStrategy tools,
            WorkflowSelector workflowSelector,
            AdaptiveRoutingStrategy adaptiveRoutingStrategy,
            FunctionCallingPolicyResolver functionCallingPolicy) {}

    private static RagConfig p7Rag() {
        return new RagConfig(
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                10,
                0.7,
                "llm",
                "emb",
                "cls",
                "SIMPLE",
                false,
                32_000,
                24_000,
                false,
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
                        List.of(snapshotId), Optional.of(snapshotId), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
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
                Optional.empty(),
                false,
                null,
                AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    private static QueryPlan plan(AmbiguityStatus status) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rw",
                "lbl",
                Optional.of(QueryType.COUNT_DOCUMENTS),
                ClassifierStatus.OK,
                QueryIntent.COUNT,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                ExpectedAnswerShape.SCALAR_COUNT,
                new AmbiguityAssessment(status, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }
}
