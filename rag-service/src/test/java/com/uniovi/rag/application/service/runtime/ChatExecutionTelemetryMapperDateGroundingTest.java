package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatExecutionTelemetryMapperDateGroundingTest {

    @Test
    void fromTraceExportsDateGroundingTelemetryForEvaluator() {
        ExecutionTrace trace = mock(ExecutionTrace.class);
        when(trace.stages()).thenReturn(List.of(new ExecutionStageTrace(
                "date_grounding_answer_policy",
                0,
                ExecutionStageOutcome.SUCCESS,
                "requestedDate=2026-02-25 requestedDatePrecision=DAY exactDateMatch=true dateMismatchDetected=false sourceDates=2026-02-25 matchedDocumentDates=2026-02-25 exactDocumentMatch=true topSourceDate=2026-02-25 closestAvailableDate=ACTA5.pdf(2026-02-25) abstentionReason= groundingPolicyApplied=DATE_AWARE_SOURCE_GROUNDING candidateSourceCountBeforeDateFilter=2 candidateSourceCountAfterDateFilter=1 dateBoostApplied=true")));
        when(trace.usedKnowledgeSnapshotIds()).thenReturn(List.of());
        when(trace.clarificationOutcome()).thenReturn("");
        when(trace.memoryOutcome()).thenReturn("");
        when(trace.routingOutcome()).thenReturn("");
        when(trace.routingRouteKind()).thenReturn("");
        when(trace.routingFallbackRouteKind()).thenReturn("");
        when(trace.judgeFinalOutcome()).thenReturn("");
        when(trace.judgeCandidateSource()).thenReturn("");
        when(trace.retrievalDiagnostics()).thenReturn(Optional.empty());
        when(trace.answerGroundingPolicy()).thenReturn("STRICT_GROUNDED");
        when(trace.abstentionReason()).thenReturn("");
        when(trace.retrievedDocumentNames()).thenReturn(List.of("ACTA5.pdf"));

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry).containsEntry("requestedDate", "2026-02-25");
        assertThat(telemetry).containsEntry("requestedDatePrecision", "DAY");
        assertThat(telemetry).containsEntry("exactDateMatch", true);
        assertThat(telemetry).containsEntry("dateMismatchDetected", false);
        assertThat(telemetry.get("sourceDates")).asList().containsExactly("2026-02-25");
        assertThat(telemetry.get("matchedDocumentDates")).asList().containsExactly("2026-02-25");
        assertThat(telemetry).containsEntry("exactDocumentMatch", true);
        assertThat(telemetry).containsEntry("topSourceDate", "2026-02-25");
        assertThat(telemetry).containsEntry("closestAvailableDate", "ACTA5.pdf(2026-02-25)");
        assertThat(telemetry).containsEntry("candidateSourceCountBeforeDateFilter", 2);
        assertThat(telemetry).containsEntry("candidateSourceCountAfterDateFilter", 1);
        assertThat(telemetry).containsEntry("dateBoostApplied", true);
        assertThat(telemetry).containsEntry("groundingPolicyApplied", "DATE_AWARE_SOURCE_GROUNDING");
    }
}
