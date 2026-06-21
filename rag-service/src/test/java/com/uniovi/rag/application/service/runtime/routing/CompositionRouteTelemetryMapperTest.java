package com.uniovi.rag.application.service.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompositionRouteTelemetryMapperTest {

    @Test
    void toolFinal_preservesAnswerAndSkipsFactualVerifier() {
        Map<String, Object> telemetry = baseTelemetry();
        telemetry.put("finalAnswerSource", "TOOL_FINAL");
        telemetry.put("routingRouteKind", AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name());
        telemetry.put("toolResultUsedAsFinal", true);
        telemetry.put("verifierAttempted", false);

        CompositionRouteTelemetryMapper.enrich(telemetry);

        assertThat(telemetry)
                .containsEntry("componentRouteDecision", "TOOL")
                .containsEntry("deterministicToolConsidered", true)
                .containsEntry("factualVerifierConsidered", false)
                .containsEntry("compositionFallbackReason", "");
    }

    @Test
    void functionFinal_whenToolNotApplicable() {
        Map<String, Object> telemetry = baseTelemetry();
        telemetry.put("finalAnswerSource", "FUNCTION_FINAL");
        telemetry.put("routingRouteKind", AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name());
        telemetry.put("functionCallAttempted", true);
        telemetry.put("toolFallbackReason", "tool_not_applicable");

        CompositionRouteTelemetryMapper.enrich(telemetry);

        assertThat(telemetry)
                .containsEntry("componentRouteDecision", "FUNCTION")
                .containsEntry("backendFunctionConsidered", true)
                .containsEntry("factualVerifierConsidered", false);
    }

    @Test
    void retrievalPath_usesSparseHybridAndFactualVerifier() {
        Map<String, Object> telemetry = baseTelemetry();
        telemetry.put("finalAnswerSource", "GENERATED");
        telemetry.put("routingRouteKind", AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name());
        telemetry.put("hybridApplied", true);
        telemetry.put("advancedRetrievalApplied", true);
        telemetry.put("sparseHit", true);
        telemetry.put("materializationStrategy", "HYBRID");
        telemetry.put("verifierAttempted", true);

        CompositionRouteTelemetryMapper.enrich(telemetry);

        assertThat(telemetry)
                .containsEntry("componentRouteDecision", "RETRIEVAL")
                .containsEntry("sparseHybridConsidered", true)
                .containsEntry("factualVerifierConsidered", true)
                .containsEntry("compositionFallbackReason", "GENERATED_FINAL");
    }

    @Test
    void sparseZeroMatches_setsFallbackReason() {
        Map<String, Object> telemetry = baseTelemetry();
        telemetry.put("finalAnswerSource", "GENERATED");
        telemetry.put("routingRouteKind", AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name());
        telemetry.put("sparseRetrievalStatus", "ZERO_MATCHES");
        telemetry.put("hybridApplied", true);

        CompositionRouteTelemetryMapper.enrich(telemetry);

        assertThat(telemetry).containsEntry("compositionFallbackReason", "SPARSE_ZERO_MATCHES");
    }

    @Test
    void precedenceChain_isStable() {
        Map<String, Object> telemetry = baseTelemetry();
        CompositionRouteTelemetryMapper.enrich(telemetry);
        assertThat(telemetry).containsEntry("componentRoutePrecedence", CompositionRouteTelemetryMapper.PRECEDENCE_CHAIN);
    }

    private static Map<String, Object> baseTelemetry() {
        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("routingAttempted", true);
        telemetry.put("adaptiveRoutingApplied", true);
        telemetry.put("useRetrieval", true);
        return telemetry;
    }
}
