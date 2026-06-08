package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LabCorpusReadinessAggregatesTest {

    private static final UUID CORPUS_ID = UUID.fromString("33805fe2-ac11-4d30-b8c5-6539d1ac3268");

    @Test
    void toSnapshot_omitsNullBlockers_forRunnableCorpus() {
        EvaluationCorpusReadinessDto readiness =
                new EvaluationCorpusReadinessDto(
                        CORPUS_ID,
                        UUID.randomUUID(),
                        5,
                        5,
                        0,
                        0,
                        null,
                        null,
                        UUID.randomUUID(),
                        false,
                        null,
                        null,
                        List.of(UUID.randomUUID()),
                        true);

        Map<String, Object> snapshot = LabCorpusReadinessAggregates.toSnapshot(CORPUS_ID, readiness);

        assertThat(snapshot).doesNotContainKey("primaryBlocker");
        assertThat(snapshot).doesNotContainKey("snapshotBlocker");
        assertThat(snapshot).doesNotContainKey("snapshotBlockerDetailCode");
        assertThat(snapshot)
                .containsEntry("corpusId", CORPUS_ID.toString())
                .containsEntry("documentCount", 5)
                .containsEntry("readyCount", 5);
        assertThatCode(() -> LabCorpusReadinessAggregates.assertCopyOfSafe(snapshot)).doesNotThrowAnyException();
    }

    @Test
    void copyFromAggregates_normalizesLegacyNullBlockers_withoutNpe() {
        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("corpusId", CORPUS_ID.toString());
        readiness.put("documentCount", 5);
        readiness.put("readyCount", 5);
        readiness.put("primaryBlocker", null);
        readiness.put("snapshotBlocker", null);
        Map<String, Object> aggregates = Map.of(LabCorpusReadinessAggregates.AGG_KEY, readiness);

        Map<String, Object> copied = LabCorpusReadinessAggregates.copyFromAggregates(aggregates);

        assertThat(copied).doesNotContainKey("primaryBlocker");
        assertThat(copied).containsEntry("readyCount", 5);
        assertThatCode(() -> Map.copyOf(copied)).doesNotThrowAnyException();
    }

    @Test
    void roundTrip_writeThenRead_matchesRunnableCorpusFields() {
        EvaluationCorpusReadinessDto readiness =
                new EvaluationCorpusReadinessDto(
                        CORPUS_ID,
                        UUID.randomUUID(),
                        3,
                        3,
                        0,
                        0,
                        null,
                        null,
                        UUID.randomUUID(),
                        false,
                        null,
                        null,
                        List.of(),
                        true);
        Map<String, Object> aggregates =
                Map.of(LabCorpusReadinessAggregates.AGG_KEY, LabCorpusReadinessAggregates.toSnapshot(CORPUS_ID, readiness));

        Map<String, Object> copied = LabCorpusReadinessAggregates.copyFromAggregates(aggregates);

        assertThat(copied).containsEntry("documentCount", 3).containsEntry("readyCount", 3);
        assertThatCode(() -> Map.copyOf(copied)).doesNotThrowAnyException();
    }
}
