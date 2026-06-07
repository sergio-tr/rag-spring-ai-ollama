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
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolKindMappings;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

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
    private final JudgeStrategy judgeStrategy;
    private final StructuredAnswerPlanService structuredAnswerPlanService;
    private final AnswerVerificationService answerVerificationService;
    private final ObjectProvider<RuntimeObservability> runtimeObservability;

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
            JudgeStrategy judgeStrategy,
            StructuredAnswerPlanService structuredAnswerPlanService,
            AnswerVerificationService answerVerificationService,
            ObjectProvider<RuntimeObservability> runtimeObservability) {
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
        this.judgeStrategy = judgeStrategy;
        this.structuredAnswerPlanService = structuredAnswerPlanService;
        this.answerVerificationService = answerVerificationService;
        this.runtimeObservability = runtimeObservability;
    }

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
        DeterministicToolExecutionResult toolResult = deterministicToolStrategy.tryExecute(base, plan);
        if (toolResult.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS && toolResult.success()) {
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

        AdaptiveRouteKind fb = routing.fallbackWorkflowRouteKind().orElseThrow();
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
                List.of(),
                toolResult,
                FcGate.notAttempted(FunctionCallingOutcome.SUPPRESSED_BY_DETERMINISTIC_TOOL),
                AdvisorSnapshot.notReached(AdvisorOutcome.NOT_REACHED_BECAUSE_DETERMINISTIC_TOOL));
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
                shortCircuited ? fr.selectedToolKind().map(Enum::name).orElse("") : "";
        return new FcGate(
                true,
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
}
