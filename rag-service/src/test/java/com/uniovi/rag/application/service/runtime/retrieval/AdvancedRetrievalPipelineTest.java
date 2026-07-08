package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.retrieval.CompressionOutcome;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievedContextSet;
import com.uniovi.rag.domain.runtime.retrieval.MetadataFilterTelemetry;
import com.uniovi.rag.domain.runtime.retrieval.SparseRetrievalFallbackStage;
import com.uniovi.rag.domain.runtime.retrieval.SparseQueryPreparation;
import com.uniovi.rag.domain.runtime.retrieval.SparseRetrievalTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.runtime.retrieval.FusionTelemetry;

@ExtendWith(MockitoExtension.class)
class AdvancedRetrievalPipelineTest {

    @Mock
    private RetrievalRequestBuilder retrievalRequestBuilder;

    @Mock
    private DenseRetrievalStrategy denseRetrievalStrategy;

    @Mock
    private HybridRetrievalStrategy hybridRetrievalStrategy;

    @Mock
    private RetrievalFusionService retrievalFusionService;

    @Mock
    private RetrievalReranker retrievalReranker;

    @Mock
    private RetrievalFilter retrievalFilter;

    @Mock
    private ContextCompressionStrategy contextCompressionStrategy;

    @Mock
    private RetrievalPromptTextBuilder retrievalPromptTextBuilder;

    @Mock
    private MetadataAppendixLoader metadataAppendixLoader;

    @Mock
    private MetadataConstraintFilter metadataConstraintFilter;

    @Mock
    private RetrievalContextExpander retrievalContextExpander;

    @InjectMocks
    private AdvancedRetrievalPipeline pipeline;

    @BeforeEach
    void stubContextExpanderPassthrough() {
        lenient()
                .when(retrievalContextExpander.expand(any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            List<RetrievalCandidate> input = inv.getArgument(2);
                            return new RetrievalContextExpander.ExpansionResult(
                                    input != null ? input : List.of(),
                                    input != null ? input.size() : 0,
                                    List.of());
                        });
    }

    @Test
    void retrieve_denseOnlyEmpty_addsRetrievalEmptyNote() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequest(sid, RetrievalMode.DENSE_ONLY);

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(denseRetrievalStrategy.retrieveWithOutcome(req)).thenReturn(emptyDenseOutcome());
        when(retrievalFilter.filterBasic(eq(req), eq(List.of()))).thenReturn(List.of());
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("");

        var out = pipeline.retrieve(ctx, plan, "DocumentDenseRagWorkflow");

        assertThat(out.traceNotes()).contains("retrieval_empty_result");
    }

    @Test
    void retrieve_whenRankerDisabled_skipsReranker_andTracesSkippedStage() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid, false);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequest(sid, RetrievalMode.DENSE_ONLY);
        List<RetrievalCandidate> dense =
                List.of(new RetrievalCandidate("c1", "x", Map.of(), 0.1, 0.0, 1, 0, sid, 1.0));

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(denseRetrievalStrategy.retrieveWithOutcome(req)).thenReturn(denseOutcome(dense));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(dense);
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseRagWorkflow");

        verify(retrievalReranker, never()).rerank(any(), any(), any());
        assertThat(out.retrievalStageTraces())
                .anyMatch(s -> "retrieval_rerank".equals(s.stageName()) && s.outcome() == ExecutionStageOutcome.SKIPPED);
        assertThat(out.diagnostics().rerankApplied()).isFalse();
    }

    @Test
    void retrieve_whenRankerEnabled_callsReranker_andTracesSuccessStage() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid, true);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequest(sid, RetrievalMode.DENSE_ONLY);
        List<RetrievalCandidate> dense =
                List.of(new RetrievalCandidate("c1", "x", Map.of(), 0.1, 0.0, 1, 0, sid, 1.0));

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(denseRetrievalStrategy.retrieveWithOutcome(req)).thenReturn(denseOutcome(dense));
        when(retrievalReranker.rerank(eq(req), eq(plan), eq(dense)))
                .thenReturn(new RetrievalReranker.RerankResult(dense, List.of()));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(dense);
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseRagWorkflow");

        verify(retrievalReranker).rerank(eq(req), eq(plan), eq(dense));
        assertThat(out.retrievalStageTraces())
                .anyMatch(s -> "retrieval_rerank".equals(s.stageName()) && s.outcome() == ExecutionStageOutcome.SUCCESS);
        assertThat(out.diagnostics().rerankApplied()).isTrue();
    }

    @Test
    void retrieve_whenPostRetrievalEnabled_appliesAdvancedFilter_andEvidenceAwareCompression() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid, true, true);
        QueryPlan plan = planWithDate("25/02/2025");
        RetrievalRequest req = retrievalRequestWithMaxChars(sid, RetrievalMode.DENSE_ONLY, 10);

        RetrievalCandidate protectedDate =
                new RetrievalCandidate("p1", "Fecha 25/02/2025 contenido", Map.of("filename", "ACTA 7.pdf"), 0.1, 0.0, 1, 0, sid, 1.0);
        RetrievalCandidate tail =
                new RetrievalCandidate("t1", "xxxxx xxxxx xxxxx", Map.of("filename", "ACTA 8.pdf"), 0.2, 0.0, 2, 0, sid, 0.9);
        List<RetrievalCandidate> dense = List.of(protectedDate, tail);

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(denseRetrievalStrategy.retrieveWithOutcome(req)).thenReturn(denseOutcome(dense));
        when(retrievalReranker.rerank(eq(req), eq(plan), eq(dense)))
                .thenReturn(new RetrievalReranker.RerankResult(dense, List.of()));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(dense);
        when(retrievalFilter.filterAdvanced(eq(req), eq(plan), any())).thenReturn(dense);
        when(metadataConstraintFilter.apply(eq(req), eq(plan), eq(dense)))
                .thenReturn(new MetadataConstraintFilter.FilterResult(dense, new MetadataFilterTelemetry(false, false)));
        when(contextCompressionStrategy.compressPreservingEvidence(eq(dense), anyInt(), any()))
                .thenReturn(new ContextCompressionStrategy.CompressionResult(List.of(protectedDate), new CompressionOutcome(50, 10, 1, List.of("drop_lowest_rerank_tail_unprotected_first"))));
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseRagWorkflow");

        verify(retrievalFilter).filterAdvanced(eq(req), eq(plan), any());
        verify(contextCompressionStrategy).compressPreservingEvidence(eq(dense), eq(10), any());
        assertThat(out.finalCandidates()).extracting(RetrievalCandidate::candidateId).contains("p1");
        assertThat(out.retrievalStageTraces())
                .anyMatch(s -> "retrieval_filter_advanced".equals(s.stageName()) && s.outcome() == ExecutionStageOutcome.SUCCESS);
        assertThat(out.retrievalStageTraces())
                .anyMatch(s -> "retrieval_compress".equals(s.stageName()) && s.outcome() == ExecutionStageOutcome.SUCCESS);
    }

    @Test
    void retrieve_dateSpecificQuestionKeepsExactDateAndDropsWrongYearFromPromptCandidates() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid, false, false);
        QueryPlan plan = planWithDate("25/02/2026");
        RetrievalRequest req = retrievalRequestWithQuery(sid, RetrievalMode.DENSE_ONLY, "Resumen del acta del 25/02/2026");

        RetrievalCandidate wrongYear =
                new RetrievalCandidate("wrong", "Fecha: 25 de febrero de 2025", Map.of("filename", "ACTA2.pdf"), 0.1, 0.0, 1, 0, sid, 1.0);
        RetrievalCandidate exact =
                new RetrievalCandidate("exact", "Fecha: 25 de febrero de 2026", Map.of("filename", "ACTA5.pdf"), 0.2, 0.0, 2, 0, sid, 0.9);
        List<RetrievalCandidate> dense = List.of(wrongYear, exact);

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(denseRetrievalStrategy.retrieveWithOutcome(req)).thenReturn(denseOutcome(dense));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(dense);
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseRagWorkflow");

        assertThat(out.finalCandidates()).containsExactly(exact);
        assertThat(out.retrievalStageTraces())
                .anyMatch(s -> "date_grounding".equals(s.stageName())
                        && s.message().contains("requestedDate=2026-02-25")
                        && s.message().contains("exactDateMatch=true")
                        && s.message().contains("after=1"));
    }

    @Test
    void retrieve_dateSpecificQuestionKeepsExactDateBeforePostFusionCapWhenRerankerDisabled() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid, false, false);
        QueryPlan plan = planWithDate("25/02/2026");
        RetrievalRequest req = retrievalRequestWithQueryAndCap(sid, RetrievalMode.DENSE_ONLY, "Resumen del acta del 25/02/2026", 1);

        RetrievalCandidate wrongYear =
                new RetrievalCandidate("wrong", "Fecha: 25 de febrero de 2025", Map.of("filename", "ACTA2.pdf"), 0.1, 0.0, 1, 0, sid, 1.0);
        RetrievalCandidate exact =
                new RetrievalCandidate("exact", "Fecha: 25 de febrero de 2026", Map.of("filename", "ACTA5.pdf"), 0.2, 0.0, 2, 0, sid, 0.9);
        List<RetrievalCandidate> dense = List.of(wrongYear, exact);

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(denseRetrievalStrategy.retrieveWithOutcome(req)).thenReturn(denseOutcome(dense));
        when(retrievalFilter.filterBasic(eq(req), any())).thenAnswer(inv -> inv.getArgument(1));
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseRagWorkflow");

        assertThat(out.finalCandidates()).containsExactly(exact);
        assertThat(out.retrievalStageTraces())
                .anyMatch(s -> "date_grounding".equals(s.stageName())
                        && s.message().contains("candidateSourceCountBeforeDateFilter=1")
                        && s.message().contains("candidateSourceCountAfterDateFilter=1")
                        && s.message().contains("dateBoostApplied=true"));
    }

    @Test
    void retrieve_hybridSparseZero_continuesDensePathAndRecordsNotes() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequest(sid, RetrievalMode.HYBRID_DENSE_SPARSE);
        List<RetrievalCandidate> dense =
                List.of(new RetrievalCandidate("c1", "x", Map.of(), 0.1, 0.0, 1, 0, sid, 1.0));

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(hybridRetrievalStrategy.dense(req)).thenReturn(dense);
        when(hybridRetrievalStrategy.sparseWithOutcome(req, plan)).thenReturn(emptySparseOutcome());
        when(retrievalFusionService.fuseWithTelemetry(eq(req), eq(dense), eq(List.of())))
                .thenReturn(
                        new RetrievalFusionService.FusionResult(
                                new RetrievedContextSet(
                                        dense,
                                        Optional.empty(),
                                        dense.size(),
                                        0,
                                        dense.size()),
                                new FusionTelemetry(
                                        "DENSE_ONLY_FALLBACK", 1, dense.size(), 0, false)));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(dense);
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseMetadataWorkflow");

        assertThat(out.traceNotes()).contains("sparse_zero_matches", "hybrid_not_applied");
        assertThat(out.diagnostics().sparseCandidateCount()).isZero();
        assertThat(out.diagnostics().denseCandidateCount()).isEqualTo(1);
    }

    @Test
    void retrieve_hybridWithSparseHits_recordsFusionOriginsInStage() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequest(sid, RetrievalMode.HYBRID_DENSE_SPARSE);
        RetrievalCandidate dense =
                new RetrievalCandidate("shared", "dense", Map.of(), 0.2, 0.0, 1, 0, sid, 0.5);
        RetrievalCandidate sparse =
                new RetrievalCandidate("shared", "sparse", Map.of(), 0.0, 0.3, 0, 1, sid, 0.4);
        RetrievedContextSet fused =
                new RetrievedContextSet(
                        List.of(dense),
                        Optional.empty(),
                        1,
                        1,
                        1,
                        0,
                        0,
                        1,
                        "dense=1;sparse=1;both=1;fused=1");

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(hybridRetrievalStrategy.dense(req)).thenReturn(List.of(dense));
        when(hybridRetrievalStrategy.sparseWithOutcome(req, plan)).thenReturn(sparseOutcome(List.of(sparse)));
        when(retrievalFusionService.fuseWithTelemetry(eq(req), any(), any()))
                .thenReturn(
                        new RetrievalFusionService.FusionResult(
                                fused,
                                new FusionTelemetry("RRF", 2, 1, 0, true)));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(List.of(dense));
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseMetadataWorkflow");

        assertThat(out.retrievalStageTraces())
                .anyMatch(
                        s ->
                                "retrieval_fuse".equals(s.stageName())
                                        && s.message().contains("both=1"));
        assertThat(out.diagnostics().sparseCandidateCount()).isEqualTo(1);
    }

    @Test
    void retrieve_forcedCompressionBudget_reducesCharsAndPreservesChunkIds() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid, true, true);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequestWithMaxChars(sid, RetrievalMode.HYBRID_DENSE_SPARSE, 80);
        RetrievalCandidate longChunk =
                new RetrievalCandidate(
                        "keep-me",
                        "x".repeat(200),
                        Map.of("chunkId", "keep-me"),
                        0.1,
                        0.0,
                        1,
                        0,
                        sid,
                        1.0);
        ContextCompressionStrategy realCompression = new ContextCompressionStrategy();

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(hybridRetrievalStrategy.dense(req)).thenReturn(List.of(longChunk));
        when(hybridRetrievalStrategy.sparseWithOutcome(req, plan)).thenReturn(emptySparseOutcome());
        when(retrievalFusionService.fuseWithTelemetry(eq(req), any(), eq(List.of())))
                .thenReturn(
                        new RetrievalFusionService.FusionResult(
                                new RetrievedContextSet(
                                        List.of(longChunk),
                                        Optional.empty(),
                                        1,
                                        0,
                                        1,
                                        1,
                                        0,
                                        0,
                                        "dense=1;sparse=0;fused=1"),
                                new FusionTelemetry(
                                        "DENSE_ONLY_FALLBACK", 1, 1, 0, false)));
        when(retrievalReranker.rerank(any(), any(), any()))
                .thenReturn(new RetrievalReranker.RerankResult(List.of(longChunk), List.of()));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(List.of(longChunk));
        when(retrievalFilter.filterAdvanced(eq(req), any(), any())).thenReturn(List.of(longChunk));
        when(metadataConstraintFilter.apply(eq(req), eq(plan), any()))
                .thenAnswer(inv -> new MetadataConstraintFilter.FilterResult(inv.getArgument(2), new MetadataFilterTelemetry(false, false)));
        when(contextCompressionStrategy.compressPreservingEvidence(any(), eq(80), any()))
                .thenAnswer(
                        inv ->
                                realCompression.compressPreservingEvidence(
                                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseMetadataWorkflow");

        assertThat(out.diagnostics().compressionCharsBefore()).isGreaterThan(out.diagnostics().compressionCharsAfter());
        assertThat(out.finalCandidates()).isNotEmpty();
        assertThat(out.finalCandidates().getFirst().candidateId()).isEqualTo("keep-me");
    }

    @Test
    void retrieve_hybridSparseFailure_fallsBackToDenseFusion() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequest(sid, RetrievalMode.HYBRID_DENSE_SPARSE);
        List<RetrievalCandidate> dense =
                List.of(new RetrievalCandidate("c1", "x", Map.of(), 0.1, 0.0, 1, 0, sid, 1.0));

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(hybridRetrievalStrategy.dense(req)).thenReturn(dense);
        when(hybridRetrievalStrategy.sparseWithOutcome(req, plan))
                .thenThrow(RagServiceException.hybridSparseRetrievalFailed(new RuntimeException("db")));
        when(retrievalFusionService.fuseWithTelemetry(eq(req), eq(dense), eq(List.of())))
                .thenReturn(
                        new RetrievalFusionService.FusionResult(
                                new RetrievedContextSet(
                                        dense,
                                        Optional.empty(),
                                        dense.size(),
                                        0,
                                        dense.size()),
                                new FusionTelemetry(
                                        "DENSE_ONLY_FALLBACK", 1, dense.size(), 0, false)));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(dense);
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseRagWorkflow");

        assertThat(out.traceNotes()).contains("sparse_unavailable", "hybrid_not_applied");
        assertThat(out.retrievalStageTraces())
                .anyMatch(
                        s ->
                                "retrieval_sparse".equals(s.stageName())
                                        && s.outcome() == ExecutionStageOutcome.SKIPPED);
        assertThat(out.diagnostics().sparseCandidateCount()).isZero();
        assertThat(out.diagnostics().denseCandidateCount()).isEqualTo(1);
    }

    @Test
    void retrieve_actaNumberAnchorKeepsOnlyMatchingDocument() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid, false, false);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req =
                retrievalRequestWithQuery(sid, RetrievalMode.DENSE_ONLY, "¿Cuántos asistentes hubo en el acta 3?");

        RetrievalCandidate acta3 =
                new RetrievalCandidate("a3", "18 asistentes", Map.of("filename", "ACTA 3.pdf"), 0.2, 0.0, 2, 0, sid, 0.9);
        RetrievalCandidate acta6 =
                new RetrievalCandidate("a6", "20 asistentes", Map.of("filename", "ACTA 6.pdf"), 0.1, 0.0, 1, 0, sid, 1.0);
        List<RetrievalCandidate> dense = List.of(acta6, acta3);

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(denseRetrievalStrategy.retrieveWithOutcome(req)).thenReturn(denseOutcome(dense));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(dense);
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseRagWorkflow");

        assertThat(out.finalCandidates()).containsExactly(acta3);
        assertThat(out.retrievalStageTraces())
                .anyMatch(
                        s ->
                                "acta_anchor_grounding".equals(s.stageName())
                                        && s.message().contains("actaNumber=3")
                                        && s.message().contains("ACTA 3.pdf")
                                        && s.message().contains("after=1"));
    }

    @Test
    void retrieve_setsEffectiveTopKAndSectionMergeReductionReasonInDiagnostics() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid, false, false);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequestWithTopKAndCap(sid, RetrievalMode.DENSE_ONLY, 8, 8);
        List<RetrievalCandidate> dense =
                List.of(
                        new RetrievalCandidate("c1", "x1", Map.of(), 0.1, 0.0, 1, 0, sid, 1.0),
                        new RetrievalCandidate("c2", "x2", Map.of(), 0.2, 0.0, 2, 0, sid, 0.9),
                        new RetrievalCandidate("c3", "x3", Map.of(), 0.3, 0.0, 3, 0, sid, 0.8),
                        new RetrievalCandidate("c4", "x4", Map.of(), 0.4, 0.0, 4, 0, sid, 0.7),
                        new RetrievalCandidate("c5", "x5", Map.of(), 0.5, 0.0, 5, 0, sid, 0.6),
                        new RetrievalCandidate("c6", "x6", Map.of(), 0.6, 0.0, 6, 0, sid, 0.5),
                        new RetrievalCandidate("c7", "x7", Map.of(), 0.7, 0.0, 7, 0, sid, 0.4),
                        new RetrievalCandidate("c8", "x8", Map.of(), 0.8, 0.0, 8, 0, sid, 0.3));
        List<RetrievalCandidate> merged = List.of(dense.get(0), dense.get(1), dense.get(2));

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(denseRetrievalStrategy.retrieveWithOutcome(req)).thenReturn(denseOutcome(dense));
        when(retrievalFilter.filterBasic(eq(req), any())).thenReturn(dense);
        when(retrievalContextExpander.expand(eq(req), eq(plan), eq(dense)))
                .thenReturn(new RetrievalContextExpander.ExpansionResult(merged, merged.size(), List.of("section_expand:8->3")));
        when(retrievalPromptTextBuilder.build(any(), any(), any(), anyBoolean())).thenReturn("CTX");

        var out = pipeline.retrieve(ctx, plan, "ChunkDenseMetadataWorkflow");

        assertThat(out.diagnostics().retrievalEffectiveTopK()).contains(8);
        assertThat(out.diagnostics().retrievalContextReductionReason()).contains("section_merge");
        assertThat(out.diagnostics().afterCompressionCount()).isEqualTo(3);
    }

    private static ExecutionContext executionContext(UUID snapshotId) {
        return executionContext(snapshotId, false);
    }

    private static ExecutionContext executionContext(UUID snapshotId, boolean rankerEnabled) {
        return executionContext(snapshotId, rankerEnabled, false);
    }

    private static ExecutionContext executionContext(UUID snapshotId, boolean rankerEnabled, boolean postRetrievalEnabled) {
        RagConfig rag = baseRag(rankerEnabled, postRetrievalEnabled);
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                new KnowledgeSnapshotSelection(
                        List.of(snapshotId), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "u",
                "u",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty());
    }

    private static RagConfig baseRag(boolean rankerEnabled, boolean postRetrievalEnabled) {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                rankerEnabled,
                postRetrievalEnabled,
                false,
                true,
                false,
                false,
                false,
                5,
                0.7,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static QueryPlan minimalPlan() {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "raw",
                "rewritten",
                "L",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                StructuredRewriteResult.identityDisabled("r", "r"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    private static RetrievalRequest retrievalRequest(UUID snapshotId, RetrievalMode mode) {
        return retrievalRequestWithMaxChars(snapshotId, mode, 24_000);
    }

    private static RetrievalRequest retrievalRequestWithMaxChars(UUID snapshotId, RetrievalMode mode, int maxChars) {
        return retrievalRequestWithQueryAndMaxChars(snapshotId, mode, "q", maxChars);
    }

    private static RetrievalRequest retrievalRequestWithQuery(UUID snapshotId, RetrievalMode mode, String query) {
        return retrievalRequestWithQueryAndMaxChars(snapshotId, mode, query, 24_000);
    }

    private static RetrievalRequest retrievalRequestWithQueryAndCap(UUID snapshotId, RetrievalMode mode, String query, int cap) {
        return new RetrievalRequest(
                query,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                mode,
                5,
                5,
                10,
                cap,
                24_000,
                RetrievalPolicy.denseFetchLimit(5),
                List.of(snapshotId),
                UUID.randomUUID(),
                Optional.empty(),
                List.of("all"),
                true,
                Optional.empty());
    }

    private static RetrievalRequest retrievalRequestWithQueryAndMaxChars(UUID snapshotId, RetrievalMode mode, String query, int maxChars) {
        return new RetrievalRequest(
                query,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                mode,
                5,
                5,
                10,
                5,
                maxChars,
                RetrievalPolicy.denseFetchLimit(5),
                List.of(snapshotId),
                UUID.randomUUID(),
                Optional.empty(),
                List.of("all"), true, Optional.empty());
    }

    private static RetrievalRequest retrievalRequestWithTopKAndCap(
            UUID snapshotId, RetrievalMode mode, int topK, int postFusionCap) {
        return new RetrievalRequest(
                "q",
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                mode,
                topK,
                topK,
                2 * topK,
                postFusionCap,
                24_000,
                RetrievalPolicy.denseFetchLimit(topK),
                List.of(snapshotId),
                UUID.randomUUID(),
                Optional.empty(),
                List.of("all"),
                true,
                Optional.empty());
    }

    private static QueryPlan planWithDate(String date) {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        date == null ? List.of() : List.of(date),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "raw",
                "rewritten",
                "L",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                StructuredRewriteResult.identityDisabled("r", "r"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    private static DenseRetrievalOutcome emptyDenseOutcome() {
        return new DenseRetrievalOutcome(List.of(), 0, 0, 0);
    }

    private static DenseRetrievalOutcome denseOutcome(List<RetrievalCandidate> dense) {
        int n = dense != null ? dense.size() : 0;
        return new DenseRetrievalOutcome(dense, n, n, n);
    }

    private static SparseRetrievalOutcome emptySparseOutcome() {
        SparseQueryPreparation prep = new SparseQueryPreparation("q", "q", List.of(), List.of(), List.of(), List.of(), List.of());
        return new SparseRetrievalOutcome(
                List.of(),
                prep,
                SparseRetrievalFallbackStage.NO_HIT,
                "",
                new SparseRetrievalTelemetry("q", "", SparseRetrievalFallbackStage.NO_HIT, false, "no_lexical_match"));
    }

    private static SparseRetrievalOutcome sparseOutcome(List<RetrievalCandidate> candidates) {
        SparseQueryPreparation prep = new SparseQueryPreparation("q", "q", List.of("term"), List.of(), List.of(), List.of(), List.of());
        return new SparseRetrievalOutcome(
                candidates,
                prep,
                SparseRetrievalFallbackStage.OR_KEYWORDS,
                "term",
                new SparseRetrievalTelemetry("q", "term", SparseRetrievalFallbackStage.OR_KEYWORDS, true, ""));
    }
}
