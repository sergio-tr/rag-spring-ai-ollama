package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.retrieval.FusionTelemetry;
import com.uniovi.rag.domain.runtime.retrieval.MetadataFilterTelemetry;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import com.uniovi.rag.domain.runtime.retrieval.SparseRetrievalFallbackStage;
import com.uniovi.rag.domain.runtime.retrieval.SparseRetrievalTelemetry;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalFusionMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatExecutionTelemetryMapperAdvancedRetrievalTest {

    @Test
    void fromTrace_exportsAdvancedRetrievalCountsAndCompression() {
        RetrievalDiagnostics diagnostics =
                new RetrievalDiagnostics(
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        Optional.of(RetrievalFusionMode.RRF_ONLY),
                        "snap",
                        5,
                        3,
                        6,
                        6,
                        6,
                        5,
                        4,
                        0,
                        1,
                        true,
                        List.of("a", "b"),
                        List.of("b", "a"),
                        Optional.empty(),
                        200,
                        120,
                        true,
                        6);
        ExecutionTrace trace = mock(ExecutionTrace.class);
        stubTraceDefaults(trace);
        when(trace.retrievalUsed()).thenReturn(true);
        when(trace.retrievalDiagnostics()).thenReturn(Optional.of(diagnostics));
        when(trace.promptContextCharCount()).thenReturn(120);
        when(trace.sourceCount()).thenReturn(4);
        when(trace.metadataUsed()).thenReturn(true);
        when(trace.stages())
                .thenReturn(
                        List.of(
                                new ExecutionStageTrace(
                                        "retrieval_sparse",
                                        1L,
                                        ExecutionStageOutcome.SUCCESS,
                                        "count=3"),
                                new ExecutionStageTrace(
                                        "retrieval_fuse",
                                        1L,
                                        ExecutionStageOutcome.SUCCESS,
                                        "count=6 origins=dense=5;sparse=3;both=2;fused=6")));

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry.get("denseCandidateCount")).isEqualTo(5);
        assertThat(telemetry.get("sparseCandidateCount")).isEqualTo(3);
        assertThat(telemetry.get("mergedCandidateCount")).isEqualTo(6);
        assertThat(telemetry.get("rerankChangedOrder")).isEqualTo(true);
        assertThat(telemetry.get("compressionApplied")).isEqualTo(true);
        assertThat(telemetry.get("compressedContextCharCount")).isEqualTo(120);
        assertThat(telemetry.get("sparseRetrievalStatus")).isEqualTo("OK");
        assertThat(telemetry.get("retrievalMode")).isEqualTo("HYBRID_DENSE_SPARSE");
        assertThat(telemetry.get("retrievalRoute")).isEqualTo("HYBRID_DENSE_SPARSE_METADATA");
        assertThat(telemetry.get("hybridApplied")).isEqualTo(true);
        assertThat(telemetry.get("candidateOrigins")).isEqualTo("dense=5;sparse=3;both=2;fused=6");
    }

    @Test
    void fromTrace_rerankNoopReason_whenOrderUnchanged() {
        RetrievalDiagnostics diagnostics =
                new RetrievalDiagnostics(
                        RetrievalMode.HYBRID_DENSE_SPARSE,
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
                        true,
                        List.of("a"),
                        List.of("a"),
                        Optional.empty(),
                        50,
                        50,
                        false,
                        2);
        ExecutionTrace trace = mock(ExecutionTrace.class);
        stubTraceDefaults(trace);
        when(trace.retrievalUsed()).thenReturn(true);
        when(trace.metadataUsed()).thenReturn(true);
        when(trace.retrievalDiagnostics()).thenReturn(Optional.of(diagnostics));
        when(trace.stages())
                .thenReturn(
                        List.of(
                                new ExecutionStageTrace(
                                        "retrieval_sparse",
                                        1L,
                                        ExecutionStageOutcome.SUCCESS,
                                        "count=0")));

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry.get("rerankNoopReason")).isEqualTo("order_unchanged");
        assertThat(telemetry.get("rerankChangedOrder")).isEqualTo(false);
    }

    @Test
    void fromTrace_sparseUnavailableStage_setsSparseStatus() {
        RetrievalDiagnostics diagnostics =
                new RetrievalDiagnostics(
                        RetrievalMode.HYBRID_DENSE_SPARSE,
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
                        Optional.empty(),
                        50,
                        50,
                        false,
                        2);
        ExecutionTrace trace = mock(ExecutionTrace.class);
        stubTraceDefaults(trace);
        when(trace.retrievalUsed()).thenReturn(true);
        when(trace.retrievalDiagnostics()).thenReturn(Optional.of(diagnostics));
        when(trace.stages())
                .thenReturn(
                        List.of(
                                new ExecutionStageTrace(
                                        "retrieval_sparse",
                                        1L,
                                        ExecutionStageOutcome.SKIPPED,
                                        "sparse_unavailable detail=db")));

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry.get("sparseRetrievalStatus")).isEqualTo("UNAVAILABLE");
        assertThat(telemetry.get("retrievalSparseStatus")).isEqualTo("sparse_unavailable");
    }

    @Test
    void fromTrace_exportsSparseAndFusionTelemetryFields() {
        RetrievalDiagnostics diagnostics =
                new RetrievalDiagnostics(
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        Optional.of(RetrievalFusionMode.RRF_ONLY),
                        "snap",
                        5,
                        3,
                        6,
                        6,
                        6,
                        5,
                        4,
                        0,
                        1,
                        true,
                        List.of("a", "b"),
                        List.of("b", "a"),
                        Optional.empty(),
                        200,
                        120,
                        true,
                        6,
                        Optional.of(
                                new SparseRetrievalTelemetry(
                                        "original q",
                                        "ascensor",
                                        SparseRetrievalFallbackStage.OR_KEYWORDS,
                                        true,
                                        "")),
                        Optional.of(new FusionTelemetry("RRF", 8, 6, 0, true)),
                        Optional.of(new MetadataFilterTelemetry(true, false)));
        ExecutionTrace trace = mock(ExecutionTrace.class);
        stubTraceDefaults(trace);
        when(trace.retrievalUsed()).thenReturn(true);
        when(trace.retrievalDiagnostics()).thenReturn(Optional.of(diagnostics));

        var telemetry = ChatExecutionTelemetryMapper.fromTrace(trace);

        assertThat(telemetry.get("sparseQueryRewritten")).isEqualTo("ascensor");
        assertThat(telemetry.get("sparseFallbackStage")).isEqualTo("OR_KEYWORDS");
        assertThat(telemetry.get("sparseHit")).isEqualTo(true);
        assertThat(telemetry.get("fusionStrategy")).isEqualTo("RRF");
        assertThat(telemetry.get("preFusionCount")).isEqualTo(8);
        assertThat(telemetry.get("postFusionCount")).isEqualTo(6);
        assertThat(telemetry.get("metadataCandidateCount")).isEqualTo(0);
        assertThat(telemetry.get("metadataFilterApplied")).isEqualTo(true);
        assertThat(telemetry.get("metadataFilterFallback")).isEqualTo(false);
        assertThat(telemetry.get("hybridApplied")).isEqualTo(true);
    }

    private static void stubTraceDefaults(ExecutionTrace trace) {
        when(trace.workflowName()).thenReturn("ChunkDenseMetadataWorkflow");
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
        when(trace.classifierStatus()).thenReturn("");
        when(trace.classifierLabel()).thenReturn("");
        when(trace.promptContextCharCount()).thenReturn(0);
        when(trace.sourceCount()).thenReturn(0);
        when(trace.packedContextBlockCount()).thenReturn(0);
        when(trace.abstentionTriggered()).thenReturn(false);
        when(trace.abstentionReason()).thenReturn("");
        when(trace.answerGroundingPolicy()).thenReturn("");
        when(trace.metadataUsed()).thenReturn(false);
        when(trace.stages()).thenReturn(List.of());
    }
}
