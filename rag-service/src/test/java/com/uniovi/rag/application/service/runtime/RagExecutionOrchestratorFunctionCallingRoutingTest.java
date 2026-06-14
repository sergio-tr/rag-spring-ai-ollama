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
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagExecutionOrchestratorFunctionCallingRoutingTest {

    private final DeterministicToolRoutingStrategy deterministicToolRoutingStrategy =
            new DeterministicToolRoutingStrategy(
                    new DeterministicToolRoutingPolicy(), new RouteExecutionGateBuilder());
    private final FunctionCallingRoutingStrategy functionCallingRoutingStrategy =
            new FunctionCallingRoutingStrategy(
                    new FunctionCallingRoutingPolicy(), new RouteExecutionGateBuilder());

    @Test
    void functionCallingEnabled_executesFunctionCallingWithoutAdaptiveOrDeterministicRouting() {
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT);
        ExecutionContext in = ctx(p9Rag());
        Harness harness = orchestrator(in, plan);

        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(harness.functionCallingPolicy().resolve(any(), eq(plan))).thenReturn(Optional.of(decision));
        when(harness.functionCallingStrategy().tryExecute(any(), eq(plan), eq(decision)))
                .thenReturn(
                        fcAttemptedResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                                "fc-answer",
                                Map.of("count", 2),
                                List.of("fc_success"),
                                true,
                                List.of()));

        var out = harness.orchestrator().execute(in);

        assertThat(out.answerText()).isEqualTo("fc-answer");
        verify(harness.adaptiveRoutingStrategy(), never()).execute(any(), any());
        verify(harness.tools(), never()).tryExecute(any(), any());
        verify(harness.workflowSelector(), never()).select(any());

        Map<String, Object> telemetry = ToolExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(out.executionTrace().routingRouteKind())
                .isEqualTo(AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name());
        assertThat(telemetry.get("deterministicToolRoute")).isEqualTo(false);
        assertThat(telemetry.get("functionCallingUsed")).isEqualTo(true);
        assertThat(telemetry.get("functionCallAttempted")).isEqualTo(true);
        assertThat(telemetry.get("functionCallName")).isEqualTo("countDocuments");
    }

    @Test
    void functionCallingEnabled_fallsBackToWorkflowWhenModelDeclines() {
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT);
        ExecutionContext in = ctx(p9Rag());
        Harness harness = orchestrator(in, plan);

        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(harness.functionCallingPolicy().resolve(any(), eq(plan))).thenReturn(Optional.of(decision));
        when(harness.functionCallingStrategy().tryExecute(any(), eq(plan), eq(decision)))
                .thenReturn(
                        fcAttemptedResult(
                                FunctionCallingOutcome.MODEL_DECLINED,
                                false,
                                Optional.empty(),
                                "",
                                Map.of(),
                                List.of("model_declined"),
                                false,
                                List.of()));

        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(harness.workflowSelector().select(any())).thenReturn(workflow);
        when(workflow.workflowName()).thenReturn("ChunkDenseMetadataWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        new RagExecutionResult(
                                "workflow-answer",
                                "ChunkDenseMetadataWorkflow",
                                true,
                                true,
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
        assertThat(telemetry.get("functionCallingUsed")).isEqualTo(false);
        assertThat(telemetry.get("functionCallAttempted")).isEqualTo(true);
        assertThat(telemetry.get("functionCallFallbackReason")).isEqualTo("model_declined");
        assertThat(out.executionTrace().routingFallbackApplied()).isTrue();
    }

    private Harness orchestrator(ExecutionContext in, QueryPlan plan) {
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
                        new com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingStrategy(
                                new com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingPolicy(),
                                new com.uniovi.rag.application.service.runtime.routing.RouteExecutionGateBuilder()),
                        judgeStrategy,
                        mock(StructuredAnswerPlanService.class),
                        mock(AnswerVerificationService.class),
                        mock(ObjectProvider.class));

        return new Harness(orchestrator, tools, workflowSelector, adaptiveRoutingStrategy, fcPolicy, fcStrategy);
    }

    private record Harness(
            RagExecutionOrchestrator orchestrator,
            DeterministicToolStrategy tools,
            WorkflowSelector workflowSelector,
            AdaptiveRoutingStrategy adaptiveRoutingStrategy,
            FunctionCallingPolicyResolver functionCallingPolicy,
            FunctionCallingStrategy functionCallingStrategy) {}

    private static RagConfig p9Rag() {
        return new RagConfig(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                12,
                0.6,
                "llm",
                "emb",
                "cls",
                "SIMPLE",
                false,
                32_000,
                24_000,
                false,
                MaterializationStrategy.HYBRID);
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

    private static FunctionCallingExecutionResult fcAttemptedResult(
            FunctionCallingOutcome outcome,
            boolean success,
            Optional<DeterministicToolKind> selectedToolKind,
            String answerText,
            Map<String, Object> normalizedPayload,
            List<String> traceNotes,
            boolean shortCircuited,
            List<com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace> stageTraces) {
        return new FunctionCallingExecutionResult(
                outcome,
                success,
                selectedToolKind,
                answerText,
                normalizedPayload,
                traceNotes,
                shortCircuited,
                stageTraces,
                Optional.empty(),
                true,
                false);
    }
}
