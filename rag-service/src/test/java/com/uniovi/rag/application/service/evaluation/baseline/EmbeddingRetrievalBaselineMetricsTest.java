package com.uniovi.rag.application.service.evaluation.baseline;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingRetrievalBaselineMetricsTest {

    @Test
    void recallAndMrr_whenGoldInSecondPosition() {
        String gold = "needle phrase";
        List<Document> docs =
                List.of(
                        new Document("a", "noise only", Map.of()),
                        new Document("b", "contains NEEDLE PHRASE here", Map.of()));
        assertThat(EmbeddingRetrievalMetrics.recallAt1(docs, gold)).isZero();
        assertThat(EmbeddingRetrievalMetrics.recallAtK(docs, gold)).isEqualTo(1.0);
        assertThat(EmbeddingRetrievalMetrics.mrr(docs, gold)).isEqualTo(0.5);
        assertThat(EmbeddingRetrievalMetrics.firstRelevantRank(docs, gold)).isEqualTo(2);
    }

    @Test
    void mrr_whenGoldMissing_isZero() {
        List<Document> docs = List.of(new Document("a", "no match", Map.of()));
        assertThat(EmbeddingRetrievalMetrics.mrr(docs, "x")).isZero();
        assertThat(EmbeddingRetrievalMetrics.recallAtK(docs, "x")).isZero();
    }
}
