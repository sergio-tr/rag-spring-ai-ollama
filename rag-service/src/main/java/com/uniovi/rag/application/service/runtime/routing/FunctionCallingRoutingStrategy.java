package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingExecutionResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.routing.RouteExecutionGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Resolves function-calling-first routing when full adaptive routing is off. */
@Service
public class FunctionCallingRoutingStrategy {

    private final FunctionCallingRoutingPolicy policy;
    private final RouteExecutionGateBuilder gateBuilder;

    public FunctionCallingRoutingStrategy(
            FunctionCallingRoutingPolicy policy, RouteExecutionGateBuilder gateBuilder) {
        this.policy = policy;
        this.gateBuilder = gateBuilder;
    }

    public AdaptiveRoutingExecutionResult execute(RagConfig rag, QueryPlan plan) {
        AdaptiveRoutingDecision decision = policy.resolve(rag, plan);
        List<ExecutionStageTrace> stages = new ArrayList<>();
        stages.add(
                new ExecutionStageTrace(
                        "routing_policy_resolve",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        "primary="
                                + decision.primaryRouteKind()
                                + " reasons="
                                + String.join("|", decision.reasons())));
        stages.add(
                new ExecutionStageTrace(
                        "routing_finalize_gate",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        "function_calling_routing=true primary=" + decision.primaryRouteKind()));

        RouteExecutionGate gate = gateBuilder.fromDecision(decision);
        AdaptiveRoutingOutcome outcome =
                gate.functionCallingAllowed()
                        ? AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED
                        : AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK;

        return new AdaptiveRoutingExecutionResult(
                outcome,
                true,
                decision.primaryRouteKind(),
                false,
                Optional.empty(),
                false,
                gate,
                List.copyOf(stages));
    }
}
