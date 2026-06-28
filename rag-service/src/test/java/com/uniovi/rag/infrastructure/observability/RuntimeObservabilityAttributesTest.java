package com.uniovi.rag.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeObservabilityAttributesTest {

    @Test
    void fromExecutionContext_emitsSafeIdsAndBlockingCount() {
        UUID conversationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        ExecutionContext ctx = mock(ExecutionContext.class);
        ResolvedRuntimeConfig resolved = mock(ResolvedRuntimeConfig.class);
        ConfigProvenance provenance = mock(ConfigProvenance.class);
        CompatibilityResult compatibility = mock(CompatibilityResult.class);

        when(ctx.conversationId()).thenReturn(conversationId);
        when(ctx.projectId()).thenReturn(projectId);
        when(ctx.correlationId()).thenReturn("trace-abc");
        when(ctx.resolved()).thenReturn(resolved);
        when(resolved.provenance()).thenReturn(provenance);
        when(provenance.presetId()).thenReturn(presetId);
        when(resolved.compatibility()).thenReturn(compatibility);
        CompatibilityViolation violation = mock(CompatibilityViolation.class);
        when(compatibility.errors()).thenReturn(List.of(violation));

        Map<String, String> attrs = RuntimeObservabilityAttributes.fromExecutionContext(ctx);

        assertThat(attrs)
                .containsEntry("conversationId", conversationId.toString())
                .containsEntry("projectId", projectId.toString())
                .containsEntry("correlationId", "trace-abc")
                .containsEntry("presetId", presetId.toString())
                .containsEntry("blockingIssueCount", "1");
        assertThat(attrs).doesNotContainKey("query");
    }

    @Test
    void fromExecutionTrace_mapsClassifierAndRetrievalWithoutRawText() {
        ExecutionTrace trace = mock(ExecutionTrace.class);
        when(trace.stages()).thenReturn(List.of(new ExecutionStageTrace(
                "qu_classify",
                5,
                ExecutionStageOutcome.SUCCESS,
                "classifierStatus=OK classifierModelId=m1 classifierLabel=COUNT_DOCUMENTS note=OK")));
        when(trace.classifierStatus()).thenReturn("OK");
        when(trace.classifierLabel()).thenReturn("COUNT_DOCUMENTS");
        when(trace.workflowName()).thenReturn("rag-default");
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
        when(trace.sourceCount()).thenReturn(3);
        when(trace.packedContextBlockCount()).thenReturn(3);
        when(trace.promptContextCharCount()).thenReturn(120);

        Map<String, String> attrs = RuntimeObservabilityAttributes.fromExecutionTrace(trace);

        assertThat(attrs)
                .containsEntry("classifierModelId", "m1")
                .containsEntry("classifierStatus", "OK")
                .containsEntry("predictedQueryType", "COUNT_DOCUMENTS")
                .containsEntry("workflowFamily", "rag-default")
                .containsEntry("retrievedChunkCount", "3")
                .containsEntry("promptCharCount", "120");
        assertThat(attrs).doesNotContainKey("query");
    }

    @Test
    void fromExecutionContext_null_returnsEmpty() {
        assertThat(RuntimeObservabilityAttributes.fromExecutionContext(null)).isEmpty();
    }
}
