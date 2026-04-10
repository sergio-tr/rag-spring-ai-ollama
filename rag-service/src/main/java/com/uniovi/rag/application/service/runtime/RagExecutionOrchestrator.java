package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorPolicyResolver;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorStrategy;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStrategy;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingPolicyResolver;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingStrategy;
import com.uniovi.rag.application.service.runtime.routing.AdaptiveRoutingStrategy;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolKindMappings;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.advisor.AdvisorDecision;
import com.uniovi.rag.domain.runtime.advisor.AdvisorExecutionResult;
import com.uniovi.rag.domain.runtime.advisor.AdvisorOutcome;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationExecutionResult;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class RagExecutionOrchestrator {

    private final WorkflowSelector workflowSelector;
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

    public RagExecutionOrchestrator(
            WorkflowSelector workflowSelector,
            QueryUnderstandingPipeline queryUnderstandingPipeline,
            ExecutionContextFactory executionContextFactory,
            DeterministicToolStrategy deterministicToolStrategy,
            FunctionCallingPolicyResolver functionCallingPolicyResolver,
            FunctionCallingStrategy functionCallingStrategy,
            AdvisorPolicyResolver advisorPolicyResolver,
            AdvisorStrategy advisorStrategy,
            ClarificationPolicyResolver clarificationPolicyResolver,
            ClarificationStrategy clarificationStrategy,
            AdaptiveRoutingStrategy adaptiveRoutingStrategy) {
        this.workflowSelector = workflowSelector;
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
    }

    public RagExecutionResult execute(ExecutionContext ctx) {
        List<ExecutionStageTrace> clarifyBeforeQu = buildClarificationPreQuStages(ctx);
        List<ExecutionStageTrace> memoryBeforeQu = List.copyOf(ctx.memoryStageTraces());
        long quStart = System.nanoTime();
        QueryPlan plan = queryUnderstandingPipeline.buildPlan(ctx);
        ExecutionContext withPlan = executionContextFactory.attachQueryPlan(ctx, plan);

        List<ExecutionStageTrace> quStages = projectQuStages(plan);
        quStages.add(
                0,
                new ExecutionStageTrace(
                        "qu_total",
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - quStart),
                        ExecutionStageOutcome.SUCCESS,
                        "qu_status=OK message=QueryUnderstandingPipeline completed"));

        ClarificationDecision clarificationDecision = clarificationPolicyResolver.resolve(withPlan, plan);
        List<ExecutionStageTrace> clarifyAfterQu = new ArrayList<>();
        clarifyAfterQu.add(clarificationPolicyStage(clarificationDecision));

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
        List<ExecutionStageTrace> routingStages = routing.routingStages();

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
        if (!rag.adaptiveRoutingEnabled()) {
            AdaptiveRouteKind compat =
                    rag.useRetrieval() ? AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE : AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
            return RoutingSnapshot.disabledByConfig(compat);
        }
        var r = adaptiveRoutingStrategy.execute(ctx, plan);
        return RoutingSnapshot.enabled(r.routingRouteKind(), r.gate(), r.stageTraces());
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
            DeterministicToolExecutionResult toolResult = deterministicToolStrategy.tryExecute(base, plan);
            if (toolResult.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS && toolResult.success()) {
                DeterministicToolKind kind =
                        toolResult.toolKind().orElseThrow(() -> new IllegalStateException("tool kind missing on success"));
                RagExecutionResult partial =
                        new RagExecutionResult(
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
                                Optional.empty());
                ExecutionTrace trace =
                        assembleTrace(
                                base,
                                partial,
                                "deterministic-tool",
                                clarifyBeforeQu,
                                memoryBeforeQu,
                                quStages,
                                clarifyAfterQu,
                                routing.routingStages(),
                                projectDeterministicToolStages(toolResult),
                                List.of(),
                                toolResult,
                                false,
                                FunctionCallingOutcome.SUPPRESSED_BY_DETERMINISTIC_TOOL,
                                "",
                                false,
                                AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_DETERMINISTIC_TOOL),
                                routing.snapshotForTrace(
                                        AdaptiveRoutingOutcome.PRIMARY_ROUTE_EXECUTED_TERMINALLY,
                                        false,
                                        Optional.empty(),
                                        false),
                                clarificationDecision);
                return new ExecutionOutcome(partial, trace);
            }

            AdaptiveRouteKind fb = routing.fallbackWorkflowRouteKind().orElseThrow();
            return executeWorkflowRoute(
                    base,
                    plan,
                    clarificationDecision,
                    routing.withOutcome(AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK, true, Optional.of(fb), true),
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu,
                    projectDeterministicToolStages(toolResult),
                    List.of(),
                    toolResult,
                    FcGate.notAttempted(FunctionCallingOutcome.SUPPRESSED_BY_DETERMINISTIC_TOOL),
                    AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_DETERMINISTIC_TOOL));
        }
        if (route == AdaptiveRouteKind.FUNCTION_CALLING_ROUTE) {
            FcGate fcGate = evaluateFunctionCallingGate(base, plan);
            if (fcGate.functionCallingOutcome() == FunctionCallingOutcome.EXECUTED_SUCCESS && fcGate.functionCallingShortCircuited()) {
                FunctionCallingExecutionResult fr = fcGate.fcResult().orElseThrow();
                DeterministicToolKind k =
                        fr.selectedToolKind()
                                .orElseThrow(() -> new IllegalStateException("tool kind missing on FC success"));
                RagExecutionResult partial =
                        new RagExecutionResult(
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
                                Optional.empty());
                ExecutionTrace trace =
                        assembleTrace(
                                base,
                                partial,
                                "function-calling",
                                clarifyBeforeQu,
                                memoryBeforeQu,
                                quStages,
                                clarifyAfterQu,
                                routing.routingStages(),
                                projectDeterministicToolStages(DeterministicToolExecutionResult.skipped(DeterministicToolOutcome.NOT_ATTEMPTED, List.of("suppressed_by_routing_fc"), Optional.empty())),
                                fcGate.stageTraces(),
                                DeterministicToolExecutionResult.skipped(DeterministicToolOutcome.NOT_ATTEMPTED, List.of("suppressed_by_routing_fc"), Optional.empty()),
                                fcGate.functionCallingAttempted(),
                                fcGate.functionCallingOutcome(),
                                fcGate.functionCallingToolKind(),
                                fcGate.functionCallingShortCircuited(),
                                AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_FUNCTION_CALLING),
                                routing.snapshotForTrace(
                                        AdaptiveRoutingOutcome.PRIMARY_ROUTE_EXECUTED_TERMINALLY,
                                        false,
                                        Optional.empty(),
                                        false),
                                clarificationDecision);
                return new ExecutionOutcome(partial, trace);
            }
            AdaptiveRouteKind fb = routing.fallbackWorkflowRouteKind().orElseThrow();
            DeterministicToolExecutionResult toolResult =
                    DeterministicToolExecutionResult.skipped(DeterministicToolOutcome.NOT_ATTEMPTED, List.of("suppressed_by_routing_fc"), Optional.empty());
            return executeWorkflowRoute(
                    base,
                    plan,
                    clarificationDecision,
                    routing.withOutcome(AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK, true, Optional.of(fb), true),
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu,
                    projectDeterministicToolStages(toolResult),
                    fcGate.stageTraces(),
                    toolResult,
                    fcGate,
                    AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_FUNCTION_CALLING));
        }
        if (route == AdaptiveRouteKind.ADVISOR_ROUTE) {
            String advisorWorkflowName = selectRetrievalWorkflowNameForAdvisor(base.resolved().toRagConfig());
            AdvisorPhaseResult advisorPhase = runAdvisorPhase(base, plan, advisorWorkflowName);
            if (!advisorPhase.snapshot().advisorAttempted()) {
                // Suppressed or failed: fallback to retrieval workflow only.
                return executeWorkflowRoute(
                        base,
                        plan,
                        clarificationDecision,
                        routing.withOutcome(AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK, true, Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE), true),
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
                        clarifyAfterQu,
                        projectDeterministicToolStages(DeterministicToolExecutionResult.skipped(DeterministicToolOutcome.NOT_ATTEMPTED, List.of("suppressed_by_routing_advisor"), Optional.empty())),
                        List.of(),
                        DeterministicToolExecutionResult.skipped(DeterministicToolOutcome.NOT_ATTEMPTED, List.of("suppressed_by_routing_advisor"), Optional.empty()),
                        FcGate.notAttempted(FunctionCallingOutcome.NOT_APPLICABLE),
                        advisorPhase.snapshot());
            }
            // Success: continue to retrieval workflow generation.
            return executeWorkflowRoute(
                    advisorPhase.ctx(),
                    plan,
                    clarificationDecision,
                    routing.withOutcome(AdaptiveRoutingOutcome.PRIMARY_ROUTE_CONTINUED_TO_WORKFLOW, false, Optional.empty(), true),
                    clarifyBeforeQu,
                    memoryBeforeQu,
                    quStages,
                    clarifyAfterQu,
                    projectDeterministicToolStages(DeterministicToolExecutionResult.skipped(DeterministicToolOutcome.NOT_ATTEMPTED, List.of("suppressed_by_routing_advisor"), Optional.empty())),
                    List.of(),
                    DeterministicToolExecutionResult.skipped(DeterministicToolOutcome.NOT_ATTEMPTED, List.of("suppressed_by_routing_advisor"), Optional.empty()),
                    FcGate.notAttempted(FunctionCallingOutcome.NOT_APPLICABLE),
                    advisorPhase.snapshot());
        }

        throw new IllegalStateException("unsupported route kind: " + route);
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
                projectDeterministicToolStages(toolResult),
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
        ExecutionWorkflow workflow = workflowSelector.select(ctxForWorkflow);
        String wname = workflow.workflowName();
        if (requiresKnowledgeSnapshots(wname)
                && ctxForWorkflow.knowledgeSnapshotSelection().orderedSnapshotIds().isEmpty()) {
            throw RagServiceException.knowledgeSnapshotUnavailable();
        }
        RagExecutionContextHolder.set(toLegacy(ctxForWorkflow));
        try {
            RagExecutionResult partial = workflow.execute(ctxForWorkflow);
            ExecutionTrace trace =
                    assembleTrace(
                            ctxForWorkflow,
                            partial,
                            wname,
                            clarifyBeforeQu,
                            memoryBeforeQu,
                            quStages,
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
                            routing.snapshotForTrace(),
                            clarificationDecision);
            return new ExecutionOutcome(partial, trace);
        } finally {
            RagExecutionContextHolder.clear();
        }
    }

    private static String selectRetrievalWorkflowNameForAdvisor(com.uniovi.rag.domain.runtime.RagConfig rag) {
        var strategy = rag.materializationStrategy();
        if (strategy == com.uniovi.rag.domain.knowledge.MaterializationStrategy.DOCUMENT_LEVEL) {
            return "DocumentDenseRagWorkflow";
        }
        if ((strategy == com.uniovi.rag.domain.knowledge.MaterializationStrategy.CHUNK_LEVEL
                || strategy == com.uniovi.rag.domain.knowledge.MaterializationStrategy.HYBRID)
                && rag.metadataEnabled()) {
            return "ChunkDenseMetadataWorkflow";
        }
        return "ChunkDenseRagWorkflow";
    }

    private record ExecutionOutcome(RagExecutionResult result, ExecutionTrace trace) {}

    private record RoutingSnapshot(
            AdaptiveRouteKind routeKind,
            Optional<AdaptiveRouteKind> fallbackWorkflowRouteKind,
            List<ExecutionStageTrace> routingStages,
            boolean routingAttempted,
            AdaptiveRoutingOutcome routingOutcome,
            boolean fallbackApplied,
            Optional<AdaptiveRouteKind> fallbackAppliedKind,
            boolean workflowSelectorInvoked) {

        static RoutingSnapshot disabledByConfig(AdaptiveRouteKind compat) {
            return new RoutingSnapshot(
                    compat,
                    Optional.empty(),
                    List.of(),
                    false,
                    AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                    false,
                    Optional.empty(),
                    true);
        }

        static RoutingSnapshot enabled(
                AdaptiveRouteKind kind,
                com.uniovi.rag.domain.runtime.routing.RouteExecutionGate gate,
                List<ExecutionStageTrace> stages) {
            return new RoutingSnapshot(
                    kind,
                    gate.fallbackRouteKind(),
                    List.copyOf(stages),
                    true,
                    AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED,
                    false,
                    Optional.empty(),
                    false);
        }

        RoutingSnapshot withOutcome(
                AdaptiveRoutingOutcome outcome,
                boolean fbApplied,
                Optional<AdaptiveRouteKind> fbKind,
                boolean workflowSelectorInvoked) {
            return new RoutingSnapshot(
                    routeKind,
                    fallbackWorkflowRouteKind,
                    routingStages,
                    routingAttempted,
                    outcome,
                    fbApplied,
                    fbKind == null ? Optional.empty() : fbKind,
                    workflowSelectorInvoked);
        }

        RoutingSnapshot snapshotForTrace() {
            return this;
        }

        RoutingSnapshot snapshotForTrace(
                AdaptiveRoutingOutcome outcome,
                boolean fbApplied,
                Optional<AdaptiveRouteKind> fbKind,
                boolean workflowSelectorInvoked) {
            return withOutcome(outcome, fbApplied, fbKind, workflowSelectorInvoked);
        }
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
        List<ExecutionStageTrace> toolStages = projectDeterministicToolStages(toolResult);
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
                assembleTrace(
                        withPlan,
                        partial,
                        "clarification",
                        clarifyBeforeQu,
                        memoryBeforeQu,
                        quStages,
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

    private static List<ExecutionStageTrace> buildClarificationPreQuStages(ExecutionContext ctx) {
        return List.of(
                new ExecutionStageTrace(
                        "clarification_state_resolve",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        clarifyResolveMessage(ctx)),
                new ExecutionStageTrace(
                        "clarification_query_refine",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        clarifyRefineMessage(ctx)));
    }

    private static String clarifyResolveMessage(ExecutionContext ctx) {
        if (ctx.clarificationDisableReason().isPresent()) {
            return "disable_reason=" + ctx.clarificationDisableReason().get();
        }
        if (ctx.invalidPendingRecoveredThisTurn()) {
            return "invalid_pending_state_recovered";
        }
        return "pending_valid="
                + ctx.validPendingExistedAtLoad()
                + " merged="
                + ctx.pendingClarificationLoadedForTrace();
    }

    private static String clarifyRefineMessage(ExecutionContext ctx) {
        return "merged_before_qu=" + ctx.pendingClarificationLoadedForTrace();
    }

    private static ExecutionStageTrace clarificationPolicyStage(ClarificationDecision d) {
        return new ExecutionStageTrace(
                "clarification_policy",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "outcome=" + d.terminalOutcome().name() + " " + d.policyTraceNote());
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
        if (result.outcome() == AdvisorOutcome.EXECUTED_SUCCESS && result.packedContextSet().isPresent()) {
            out = executionContextFactory.attachAdvisorPackedContextSet(ctx, result.packedContextSet().get());
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

    private record AdvisorPhaseResult(ExecutionContext ctx, AdvisorSnapshot snapshot) {}

    private record AdvisorSnapshot(
            List<ExecutionStageTrace> advisorStages,
            boolean advisorAttempted,
            boolean advisorShortCircuitedContextPrep,
            String advisorKindsExecuted,
            AdvisorOutcome advisorOutcome,
            int packedContextBlockCount,
            int packedContextSourceCount) {

        static AdvisorSnapshot notReached(AdvisorOutcome outcome) {
            return new AdvisorSnapshot(List.of(), false, false, "", outcome, 0, 0);
        }

        static AdvisorSnapshot suppressed(List<ExecutionStageTrace> stages) {
            return new AdvisorSnapshot(stages, false, false, "", AdvisorOutcome.SUPPRESSED_BY_POLICY, 0, 0);
        }

        static AdvisorSnapshot fromExecution(List<ExecutionStageTrace> stages, AdvisorExecutionResult result) {
            Optional<PackedContextSet> packed = result.packedContextSet();
            int blocks = packed.map(PackedContextSet::totalBlockCount).orElse(0);
            int sources = packed.map(PackedContextSet::totalSourceCount).orElse(0);
            boolean shortCirc =
                    result.outcome() == AdvisorOutcome.EXECUTED_SUCCESS && result.shortCircuitedContextPrep();
            return new AdvisorSnapshot(
                    stages,
                    true,
                    shortCirc,
                    "RETRIEVAL_ADVISOR,CONTEXT_PACKING_ADVISOR",
                    result.outcome(),
                    blocks,
                    sources);
        }
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
                shortCircuited ? fr.selectedToolKind().map(Enum::name).orElse("") : "";
        return new FcGate(
                true,
                fr.outcome(),
                toolKindStr,
                shortCircuited,
                Optional.of(fr),
                fr.stageTraces());
    }

    private record FcGate(
            boolean functionCallingAttempted,
            FunctionCallingOutcome functionCallingOutcome,
            String functionCallingToolKind,
            boolean functionCallingShortCircuited,
            Optional<FunctionCallingExecutionResult> fcResult,
            List<ExecutionStageTrace> stageTraces) {

        static FcGate blockedByDeterministicFailure() {
            return new FcGate(
                    false,
                    FunctionCallingOutcome.FC_BLOCKED_BY_DETERMINISTIC_TOOL_FAILURE,
                    "",
                    false,
                    Optional.empty(),
                    List.of());
        }

        static FcGate notAttempted(FunctionCallingOutcome outcome) {
            return new FcGate(false, outcome, "", false, Optional.empty(), List.of());
        }
    }

    private static boolean requiresKnowledgeSnapshots(String workflowName) {
        return "FullCorpusWorkflow".equals(workflowName)
                || "DocumentDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseMetadataWorkflow".equals(workflowName);
    }

    private static RagExecutionContext toLegacy(ExecutionContext ctx) {
        return new RagExecutionContext(
                ctx.conversationId() != null ? ctx.conversationId().toString() : null,
                ctx.userId() != null ? ctx.userId().toString() : null,
                ctx.projectId() != null ? ctx.projectId().toString() : null,
                ctx.resolved().toRagConfig(),
                ctx.documentFilter(),
                ctx.correlationId());
    }

    private static ExecutionTrace assembleTrace(
            ExecutionContext ctx,
            RagExecutionResult partial,
            String workflowName,
            List<ExecutionStageTrace> clarificationStagesBeforeQu,
            List<ExecutionStageTrace> memoryStagesBeforeQu,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> clarificationStagesAfterQu,
            List<ExecutionStageTrace> routingStages,
            List<ExecutionStageTrace> toolStages,
            List<ExecutionStageTrace> fcStages,
            DeterministicToolExecutionResult toolResult,
            boolean functionCallingAttempted,
            FunctionCallingOutcome functionCallingOutcome,
            String functionCallingToolKind,
            boolean functionCallingShortCircuited,
            AdvisorSnapshot advisor,
            RoutingSnapshot routing,
            ClarificationDecision clarificationDecision) {
        List<ExecutionStageTrace> all = new ArrayList<>();
        all.addAll(clarificationStagesBeforeQu);
        all.addAll(memoryStagesBeforeQu);
        all.addAll(quStages);
        all.addAll(clarificationStagesAfterQu);
        all.addAll(routingStages);
        all.addAll(toolStages);
        all.addAll(fcStages);
        all.addAll(advisor.advisorStages());
        all.addAll(partial.workflowStageTraces());
        QueryPlan qp = ctx.queryPlan().orElse(null);
        String toolOutcome = toolResult.outcome().name();
        String toolKind = toolResult.toolKind().map(Enum::name).orElse("");
        String toolDetail = buildToolDetail(toolResult);
        boolean pendingConsumed = ctx.pendingClarificationLoadedForTrace();
        boolean questionAsked = clarificationDecision.ask();
        return new ExecutionTrace(
                List.copyOf(all),
                workflowName,
                partial.retrievalUsed(),
                partial.metadataUsed(),
                partial.usedKnowledgeSnapshotIds(),
                partial.usedResolvedConfigSnapshotId(),
                partial.usedConfigHash(),
                qp != null ? qp.queryPlanVersion() : "",
                qp != null ? qp.classifierStatus().name() : "",
                qp != null ? qp.classifierLabel() : "",
                qp != null ? qp.expectedAnswerShape().name() : "",
                qp != null ? qp.ambiguityAssessment().status().name() : "",
                ctx.resolved().compatibility().severity().name(),
                ctx.memoryAttempted(),
                ctx.memoryOutcome().name(),
                ctx.memoryHistoryLoaded(),
                ctx.memoryCondensationAttempted(),
                ctx.memoryCondensationUsed(),
                ctx.memoryFallbackApplied(),
                routing.routingAttempted(),
                routing.routingOutcome().name(),
                routing.routeKind().name(),
                routing.fallbackApplied(),
                routing.fallbackAppliedKind().map(Enum::name).orElse(""),
                routing.workflowSelectorInvoked(),
                toolOutcome,
                toolKind,
                toolDetail,
                functionCallingAttempted,
                functionCallingOutcome.name(),
                functionCallingToolKind != null ? functionCallingToolKind : "",
                functionCallingShortCircuited,
                partial.retrievalDiagnostics(),
                advisor.advisorAttempted(),
                advisor.advisorShortCircuitedContextPrep(),
                advisor.advisorKindsExecuted(),
                advisor.advisorOutcome().name(),
                advisor.packedContextBlockCount(),
                advisor.packedContextSourceCount(),
                true,
                clarificationDecision.terminalOutcome().name(),
                pendingConsumed,
                questionAsked);
    }

    private static String buildToolDetail(DeterministicToolExecutionResult toolResult) {
        String notes = toolResult.traceNotes().stream().collect(Collectors.joining(";"));
        if (toolResult.outcome() == DeterministicToolOutcome.EXECUTED_FAILED_INFRA) {
            return "tool_fallback_to_workflow;" + notes;
        }
        return notes;
    }

    private static List<ExecutionStageTrace> projectDeterministicToolStages(DeterministicToolExecutionResult r) {
        List<ExecutionStageTrace> out = new ArrayList<>();
        String msgBase = "outcome=" + r.outcome() + " success=" + r.success();
        String notes = String.join(" | ", r.traceNotes());
        out.add(
                new ExecutionStageTrace(
                        "tool_resolve",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        msgBase + " notes=" + notes));

        ExecutionStageOutcome execOutcome;
        if (r.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS) {
            execOutcome = ExecutionStageOutcome.SUCCESS;
        } else if (r.outcome() == DeterministicToolOutcome.EXECUTED_FAILED_INFRA) {
            execOutcome = ExecutionStageOutcome.FAILED;
        } else {
            execOutcome = ExecutionStageOutcome.SKIPPED;
        }
        out.add(new ExecutionStageTrace("tool_execute", 0L, execOutcome, msgBase));

        ExecutionStageOutcome mapOutcome =
                r.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS
                        ? ExecutionStageOutcome.SUCCESS
                        : ExecutionStageOutcome.SKIPPED;
        out.add(new ExecutionStageTrace("tool_result_map", 0L, mapOutcome, msgBase));
        return out;
    }

    private static List<ExecutionStageTrace> projectQuStages(QueryPlan plan) {
        List<ExecutionStageTrace> out = new ArrayList<>();
        for (String line : plan.pipelineNotes()) {
            ExecutionStageTrace st = parseStageTraceLine(line);
            if (st != null && isFrozenQuStageName(st.stageName())) {
                out.add(st);
            }
        }
        return out;
    }

    private static boolean isFrozenQuStageName(String name) {
        return "qu_normalize".equals(name)
                || "qu_classify".equals(name)
                || "qu_extract_entities".equals(name)
                || "qu_rewrite".equals(name)
                || "qu_resolve_intent".equals(name)
                || "qu_resolve_answer_shape".equals(name)
                || "qu_assess_ambiguity".equals(name);
    }

    private static ExecutionStageTrace parseStageTraceLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        String stageName = parts[0].trim();
        String quStatus = extractToken(line, "qu_status=");
        String durationRaw = extractToken(line, "durationMs=");
        long durationMs = 0;
        try {
            durationMs = Long.parseLong(durationRaw);
        } catch (Exception ignored) {
            durationMs = 0;
        }
        ExecutionStageOutcome outcome =
                switch (quStatus) {
                    case "OK", "FALLBACK" -> ExecutionStageOutcome.SUCCESS;
                    case "DISABLED" -> ExecutionStageOutcome.SKIPPED;
                    case "ERROR" -> ExecutionStageOutcome.FAILED;
                    default -> ExecutionStageOutcome.SUCCESS;
                };
        return new ExecutionStageTrace(
                stageName, durationMs, outcome, "qu_status=" + quStatus + " " + extractMessage(line));
    }

    private static String extractToken(String line, String key) {
        int idx = line.indexOf(key);
        if (idx < 0) {
            return "";
        }
        int start = idx + key.length();
        int end = line.indexOf(' ', start);
        return end < 0 ? line.substring(start).trim() : line.substring(start, end).trim();
    }

    private static String extractMessage(String line) {
        int idx = line.indexOf("message=");
        if (idx < 0) {
            return "";
        }
        return "message=" + line.substring(idx + "message=".length()).trim();
    }
}
