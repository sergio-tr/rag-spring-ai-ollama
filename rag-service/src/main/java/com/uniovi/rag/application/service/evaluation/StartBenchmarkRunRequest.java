package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.EvaluationRunKind;

import java.util.Objects;
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
        String campaignName,
        /** When true, Lab is allowed to perform controlled reindex/snapshot operations for this benchmark. */
        Boolean autoReindex,
        /** When true, the user explicitly accepts that the project ACTIVE snapshot may be mutated during the run. */
        Boolean allowActiveSnapshotMutation,
        /** When true, prefer reusing the current compatible ACTIVE snapshot instead of triggering rebuild. */
        Boolean reuseCompatibleActiveSnapshot,
        /** When true, fail the benchmark when auto-reindex fails (instead of skipping incompatible groups). */
        Boolean failOnReindexFailure,
        /** When true, Lab loads classpath resources into project documents before RAG_PRESET_END_TO_END (explicit only). */
        Boolean bootstrapCorpusFromClasspathDocs,
        /** Ant-style classpath pattern; default matches project docs tree via {@link #classpathDocsLocationOrDefault()}. */
        String classpathDocsLocation,
        /** Corpus scope string for bootstrap rows; default PROJECT_SHARED. */
        String bootstrapCorpusScope,
        /** When true, reuse READY rows with same filename (+checksum when present). */
        Boolean bootstrapSkipExisting,
        /** When true, fail the run when any classpath document cannot be ingested. */
        Boolean bootstrapFailOnDocumentError,
        /**
         * Parallel {@code knowledge_index_snapshot} ids for multi-embedding Lab campaigns (same length as resolved
         * embedding models when more than one model is requested).
         */
        List<UUID> indexSnapshotIds) {

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
        autoReindex = Boolean.TRUE.equals(autoReindex);
        allowActiveSnapshotMutation = Boolean.TRUE.equals(allowActiveSnapshotMutation);
        reuseCompatibleActiveSnapshot =
                reuseCompatibleActiveSnapshot == null ? Boolean.TRUE : Boolean.TRUE.equals(reuseCompatibleActiveSnapshot);
        failOnReindexFailure =
                failOnReindexFailure == null ? Boolean.TRUE : Boolean.TRUE.equals(failOnReindexFailure);
        bootstrapCorpusFromClasspathDocs = Boolean.TRUE.equals(bootstrapCorpusFromClasspathDocs);
        bootstrapSkipExisting = bootstrapSkipExisting == null ? Boolean.TRUE : Boolean.TRUE.equals(bootstrapSkipExisting);
        bootstrapFailOnDocumentError =
                bootstrapFailOnDocumentError == null ? Boolean.TRUE : Boolean.TRUE.equals(bootstrapFailOnDocumentError);
        indexSnapshotIds =
                indexSnapshotIds == null
                        ? List.of()
                        : indexSnapshotIds.stream().filter(Objects::nonNull).toList();
    }

    public boolean embeddingDownstreamRagEffective() {
        return Boolean.TRUE.equals(embeddingDownstreamRag);
    }

    public boolean useWorkbookCandidatesEffective() {
        return Boolean.TRUE.equals(useWorkbookCandidates);
    }

    public boolean autoReindexEffective() {
        return Boolean.TRUE.equals(autoReindex);
    }

    public boolean allowActiveSnapshotMutationEffective() {
        return Boolean.TRUE.equals(allowActiveSnapshotMutation);
    }

    public boolean reuseCompatibleActiveSnapshotEffective() {
        return Boolean.TRUE.equals(reuseCompatibleActiveSnapshot);
    }

    public boolean failOnReindexFailureEffective() {
        return Boolean.TRUE.equals(failOnReindexFailure);
    }

    public boolean bootstrapCorpusFromClasspathDocsEffective() {
        return Boolean.TRUE.equals(bootstrapCorpusFromClasspathDocs);
    }

    public String classpathDocsLocationOrDefault() {
        return classpathDocsLocation != null && !classpathDocsLocation.isBlank()
                ? classpathDocsLocation.trim()
                : "classpath*:docs/**/*";
    }

    public String bootstrapCorpusScopeOrDefault() {
        return bootstrapCorpusScope != null && !bootstrapCorpusScope.isBlank()
                ? bootstrapCorpusScope.trim()
                : "PROJECT_SHARED";
    }

    public boolean bootstrapSkipExistingEffective() {
        return Boolean.TRUE.equals(bootstrapSkipExisting);
    }

    public boolean bootstrapFailOnDocumentErrorEffective() {
        return Boolean.TRUE.equals(bootstrapFailOnDocumentError);
    }
}
