package com.uniovi.rag.application.service.evaluation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Evaluation orchestration output → canonical persistence (run aggregates + per-question rows).
 * Complements {@code RagPresetCampaignAxisIntegrationTest} preset-axis grouping.
 */
class EvaluationBenchmarkPersistenceIntegrationTest {

    @Test
    void stageCJudgePayload_persistsRowsAndMergesDemoBestPresetAggregates() {
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);
        EvaluationCanonicalPersistenceService persistence =
                new EvaluationCanonicalPersistenceService(runs, results, true);

        UUID runId = UUID.randomUUID();
        EvaluationRunEntity run = mock(EvaluationRunEntity.class);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(run.getAggregatesJson())
                .thenReturn(
                        new LinkedHashMap<>(
                                Map.of(
                                        "presetKey",
                                        "Demo_Best",
                                        "presetLabel",
                                        "Demo Best",
                                        "comparisonAxis",
                                        "PRESET")));

        Map<String, Object> ce02Row = new LinkedHashMap<>();
        ce02Row.put("question", "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?");
        ce02Row.put("correct_answer", "No existen reuniones con exactamente 21 asistentes.");
        ce02Row.put("generated_answer", "No existen registros de reuniones con exactamente 21 asistentes.");
        ce02Row.put("query_type", "COUNT_AND_EXPLAIN");
        ce02Row.put("llm_evaluation", "correctness: 3");
        ce02Row.put("dataset_question_id", "FD-CE-02");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("results", List.of(ce02Row));
        payload.put(
                "evaluation_summary",
                Map.of("executed", 29, "passed", 26, "failed", 3, "cancelled", false));

        persistence.persistLlmJudgeFromEvaluationMap(runId, payload, BenchmarkKind.RAG_PRESET_END_TO_END);

        verify(results).saveAll(notNull());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> aggregatesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(run).setAggregatesJson(aggregatesCaptor.capture());
        assertThat(aggregatesCaptor.getValue())
                .containsEntry("presetKey", "Demo_Best")
                .containsEntry("passed", 26)
                .containsEntry("executed", 29);
        verify(run).setStatus(EvaluationRunStatus.DONE);
        verify(run).setCompletedAt(notNull());
        verify(runs).save(run);
    }
}
