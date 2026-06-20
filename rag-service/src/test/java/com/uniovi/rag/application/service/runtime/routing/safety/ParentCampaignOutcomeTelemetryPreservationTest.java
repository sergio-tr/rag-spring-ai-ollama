package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.preset.CampaignParentOutcome;
import com.uniovi.rag.application.service.runtime.ChatExecutionTelemetryMapper;
import com.uniovi.rag.application.service.runtime.ToolExecutionTelemetryMapper;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import org.junit.jupiter.api.Test;

class ParentCampaignOutcomeTelemetryPreservationTest {

    @Test
    void preservesToolFinalSignalsForCampaignReplayExport() {
        ExecutionTrace replay =
                ExecutionTrace.campaignParentReplay(
                        "deterministic-tool",
                        false,
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name(),
                        false);
        CampaignParentOutcome record =
                new CampaignParentOutcome(
                        "Una. La acta 1.pdf tuvo menos de diez participantes.",
                        "deterministic-tool",
                        false,
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name(),
                        false,
                        true,
                        "count_documents",
                        "TOOL_FINAL",
                        "COMPLETE",
                        "SAFE",
                        "true");

        ExecutionTrace preserved =
                ParentCampaignOutcomeTelemetryPreservation.preserveParentToolSignals(replay, record);

        assertThat(preserved.deterministicToolOutcome())
                .isEqualTo(DeterministicToolOutcome.EXECUTED_SUCCESS.name());
        assertThat(preserved.routingRouteKind())
                .isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name());
        assertThat(preserved.routingFallbackApplied()).isFalse();

        var toolTelemetry = ToolExecutionTelemetryMapper.fromTrace(preserved);
        assertThat(toolTelemetry.get("toolExecuted")).isEqualTo(true);
        assertThat(toolTelemetry.get("toolResultUsedAsFinal")).isEqualTo(true);

        var chatTelemetry = ChatExecutionTelemetryMapper.fromTrace(preserved);
        assertThat(chatTelemetry.get("toolResultUsedAsFinal")).isEqualTo(true);
    }
}
