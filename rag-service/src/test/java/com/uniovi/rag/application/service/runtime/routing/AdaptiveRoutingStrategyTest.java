package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingExecutionResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.routing.RouteExecutionGate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptiveRoutingStrategyTest {

    @Test
    void execute_whenDisabledByConfig_producesDisabledOutcome_andNoStages() {
        AdaptiveRoutingPolicyResolver policy = mock(AdaptiveRoutingPolicyResolver.class);
        com.uniovi.rag.application.service.runtime.routing.RouteExecutionGate gateBuilder =
                mock(com.uniovi.rag.application.service.runtime.routing.RouteExecutionGate.class);
        AdaptiveRoutingStrategy s = new AdaptiveRoutingStrategy(policy, gateBuilder);

        AdaptiveRoutingDecision d =
                new AdaptiveRoutingDecision(
                        AdaptiveRoutingMode.DISABLED,
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        Optional.empty(),
                        List.of("disabled_by_config"),
                        List.of());
        when(policy.resolve(any(), any())).thenReturn(d);

        AdaptiveRoutingExecutionResult out = s.execute(mock(com.uniovi.rag.domain.runtime.engine.ExecutionContext.class), mock(QueryPlan.class));
        assertEquals(AdaptiveRoutingOutcome.DISABLED_BY_CONFIG, out.outcome());
        assertFalse(out.routingAttempted());
        assertTrue(out.stageTraces().isEmpty());
        assertTrue(out.gate().workflowAllowed());
        assertTrue(out.workflowSelectorInvoked());
    }

    @Test
    void execute_whenEnabled_emitsDeterministicStageTraces_andUsesGateBuilder() {
        AdaptiveRoutingPolicyResolver policy = mock(AdaptiveRoutingPolicyResolver.class);
        com.uniovi.rag.application.service.runtime.routing.RouteExecutionGate gateBuilder =
                mock(com.uniovi.rag.application.service.runtime.routing.RouteExecutionGate.class);
        AdaptiveRoutingStrategy s = new AdaptiveRoutingStrategy(policy, gateBuilder);

        AdaptiveRoutingDecision d =
                new AdaptiveRoutingDecision(
                        AdaptiveRoutingMode.ENABLED,
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                        Optional.of(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE),
                        List.of("selected=DETERMINISTIC_TOOL_ROUTE"),
                        List.of());
        when(policy.resolve(any(), any())).thenReturn(d);

        RouteExecutionGate gate =
                new RouteExecutionGate(
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                        false,
                        true,
                        false,
                        false,
                        true,
                        Optional.of(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE),
                        false);
        when(gateBuilder.fromDecision(eq(d))).thenReturn(gate);

        AdaptiveRoutingExecutionResult out = s.execute(mock(com.uniovi.rag.domain.runtime.engine.ExecutionContext.class), mock(QueryPlan.class));
        assertEquals(AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK, out.outcome());
        assertTrue(out.routingAttempted());
        assertEquals(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE, out.routingRouteKind());
        assertEquals(gate, out.gate());
        assertEquals(4, out.stageTraces().size());
    }
}

