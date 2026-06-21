package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.AdvisorPhaseResult;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.AdvisorSnapshot;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.ExecutionOutcome;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.FcGate;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.JudgeSnapshot;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.RoutingSnapshot;
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
import com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingStrategy;
import com.uniovi.rag.application.service.evaluation.preset.CampaignParentOutcome;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.application.service.runtime.routing.safety.IntegratedParentCampaignOutcomeResolver;
import com.uniovi.rag.application.service.runtime.routing.safety.IntegratedParentCandidateMaterializer;
import com.uniovi.rag.application.service.runtime.routing.safety.IntegratedParentPresetExecutionScope;
import com.uniovi.rag.application.service.runtime.routing.safety.IntegratedParentPresetSnapshotResolver;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicRouteSafetyService;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicSafetyTelemetry;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicSafetyTelemetrySupport;
import com.uniovi.rag.application.service.runtime.routing.safety.ParentAnswerFingerprint;
import com.uniovi.rag.application.service.runtime.routing.safety.ParentFinalAnswerSources;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.Decision;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.ParentBaseline;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.WinnerKind;
import com.uniovi.rag.application.service.runtime.routing.safety.P7BaselineFloorSelector;
import com.uniovi.rag.application.service.runtime.routing.safety.P7BaselineFloorSelector.P7Decision;
import com.uniovi.rag.application.service.runtime.routing.safety.P15ParentCandidateSafetyPolicy;
import com.uniovi.rag.application.service.runtime.routing.safety.ParentCampaignOutcomeTelemetryPreservation;
import com.uniovi.rag.application.service.runtime.routing.safety.ParentCandidateSnapshot;
import java.util.Objects;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolKindMappings;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateValidationResult;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.advisor.AdvisorDecision;
import com.uniovi.rag.domain.runtime.advisor.AdvisorExecutionResult;
import com.uniovi.rag.domain.runtime.advisor.AdvisorOutcome;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationExecutionResult;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallProposal;

@Component
public class RagExecutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RagExecutionOrchestrator.class);

    private final WorkflowSelector workflowSelector;
    private final DirectLlmWorkflow snapshotFallbackDirectLlmWorkflow;
    private final QueryUnderstandingPipeline queryUnderstandingPipeline;
    private final ExecutionContextFactory executionContextFactory;
    private final DeterministicToolStrategy deterministicToolStrategy;
    private final FunctionCallingPolicyResolver functionCallingPolicyResolver;
    private final FunctionCallingStrategy functionCallingStrategy;
    private final AdvisorPolicyResolver advisorPolicyResolver;
    private final AdvisorStrategy advisorStrategy;
    private final ClarificationPolicyResolver clarificationPolicyResolver;
    private final ClarificationStrategy clarificationStrategy;
    private final AdaptiveRoutingStrategy adaptiveRoutingStrategy;
    private final DeterministicToolRoutingStrategy deterministicToolRoutingStrategy;
    private final FunctionCallingRoutingStrategy functionCallingRoutingStrategy;
    private final AdvisorRoutingStrategy advisorRoutingStrategy;
    private final JudgeStrategy judgeStrategy;
    private final StructuredAnswerPlanService structuredAnswerPlanService;
    private final AnswerVerificationService answerVerificationService;
    private final ObjectProvider<RuntimeObservability> runtimeObservability;
    private final MonotonicRouteSafetyService monotonicRouteSafetyService;
    private final ObjectProvider<IntegratedParentPresetSnapshotResolver> parentSnapshotResolverProvider;
    private final ObjectProvider<IntegratedParentCampaignOutcomeResolver> parentCampaignOutcomeResolverProvider;

    public RagExecutionOrchestrator(
            WorkflowSelector workflowSelector,
            DirectLlmWorkflow snapshotFallbackDirectLlmWorkflow,
            QueryUnderstandingPipeline queryUnderstandingPipeline,
            ExecutionContextFactory executionContextFactory,
            DeterministicToolStrategy deterministicToolStrategy,
            FunctionCallingPolicyResolver functionCallingPolicyResolver,
            FunctionCallingStrategy functionCallingStrategy,
            AdvisorPolicyResolver advisorPolicyResolver,
            AdvisorStrategy advisorStrategy,
            ClarificationPolicyResolver clarificationPolicyResolver,
            ClarificationStrategy clarificationStrategy,
            AdaptiveRoutingStrategy adaptiveRoutingStrategy,
            DeterministicToolRoutingStrategy deterministicToolRoutingStrategy,
            FunctionCallingRoutingStrategy functionCallingRoutingStrategy,
            AdvisorRoutingStrategy advisorRoutingStrategy,
            JudgeStrategy judgeStrategy,
            StructuredAnswerPlanService structuredAnswerPlanService,
            AnswerVerificationService answerVerificationService,
            ObjectProvider<RuntimeObservability> runtimeObservability,
            MonotonicRouteSafetyService monotonicRouteSafetyService,
            ObjectProvider<IntegratedParentPresetSnapshotResolver> parentSnapshotResolverProvider,
            ObjectProvider<IntegratedParentCampaignOutcomeResolver> parentCampaignOutcomeResolverProvider) {
        this.workflowSelector = workflowSelector;
        this.snapshotFallbackDirectLlmWorkflow = snapshotFallbackDirectLlmWorkflow;
        this.queryUnderstandingPipeline = queryUnderstandingPipeline;
        this.executionContextFactory = executionContextFactory;
        this.deterministicToolStrategy = deterministicToolStrategy;
        this.functionCallingPolicyResolver = functionCallingPolicyResolver;
        this.functionCallingStrategy = functionCallingStrategy;
        this.advisorPolicyResolver = advisorPolicyResolver;
        this.advisorStrategy = advisorStrategy;
        this.clarificationPolicyResolver = clarificationPolicyResolver;
        this.clarificationStrategy = clarificationStrategy;
        this.adaptiveRoutingStrategy = adaptiveRoutingStrategy;
        this.deterministicToolRoutingStrategy = deterministicToolRoutingStrategy;
        this.functionCallingRoutingStrategy = functionCallingRoutingStrategy;
        this.advisorRoutingStrategy = advisorRoutingStrategy;
        this.judgeStrategy = judgeStrategy;
        this.structuredAnswerPlanService = structuredAnswerPlanService;
        this.answerVerificationService = answerVerificationService;
        this.runtimeObservability = runtimeObservability;
        this.monotonicRouteSafetyService = monotonicRouteSafetyService;
        this.parentSnapshotResolverProvider = parentSnapshotResolverProvider;
        this.parentCampaignOutcomeResolverProvider = parentCampaignOutcomeResolverProvider;
    }

    private record ParentExecutionAttempt(
            Optional<ExecutionOutcome> outcome,
            boolean campaignOutcomeReused,
            Optional<CampaignParentOutcome> campaignRecord) {}

    public RagExecutionResult execute(ExecutionContext ctx) {
        List<ExecutionStageTrace> clarifyBeforeQu = RagExecutionTraceSupport.buildClarificationPreQuStages(ctx);
        List<ExecutionStageTrace> memoryBeforeQu = List.copyOf(ctx.memoryStageTraces());
        long quStart = System.nanoTime();
        QueryPlan plan = queryUnderstandingPipeline.buildPlan(ctx);
        ExecutionContext withPlan = executionContextFactory.attachQueryPlan(ctx, plan);

        List<ExecutionStageTrace> quStages = RagExecutionTraceSupport.projectQuStages(plan);
        quStages.add(
                0,
                new ExecutionStageTrace(
                        "qu_total",
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - quStart),
                        ExecutionStageOutcome.SUCCESS,
                        "qu_status=OK message=QueryUnderstandingPipeline completed"));

        ClarificationDecision clarificationDecision = clarificationPolicyResolver.resolve(withPlan, plan);
        List<ExecutionStageTrace> clarifyAfterQu = new ArrayList<>();
        clarifyAfterQu.add(RagExecutionTraceSupport.clarificationPolicyStage(clarificationDecision));

        if (clarificationDecision.ask()) {
            ClarificationExecutionResult cr =
                    clarificationStrategy.executeAsk(withPlan, plan, clarificationDecision);
            clarifyAfterQu.addAll(cr.stageTraces());
            return finishClarificationAskShortCircuit(
                    withPlan,
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu,
                    cr,
                    clarificationDecision);
        }

        if (clarificationDecision.terminalOutcome() == ClarificationOutcome.RESOLVED_FROM_PENDING) {
            clarificationStrategy.clearAfterResolved(withPlan.conversationId());
        }

        RoutingSnapshot routing = resolveRoutingSnapshot(withPlan, plan);

        if (withPlan.resolved().toRagConfig().adaptiveRoutingEnabled()
                && !withPlan.resolved().toRagConfig().useAdvisor()) {
            ExecutionOutcome integratedOutcome =
                    executeIntegratedAdaptiveRoute(
                            withPlan,
                            plan,
                            clarificationDecision,
                            routing,
                            clarifyBeforeQu,
                            memoryBeforeQu,
                            quStages,
                            clarifyAfterQu);
            return integratedOutcome.result().withFinalTrace(integratedOutcome.trace());
        }

        ExecutionOutcome outcome =
                executeSelectedRoute(
                        withPlan,
                        plan,
                        clarificationDecision,
                        routing,
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        clarifyAfterQu);
        return outcome.result().withFinalTrace(outcome.trace());
    }

    private RoutingSnapshot resolveRoutingSnapshot(ExecutionContext ctx, QueryPlan plan) {
        var rag = ctx.resolved().toRagConfig();
        if (rag.adaptiveRoutingEnabled()) {
            var r = adaptiveRoutingStrategy.execute(ctx, plan);
            return RoutingSnapshot.enabled(r.routingRouteKind(), r.gate(), r.stageTraces());
        }
        if (rag.toolsEnabled()) {
            var structuredToolRoute = deterministicToolRoutingStrategy.execute(rag, plan);
            if (structuredToolRoute.routingRouteKind() == AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE) {
                return RoutingSnapshot.enabled(
                        structuredToolRoute.routingRouteKind(),
                        structuredToolRoute.gate(),
                        structuredToolRoute.stageTraces());
            }
        }
        if (rag.deterministicToolRoutingEnabled() && !rag.functionCallingEnabled()) {
            var r = deterministicToolRoutingStrategy.execute(rag, plan);
            return RoutingSnapshot.enabled(r.routingRouteKind(), r.gate(), r.stageTraces());
        }
        if (rag.functionCallingEnabled() && !rag.adaptiveRoutingEnabled()) {
            var r = functionCallingRoutingStrategy.execute(rag, plan);
            return RoutingSnapshot.enabled(r.routingRouteKind(), r.gate(), r.stageTraces());
        }
        if (rag.useAdvisor()
                && !rag.functionCallingEnabled()
                && !rag.deterministicToolRoutingEnabled()
                && !rag.adaptiveRoutingEnabled()) {
            var r = advisorRoutingStrategy.execute(rag, plan);
            return RoutingSnapshot.enabled(r.routingRouteKind(), r.gate(), r.stageTraces());
        }
        AdaptiveRouteKind compat =
                rag.useRetrieval() ? AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE : AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
        return RoutingSnapshot.disabledByConfig(compat);
    }

    private ExecutionOutcome executeSelectedRoute(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu) {

        AdaptiveRouteKind route = routing.routeKind();

        // Family exclusivity: only the selected route family executes first.
        if (route == AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE || route == AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE) {
            return executeWorkflowRoute(
                    base,
                    plan,
                    clarificationDecision,
                    routing.withOutcome(AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED, false, Optional.empty(), true),
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu);
        }
        if (route == AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE) {
            return executeDeterministicToolRoute(
                    base,
                    plan,
                    clarificationDecision,
                    routing,
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu);
        }
        if (route == AdaptiveRouteKind.FUNCTION_CALLING_ROUTE) {
            return executeFunctionCallingRoute(
                    base,
                    plan,
                    clarificationDecision,
                    routing,
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu);
        }
        if (route == AdaptiveRouteKind.ADVISOR_ROUTE) {
            return executeAdvisorRoute(
                    base,
                    plan,
                    clarificationDecision,
                    routing,
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu);
        }

        throw new IllegalStateException("unsupported route kind: " + route);
    }

    private ExecutionOutcome executeDeterministicToolRoute(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu) {
        var rag = base.resolved().toRagConfig();
        MonotonicSafetyTelemetry telemetry =
                MonotonicSafetyTelemetry.create()
                        .candidateToolConsidered(true)
                        .candidateRetrievalConsidered(rag.useRetrieval());

        DeterministicToolExecutionResult toolResult = deterministicToolStrategy.tryExecute(base, plan);
        Optional<MonotonicRouteSafetyService.CandidateScore> toolScore = Optional.empty();
        if (toolResult.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS && toolResult.success()) {
            RouteCandidateValidationResult validation =
                    monotonicRouteSafetyService.validateToolResult(plan, toolResult);
            if (validation.safe()) {
                toolScore =
                        Optional.of(
                                new MonotonicRouteSafetyService.CandidateScore(
                                        "TOOL", validation, toolResult.answerText()));
            } else {
                telemetry.toolCandidateRejected(true)
                        .rejectCandidate("TOOL", String.join(",", validation.rejectionReasons()));
            }
        }

        AdaptiveRouteKind fallbackRoute =
                routing.fallbackWorkflowRouteKind().orElse(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
        ExecutionOutcome retrievalOutcome =
                executeWorkflowRoute(
                        base,
                        plan,
                        clarificationDecision,
                        routing.withOutcome(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                                true,
                                Optional.of(fallbackRoute),
                                true),
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        clarifyAfterQu,
                        RagExecutionTraceSupport.projectDeterministicToolStages(toolResult),
                        List.of(),
                        toolResult,
                        FcGate.notAttempted(FunctionCallingOutcome.SUPPRESSED_BY_DETERMINISTIC_TOOL),
                        AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_DETERMINISTIC_TOOL));

        boolean retrievalAbstained = retrievalOutcome.trace().abstentionTriggered();
        RouteCandidateValidationResult retrievalValidation =
                monotonicRouteSafetyService.validateRetrievalAnswer(
                        plan, retrievalOutcome.result().answerText(), retrievalAbstained);
        if (!retrievalValidation.safe()) {
            telemetry.retrievalCandidateRejected(true)
                    .rejectCandidate("RETRIEVAL", String.join(",", retrievalValidation.rejectionReasons()));
        }

        Optional<MonotonicRouteSafetyService.CandidateScore> retrievalScore = Optional.empty();
        if (retrievalValidation.safe()) {
            retrievalScore =
                    Optional.of(
                            new MonotonicRouteSafetyService.CandidateScore(
                                    "RETRIEVAL",
                                    retrievalValidation,
                                    retrievalOutcome.result().answerText()));
        }

        Optional<ParentSelectionResult> p6Parent = Optional.empty();
        Optional<ParentBaseline> p6Floor = Optional.empty();
        if (LabBenchmarkExecutionContext.currentDatasetQuestionId().isPresent()) {
            p6Parent =
                    probeParentPreset(
                            base, plan, clarificationDecision, telemetry, RagExperimentalPresetCode.P6);
            p6Floor = toCampaignBaseline(p6Parent);
        }

        if (p6Floor.isEmpty()) {
            return executeDeterministicToolRouteWithoutBaselineFloor(
                    base,
                    plan,
                    clarificationDecision,
                    routing,
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu,
                    toolResult,
                    toolScore,
                    retrievalOutcome,
                    retrievalValidation,
                    telemetry);
        }

        boolean abstentionRequired = retrievalScore.isEmpty() && !retrievalValidation.safe();

        P7Decision p7FloorDecision =
                P7BaselineFloorSelector.resolve(p6Floor, toolScore, retrievalScore, abstentionRequired);
        Decision floorDecision = P7BaselineFloorSelector.toP15Decision(p7FloorDecision);
        if (floorDecision.winner() == WinnerKind.ABSTENTION) {
            Optional<ParentSelectionResult> safeParent =
                    p6Parent.or(
                            () ->
                                    recoverCampaignParentForAbstention(
                                            base,
                                            plan,
                                            clarificationDecision,
                                            telemetry,
                                            RagExperimentalPresetCode.P6));
            if (safeParent.isPresent()) {
                floorDecision =
                        new Decision(
                                WinnerKind.PARENT_P6,
                                toCampaignBaseline(safeParent).or(() -> toParentBaseline(safeParent)),
                                Optional.empty(),
                                true,
                                false,
                                false,
                                "",
                                true,
                                true);
            }
        }
        applyBaselineFloorTelemetry(telemetry, floorDecision);

        return executeBaselineFloorDecision(
                floorDecision,
                Optional.empty(),
                p6Parent,
                base,
                plan,
                clarificationDecision,
                routing,
                clarifyBeforeQu,
                memoryBeforeQu,
                quStages,
                clarifyAfterQu,
                telemetry,
                Optional.empty(),
                retrievalOutcome,
                toolResult,
                FcGate.notAttempted(FunctionCallingOutcome.SUPPRESSED_BY_DETERMINISTIC_TOOL));
    }

    private ExecutionOutcome executeDeterministicToolRouteWithoutBaselineFloor(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu,
            DeterministicToolExecutionResult toolResult,
            Optional<MonotonicRouteSafetyService.CandidateScore> toolScore,
            ExecutionOutcome retrievalOutcome,
            RouteCandidateValidationResult retrievalValidation,
            MonotonicSafetyTelemetry telemetry) {
        if (toolScore.isPresent()) {
            return finishDeterministicToolTerminal(
                    base,
                    plan,
                    clarificationDecision,
                    routing,
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu,
                    toolResult);
        }

        if (telemetry.toolCandidateRejected()) {
            telemetry.selectedCandidateSource("RETRIEVAL")
                    .monotonicRegressionPrevented(true)
                    .parentFallbackUsed(true)
                    .constraintCoverageStatus(retrievalValidation.constraintCoverageStatus());
        }
        return finalizeIntegratedRetrievalOutcome(retrievalOutcome, telemetry, true);
    }

    private ExecutionOutcome finishDeterministicToolTerminal(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu,
            DeterministicToolExecutionResult toolResult) {
        DeterministicToolKind kind =
                toolResult.toolKind().orElseThrow(() -> new IllegalStateException("tool kind missing on success"));
        RagExecutionResult partial = buildDeterministicToolPartial(base, toolResult, kind);
        JudgeSnapshot judge =
                runJudge(
                        base,
                        plan,
                        routing.routeKind(),
                        partial.workflowName(),
                        JudgeCandidateSource.DETERMINISTIC_TOOL,
                        partial.answerText());
        RagExecutionResult judgedPartial = applyJudgeToResult(partial, judge);
        ExecutionTrace trace =
                RagExecutionTraceSupport.assembleTrace(
                        base,
                        judgedPartial,
                        partial.workflowName(),
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        List.of(),
                        clarifyAfterQu,
                        routing.routingStages(),
                        RagExecutionTraceSupport.projectDeterministicToolStages(toolResult),
                        List.of(),
                        toolResult,
                        false,
                        FunctionCallingOutcome.SUPPRESSED_BY_DETERMINISTIC_TOOL,
                        "",
                        false,
                        AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_DETERMINISTIC_TOOL),
                        judge,
                        routing.snapshotForTrace(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_EXECUTED_TERMINALLY,
                                false,
                                Optional.empty(),
                                false),
                        clarificationDecision);
        return new ExecutionOutcome(judgedPartial, trace);
    }

    private static RagExecutionResult buildDeterministicToolPartial(
            ExecutionContext base, DeterministicToolExecutionResult toolResult, DeterministicToolKind kind) {
        return new RagExecutionResult(
                toolResult.answerText(),
                "deterministic-tool",
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                base.knowledgeSnapshotSelection().orderedSnapshotIds(),
                ExecutionTrace.placeholder(),
                "deterministic-tool",
                DeterministicToolKindMappings.toQueryType(kind),
                true,
                List.of(),
                Optional.empty(),
                List.of());
    }

    private ExecutionOutcome executeFunctionCallingRoute(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu) {
        FcGate fcGate = evaluateFunctionCallingGate(base, plan);
        if (fcGate.functionCallingOutcome() == FunctionCallingOutcome.EXECUTED_SUCCESS
                && fcGate.functionCallingShortCircuited()) {
            RouteCandidateValidationResult validation =
                    monotonicRouteSafetyService.validateFunctionResult(
                            plan, fcGate.fcResult().orElseThrow());
            if (!validation.safe()) {
                appendMonotonicSafetyStage(
                        clarifyAfterQu,
                        MonotonicSafetyTelemetry.create()
                                .candidateFunctionConsidered(true)
                                .functionCandidateRejected(true)
                                .monotonicRegressionPrevented(true)
                                .parentFallbackUsed(true)
                                .selectedCandidateSource("RETRIEVAL")
                                .rejectCandidate("FUNCTION", String.join(",", validation.rejectionReasons()))
                                .constraintCoverageStatus(validation.constraintCoverageStatus()));
                AdaptiveRouteKind fb =
                        routing.fallbackWorkflowRouteKind()
                                .orElse(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
                DeterministicToolExecutionResult toolResult =
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_ATTEMPTED,
                                List.of("suppressed_by_routing_fc"),
                                Optional.empty());
                return executeWorkflowRoute(
                        base,
                        plan,
                        clarificationDecision,
                        routing.withOutcome(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                                true,
                                Optional.of(fb),
                                true),
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        clarifyAfterQu,
                        RagExecutionTraceSupport.projectDeterministicToolStages(toolResult),
                        fcGate.stageTraces(),
                        toolResult,
                        fcGate,
                        AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_FUNCTION_CALLING));
            }
            return finishFunctionCallingTerminal(
                    base,
                    plan,
                    clarificationDecision,
                    routing,
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu,
                    fcGate);
        }

        AdaptiveRouteKind fb = routing.fallbackWorkflowRouteKind().orElseThrow();
        DeterministicToolExecutionResult toolResult =
                DeterministicToolExecutionResult.skipped(
                        DeterministicToolOutcome.NOT_ATTEMPTED,
                        List.of("suppressed_by_routing_fc"),
                        Optional.empty());
        return executeWorkflowRoute(
                base,
                plan,
                clarificationDecision,
                routing.withOutcome(
                        AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                        true,
                        Optional.of(fb),
                        true),
                clarifyBeforeQu,
                memoryBeforeQu,
                quStages,
                clarifyAfterQu,
                RagExecutionTraceSupport.projectDeterministicToolStages(toolResult),
                fcGate.stageTraces(),
                toolResult,
                fcGate,
                AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_FUNCTION_CALLING));
    }

    private ExecutionOutcome finishFunctionCallingTerminal(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu,
            FcGate fcGate) {
        FunctionCallingExecutionResult fr = fcGate.fcResult().orElseThrow();
        DeterministicToolKind k =
                fr.selectedToolKind()
                        .orElseThrow(() -> new IllegalStateException("tool kind missing on FC success"));
        RagExecutionResult partial = buildFunctionCallingPartial(base, fr, k);
        JudgeSnapshot judge =
                runJudge(
                        base,
                        plan,
                        routing.routeKind(),
                        partial.workflowName(),
                        JudgeCandidateSource.FUNCTION_CALLING,
                        partial.answerText());
        RagExecutionResult judgedPartial = applyJudgeToResult(partial, judge);
        DeterministicToolExecutionResult toolResult =
                DeterministicToolExecutionResult.skipped(
                        DeterministicToolOutcome.NOT_ATTEMPTED,
                        List.of("suppressed_by_routing_fc"),
                        Optional.empty());
        ExecutionTrace trace =
                RagExecutionTraceSupport.assembleTrace(
                        base,
                        judgedPartial,
                        partial.workflowName(),
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        List.of(),
                        clarifyAfterQu,
                        routing.routingStages(),
                        RagExecutionTraceSupport.projectDeterministicToolStages(toolResult),
                        fcGate.stageTraces(),
                        toolResult,
                        fcGate.functionCallingAttempted(),
                        fcGate.functionCallingOutcome(),
                        fcGate.functionCallingToolKind(),
                        fcGate.functionCallingShortCircuited(),
                        AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_FUNCTION_CALLING),
                        judge,
                        routing.snapshotForTrace(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_EXECUTED_TERMINALLY,
                                false,
                                Optional.empty(),
                                false),
                        clarificationDecision);
        return new ExecutionOutcome(judgedPartial, trace);
    }

    private static RagExecutionResult buildFunctionCallingPartial(
            ExecutionContext base, FunctionCallingExecutionResult fr, DeterministicToolKind k) {
        return new RagExecutionResult(
                fr.answerText(),
                "function-calling",
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                base.knowledgeSnapshotSelection().orderedSnapshotIds(),
                ExecutionTrace.placeholder(),
                "function-calling",
                DeterministicToolKindMappings.toQueryType(k),
                true,
                List.of(),
                Optional.empty(),
                List.of());
    }

    private ExecutionOutcome executeAdvisorRoute(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu) {
        String advisorWorkflowName = selectRetrievalWorkflowNameForAdvisor(base.resolved().toRagConfig());
        AdvisorPhaseResult advisorPhase = runAdvisorPhase(base, plan, advisorWorkflowName);

        DeterministicToolExecutionResult toolResult =
                DeterministicToolExecutionResult.skipped(
                        DeterministicToolOutcome.NOT_ATTEMPTED,
                        List.of("suppressed_by_routing_advisor"),
                        Optional.empty());

        if (!advisorPhase.snapshot().advisorAttempted()) {
            // Suppressed or failed: fallback to retrieval workflow only.
            return executeWorkflowRoute(
                    base,
                    plan,
                    clarificationDecision,
                    routing.withOutcome(
                            AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                            true,
                            Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                            true),
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu,
                    RagExecutionTraceSupport.projectDeterministicToolStages(toolResult),
                    List.of(),
                    toolResult,
                    FcGate.notAttempted(FunctionCallingOutcome.NOT_APPLICABLE),
                    advisorPhase.snapshot());
        }

        // Success: continue to retrieval workflow generation.
        return executeWorkflowRoute(
                advisorPhase.ctx(),
                plan,
                clarificationDecision,
                routing.withOutcome(
                        AdaptiveRoutingOutcome.PRIMARY_ROUTE_CONTINUED_TO_WORKFLOW,
                        false,
                        Optional.empty(),
                        true),
                clarifyBeforeQu,
                memoryBeforeQu,
                quStages,
                clarifyAfterQu,
                RagExecutionTraceSupport.projectDeterministicToolStages(toolResult),
                List.of(),
                toolResult,
                FcGate.notAttempted(FunctionCallingOutcome.NOT_APPLICABLE),
                advisorPhase.snapshot());
    }

    private ExecutionOutcome executeWorkflowRoute(
            ExecutionContext ctxForWorkflow,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu) {
        DeterministicToolExecutionResult toolResult =
                DeterministicToolExecutionResult.skipped(DeterministicToolOutcome.NOT_ATTEMPTED, List.of("suppressed_by_routing_workflow"), Optional.empty());
        return executeWorkflowRoute(
                ctxForWorkflow,
                plan,
                clarificationDecision,
                routing,
                clarifyBeforeQu,
                memoryBeforeQu,
                quStages,
                clarifyAfterQu,
                RagExecutionTraceSupport.projectDeterministicToolStages(toolResult),
                List.of(),
                toolResult,
                FcGate.notAttempted(FunctionCallingOutcome.NOT_APPLICABLE),
                AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_ROUTING));
    }

    private ExecutionOutcome executeWorkflowRoute(
            ExecutionContext ctxForWorkflow,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu,
            List<ExecutionStageTrace> toolStages,
            List<ExecutionStageTrace> fcStages,
            DeterministicToolExecutionResult toolResult,
            FcGate fcGate,
            AdvisorSnapshot advisorSnapshot) {
        List<ExecutionStageTrace> reasoningStages = new ArrayList<>();
        ExecutionContext effectiveCtx = ctxForWorkflow;
        var rag = ctxForWorkflow.resolved().toRagConfig();
        if (rag.reasoningEnabled()) {
            var planResult = structuredAnswerPlanService.plan(ctxForWorkflow, plan);
            reasoningStages.addAll(planResult.stageTraces());
            if (planResult.plan().isPresent()) {
                effectiveCtx = executionContextFactory.attachStructuredAnswerPlan(ctxForWorkflow, planResult.plan().get());
            }
        }

        ExecutionWorkflow workflow =
                selectExecutableWorkflow(effectiveCtx, workflowSelector.select(effectiveCtx));
        String wname = workflow.workflowName();
        RagExecutionContextHolder.set(toRagExecutionContextHolder(effectiveCtx));
        try {
            RuntimeObservability obs = runtimeObservability != null ? runtimeObservability.getIfAvailable() : null;
            final int promptChars =
                    plan != null && plan.rewrittenQueryText() != null ? plan.rewrittenQueryText().length() : 0;
            final String workflowFamily = wname;
            final ExecutionContext workflowCtx = effectiveCtx;
            RagExecutionResult partial =
                    obs != null
                            ? obs.promptCompose(workflowFamily, promptChars, () -> workflow.execute(workflowCtx))
                            : workflow.execute(effectiveCtx);

            // Minimal post-verification (no retry): only when reasoning is enabled and strategy explicitly requests verify.
            if (rag.reasoningEnabled()
                    && rag.reasoningStrategy() != null
                    && "PLAN_AND_VERIFY".equalsIgnoreCase(rag.reasoningStrategy())) {
                String contextPreview = extractPackedContextPreview(partial.workflowStageTraces());
                var v = answerVerificationService.verify(effectiveCtx, plan.rewrittenQueryText(), contextPreview, partial.answerText());
                reasoningStages.add(v.stageTrace());
            }

            JudgeSnapshot judge =
                    runJudge(
                            effectiveCtx,
                            plan,
                            routing.routeKind(),
                            wname,
                            JudgeCandidateSource.WORKFLOW,
                            partial.answerText());
            RagExecutionResult judgedPartial = applyJudgeToResult(partial, judge);
            ExecutionTrace trace =
                    RagExecutionTraceSupport.assembleTrace(
                            effectiveCtx,
                            judgedPartial,
                            wname,
                            clarifyBeforeQu,
                            memoryBeforeQu,
                            quStages,
                            reasoningStages,
                            clarifyAfterQu,
                            routing.routingStages(),
                            toolStages,
                            fcStages,
                            toolResult,
                            fcGate.functionCallingAttempted(),
                            fcGate.functionCallingOutcome(),
                            fcGate.functionCallingToolKind(),
                            fcGate.functionCallingShortCircuited(),
                            advisorSnapshot,
                            judge,
                            routing.snapshotForTrace(),
                            clarificationDecision);
            return new ExecutionOutcome(judgedPartial, trace);
        } finally {
            RagExecutionContextHolder.clear();
        }
    }

    private static String selectRetrievalWorkflowNameForAdvisor(RagConfig rag) {
        var strategy = rag.materializationStrategy();
        if (strategy == MaterializationStrategy.DOCUMENT_LEVEL) {
            return "DocumentDenseRagWorkflow";
        }
        if ((strategy == MaterializationStrategy.CHUNK_LEVEL
                || strategy == MaterializationStrategy.HYBRID)
                && rag.metadataEnabled()) {
            return "ChunkDenseMetadataWorkflow";
        }
        return "ChunkDenseRagWorkflow";
    }

    private static String extractPackedContextPreview(List<ExecutionStageTrace> stages) {
        if (stages == null || stages.isEmpty()) {
            return "";
        }
        for (ExecutionStageTrace s : stages) {
            if (s != null && "packed_context_preview".equals(s.stageName()) && s.message() != null) {
                String m = s.message();
                int idx = m.indexOf("preview=");
                if (idx >= 0) {
                    return m.substring(idx + "preview=".length()).trim();
                }
                return m.trim();
            }
        }
        return "";
    }

    private RagExecutionResult finishClarificationAskShortCircuit(
            ExecutionContext withPlan,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu,
            ClarificationExecutionResult cr,
            ClarificationDecision clarificationDecision) {
        DeterministicToolExecutionResult toolResult =
                DeterministicToolExecutionResult.skipped(
                        DeterministicToolOutcome.NOT_ATTEMPTED,
                        List.of("suppressed_clarification_ask"),
                        Optional.empty());
        List<ExecutionStageTrace> toolStages = RagExecutionTraceSupport.projectDeterministicToolStages(toolResult);
        FcGate fcGate = FcGate.notAttempted(FunctionCallingOutcome.SUPPRESSED_BY_CLARIFICATION);
        RagExecutionResult partial =
                RagExecutionResult.withPlaceholderTrace(
                        cr.answerText(),
                        "clarification",
                        false,
                        false,
                        withPlan.knowledgeSnapshotSelection().orderedSnapshotIds(),
                        "none",
                        List.of());
        ExecutionTrace trace =
                RagExecutionTraceSupport.assembleTrace(
                        withPlan,
                        partial,
                        "clarification",
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        List.of(),
                        clarifyAfterQu,
                        List.of(),
                        toolStages,
                        fcGate.stageTraces(),
                        toolResult,
                        fcGate.functionCallingAttempted(),
                        fcGate.functionCallingOutcome(),
                        fcGate.functionCallingToolKind(),
                        fcGate.functionCallingShortCircuited(),
                        AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_CLARIFICATION),
                        JudgeSnapshot.notAttempted(JudgeCandidateSource.WORKFLOW),
                        new RoutingSnapshot(
                                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                                Optional.empty(),
                                List.of(),
                                false,
                                AdaptiveRoutingOutcome.SUPPRESSED_BY_CLARIFICATION_SHORT_CIRCUIT,
                                false,
                                Optional.empty(),
                                false),
                        clarificationDecision);
        return partial.withFinalTrace(trace);
    }

    private JudgeSnapshot runJudge(
            ExecutionContext ctx,
            QueryPlan plan,
            AdaptiveRouteKind routeKind,
            String workflowName,
            JudgeCandidateSource source,
            String candidateAnswerText
    ) {
        if (!ctx.resolved().toRagConfig().judgeEnabled()) {
            return JudgeSnapshot.notAttempted(source);
        }
        JudgeExecutionResult r =
                judgeStrategy.execute(ctx, plan, routeKind, workflowName, source, candidateAnswerText);
        return JudgeSnapshot.fromResult(source, r);
    }

    private static RagExecutionResult applyJudgeToResult(RagExecutionResult base, JudgeSnapshot judge) {
        if (judge.finalAnswerFromRetry()) {
            return new RagExecutionResult(
                    judge.finalAnswerText(),
                    base.workflowName(),
                    base.retrievalUsed(),
                    base.metadataUsed(),
                    base.usedResolvedConfigSnapshotId(),
                    base.usedConfigHash(),
                    base.usedKnowledgeSnapshotIds(),
                    base.executionTrace(),
                    base.toolUsedLabel(),
                    base.resolvedQueryType(),
                    base.usedTool(),
                    base.workflowStageTraces(),
                    base.retrievalDiagnostics(),
                    base.responseSources());
        }
        return base;
    }

    private AdvisorPhaseResult runAdvisorPhase(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        AdvisorDecision decision = advisorPolicyResolver.resolve(ctx, plan);
        List<ExecutionStageTrace> stages = new ArrayList<>();
        stages.add(
                new ExecutionStageTrace(
                        "advisor_policy",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        "selected=" + decision.selected()));
        if (!decision.selected()) {
            return new AdvisorPhaseResult(ctx, AdvisorSnapshot.suppressed(stages));
        }
        AdvisorExecutionResult result = advisorStrategy.execute(ctx, plan, workflowName, decision);
        if (result.outcome() != AdvisorOutcome.FAILED_RESERVED_KIND) {
            stages.add(advisorRetrievalStage(result));
            if (includesContextPackStage(result.outcome())) {
                stages.add(advisorContextPackStage(result));
            }
        }
        ExecutionContext out = ctx;
        var packed = result.packedContextSet();
        if (result.outcome() == AdvisorOutcome.EXECUTED_SUCCESS && packed.isPresent()) {
            out = executionContextFactory.attachAdvisorPackedContextSet(ctx, packed.get());
        }
        return new AdvisorPhaseResult(out, AdvisorSnapshot.fromExecution(stages, result));
    }

    private static boolean includesContextPackStage(AdvisorOutcome outcome) {
        return outcome == AdvisorOutcome.EXECUTED_SUCCESS || outcome == AdvisorOutcome.EXECUTED_FAILED_PACKING;
    }

    private static ExecutionStageTrace advisorRetrievalStage(AdvisorExecutionResult result) {
        ExecutionStageOutcome o =
                result.outcome() == AdvisorOutcome.EXECUTED_FAILED_RETRIEVAL
                        ? ExecutionStageOutcome.FAILED
                        : ExecutionStageOutcome.SUCCESS;
        return new ExecutionStageTrace(
                "advisor_retrieval", 0L, o, "outcome=" + result.outcome());
    }

    private static ExecutionStageTrace advisorContextPackStage(AdvisorExecutionResult result) {
        ExecutionStageOutcome o =
                result.outcome() == AdvisorOutcome.EXECUTED_FAILED_PACKING
                        ? ExecutionStageOutcome.FAILED
                        : ExecutionStageOutcome.SUCCESS;
        return new ExecutionStageTrace(
                "advisor_context_pack", 0L, o, "outcome=" + result.outcome());
    }

    private FcGate evaluateFunctionCallingGate(ExecutionContext ctx, QueryPlan plan) {
        var rag = ctx.resolved().toRagConfig();
        if (!rag.functionCallingEnabled()) {
            return FcGate.notAttempted(FunctionCallingOutcome.DISABLED_BY_CONFIG);
        }
        if (plan.ambiguityAssessment().status() != AmbiguityStatus.SUFFICIENT) {
            return FcGate.notAttempted(FunctionCallingOutcome.SUPPRESSED_BY_AMBIGUITY);
        }
        Optional<FunctionCallingDecision> decision = functionCallingPolicyResolver.resolve(ctx, plan);
        if (decision.isEmpty()) {
            return FcGate.notAttempted(FunctionCallingOutcome.NOT_APPLICABLE);
        }
        FunctionCallingExecutionResult fr =
                functionCallingStrategy.tryExecute(ctx, plan, decision.get());
        boolean shortCircuited =
                fr.outcome() == FunctionCallingOutcome.EXECUTED_SUCCESS && fr.shortCircuited();
        String toolKindStr =
                fr.selectedToolKind()
                        .or(() -> fr.proposal().flatMap(FunctionCallProposal::toolKind))
                        .map(Enum::name)
                        .orElse("");
        return new FcGate(
                fr.backendFunctionCallAttempted() || fr.nativeProviderFunctionCallAttempted(),
                fr.outcome(),
                toolKindStr,
                shortCircuited,
                Optional.of(fr),
                fr.stageTraces());
    }

    /**
     * Chat/product turns avoid failing when no ACTIVE knowledge snapshots exist: dense/full-corpus workflows fall back to
     * {@link DirectLlmWorkflow} so greetings and general questions still get an LLM reply when allowed by config.
     */
    ExecutionWorkflow selectExecutableWorkflow(ExecutionContext ctx, ExecutionWorkflow selected) {
        String requestedName = selected.workflowName();
        if (requiresKnowledgeSnapshots(requestedName)
                && ctx.knowledgeSnapshotSelection().orderedSnapshotIds().isEmpty()) {
            log.warn(
                    "chat_snapshot_fallback correlationId={} requestedWorkflow={} fallbackWorkflow=DirectLlmWorkflow reason=no_active_snapshots",
                    ctx.correlationId(),
                    requestedName);
            return snapshotFallbackDirectLlmWorkflow;
        }
        return selected;
    }

    private static boolean requiresKnowledgeSnapshots(String workflowName) {
        return "FullCorpusWorkflow".equals(workflowName)
                || "DocumentDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseMetadataWorkflow".equals(workflowName);
    }

    private static RagExecutionContext toRagExecutionContextHolder(ExecutionContext ctx) {
        return new RagExecutionContext(
                ctx.conversationId() != null ? ctx.conversationId().toString() : null,
                ctx.userId() != null ? ctx.userId().toString() : null,
                ctx.projectId() != null ? ctx.projectId().toString() : null,
                ctx.resolved().toRagConfig(),
                ctx.documentFilter(),
                ctx.correlationId());
    }

    private ExecutionOutcome executeIntegratedAdaptiveRoute(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu) {
        var rag = base.resolved().toRagConfig();
        MonotonicSafetyTelemetry telemetry =
                MonotonicSafetyTelemetry.create()
                        .candidateToolConsidered(rag.toolsEnabled())
                        .candidateFunctionConsidered(rag.functionCallingEnabled())
                        .candidateRetrievalConsidered(rag.useRetrieval());

        Optional<MonotonicRouteSafetyService.CandidateScore> toolScore = Optional.empty();
        DeterministicToolExecutionResult toolResult =
                DeterministicToolExecutionResult.skipped(
                        DeterministicToolOutcome.NOT_ATTEMPTED,
                        List.of("integrated_route_probe"),
                        Optional.empty());
        if (rag.toolsEnabled()) {
            toolResult = deterministicToolStrategy.tryExecute(base, plan);
            if (toolResult.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS && toolResult.success()) {
                RouteCandidateValidationResult validation =
                        monotonicRouteSafetyService.validateToolResult(plan, toolResult);
                if (validation.safe()) {
                    toolScore =
                            Optional.of(
                                    new MonotonicRouteSafetyService.CandidateScore(
                                            "TOOL", validation, toolResult.answerText()));
                } else {
                    telemetry.toolCandidateRejected(true)
                            .rejectCandidate("TOOL", String.join(",", validation.rejectionReasons()));
                }
            }
        }

        Optional<MonotonicRouteSafetyService.CandidateScore> functionScore = Optional.empty();
        Optional<RouteCandidateValidationResult> rejectedFunctionValidation = Optional.empty();
        FcGate fcGate = FcGate.notAttempted(FunctionCallingOutcome.NOT_APPLICABLE);
        if (rag.functionCallingEnabled()) {
            fcGate = evaluateFunctionCallingGate(base, plan);
            if (fcGate.functionCallingOutcome() == FunctionCallingOutcome.EXECUTED_SUCCESS
                    && fcGate.functionCallingShortCircuited()) {
                String functionAnswer = fcGate.fcResult().orElseThrow().answerText();
                RouteCandidateValidationResult validation =
                        monotonicRouteSafetyService.validateFunctionResult(
                                plan, fcGate.fcResult().orElseThrow());
                if (validation.safe()) {
                    functionScore =
                            Optional.of(
                                    new MonotonicRouteSafetyService.CandidateScore(
                                            "FUNCTION", validation, functionAnswer));
                } else {
                    rejectedFunctionValidation = Optional.of(validation);
                    telemetry.functionCandidateRejected(true)
                            .rejectCandidate("FUNCTION", String.join(",", validation.rejectionReasons()));
                }
            }
        }

        ExecutionOutcome retrievalOutcome =
                executeWorkflowRoute(
                        base,
                        plan,
                        clarificationDecision,
                        routing.withOutcome(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                                true,
                                Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                                true),
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        clarifyAfterQu,
                        RagExecutionTraceSupport.projectDeterministicToolStages(toolResult),
                        fcGate.stageTraces(),
                        toolResult,
                        fcGate,
                        AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_ROUTING));

        boolean retrievalAbstained = retrievalOutcome.trace().abstentionTriggered();
        RouteCandidateValidationResult retrievalValidation =
                monotonicRouteSafetyService.validateRetrievalAnswer(
                        plan, retrievalOutcome.result().answerText(), retrievalAbstained);
        if (!retrievalValidation.safe()) {
            telemetry.retrievalCandidateRejected(true)
                    .rejectCandidate("RETRIEVAL", String.join(",", retrievalValidation.rejectionReasons()));
        }

        Optional<MonotonicRouteSafetyService.CandidateScore> retrievalScore = Optional.empty();
        if (retrievalValidation.safe()) {
            retrievalScore =
                    Optional.of(
                            new MonotonicRouteSafetyService.CandidateScore(
                                    "RETRIEVAL",
                                    retrievalValidation,
                                    retrievalOutcome.result().answerText()));
        }

        Optional<ParentSelectionResult> p7Parent =
                probeParentPreset(
                        base, plan, clarificationDecision, telemetry, RagExperimentalPresetCode.P7);
        Optional<ParentSelectionResult> p6Parent =
                probeParentPreset(
                        base, plan, clarificationDecision, telemetry, RagExperimentalPresetCode.P6);

        boolean abstentionRequired = retrievalScore.isEmpty() && !retrievalValidation.safe();

        Optional<ParentBaseline> p7Floor = toCampaignBaseline(p7Parent);
        if (p7Floor.isEmpty()
                && useExtendedParentFloor(telemetry, functionScore, toolScore, abstentionRequired)) {
            p7Floor = toParentBaseline(p7Parent);
        }
        Optional<ParentBaseline> p6Floor =
                p7Floor.isPresent()
                        ? Optional.empty()
                        : toCampaignBaseline(p6Parent);
        if (p6Floor.isEmpty()
                && p7Floor.isEmpty()
                && useExtendedParentFloor(telemetry, functionScore, toolScore, abstentionRequired)) {
            p6Floor = toParentBaseline(p6Parent);
        }

        Decision floorDecision =
                P15BaselineFloorSelector.resolve(
                        p7Floor,
                        p6Floor,
                        functionScore,
                        toolScore,
                        retrievalScore,
                        abstentionRequired);
        if (floorDecision.winner() == WinnerKind.ABSTENTION) {
            Optional<ParentSelectionResult> safeParent =
                    p7Parent.or(() -> p6Parent).or(() -> recoverCampaignParentForAbstention(
                            base, plan, clarificationDecision, telemetry, RagExperimentalPresetCode.P7))
                            .or(() -> recoverCampaignParentForAbstention(
                                    base, plan, clarificationDecision, telemetry, RagExperimentalPresetCode.P6));
            if (safeParent.isPresent()) {
                floorDecision =
                        new Decision(
                                safeParent.get().parentPreset() == RagExperimentalPresetCode.P6
                                        ? WinnerKind.PARENT_P6
                                        : WinnerKind.PARENT_P7,
                                toCampaignBaseline(safeParent).or(() -> toParentBaseline(safeParent)),
                                Optional.empty(),
                                true,
                                false,
                                false,
                                "",
                                true,
                                true);
            }
        }
        applyBaselineFloorTelemetry(telemetry, floorDecision);

        return executeBaselineFloorDecision(
                floorDecision,
                p7Parent,
                p6Parent,
                base,
                plan,
                clarificationDecision,
                routing,
                clarifyBeforeQu,
                memoryBeforeQu,
                quStages,
                clarifyAfterQu,
                telemetry,
                rejectedFunctionValidation,
                retrievalOutcome,
                toolResult,
                fcGate);
    }

    private static boolean useExtendedParentFloor(
            MonotonicSafetyTelemetry telemetry,
            Optional<MonotonicRouteSafetyService.CandidateScore> functionScore,
            Optional<MonotonicRouteSafetyService.CandidateScore> toolScore,
            boolean abstentionRequired) {
        if (telemetry.functionCandidateRejected() || functionScore.isPresent()) {
            return true;
        }
        if (hasSafeConstraintCompleteTool(toolScore)) {
            return false;
        }
        return abstentionRequired;
    }

    private static boolean hasSafeConstraintCompleteTool(
            Optional<MonotonicRouteSafetyService.CandidateScore> toolScore) {
        return toolScore.filter(P15BaselineFloorSelector::isDemonstrablyStrongNative).isPresent();
    }

    private static Optional<ParentBaseline> toParentBaseline(Optional<ParentSelectionResult> parent) {
        return parent.map(
                selection ->
                        new ParentBaseline(
                                selection.parentPreset(),
                                selection.source(),
                                selection.validation()));
    }

    private static Optional<ParentBaseline> toCampaignBaseline(Optional<ParentSelectionResult> parent) {
        return parent.filter(ParentSelectionResult::campaignOutcomeReused)
                .flatMap(
                        selection ->
                                P15BaselineFloorSelector.toBaselineFromValidation(
                                        selection.parentPreset(),
                                        selection.source(),
                                        selection.validation()));
    }

    private static void applyBaselineFloorTelemetry(
            MonotonicSafetyTelemetry telemetry, Decision decision) {
        decision.baseline()
                .ifPresent(
                        baseline -> {
                            telemetry.baselineCandidateSource(baseline.source())
                                    .baselineCandidatePresetCode(baseline.preset().name());
                        });
        telemetry.baselineCandidateSelected(decision.baselineCandidateSelected())
                .baselineOverrideAttempted(decision.baselineOverrideAttempted())
                .baselineOverrideAccepted(decision.baselineOverrideAccepted())
                .baselineOverrideRejectedReason(decision.baselineOverrideRejectedReason())
                .monotonicFloorApplied(decision.monotonicFloorApplied())
                .monotonicFloorPreventedRegression(decision.monotonicFloorPreventedRegression());
        if (decision.baselineCandidateSelected() && decision.baseline().isPresent()) {
            telemetry.baselineFloorReason(
                    floorReasonFor(decision.winner(), decision.baselineOverrideRejectedReason()));
        }
        if (decision.monotonicFloorPreventedRegression()) {
            telemetry.monotonicRegressionPrevented(true);
        }
    }

    private static String floorReasonFor(WinnerKind winner, String overrideRejectedReason) {
        if (overrideRejectedReason != null && !overrideRejectedReason.isBlank()) {
            return "baseline_floor_kept_parent:" + overrideRejectedReason;
        }
        return switch (winner) {
            case PARENT_P7 -> "parent_p7_over_abstention";
            case PARENT_P6 -> "parent_p6_over_abstention";
            default -> "baseline_floor_parent_selected";
        };
    }

    private ExecutionOutcome executeBaselineFloorDecision(
            Decision decision,
            Optional<ParentSelectionResult> p7Parent,
            Optional<ParentSelectionResult> p6Parent,
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RoutingSnapshot routing,
            List<ExecutionStageTrace> clarifyBeforeQu,
            List<ExecutionStageTrace> memoryBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarifyAfterQu,
            MonotonicSafetyTelemetry telemetry,
            Optional<RouteCandidateValidationResult> rejectedFunctionValidation,
            ExecutionOutcome retrievalOutcome,
            DeterministicToolExecutionResult toolResult,
            FcGate fcGate) {
        return switch (decision.winner()) {
            case PARENT_P7, PARENT_P6 ->
                    commitParentSelection(
                            (decision.winner() == WinnerKind.PARENT_P6 ? p6Parent : p7Parent).orElseThrow(),
                            clarifyAfterQu,
                            telemetry,
                            rejectedFunctionValidation,
                            decision.nativeWinner()
                                    .filter(nativeWinner -> "FUNCTION".equals(nativeWinner.source()))
                                    .isPresent());
            case FUNCTION -> {
                MonotonicRouteSafetyService.CandidateScore winner = decision.nativeWinner().orElseThrow();
                telemetry.selectedCandidateSource(winner.source())
                        .routeConfidence(winner.validation().confidence())
                        .constraintCoverageStatus(winner.validation().constraintCoverageStatus());
                appendMonotonicSafetyStage(clarifyAfterQu, telemetry);
                yield finishFunctionCallingTerminal(
                        base,
                        plan,
                        clarificationDecision,
                        routing,
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        clarifyAfterQu,
                        fcGate);
            }
            case TOOL -> {
                MonotonicRouteSafetyService.CandidateScore winner = decision.nativeWinner().orElseThrow();
                telemetry.selectedCandidateSource(winner.source())
                        .routeConfidence(winner.validation().confidence())
                        .constraintCoverageStatus(winner.validation().constraintCoverageStatus());
                appendMonotonicSafetyStage(clarifyAfterQu, telemetry);
                yield finishDeterministicToolTerminal(
                        base,
                        plan,
                        clarificationDecision,
                        routing,
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        clarifyAfterQu,
                        toolResult);
            }
            case RETRIEVAL -> {
                MonotonicRouteSafetyService.CandidateScore winner =
                        decision.nativeWinner()
                                .orElse(
                                        new MonotonicRouteSafetyService.CandidateScore(
                                                "RETRIEVAL",
                                                monotonicRouteSafetyService.validateRetrievalAnswer(
                                                        plan,
                                                        retrievalOutcome.result().answerText(),
                                                        retrievalOutcome.trace().abstentionTriggered()),
                                                retrievalOutcome.result().answerText()));
                telemetry.selectedCandidateSource(winner.source())
                        .routeConfidence(winner.validation().confidence())
                        .constraintCoverageStatus(winner.validation().constraintCoverageStatus());
                if (telemetry.functionCandidateRejected() || telemetry.toolCandidateRejected()) {
                    telemetry.parentFallbackUsed(true);
                }
                appendMonotonicSafetyStage(clarifyAfterQu, telemetry);
                yield finalizeIntegratedRetrievalOutcome(retrievalOutcome, telemetry, true);
            }
            case ABSTENTION -> {
                telemetry.selectedCandidateSource("ABSTENTION");
                appendMonotonicSafetyStage(clarifyAfterQu, telemetry);
                yield finalizeIntegratedAbstentionOutcome(retrievalOutcome, telemetry, plan);
            }
        };
    }

    private Optional<ParentSelectionResult> probeParentPreset(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            MonotonicSafetyTelemetry telemetry,
            RagExperimentalPresetCode parentPreset) {
        telemetry.parentCandidateConsidered(true);
        String source = parentSourceFor(parentPreset);
        ParentExecutionAttempt parentAttempt =
                executeIsolatedParentPreset(base, plan, clarificationDecision, parentPreset);
        if (parentAttempt.outcome().isEmpty()) {
            if (LabBenchmarkExecutionContext.currentDatasetQuestionId().isPresent()) {
                telemetry.parentCampaignOutcomeMissing(true);
            }
            telemetry.rejectCandidate(source, CampaignParentOutcome.MISSING_PARENT_REJECTION);
            return Optional.empty();
        }
        ParentCandidateSnapshot parentSnapshot =
                ParentCandidateSnapshot.capture(
                        parentPreset,
                        parentAttempt.outcome().orElseThrow(),
                        parentAttempt.campaignRecord(),
                        parentAttempt.campaignOutcomeReused());
        RouteCandidateValidationResult parentValidation =
                validateParentCandidateOutcome(
                        plan,
                        parentSnapshot.toPreservedOutcome(),
                        parentAttempt.campaignRecord(),
                        parentAttempt.campaignOutcomeReused());
        if (!parentValidation.safe()) {
            telemetry.rejectCandidate(source, String.join(",", parentValidation.rejectionReasons()));
            return Optional.empty();
        }
        if (!P15ParentCandidateSafetyPolicy.isBaselineEligible(
                parentAttempt.campaignRecord(), parentValidation)) {
            telemetry.rejectCandidate(source, "parent_baseline_policy_rejected");
            return Optional.empty();
        }
        ExecutionOutcome preservedOutcome = parentSnapshot.toPreservedOutcome();
        ParentPreservationVerification preservation =
                verifyParentPreservation(
                        parentSnapshot, preservedOutcome, parentAttempt.campaignOutcomeReused());
        if (!preservation.preserved()) {
            telemetry.parentFinalAnswerHash(preservation.parentFinalAnswerHash())
                    .parentMatcherVisibleAnswerHash(preservation.parentMatcherVisibleAnswerHash())
                    .selectedFinalAnswerHash(preservation.selectedFinalAnswerHash())
                    .selectedMatcherVisibleAnswerHash(preservation.selectedMatcherVisibleAnswerHash())
                    .parentFinalAnswerPreserved(false)
                    .parentAnswerMismatchReason(preservation.mismatchReason());
            telemetry.rejectCandidate(source, preservation.mismatchReason());
            return Optional.empty();
        }
        return Optional.of(
                new ParentSelectionResult(
                        parentPreset,
                        source,
                        parentSnapshot,
                        parentValidation,
                        preservation,
                        parentAttempt.campaignOutcomeReused(),
                        parentAttempt.campaignRecord()));
    }

    private Optional<ParentSelectionResult> recoverCampaignParentForAbstention(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            MonotonicSafetyTelemetry telemetry,
            RagExperimentalPresetCode parentPreset) {
        IntegratedParentCampaignOutcomeResolver campaignResolver =
                parentCampaignOutcomeResolverProvider.getIfAvailable();
        if (campaignResolver == null) {
            return Optional.empty();
        }
        Optional<String> datasetQuestionId = campaignResolver.currentDatasetQuestionId();
        if (datasetQuestionId.isEmpty()) {
            return Optional.empty();
        }
        Optional<CampaignParentOutcome> campaignRecord =
                LabBenchmarkExecutionContext.campaignParentOutcome(
                        parentPreset.name(), datasetQuestionId.get());
        if (campaignRecord.isEmpty()) {
            return Optional.empty();
        }
        Optional<RouteCandidateValidationResult> trusted =
                P15ParentCandidateSafetyPolicy.trustedCampaignValidation(campaignRecord.get());
        if (trusted.isEmpty()) {
            return Optional.empty();
        }
        Optional<ExecutionOutcome> campaignOutcome =
                campaignResolver.tryResolve(parentPreset, datasetQuestionId.get());
        if (campaignOutcome.isEmpty()) {
            return Optional.empty();
        }
        String source = parentSourceFor(parentPreset);
        ParentCandidateSnapshot parentSnapshot =
                ParentCandidateSnapshot.capture(
                        parentPreset,
                        campaignOutcome.get(),
                        campaignRecord,
                        true);
        ExecutionOutcome preservedOutcome = parentSnapshot.toPreservedOutcome();
        ParentPreservationVerification preservation =
                verifyParentPreservation(parentSnapshot, preservedOutcome, true);
        if (!preservation.preserved()) {
            return Optional.empty();
        }
        return Optional.of(
                new ParentSelectionResult(
                        parentPreset,
                        source,
                        parentSnapshot,
                        trusted.get(),
                        preservation,
                        true,
                        campaignRecord));
    }

    private record ParentSelectionResult(
            RagExperimentalPresetCode parentPreset,
            String source,
            ParentCandidateSnapshot snapshot,
            RouteCandidateValidationResult validation,
            ParentPreservationVerification preservation,
            boolean campaignOutcomeReused,
            Optional<CampaignParentOutcome> campaignRecord) {}

    private Optional<ParentSelectionResult> probeSafeParentSelection(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            MonotonicSafetyTelemetry telemetry) {
        Optional<ParentSelectionResult> p7 =
                probeParentPreset(base, plan, clarificationDecision, telemetry, RagExperimentalPresetCode.P7);
        if (p7.isPresent()) {
            return p7;
        }
        return probeParentPreset(base, plan, clarificationDecision, telemetry, RagExperimentalPresetCode.P6);
    }

    private ExecutionOutcome commitParentSelection(
            ParentSelectionResult selection,
            List<ExecutionStageTrace> clarifyAfterQu,
            MonotonicSafetyTelemetry telemetry,
            Optional<RouteCandidateValidationResult> rejectedFunctionValidation,
            boolean preferOverSafeFunction) {
        if (preferOverSafeFunction) {
            telemetry.functionCandidateRejected(true)
                    .rejectCandidate("FUNCTION", "function_superseded_by_supported_parent");
        } else {
            rejectedFunctionValidation.ifPresent(telemetry::augmentFunctionRejectionWhenParentSupported);
        }
        telemetry.selectedCandidateSource(selection.source())
                .selectedParentPreset(selection.parentPreset().name())
                .parentFallbackUsed(true)
                .parentCampaignOutcomeReused(selection.campaignOutcomeReused())
                .parentFinalAnswerHash(selection.preservation().parentFinalAnswerHash())
                .parentMatcherVisibleAnswerHash(selection.preservation().parentMatcherVisibleAnswerHash())
                .selectedFinalAnswerHash(selection.preservation().selectedFinalAnswerHash())
                .selectedMatcherVisibleAnswerHash(selection.preservation().selectedMatcherVisibleAnswerHash())
                .parentFinalAnswerPreserved(true)
                .parentAnswerMismatchReason("")
                .parentSelectedFinalAnswerLength(selection.snapshot().answerLength())
                .monotonicRegressionPrevented(true)
                .routeConfidence(selection.validation().confidence())
                .constraintCoverageStatus(selection.validation().constraintCoverageStatus());
        appendMonotonicSafetyStage(clarifyAfterQu, telemetry);
        return finalizeIntegratedParentOutcome(
                selection.snapshot(), telemetry, selection.campaignRecord());
    }

    private Optional<ExecutionOutcome> trySelectParentCandidate(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            List<ExecutionStageTrace> clarifyAfterQu,
            MonotonicSafetyTelemetry telemetry,
            Optional<RouteCandidateValidationResult> rejectedFunctionValidation,
            boolean preferOverSafeFunction) {
        Optional<ParentSelectionResult> selection =
                probeSafeParentSelection(base, plan, clarificationDecision, telemetry);
        if (selection.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                commitParentSelection(
                        selection.get(),
                        clarifyAfterQu,
                        telemetry,
                        rejectedFunctionValidation,
                        preferOverSafeFunction));
    }

    private record ParentPreservationVerification(
            boolean preserved,
            String parentFinalAnswerHash,
            String parentMatcherVisibleAnswerHash,
            String selectedFinalAnswerHash,
            String selectedMatcherVisibleAnswerHash,
            String mismatchReason) {}

    private static ParentPreservationVerification verifyParentPreservation(
            ParentCandidateSnapshot snapshot,
            ExecutionOutcome finalizedOutcome,
            boolean campaignOutcomeReused) {
        String parentText = snapshot.parentFinalAnswerText();
        String parentMatcherText = snapshot.parentMatcherVisibleAnswer();
        String selectedText = finalizedOutcome.result().answerText();
        String parentHash = snapshot.parentFinalAnswerHash();
        String parentMatcherHash = snapshot.parentMatcherVisibleAnswerHash();
        String selectedHash = ParentAnswerFingerprint.sha256Hex(selectedText);
        String selectedMatcherHash = ParentAnswerFingerprint.sha256Hex(selectedText);
        boolean textMatch = Objects.equals(parentText, selectedText);
        boolean matcherMatch = Objects.equals(parentMatcherText, selectedText);
        boolean hashMatch = Objects.equals(parentHash, selectedHash);
        boolean inLabBenchmark = LabBenchmarkExecutionContext.currentDatasetQuestionId().isPresent();
        boolean preserved;
        String mismatchReason = "";
        if (inLabBenchmark) {
            if (!campaignOutcomeReused) {
                preserved = false;
                mismatchReason = "parent_campaign_outcome_not_reused";
            } else if (!textMatch) {
                preserved = false;
                mismatchReason = "parent_answer_text_mismatch";
            } else if (!matcherMatch) {
                preserved = false;
                mismatchReason = "parent_matcher_visible_answer_mismatch";
            } else if (!hashMatch) {
                preserved = false;
                mismatchReason = "parent_answer_hash_mismatch";
            } else {
                preserved = true;
            }
        } else if (!textMatch) {
            preserved = false;
            mismatchReason = "parent_answer_text_mismatch";
        } else if (!matcherMatch) {
            preserved = false;
            mismatchReason = "parent_matcher_visible_answer_mismatch";
        } else if (!hashMatch) {
            preserved = false;
            mismatchReason = "parent_answer_hash_mismatch";
        } else {
            preserved = true;
        }
        return new ParentPreservationVerification(
                preserved,
                parentHash,
                parentMatcherHash,
                selectedHash,
                selectedMatcherHash,
                mismatchReason);
    }

    private RouteCandidateValidationResult validateParentCandidateOutcome(
            QueryPlan plan,
            ExecutionOutcome parentOutcome,
            Optional<CampaignParentOutcome> campaignRecord,
            boolean campaignOutcomeReused) {
        if (campaignOutcomeReused && campaignRecord.isPresent()) {
            Optional<RouteCandidateValidationResult> trusted =
                    P15ParentCandidateSafetyPolicy.trustedCampaignValidation(campaignRecord.get());
            if (trusted.isPresent()) {
                return trusted.get();
            }
        }
        RagExecutionResult result = parentOutcome.result();
        if (result.usedTool() && "deterministic-tool".equals(result.workflowName())) {
            return monotonicRouteSafetyService.validateToolResult(
                    plan,
                    new DeterministicToolExecutionResult(
                            Optional.empty(),
                            DeterministicToolOutcome.EXECUTED_SUCCESS,
                            true,
                            result.answerText(),
                            Map.of(),
                            List.of()));
        }
        return monotonicRouteSafetyService.validateRetrievalAnswer(
                plan, result.answerText(), parentOutcome.trace().abstentionTriggered());
    }

    private static String parentSourceFor(RagExperimentalPresetCode parentPreset) {
        return parentPreset == IntegratedParentCandidateMaterializer.DEFAULT_PARENT_PRESET
                ? "PARENT_P7"
                : "PARENT_P6";
    }

    private ParentExecutionAttempt executeIsolatedParentPreset(
            ExecutionContext base,
            QueryPlan plan,
            ClarificationDecision clarificationDecision,
            RagExperimentalPresetCode parentPreset) {
        IntegratedParentCampaignOutcomeResolver campaignResolver =
                parentCampaignOutcomeResolverProvider.getIfAvailable();
        if (campaignResolver != null) {
            Optional<String> datasetQuestionId = campaignResolver.currentDatasetQuestionId();
            if (datasetQuestionId.isPresent()) {
                Optional<CampaignParentOutcome> campaignRecord =
                        LabBenchmarkExecutionContext.campaignParentOutcome(
                                parentPreset.name(), datasetQuestionId.get());
                Optional<ExecutionOutcome> campaignOutcome =
                        campaignResolver.tryResolve(parentPreset, datasetQuestionId.get());
                if (campaignOutcome.isPresent() && campaignRecord.isPresent()) {
                    return new ParentExecutionAttempt(
                            campaignOutcome, true, campaignRecord);
                }
                return new ParentExecutionAttempt(
                        Optional.empty(), false, Optional.empty());
            }
        }
        ExecutionOutcome isolatedOutcome =
                IntegratedParentPresetExecutionScope.runWithParentPresetBinding(
                        parentPreset,
                        () -> {
                            KnowledgeSnapshotSelection snapshots = resolveParentSnapshots(base, parentPreset);
                            ExecutionContext parentCtx =
                                    IntegratedParentCandidateMaterializer.materialize(base, parentPreset, snapshots);
                            List<ExecutionStageTrace> clarifyBeforeQu =
                                    RagExecutionTraceSupport.buildClarificationPreQuStages(parentCtx);
                            List<ExecutionStageTrace> memoryBeforeQu = List.copyOf(parentCtx.memoryStageTraces());
                            List<ExecutionStageTrace> quStages = RagExecutionTraceSupport.projectQuStages(plan);
                            List<ExecutionStageTrace> clarifyAfterQu = new ArrayList<>();
                            clarifyAfterQu.add(
                                    RagExecutionTraceSupport.clarificationPolicyStage(clarificationDecision));
                            RoutingSnapshot routing = resolveRoutingSnapshot(parentCtx, plan);
                            return executeSelectedRoute(
                                    parentCtx,
                                    plan,
                                    clarificationDecision,
                                    routing,
                                    clarifyBeforeQu,
                                    memoryBeforeQu,
                                    quStages,
                                    clarifyAfterQu);
                        });
        return new ParentExecutionAttempt(
                Optional.of(isolatedOutcome), false, Optional.empty());
    }

    private KnowledgeSnapshotSelection resolveParentSnapshots(
            ExecutionContext base, RagExperimentalPresetCode parentPreset) {
        IntegratedParentPresetSnapshotResolver resolver = parentSnapshotResolverProvider.getIfAvailable();
        if (resolver != null) {
            return resolver.resolve(base, parentPreset);
        }
        return IntegratedParentCandidateMaterializer.fallbackSnapshots(base);
    }

    private static ExecutionOutcome finalizeIntegratedParentOutcome(
            ParentCandidateSnapshot parentSnapshot,
            MonotonicSafetyTelemetry telemetry,
            Optional<CampaignParentOutcome> campaignRecord) {
        ExecutionOutcome parentOutcome = parentSnapshot.toPreservedOutcome();
        String parentRouteKind = parentSnapshot.parentRouteDecision();
        if (parentRouteKind == null || parentRouteKind.isBlank()) {
            parentRouteKind = parentOutcome.trace().routingRouteKind();
        }
        if (parentRouteKind == null || parentRouteKind.isBlank()) {
            parentRouteKind = AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name();
        }
        String finalSource = parentSnapshot.parentFinalAnswerSource();
        if (finalSource.isBlank()) {
            finalSource = ParentFinalAnswerSources.forPreset(parentSnapshot.parentPresetCode());
        }
        ExecutionStageTrace finalSourceStage =
                new ExecutionStageTrace(
                        "final_answer_source",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        "finalAnswerSource=" + finalSource);
        ExecutionTrace preservedTrace = parentOutcome.trace();
        ExecutionTrace trace =
                preservedTrace
                        .withIntegratedMonotonicFallback(true, parentRouteKind)
                        .withAppendedStages(finalSourceStage);
        if (campaignRecord.isPresent()) {
            trace =
                    ParentCampaignOutcomeTelemetryPreservation.preserveParentToolSignals(
                            trace, campaignRecord.get());
        }
        trace = MonotonicSafetyTelemetrySupport.withLeadingMonotonicSafetyStage(trace, telemetry);
        return new ExecutionOutcome(parentOutcome.result(), trace);
    }

    private static ExecutionOutcome finalizeIntegratedRetrievalOutcome(
            ExecutionOutcome retrievalOutcome, MonotonicSafetyTelemetry telemetry, boolean applyFallbackTrace) {
        ExecutionTrace trace = retrievalOutcome.trace();
        if (applyFallbackTrace
                && (telemetry.parentFallbackUsed()
                        || telemetry.functionCandidateRejected()
                        || telemetry.toolCandidateRejected())) {
            trace =
                    trace.withIntegratedMonotonicFallback(
                            true, AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name());
        }
        ExecutionStageTrace monotonicStage =
                new ExecutionStageTrace(
                        MonotonicSafetyTelemetrySupport.STAGE_NAME,
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        MonotonicSafetyTelemetrySupport.stageMessage(telemetry));
        return new ExecutionOutcome(
                retrievalOutcome.result(), trace.withAppendedStages(monotonicStage));
    }

    private static ExecutionOutcome finalizeIntegratedAbstentionOutcome(
            ExecutionOutcome basis,
            MonotonicSafetyTelemetry telemetry,
            QueryPlan plan) {
        String abstain =
                RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(plan.rewrittenQueryText());
        RagExecutionResult abstainedResult =
                new RagExecutionResult(
                        abstain,
                        basis.result().workflowName(),
                        basis.result().retrievalUsed(),
                        basis.result().metadataUsed(),
                        basis.result().usedResolvedConfigSnapshotId(),
                        basis.result().usedConfigHash(),
                        basis.result().usedKnowledgeSnapshotIds(),
                        basis.result().executionTrace(),
                        basis.result().toolUsedLabel(),
                        basis.result().resolvedQueryType(),
                        basis.result().usedTool(),
                        basis.result().workflowStageTraces(),
                        basis.result().retrievalDiagnostics(),
                        basis.result().responseSources());
        ExecutionTrace trace =
                MonotonicSafetyTelemetrySupport.withLeadingMonotonicSafetyStage(
                        basis.trace()
                                .withIntegratedMonotonicFallback(
                                        true, AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name()),
                        telemetry);
        return new ExecutionOutcome(abstainedResult, trace);
    }

    private static void appendMonotonicSafetyStage(
            List<ExecutionStageTrace> clarifyAfterQu, MonotonicSafetyTelemetry telemetry) {
        clarifyAfterQu.add(
                new ExecutionStageTrace(
                        MonotonicSafetyTelemetrySupport.STAGE_NAME,
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        MonotonicSafetyTelemetrySupport.stageMessage(telemetry)));
    }
}
