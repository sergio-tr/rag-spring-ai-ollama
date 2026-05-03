package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteExecutionGateBuilderTest {

    private final RouteExecutionGateBuilder b = new RouteExecutionGateBuilder();

    @Test
    void fromDecision_whenPrimaryIsWorkflow_doesNotRequireFallback_andEnablesWorkflowSelector() {
        AdaptiveRoutingDecision d =
                new AdaptiveRoutingDecision(
                        AdaptiveRoutingMode.ENABLED,
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        Optional.empty(),
                        List.of(),
                        List.of());

        var gate = b.fromDecision(d);

        assertThat(gate.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE);
        assertThat(gate.workflowAllowed()).isTrue();
        assertThat(gate.fallbackRequired()).isFalse();
        assertThat(gate.workflowSelectorRequired()).isTrue();
        assertThat(gate.fallbackRouteKind()).isEmpty();
    }

    @Test
    void fromDecision_whenPrimaryNotWorkflow_requiresFallbackKind() {
        AdaptiveRoutingDecision d =
                new AdaptiveRoutingDecision(
                        AdaptiveRoutingMode.ENABLED,
                        AdaptiveRouteKind.FUNCTION_CALLING_ROUTE,
                        Optional.empty(),
                        List.of(),
                        List.of());

        assertThatThrownBy(() -> b.fromDecision(d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fallbackWorkflowRouteKind required");
    }

    @Test
    void fromDecision_whenPrimaryNotWorkflow_andFallbackProvided_buildsGate() {
        AdaptiveRoutingDecision d =
                new AdaptiveRoutingDecision(
                        AdaptiveRoutingMode.ENABLED,
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                        Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                        List.of("r"),
                        List.of());

        var gate = b.fromDecision(d);

        assertThat(gate.workflowAllowed()).isFalse();
        assertThat(gate.deterministicToolsAllowed()).isTrue();
        assertThat(gate.fallbackRequired()).isTrue();
        assertThat(gate.fallbackRouteKind()).contains(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
    }
}

