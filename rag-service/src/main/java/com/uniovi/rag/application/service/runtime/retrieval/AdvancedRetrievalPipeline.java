package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CompressionOutcome;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import com.uniovi.rag.domain.runtime.retrieval.RerankOutcome;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalFusionMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.RetrievedContextSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Single entrypoint for advanced snapshot-bound retrieval.
 */
@Service
public class AdvancedRetrievalPipeline {

    public static final String WORKFLOW_CHUNK_DENSE_METADATA = "ChunkDenseMetadataWorkflow";

    private final RetrievalRequestBuilder retrievalRequestBuilder;
    private final DenseRetrievalStrategy denseRetrievalStrategy;
    private final HybridRetrievalStrategy hybridRetrievalStrategy;
    private final RetrievalFusionService retrievalFusionService;
    private final RetrievalReranker retrievalReranker;
    private final RetrievalFilter retrievalFilter;
    private final ContextCompressionStrategy contextCompressionStrategy;
    private final RetrievalPromptTextBuilder retrievalPromptTextBuilder;
    private final MetadataAppendixLoader metadataAppendixLoader;

    public AdvancedRetrievalPipeline(
            RetrievalRequestBuilder retrievalRequestBuilder,
            DenseRetrievalStrategy denseRetrievalStrategy,
            HybridRetrievalStrategy hybridRetrievalStrategy,
            RetrievalFusionService retrievalFusionService,
            RetrievalReranker retrievalReranker,
            RetrievalFilter retrievalFilter,
            ContextCompressionStrategy contextCompressionStrategy,
            RetrievalPromptTextBuilder retrievalPromptTextBuilder,
            MetadataAppendixLoader metadataAppendixLoader) {
        this.retrievalRequestBuilder = retrievalRequestBuilder;
        this.denseRetrievalStrategy = denseRetrievalStrategy;
        this.hybridRetrievalStrategy = hybridRetrievalStrategy;
        this.retrievalFusionService = retrievalFusionService;
        this.retrievalReranker = retrievalReranker;
        this.retrievalFilter = retrievalFilter;
        this.contextCompressionStrategy = contextCompressionStrategy;
        this.retrievalPromptTextBuilder = retrievalPromptTextBuilder;
        this.metadataAppendixLoader = metadataAppendixLoader;
    }

    public CuratedContextSet retrieve(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        List<ExecutionStageTrace> traces = new ArrayList<>();
        List<String> traceNotes = new ArrayList<>();

        long tBuild = System.nanoTime();
        RetrievalRequest req = retrievalRequestBuilder.build(ctx, plan);
        traces.add(stage("retrieval_build_request", tBuild, ExecutionStageOutcome.SUCCESS, ""));

        RetrievedContextSet retrieved;
        if (req.mode() == RetrievalMode.DENSE_ONLY) {
            long tDense = System.nanoTime();
            List<RetrievalCandidate> dense = denseRetrievalStrategy.retrieve(req);
            traces.add(stage("retrieval_dense", tDense, ExecutionStageOutcome.SUCCESS, "count=" + dense.size()));
            traces.add(skipped("retrieval_sparse", "mode=DENSE_ONLY"));
            traces.add(skipped("retrieval_fuse", "mode=DENSE_ONLY"));
            retrieved =
                    new RetrievedContextSet(
                            dense,
                            Optional.empty(),
                            dense.size(),
                            0,
                            dense.size());
        } else {
            long tDense = System.nanoTime();
            List<RetrievalCandidate> dense = hybridRetrievalStrategy.dense(req);
            traces.add(stage("retrieval_dense", tDense, ExecutionStageOutcome.SUCCESS, "count=" + dense.size()));
            long tSparse = System.nanoTime();
            List<RetrievalCandidate> sparse = hybridRetrievalStrategy.sparse(req);
            traces.add(stage("retrieval_sparse", tSparse, ExecutionStageOutcome.SUCCESS, "count=" + sparse.size()));
            long tFuse = System.nanoTime();
            retrieved = retrievalFusionService.fuse(req, dense, sparse);
            traces.add(stage("retrieval_fuse", tFuse, ExecutionStageOutcome.SUCCESS, "count=" + retrieved.fusedCount()));
        }

        if (retrieved.candidates().isEmpty()) {
            traceNotes.add("retrieval_empty_result");
        }

        long tRerank = System.nanoTime();
        RetrievalReranker.RerankResult rerankResult = retrievalReranker.rerank(req, plan, retrieved.candidates());
        traces.add(
                stage(
                        "retrieval_rerank",
                        tRerank,
                        ExecutionStageOutcome.SUCCESS,
                        "count=" + rerankResult.candidates().size()));

        long tFilter = System.nanoTime();
        List<RetrievalCandidate> filtered = retrievalFilter.filter(req, plan, rerankResult.candidates());
        traces.add(stage("retrieval_filter", tFilter, ExecutionStageOutcome.SUCCESS, "count=" + filtered.size()));

        long tCompress = System.nanoTime();
        ContextCompressionStrategy.CompressionResult compressed = contextCompressionStrategy.compress(filtered, req.maxContextChars());
        traces.add(stage("retrieval_compress", tCompress, ExecutionStageOutcome.SUCCESS, "count=" + compressed.candidates().size()));

        RetrievalLayout layout =
                "DocumentDenseRagWorkflow".equals(workflowName)
                        ? RetrievalLayout.DOCUMENT_COMBINED
                        : RetrievalLayout.CHUNK_SEPARATE;
        String prompt = retrievalPromptTextBuilder.build(compressed.candidates(), req.queryText(), layout);
        if (compressed.candidates().isEmpty()) {
            prompt = "";
        }

        if (WORKFLOW_CHUNK_DENSE_METADATA.equals(workflowName)) {
            String appendix = metadataAppendixLoader.loadAppendix(ctx, plan, compressed.candidates());
            if (appendix != null && !appendix.isBlank()) {
                if (!prompt.isEmpty()) {
                    prompt = prompt + "\n\n" + appendix.trim();
                } else {
                    prompt = appendix.trim();
                }
            }
        }

        String snapJoin =
                ctx.knowledgeSnapshotSelection().orderedSnapshotIds().stream()
                        .sorted(Comparator.comparing(UUID::toString))
                        .map(UUID::toString)
                        .collect(Collectors.joining(","));

        CompressionOutcome comp = compressed.outcome();
        RetrievalDiagnostics diagnostics =
                new RetrievalDiagnostics(
                        req.mode(),
                        retrieved.fusionModeUsed(),
                        snapJoin,
                        retrieved.denseInputCount(),
                        retrieved.sparseInputCount(),
                        retrieved.fusedCount(),
                        rerankResult.candidates().size(),
                        filtered.size(),
                        compressed.candidates().size());

        return new CuratedContextSet(
                compressed.candidates(),
                prompt,
                comp,
                traceNotes,
                diagnostics,
                rerankResult.outcomes(),
                traces);
    }

    private static ExecutionStageTrace stage(
            String name, long startNanos, ExecutionStageOutcome outcome, String message) {
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        return new ExecutionStageTrace(name, ms, outcome, message != null ? message : "");
    }

    private static ExecutionStageTrace skipped(String name, String detail) {
        return new ExecutionStageTrace(name, 0L, ExecutionStageOutcome.SKIPPED, detail != null ? detail : "");
    }
}
