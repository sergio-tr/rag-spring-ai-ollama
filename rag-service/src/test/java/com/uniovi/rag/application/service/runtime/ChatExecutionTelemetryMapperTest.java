package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.infrastructure.observability.RuntimeObservabilityAttributes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatExecutionTelemetryMapperTest {

    @Test
    void fromTrace_null_returnsEmptyMap() {
        assertThat(ChatExecutionTelemetryMapper.fromTrace(null)).isEmpty();
    }

    @Test
    void fromTrace_placeholder_hasSafeDefaults() {
        assertThat(ChatExecutionTelemetryMapper.fromTrace(ExecutionTrace.placeholder()))
                .containsEntry("clarificationRequired", false)
                .containsEntry("memoryAttempted", false)
                .containsEntry("routingAttempted", true)
                .containsEntry("judgeAttempted", false)
                .containsEntry("reasoningAttempted", false);
    }

    @Test
    void observabilityAttributes_fromTrace_excludeForbiddenKeys() {
        ExecutionTrace trace = mock(ExecutionTrace.class);
        when(trace.stages()).thenReturn(List.of(new ExecutionStageTrace(
                "qu_classify",
                3,
                ExecutionStageOutcome.SUCCESS,
                "classifierStatus=OK classifierModelId=cls classifierLabel=COUNT_DOCUMENTS note=OK")));
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

        var attrs = RuntimeObservabilityAttributes.fromExecutionTrace(trace);

        assertThat(attrs).containsKeys("classifierModelId", "classifierStatus", "predictedQueryType");
        assertThat(attrs).doesNotContainKey("query");
        assertThat(ChatExecutionTelemetryMapper.fromTrace(trace)).doesNotContainKey("query");
    }
}
