package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatExecutionTelemetryMapperContextChunkTest {

    @Test
    void fromTrace_usesRetrievalCompressionCountForContextChunkCount() {
        RetrievalDiagnostics diagnostics =
                new RetrievalDiagnostics(
                        RetrievalMode.DENSE_ONLY,
                        Optional.empty(),
                        "snap",
                        2,
                        0,
                        2,
                        2,
                        2,
                        2,
                        2,
                        0,
                        0,
                        false,
                        List.of(),
                        List.of(),
                        Optional.empty(), 0, 0, false, 0);
        ExecutionTrace trace = mock(ExecutionTrace.class);
        stubTraceDefaults(trace);
        when(trace.retrievalUsed()).thenReturn(true);
        when(trace.retrievalDiagnostics()).thenReturn(Optional.of(diagnostics));
        when(trace.promptContextCharCount()).thenReturn(120);
        when(trace.sourceCount()).thenReturn(2);
        when(trace.packedContextBlockCount()).thenReturn(0);

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry.get("contextChunkCount")).isEqualTo(2);
        assertThat(telemetry.get("retrievalDenseCandidateCount")).isEqualTo(2);
        assertThat(telemetry.get("retrievalAfterFilterCount")).isEqualTo(2);
        assertThat(telemetry.get("promptContextCharCount")).isEqualTo(120);
        assertThat(telemetry.get("sourceCount")).isEqualTo(2);
        assertThat(telemetry.get("effectiveContextPresent")).isEqualTo(true);
    }

    @Test
    void fromTrace_omitsContextChunkCountWhenRetrievalNotUsed() {
        ExecutionTrace trace = mock(ExecutionTrace.class);
        stubTraceDefaults(trace);
        when(trace.retrievalUsed()).thenReturn(false);
        when(trace.retrievalDiagnostics()).thenReturn(Optional.empty());
        when(trace.promptContextCharCount()).thenReturn(0);
        when(trace.sourceCount()).thenReturn(0);
        when(trace.packedContextBlockCount()).thenReturn(0);

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry).doesNotContainKey("contextChunkCount");
    }

    private static void stubTraceDefaults(ExecutionTrace trace) {
        when(trace.stages()).thenReturn(List.of());
        when(trace.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(trace.usedKnowledgeSnapshotIds()).thenReturn(List.of());
        when(trace.clarificationQuestionAsked()).thenReturn(false);
        when(trace.clarificationOutcome()).thenReturn("");
        when(trace.memoryAttempted()).thenReturn(false);
        when(trace.memoryOutcome()).thenReturn("");
        when(trace.memoryCondensationUsed()).thenReturn(false);
        when(trace.memoryFallbackApplied()).thenReturn(false);
        when(trace.routingAttempted()).thenReturn(false);
        when(trace.routingOutcome()).thenReturn("");
        when(trace.routingRouteKind()).thenReturn("");
        when(trace.routingFallbackApplied()).thenReturn(false);
        when(trace.routingFallbackRouteKind()).thenReturn("");
        when(trace.judgeAttempted()).thenReturn(false);
        when(trace.judgeFinalOutcome()).thenReturn("");
        when(trace.judgeFinalAnswerFromRetry()).thenReturn(false);
        when(trace.judgeCandidateSource()).thenReturn("");
        when(trace.answerGroundingPolicy()).thenReturn("");
        when(trace.abstentionTriggered()).thenReturn(false);
        when(trace.abstentionReason()).thenReturn("");
    }
}
