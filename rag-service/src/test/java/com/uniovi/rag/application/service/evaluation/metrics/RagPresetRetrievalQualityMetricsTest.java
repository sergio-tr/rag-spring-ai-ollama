package com.uniovi.rag.application.service.evaluation.metrics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPresetRetrievalQualityMetricsTest {

    @Test
    void withoutGoldLabels_marksQualityUnavailable() {
        Map<String, Object> out =
                RagPresetRetrievalQualityMetrics.compute(
                        Map.of("retrieved_chunk_ids", List.of("snap:doc:0")));
        assertThat(out.get("retrievalQualityStatus")).isEqualTo(RetrievalQualityStatus.NOT_AVAILABLE.name());
        assertThat(out).doesNotContainKey("recallAt1");
    }

    @Test
    void withGoldSymbolOverlap_computesRecallAndMrr() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(DatasetMetricContract.KEY_GOLD_DOCUMENT_IDS, List.of("ACTA_1"));
        mp.put("retrieved_chunk_ids", List.of("snap:ACTA_1:chunk0", "other"));

        Map<String, Object> out = RagPresetRetrievalQualityMetrics.compute(mp);

        assertThat(out.get("retrievalQualityStatus")).isEqualTo(RetrievalQualityStatus.COMPUTED.name());
        assertThat(out.get("recallAt1")).isEqualTo(1.0);
        assertThat(out.get("mrr")).isEqualTo(1.0);
        assertThat(out.get("ndcgAt5")).isEqualTo(1.0);
    }

    @Test
    void withoutRetrievedIds_marksQualityUnavailable() {
        Map<String, Object> mp = Map.of(DatasetMetricContract.KEY_GOLD_CHUNK_IDS, List.of("CHUNK_1"));
        Map<String, Object> out = RagPresetRetrievalQualityMetrics.compute(mp);
        assertThat(out.get("retrievalQualityStatus")).isEqualTo(RetrievalQualityStatus.NOT_AVAILABLE.name());
    }
}
