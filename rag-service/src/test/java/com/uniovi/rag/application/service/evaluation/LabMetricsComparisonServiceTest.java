package com.uniovi.rag.application.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LabMetricsComparisonServiceTest {

    @Mock EvaluationRunRepository runRepo;
    @Mock EvaluationResultRepository resultRepo;

    @Test
    void comparesTwoLlmRunsByModelId() {
        UUID userId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        EvaluationRunEntity run1 = llmRun(r1, "sha", Instant.parse("2026-01-01T00:00:00Z"));
        EvaluationRunEntity run2 = llmRun(r2, "sha", Instant.parse("2026-01-02T00:00:00Z"));

        when(runRepo.findByIdInAndUser_Id(List.of(r1, r2), userId)).thenReturn(List.of(run1, run2));
        when(resultRepo.findByRun_IdOrderByEvaluatedAtAsc(r1))
                .thenReturn(List.of(llmItem(run1, "mA", true, 120), llmItem(run1, "mA", false, 80)));
        when(resultRepo.findByRun_IdOrderByEvaluatedAtAsc(r2))
                .thenReturn(List.of(llmItem(run2, "mB", true, 60), llmItem(run2, "mB", true, 70)));

        LabMetricsComparisonService svc = new LabMetricsComparisonService(runRepo, resultRepo);
        Map<String, Object> out = svc.compareMetrics(userId, List.of(r1, r2), null, null);
        assertEquals(true, out.get("comparable"));
        assertEquals("LLM_JUDGE_QA", out.get("benchmarkKind"));
        assertEquals("LLM_MODEL", out.get("dimensionType"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("tableRows");
        assertNotNull(rows);
        // One row per (run, model bucket). Each run only uses one modelId.
        assertEquals(2, rows.size());
    }

    @Test
    void comparesTwoRagRunsByPresetCodeEvenWhenPresetIdDiffers() {
        UUID userId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        EvaluationRunEntity run1 = ragRun(r1, "sha", Instant.parse("2026-01-01T00:00:00Z"));
        EvaluationRunEntity run2 = ragRun(r2, "sha", Instant.parse("2026-01-02T00:00:00Z"));

        when(runRepo.findByIdInAndUser_Id(List.of(r1, r2), userId)).thenReturn(List.of(run1, run2));
        when(resultRepo.findByRun_IdOrderByEvaluatedAtAsc(r1))
                .thenReturn(List.of(ragItem(run1, "P7", true, 10), ragItem(run1, "P8", false, 20)));
        when(resultRepo.findByRun_IdOrderByEvaluatedAtAsc(r2))
                .thenReturn(List.of(ragItem(run2, "P7", true, 30)));

        LabMetricsComparisonService svc = new LabMetricsComparisonService(runRepo, resultRepo);
        Map<String, Object> out = svc.compareMetrics(userId, List.of(r1, r2), null, null);
        assertEquals("RAG_PRESET_END_TO_END", out.get("benchmarkKind"));
        assertEquals("PRESET_CODE", out.get("dimensionType"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("tableRows");
        // run1 has P7 + P8, run2 has P7
        assertEquals(3, rows.size());
    }

    @Test
    void rejectsIncompatibleRuns() {
        UUID userId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        EvaluationRunEntity run1 = llmRun(r1, "sha-a", Instant.parse("2026-01-01T00:00:00Z"));
        EvaluationRunEntity run2 = llmRun(r2, "sha-b", Instant.parse("2026-01-02T00:00:00Z"));
        when(runRepo.findByIdInAndUser_Id(List.of(r1, r2), userId)).thenReturn(List.of(run1, run2));

        LabMetricsComparisonService svc = new LabMetricsComparisonService(runRepo, resultRepo);
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> svc.compareMetrics(userId, List.of(r1, r2), null, null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void exportCsvContainsExpectedColumns() {
        UUID userId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        EvaluationRunEntity run1 = llmRun(r1, "sha", Instant.parse("2026-01-01T00:00:00Z"));
        EvaluationRunEntity run2 = llmRun(r2, "sha", Instant.parse("2026-01-02T00:00:00Z"));

        when(runRepo.findByIdInAndUser_Id(List.of(r1, r2), userId)).thenReturn(List.of(run1, run2));
        when(resultRepo.findByRun_IdOrderByEvaluatedAtAsc(r1)).thenReturn(List.of(llmItem(run1, "mA", true, 1)));
        when(resultRepo.findByRun_IdOrderByEvaluatedAtAsc(r2)).thenReturn(List.of(llmItem(run2, "mB", true, 1)));

        LabMetricsComparisonService svc = new LabMetricsComparisonService(runRepo, resultRepo);
        String csv = svc.exportComparisonTableCsv(userId, List.of(r1, r2), null, null);
        String header = csv.split("\n", 2)[0];
        // Spot-check core fields
        assertEquals(true, header.contains("dimensionType"));
        assertEquals(true, header.contains("executedCount"));
        assertEquals(true, header.contains("meanNormalizedExactMatch"));
        assertEquals(true, header.contains("meanLatencyMsWherePresent"));
    }

    private static EvaluationRunEntity llmRun(UUID id, String sha, Instant createdAt) {
        EvaluationRunEntity r = new EvaluationRunEntity();
        r.setId(id);
        r.setBenchmarkKind("LLM_JUDGE_QA");
        r.setDatasetSha256(sha);
        r.setRunKind("SCIENCE");
        r.setWorkflowSchemaVersion("v1");
        r.setCreatedAt(createdAt);
        return r;
    }

    private static EvaluationRunEntity ragRun(UUID id, String sha, Instant createdAt) {
        EvaluationRunEntity r = new EvaluationRunEntity();
        r.setId(id);
        r.setBenchmarkKind("RAG_PRESET_END_TO_END");
        r.setDatasetSha256(sha);
        r.setRunKind("SCIENCE");
        r.setWorkflowSchemaVersion("v1");
        r.setCreatedAt(createdAt);
        // No index fingerprint for this unit test; compatibility checker only enforces it when present.
        return r;
    }

    private static EvaluationResultEntity llmItem(EvaluationRunEntity run, String modelId, boolean exact, long latencyMs) {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setRun(run);
        e.setBenchmarkKind("LLM_JUDGE_QA");
        e.setQueryType("QA");
        e.setExpectedAnswer("A");
        e.setActualAnswer(exact ? "A" : "B");
        e.setLatencyMs(latencyMs);
        e.setEvaluatedAt(Instant.now());
        e.setMetricsPayload(Map.of(BenchmarkResultRowKeys.LLM_MODEL_ID, modelId));
        return e;
    }

    private static EvaluationResultEntity ragItem(EvaluationRunEntity run, String presetCode, boolean exact, long latencyMs) {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setRun(run);
        e.setBenchmarkKind("RAG_PRESET_END_TO_END");
        e.setQueryType("RAG");
        e.setExpectedAnswer("A");
        e.setActualAnswer(exact ? "A" : "B");
        e.setLatencyMs(latencyMs);
        e.setEvaluatedAt(Instant.now());
        e.setMetricsPayload(Map.of(BenchmarkResultRowKeys.PRESET_CODE, presetCode));
        return e;
    }
}

