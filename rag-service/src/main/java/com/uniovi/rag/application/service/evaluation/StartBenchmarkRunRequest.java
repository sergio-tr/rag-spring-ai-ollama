package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.EvaluationRunKind;

import java.util.List;
import java.util.UUID;

/**
 * Body for {@code POST /lab/benchmarks/{kind}/runs} (JSON benchmarks).
 */
public record StartBenchmarkRunRequest(
        UUID datasetId,
        UUID projectId,
        EvaluationRunKind runKind,
        String name,
        UUID resolvedConfigSnapshotId,
        UUID indexSnapshotId,
        UUID presetId,
        /** When true, {@link com.uniovi.rag.domain.evaluation.BenchmarkKind#EMBEDDING_RETRIEVAL} runs retrieval then a fixed-LLM answer step. */
        Boolean embeddingDownstreamRag,
        /** Optional subset of workbook protocol presets (e.g. P0..P14) for RAG benchmark runs. */
        List<String> experimentalPresetCodes,
        /** Optional Ollama chat tag override persisted on {@code evaluation_run.llm_model_id}. */
        String llmModelId,
        /** Optional Ollama embedding tag override persisted on {@code evaluation_run.embedding_model_id}. */
        String embeddingModelId,
        /** Optional list of Ollama chat tags for multi-model campaigns (LLM baseline). */
        List<String> llmModelIds,
        /** Optional list of embedding tags for multi-model campaigns (embedding baseline). */
        List<String> embeddingModelIds,
        /** When true, derive model candidates from the workbook sheets (llm_candidates / embedding_candidates). */
        Boolean useWorkbookCandidates,
        /** Optional campaign display name; only used for multi-model requests. */
        String campaignName) {

    public StartBenchmarkRunRequest {
        runKind = runKind == null ? EvaluationRunKind.PRODUCT_EXPLORATION : runKind;
        experimentalPresetCodes =
                experimentalPresetCodes == null
                        ? List.of()
                        : experimentalPresetCodes.stream()
                                .filter(s -> s != null && !s.isBlank())
                                .map(String::trim)
                                .toList();
        llmModelIds =
                llmModelIds == null
                        ? List.of()
                        : llmModelIds.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        embeddingModelIds =
                embeddingModelIds == null
                        ? List.of()
                        : embeddingModelIds.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
    }

    public boolean embeddingDownstreamRagEffective() {
        return Boolean.TRUE.equals(embeddingDownstreamRag);
    }

    public boolean useWorkbookCandidatesEffective() {
        return Boolean.TRUE.equals(useWorkbookCandidates);
    }
}
