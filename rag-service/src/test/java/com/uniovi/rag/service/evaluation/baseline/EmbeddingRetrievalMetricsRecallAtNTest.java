package com.uniovi.rag.service.evaluation.baseline;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingRetrievalMetricsRecallAtNTest {

    @Test
    void recallAt5_firstHitAtFourth_returnsZeroWhenNIs3() {
        List<Document> docs =
                List.of(
                        new Document("d0", "no", Map.of()),
                        new Document("d1", "no2", Map.of()),
                        new Document("d2", "no3", Map.of()),
                        new Document("d3", "needle gold phrase here", Map.of()));
        assertThat(EmbeddingRetrievalMetrics.recallAtN(docs, "gold phrase", 3)).isEqualTo(0.0);
        assertThat(EmbeddingRetrievalMetrics.recallAtN(docs, "gold phrase", 5)).isEqualTo(1.0);
    }
}
