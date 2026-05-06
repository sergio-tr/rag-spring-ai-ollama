package com.uniovi.rag.service.evaluation.baseline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingRetrievalMetricsGoldIdsTest {

    @Test
    void mrr_isOneThird_whenFirstGoldIsAtPosition3() {
        List<String> retrieved = List.of("A", "B", "GOLD");
        Set<String> gold = Set.of("GOLD");
        assertThat(EmbeddingRetrievalMetrics.firstRelevantRankByIds(retrieved, gold)).isEqualTo(3);
        assertThat(EmbeddingRetrievalMetrics.mrrByIds(retrieved, gold)).isEqualTo(1.0 / 3.0);
    }

    @Test
    void recallAtN_hits_whenAnyGoldInPrefix() {
        List<String> retrieved = List.of("X", "G1", "Y", "G2");
        Set<String> gold = Set.of("G2", "G1");
        assertThat(EmbeddingRetrievalMetrics.recallAtNByIds(retrieved, gold, 1)).isEqualTo(0.0);
        assertThat(EmbeddingRetrievalMetrics.recallAtNByIds(retrieved, gold, 2)).isEqualTo(1.0);
        assertThat(EmbeddingRetrievalMetrics.recallAtKByIds(retrieved, gold)).isEqualTo(1.0);
    }

    @Test
    void idsAreMatchedCaseInsensitivelyAndTrimmed() {
        List<String> retrieved = List.of("  doc_1:2 ");
        Set<String> gold = Set.of("DOC_1:2");
        assertThat(EmbeddingRetrievalMetrics.recallAtKByIds(retrieved, gold)).isEqualTo(1.0);
        assertThat(EmbeddingRetrievalMetrics.mrrByIds(retrieved, gold)).isEqualTo(1.0);
    }

    @Test
    void emptyGoldOrRetrieved_yieldsZeros() {
        assertThat(EmbeddingRetrievalMetrics.recallAtKByIds(List.of(), Set.of("A"))).isEqualTo(0.0);
        assertThat(EmbeddingRetrievalMetrics.recallAtKByIds(List.of("A"), Set.of())).isEqualTo(0.0);
        assertThat(EmbeddingRetrievalMetrics.mrrByIds(List.of(), Set.of("A"))).isEqualTo(0.0);
        assertThat(EmbeddingRetrievalMetrics.firstRelevantRankByIds(List.of("A"), Set.of())).isEqualTo(0);
    }
}

