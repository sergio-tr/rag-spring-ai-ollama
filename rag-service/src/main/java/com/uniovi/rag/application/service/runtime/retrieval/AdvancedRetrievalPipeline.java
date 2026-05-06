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
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.RetrievedContextSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
        boolean rerankEnabled = ctx.resolved().toRagConfig().rankerEnabled();
        List<String> beforeTop = topCandidateIds(retrieved.candidates(), 5);
        RetrievalReranker.RerankResult rerankResult;
        if (!rerankEnabled) {
            rerankResult = identityRerank(req, retrieved.candidates());
            traces.add(skipped("retrieval_rerank", "rankerEnabled=false count=" + rerankResult.candidates().size()));
        } else {
            rerankResult = retrievalReranker.rerank(req, plan, retrieved.candidates());
            traces.add(
                    stage(
                            "retrieval_rerank",
                            tRerank,
                            ExecutionStageOutcome.SUCCESS,
                            "count=" + rerankResult.candidates().size()));
        }
        List<String> afterTop = topCandidateIds(rerankResult.candidates(), 5);
        Optional<String> rerankScoreSummary = rerankEnabled
                ? Optional.ofNullable(scoreSummary(rerankResult.outcomes(), 5))
                : Optional.empty();

        long tFilterBasic = System.nanoTime();
        List<RetrievalCandidate> filteredBasic = retrievalFilter.filterBasic(req, rerankResult.candidates());
        traces.add(stage("retrieval_filter_basic", tFilterBasic, ExecutionStageOutcome.SUCCESS, "count=" + filteredBasic.size()));

        boolean postRetrievalEnabled = ctx.resolved().toRagConfig().postRetrievalEnabled();
        List<RetrievalCandidate> filteredFinal;
        int protectedCount = 0;
        int droppedByCompression = 0;
        ContextCompressionStrategy.CompressionResult compressed;

        if (!postRetrievalEnabled) {
            traces.add(skipped("retrieval_filter_advanced", "postRetrievalEnabled=false"));
            traces.add(skipped("retrieval_compress", "postRetrievalEnabled=false"));
            filteredFinal = filteredBasic;
            compressed =
                    new ContextCompressionStrategy.CompressionResult(
                            filteredFinal, new CompressionOutcome(totalChars(filteredFinal), totalChars(filteredFinal), 0, List.of("postretrieval_disabled")));
        } else {
            long tFilterAdv = System.nanoTime();
            filteredFinal = retrievalFilter.filterAdvanced(req, plan, rerankResult.candidates());
            traces.add(stage("retrieval_filter_advanced", tFilterAdv, ExecutionStageOutcome.SUCCESS, "count=" + filteredFinal.size()));

            Set<String> protectedIds = protectedCandidateIds(req, plan, filteredFinal);
            protectedCount = protectedIds.size();

            long tCompress = System.nanoTime();
            compressed =
                    contextCompressionStrategy.compressPreservingEvidence(
                            filteredFinal, req.maxContextChars(), protectedIds);
            traces.add(
                    stage(
                            "retrieval_compress",
                            tCompress,
                            ExecutionStageOutcome.SUCCESS,
                            "count=" + compressed.candidates().size()));
            droppedByCompression = compressed.outcome() != null ? compressed.outcome().droppedCandidateCount() : 0;

            if (compressed.candidates().isEmpty() && !filteredBasic.isEmpty()) {
                traceNotes.add("postretrieval_empty_fallback_to_basic");
                compressed =
                        new ContextCompressionStrategy.CompressionResult(
                                filteredBasic,
                                new CompressionOutcome(
                                        totalChars(filteredFinal),
                                        totalChars(filteredBasic),
                                        Math.max(0, filteredFinal.size() - filteredBasic.size()),
                                        List.of("fallback_to_basic_after_empty_postretrieval")));
            }
        }

        RetrievalLayout layout =
                "DocumentDenseRagWorkflow".equals(workflowName)
                        ? RetrievalLayout.DOCUMENT_COMBINED
                        : RetrievalLayout.CHUNK_SEPARATE;
        long tPack = System.nanoTime();
        String prompt = retrievalPromptTextBuilder.build(compressed.candidates(), req.queryText(), layout);
        traces.add(stage("context_pack", tPack, ExecutionStageOutcome.SUCCESS, "chars=" + (prompt != null ? prompt.length() : 0)));
        if (compressed.candidates().isEmpty()) {
            prompt = "";
        }
        if ((prompt == null || prompt.isBlank()) && !compressed.candidates().isEmpty()) {
            // Defensive: ensure non-empty prompt when we still have candidates.
            prompt = minimalPrompt(compressed.candidates());
            traceNotes.add("context_pack_fallback_minimal_prompt");
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
                        rerankResult.candidates().size(),
                        postRetrievalEnabled ? filteredFinal.size() : filteredBasic.size(),
                        compressed.candidates().size(),
                        protectedCount,
                        droppedByCompression,
                        rerankEnabled,
                        beforeTop,
                        afterTop,
                        rerankScoreSummary);

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

    private static RetrievalReranker.RerankResult identityRerank(RetrievalRequest req, List<RetrievalCandidate> candidates) {
        int cap = Math.max(0, req.postFusionCap());
        List<RetrievalCandidate> out = candidates == null ? List.of() : candidates.stream().limit(cap).toList();
        return new RetrievalReranker.RerankResult(out, List.of());
    }

    private static List<String> topCandidateIds(List<RetrievalCandidate> candidates, int max) {
        if (candidates == null || candidates.isEmpty() || max <= 0) {
            return List.of();
        }
        return candidates.stream()
                .limit(max)
                .map(RetrievalCandidate::candidateId)
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    private static String scoreSummary(List<RerankOutcome> outcomes, int max) {
        if (outcomes == null || outcomes.isEmpty() || max <= 0) {
            return null;
        }
        return outcomes.stream()
                .limit(max)
                .map(o -> o.candidateId() + ":" + String.format("%.2f", o.rerankScore()))
                .collect(Collectors.joining(","));
    }

    private static Set<String> protectedCandidateIds(RetrievalRequest req, QueryPlan plan, List<RetrievalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();

        // Explicit document selection is a strong signal: protect candidates from allowlisted docs.
        Set<String> allowlistedDocs = req.documentAllowlistIsAll() ? Set.of() : req.documentAllowlist().stream()
                .filter(s -> s != null && !s.isBlank() && !"all".equalsIgnoreCase(s.trim()))
                .map(String::trim)
                .collect(Collectors.toSet());

        List<String> dates = plan.entityExtractionResult() != null ? plan.entityExtractionResult().dates() : List.of();
        List<String> people = plan.entityExtractionResult() != null ? plan.entityExtractionResult().people() : List.of();
        List<String> topics = plan.entityExtractionResult() != null ? plan.entityExtractionResult().topics() : List.of();
        List<String> orgs = plan.entityExtractionResult() != null ? plan.entityExtractionResult().organizations() : List.of();

        for (RetrievalCandidate c : candidates) {
            if (c == null) {
                continue;
            }
            String id = c.candidateId();
            if (id == null || id.isBlank()) {
                continue;
            }
            String docId = extractDocId(c);
            if (!allowlistedDocs.isEmpty() && docId != null && allowlistedDocs.contains(docId)) {
                out.add(id);
                continue;
            }
            if (matchesAnyToken(c, dates) || matchesAnyToken(c, people) || matchesAnyToken(c, topics) || matchesAnyToken(c, orgs)) {
                out.add(id);
            }
        }
        return Set.copyOf(out);
    }

    private static String extractDocId(RetrievalCandidate c) {
        if (c == null || c.metadata() == null) {
            return null;
        }
        Object id = c.metadata().get("document_id");
        if (id == null) {
            id = c.metadata().get("documentId");
        }
        if (id == null) {
            id = c.metadata().get("projectDocumentId");
        }
        return id != null ? String.valueOf(id) : null;
    }

    private static boolean matchesAnyToken(RetrievalCandidate c, List<String> tokens) {
        if (tokens == null || tokens.isEmpty() || c == null) {
            return false;
        }
        String content = c.content() != null ? c.content() : "";
        String filename = c.metadata() != null && c.metadata().get("filename") != null ? String.valueOf(c.metadata().get("filename")) : "";
        String hay = (content + "\n" + filename).toLowerCase(Locale.ROOT);
        for (String t : tokens) {
            if (t == null || t.isBlank()) {
                continue;
            }
            String needle = t.toLowerCase(Locale.ROOT).trim();
            if (hay.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String minimalPrompt(List<RetrievalCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(2, candidates.size());
        for (int i = 0; i < n; i++) {
            RetrievalCandidate c = candidates.get(i);
            if (c == null || c.content() == null || c.content().isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(c.content().trim());
        }
        return sb.toString();
    }

    private static int totalChars(List<RetrievalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (RetrievalCandidate c : candidates) {
            if (c != null && c.content() != null) {
                n += c.content().length();
            }
        }
        return n;
    }
}
