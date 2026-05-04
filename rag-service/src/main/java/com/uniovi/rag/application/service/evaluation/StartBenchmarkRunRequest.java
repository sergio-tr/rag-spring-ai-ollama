package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.EvaluationRunKind;

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
        /** Optional Ollama chat tag override persisted on {@code evaluation_run.llm_model_id}. */
        String llmModelId,
        /** Optional Ollama embedding tag override persisted on {@code evaluation_run.embedding_model_id}. */
        String embeddingModelId) {

    public StartBenchmarkRunRequest {
        runKind = runKind == null ? EvaluationRunKind.PRODUCT_EXPLORATION : runKind;
    }

    public boolean embeddingDownstreamRagEffective() {
        return Boolean.TRUE.equals(embeddingDownstreamRag);
    }
}
