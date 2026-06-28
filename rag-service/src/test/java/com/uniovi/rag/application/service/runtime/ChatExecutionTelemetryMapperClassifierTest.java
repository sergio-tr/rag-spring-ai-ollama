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

class ChatExecutionTelemetryMapperClassifierTest {

    @Test
    void fromTrace_exportsClassifierModelFromQuClassifyStage() {
        ExecutionTrace trace = mock(ExecutionTrace.class);
        when(trace.stages()).thenReturn(List.of(new ExecutionStageTrace(
                "qu_classify",
                12,
                ExecutionStageOutcome.SUCCESS,
                "classifierStatus=OK classifierModelId=my-trained-tag classifierLabel=COUNT_DOCUMENTS note=OK")));
        when(trace.classifierStatus()).thenReturn("OK");
        when(trace.classifierLabel()).thenReturn("COUNT_DOCUMENTS");
        when(trace.usedKnowledgeSnapshotIds()).thenReturn(List.of());
        when(trace.clarificationOutcome()).thenReturn("");
        when(trace.memoryOutcome()).thenReturn("");
        when(trace.routingOutcome()).thenReturn("");
        when(trace.routingRouteKind()).thenReturn("");
        when(trace.routingFallbackRouteKind()).thenReturn("");
        when(trace.judgeFinalOutcome()).thenReturn("");
        when(trace.judgeCandidateSource()).thenReturn("");
        when(trace.retrievalDiagnostics()).thenReturn(Optional.empty());
        when(trace.answerGroundingPolicy()).thenReturn("");
        when(trace.abstentionReason()).thenReturn("");
        when(trace.retrievedDocumentNames()).thenReturn(List.of());

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry).containsEntry("classifierModelId", "my-trained-tag");
        assertThat(telemetry).containsEntry("classifierModelIdUsed", "my-trained-tag");
        assertThat(telemetry).containsEntry("classifierLabel", "COUNT_DOCUMENTS");
        assertThat(telemetry).containsEntry("classifierStatus", "OK");
        assertThat(telemetry).containsEntry("predictedQueryType", "COUNT_DOCUMENTS");
        assertThat(telemetry).containsEntry("classifierFallback", false);
    }

    @Test
    void fromTrace_exportsClassifierFallbackReasonFromQuClassifyNote() {
        ExecutionTrace trace = mock(ExecutionTrace.class);
        when(trace.stages()).thenReturn(List.of(new ExecutionStageTrace(
                "qu_classify",
                5,
                ExecutionStageOutcome.SUCCESS,
                "classifierStatus=UNAVAILABLE classifierModelId=default classifierLabel=UNCLASSIFIED note=UNAVAILABLE: timeout")));
        when(trace.classifierStatus()).thenReturn("UNAVAILABLE");
        when(trace.classifierLabel()).thenReturn("UNCLASSIFIED");
        when(trace.usedKnowledgeSnapshotIds()).thenReturn(List.of());
        when(trace.clarificationOutcome()).thenReturn("");
        when(trace.memoryOutcome()).thenReturn("");
        when(trace.routingOutcome()).thenReturn("");
        when(trace.routingRouteKind()).thenReturn("");
        when(trace.routingFallbackRouteKind()).thenReturn("");
        when(trace.judgeFinalOutcome()).thenReturn("");
        when(trace.judgeCandidateSource()).thenReturn("");
        when(trace.retrievalDiagnostics()).thenReturn(Optional.empty());
        when(trace.answerGroundingPolicy()).thenReturn("");
        when(trace.abstentionReason()).thenReturn("");
        when(trace.retrievedDocumentNames()).thenReturn(List.of());

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry).containsEntry("classifierFallback", true);
        assertThat(telemetry).containsEntry("classifierFallbackReason", "UNAVAILABLE: timeout");
    }

    @Test
    void fromTrace_exportsConfidenceAndLabelSetHash() {
        ExecutionTrace trace = mock(ExecutionTrace.class);
        when(trace.stages()).thenReturn(List.of(new ExecutionStageTrace(
                "qu_classify",
                8,
                ExecutionStageOutcome.SUCCESS,
                "classifierStatus=OK classifierModelId=default classifierLabel=COUNT_DOCUMENTS classifierConfidence=0.88 classifierLabelSetHash=abc123 note=OK")));
        when(trace.classifierStatus()).thenReturn("OK");
        when(trace.classifierLabel()).thenReturn("COUNT_DOCUMENTS");
        stubTraceDefaults(trace);

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry).containsEntry("classifierConfidence", 0.88);
        assertThat(telemetry).containsEntry("classifierLabelSetHash", "abc123");
        assertThat(telemetry).containsEntry("queryTypePredicted", "COUNT_DOCUMENTS");
    }

    @Test
    void fromTrace_exportsClassifierFallbackOnLowConfidence() {
        ExecutionTrace trace = mock(ExecutionTrace.class);
        when(trace.stages()).thenReturn(List.of(new ExecutionStageTrace(
                "qu_classify",
                5,
                ExecutionStageOutcome.SUCCESS,
                "classifierStatus=LOW_CONFIDENCE classifierModelId=default classifierLabel=UNCLASSIFIED classifierConfidence=0.2 note=LOW_CONFIDENCE")));
        when(trace.classifierStatus()).thenReturn("LOW_CONFIDENCE");
        when(trace.classifierLabel()).thenReturn("UNCLASSIFIED");
        stubTraceDefaults(trace);

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry).containsEntry("classifierFallback", true);
        assertThat(telemetry).containsEntry("classifierFallbackReason", "LOW_CONFIDENCE");
        assertThat(telemetry).doesNotContainKey("predictedQueryType");
    }

    @Test
    void fromTrace_exportsClassifierFallbackOnInvalidOutput() {
        ExecutionTrace trace = mock(ExecutionTrace.class);
        when(trace.stages()).thenReturn(List.of(new ExecutionStageTrace(
                "qu_classify",
                5,
                ExecutionStageOutcome.SUCCESS,
                "classifierStatus=INVALID_OUTPUT classifierModelId=default classifierLabel=UNCLASSIFIED note=INVALID_OUTPUT")));
        when(trace.classifierStatus()).thenReturn("INVALID_OUTPUT");
        when(trace.classifierLabel()).thenReturn("UNCLASSIFIED");
        stubTraceDefaults(trace);

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry).containsEntry("classifierStatus", "INVALID_OUTPUT");
        assertThat(telemetry).containsEntry("classifierFallback", true);
        assertThat(telemetry).containsEntry("classifierFallbackReason", "INVALID_OUTPUT");
        assertThat(telemetry).doesNotContainKey("predictedQueryType");
    }

    private static void stubTraceDefaults(ExecutionTrace trace) {
        when(trace.usedKnowledgeSnapshotIds()).thenReturn(List.of());
        when(trace.clarificationOutcome()).thenReturn("");
        when(trace.memoryOutcome()).thenReturn("");
        when(trace.routingOutcome()).thenReturn("");
        when(trace.routingRouteKind()).thenReturn("");
        when(trace.routingFallbackRouteKind()).thenReturn("");
        when(trace.judgeFinalOutcome()).thenReturn("");
        when(trace.judgeCandidateSource()).thenReturn("");
        when(trace.retrievalDiagnostics()).thenReturn(Optional.empty());
        when(trace.answerGroundingPolicy()).thenReturn("");
        when(trace.abstentionReason()).thenReturn("");
        when(trace.retrievedDocumentNames()).thenReturn(List.of());
    }
}
