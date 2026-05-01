package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingExecutionResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.routing.RouteExecutionGate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Single runtime entrypoint for P13 adaptive routing. Deterministic and rule-based.
 */
@Service
public class AdaptiveRoutingStrategy {

    private final AdaptiveRoutingPolicyResolver policyResolver;
    private final RouteExecutionGateBuilder gateBuilder;

    public AdaptiveRoutingStrategy(
            AdaptiveRoutingPolicyResolver policyResolver,
            RouteExecutionGateBuilder gateBuilder) {
        this.policyResolver = policyResolver;
        this.gateBuilder = gateBuilder;
    }

    public AdaptiveRoutingExecutionResult execute(ExecutionContext ctx, QueryPlan plan) {
        AdaptiveRoutingDecision decision = policyResolver.resolve(ctx, plan);
        if (decision.mode() == AdaptiveRoutingMode.DISABLED) {
            RouteExecutionGate gate = new RouteExecutionGate(
                    decision.primaryRouteKind(),
                    true,
                    false,
                    false,
                    false,
                    false,
                    Optional.empty(),
                    true);
            return new AdaptiveRoutingExecutionResult(
                    AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                    false,
                    decision.primaryRouteKind(),
                    false,
                    Optional.empty(),
                    true,
                    gate,
                    List.of());
        }

        List<ExecutionStageTrace> stages = new ArrayList<>();
        stages.add(new ExecutionStageTrace(
                "routing_policy_resolve",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "primary=" + decision.primaryRouteKind() + " reasons=" + String.join("|", decision.reasons())));
        stages.add(new ExecutionStageTrace(
                "routing_evaluate_capabilities",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "policy_only=true"));
        stages.add(new ExecutionStageTrace(
                "routing_decide_route_family",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "selected=" + decision.primaryRouteKind()));
        stages.add(new ExecutionStageTrace(
                "routing_finalize_gate",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "primary=" + decision.primaryRouteKind()));

        RouteExecutionGate gate = gateBuilder.fromDecision(decision);
        boolean workflowSelectorInvoked = false;

        AdaptiveRoutingOutcome outcome;
        if (gate.workflowAllowed()) {
            outcome = AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED;
        } else if (gate.advisorAllowed()) {
            outcome = AdaptiveRoutingOutcome.PRIMARY_ROUTE_CONTINUED_TO_WORKFLOW;
        } else {
            outcome = AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK;
        }

        return new AdaptiveRoutingExecutionResult(
                outcome,
                true,
                decision.primaryRouteKind(),
                false,
                Optional.empty(),
                workflowSelectorInvoked,
                gate,
                List.copyOf(stages));
    }
}

