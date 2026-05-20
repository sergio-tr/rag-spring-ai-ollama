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
    }
}
