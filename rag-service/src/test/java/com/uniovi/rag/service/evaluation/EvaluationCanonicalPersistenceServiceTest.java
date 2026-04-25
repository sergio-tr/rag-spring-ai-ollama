package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EvaluationCanonicalPersistenceService}.
 */
class EvaluationCanonicalPersistenceServiceTest {

    @Test
    void markRunFailed_updatesRun_whenPersistenceEnabled() {
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCanonicalPersistenceService sut = new EvaluationCanonicalPersistenceService(runs, results, true);

        UUID runId = UUID.randomUUID();
        EvaluationRunEntity run = mock(EvaluationRunEntity.class);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(run));

        sut.markRunFailed(runId, "boom");

        verify(run).setStatus(EvaluationRunStatus.ERROR);
        verify(run).setCompletedAt(notNull());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> agg = ArgumentCaptor.forClass(Map.class);
        verify(run).setAggregatesJson(agg.capture());
        verify(runs).save(run);

        assertThat(agg.getValue()).containsEntry("error", "boom");
    }

    @Test
    void markRunFailed_noOps_whenPersistenceDisabled() {
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCanonicalPersistenceService sut = new EvaluationCanonicalPersistenceService(runs, results, false);

        sut.markRunFailed(UUID.randomUUID(), "boom");

        verifyNoInteractions(runs);
        verifyNoInteractions(results);
    }

    @Test
    void persistLlmJudgeFromEvaluationMap_savesResultsAndMarksRunDone() {
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCanonicalPersistenceService sut = new EvaluationCanonicalPersistenceService(runs, results, true);

        UUID runId = UUID.randomUUID();
        EvaluationRunEntity run = mock(EvaluationRunEntity.class);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(run));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("question", "q1");
        row.put("correct_answer", "a1");
        row.put("generated_answer", "a2");
        row.put("query_type", "BOOLEAN_QUERY");
        row.put("llm_evaluation", "correctness: 3");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("results", List.of(row));
        payload.put("evaluation_summary", Map.of("ok", true));

        sut.persistLlmJudgeFromEvaluationMap(runId, payload, BenchmarkKind.LLM_JUDGE_QA);

        verify(results).saveAll(notNull());
        verify(run).setAggregatesJson(notNull());
        verify(run).setStatus(EvaluationRunStatus.DONE);
        verify(run).setProgress(100);
        verify(run).setCompletedAt(notNull());
        verify(runs).save(run);
    }

    @Test
    void persistEmbeddingRetrievalResults_savesRowsAndMarksRunDone() {
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCanonicalPersistenceService sut = new EvaluationCanonicalPersistenceService(runs, results, true);

        UUID runId = UUID.randomUUID();
        EvaluationRunEntity run = mock(EvaluationRunEntity.class);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(run));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("question", "q");
        r.put("expected_answer", "e");
        r.put("top_document_id", "doc-1");
        r.put("latency_ms", 12L);
        r.put("metrics", Map.of("k", "v"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("results", List.of(r));
        payload.put("evaluation_summary", Map.of("n", 1));

        sut.persistEmbeddingRetrievalResults(runId, payload);

        verify(results).saveAll(notNull());
        verify(run).setStatus(EvaluationRunStatus.DONE);
        verify(runs).save(run);
    }

    @Test
    void persistClassifierMetrics_savesAggregateResultAndMarksRunDone() {
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCanonicalPersistenceService sut = new EvaluationCanonicalPersistenceService(runs, results, true);

        UUID runId = UUID.randomUUID();
        EvaluationRunEntity run = mock(EvaluationRunEntity.class);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(run));

        Map<String, Object> resp = Map.of("acc", 0.9);
        sut.persistClassifierMetrics(runId, resp);

        verify(results).save(notNull());
        verify(run).setStatus(EvaluationRunStatus.DONE);
        verify(runs).save(run);
    }
}
