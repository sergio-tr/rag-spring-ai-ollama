package com.uniovi.rag.application.service.evaluation.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagPresetAdvancedRetrievalMetricsTest {

    @Test
    void compute_hybridPresetWithSparseAndDense_marksHybridApplied() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("useRetrieval", true);
        mp.put("materializationStrategy", "HYBRID");
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put("retrievalMode", "HYBRID_DENSE_SPARSE");
        mp.put("denseCandidateCount", 5);
        mp.put("sparseCandidateCount", 3);
        mp.put("mergedCandidateCount", 6);
        mp.put("rerankApplied", true);
        mp.put("rerankChangedOrder", true);
        mp.put("compressionApplied", true);
        mp.put("compressedContextCharCount", 120);
        mp.put("contextChunkCount", 4);
        mp.put("promptContextCharCount", 500);
        mp.put("sparseRetrievalStatus", "OK");

        Map<String, Object> out = RagPresetAdvancedRetrievalMetrics.compute(mp);

        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED)).isEqualTo(true);
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_APPLIED)).isEqualTo(true);
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_CANDIDATE_COUNT)).isEqualTo(8);
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_CANDIDATE_ORIGINS)).isEqualTo("dense=5;sparse=3;fused=6");
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_STRATEGY))
                .isEqualTo("HYBRID_DENSE_SPARSE_RRF_RERANK_COMPRESS");
    }

    @Test
    void compute_prefersExplicitCandidateOriginsWithBothCounts() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("useRetrieval", true);
        mp.put("materializationStrategy", "HYBRID");
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put("retrievalMode", "HYBRID_DENSE_SPARSE");
        mp.put("denseCandidateCount", 2);
        mp.put("sparseCandidateCount", 2);
        mp.put("mergedCandidateCount", 3);
        mp.put("candidateOrigins", "dense=2;sparse=2;both=1;fused=3");
        mp.put("rerankApplied", true);
        mp.put("rerankChangedOrder", false);

        Map<String, Object> out = RagPresetAdvancedRetrievalMetrics.compute(mp);

        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_CANDIDATE_ORIGINS))
                .isEqualTo("dense=2;sparse=2;both=1;fused=3");
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_RERANK_NOOP_REASON)).isEqualTo("order_unchanged");
    }

    @Test
    void compute_sparseZero_recordsSparseZeroFallback() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("useRetrieval", true);
        mp.put("materializationStrategy", "HYBRID");
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put("retrievalMode", "HYBRID_DENSE_SPARSE");
        mp.put("denseCandidateCount", 3);
        mp.put("sparseCandidateCount", 0);
        mp.put("mergedCandidateCount", 3);
        mp.put("sparseRetrievalStatus", "ZERO_MATCHES");

        Map<String, Object> out = RagPresetAdvancedRetrievalMetrics.compute(mp);

        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_APPLIED)).isEqualTo(false);
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_FALLBACK_REASON))
                .isEqualTo("sparse_zero_matches");
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_STRATEGY))
                .isEqualTo("HYBRID_DENSE_FALLBACK");
    }

    @Test
    void compute_denseOnlyPreset_doesNotMarkAdvancedRetrieval() {
        Map<String, Object> mp = Map.of(
                "useRetrieval", true,
                "materializationStrategy", "CHUNK_LEVEL",
                "denseCandidateCount", 4);

        Map<String, Object> out = RagPresetAdvancedRetrievalMetrics.compute(mp);

        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED)).isEqualTo(false);
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_FALLBACK_REASON))
                .isEqualTo("preset_not_hybrid");
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_APPLIED)).isEqualTo(false);
    }

    @Test
    void compute_sparseUnavailable_recordsFallbackReason() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("useRetrieval", true);
        mp.put("materializationStrategy", "HYBRID");
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put("retrievalMode", "HYBRID_DENSE_SPARSE");
        mp.put("denseCandidateCount", 2);
        mp.put("sparseCandidateCount", 0);
        mp.put("mergedCandidateCount", 2);
        mp.put("sparseRetrievalStatus", "UNAVAILABLE");

        Map<String, Object> out = RagPresetAdvancedRetrievalMetrics.compute(mp);

        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED)).isEqualTo(true);
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_APPLIED)).isEqualTo(false);
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_FALLBACK_REASON))
                .isEqualTo("sparse_unavailable");
    }

    @Test
    void compute_hybridPresetFailedExecution_doesNotMarkAdvancedRetrievalApplied() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("useRetrieval", true);
        mp.put("materializationStrategy", "HYBRID");
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.FAILED.name());

        Map<String, Object> out = RagPresetAdvancedRetrievalMetrics.compute(mp);

        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED)).isEqualTo(false);
        assertThat(out.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_STRATEGY))
                .isEqualTo("HYBRID_NOT_EXECUTED");
        assertThat(out).doesNotContainKey(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_FALLBACK_REASON);
    }
}
