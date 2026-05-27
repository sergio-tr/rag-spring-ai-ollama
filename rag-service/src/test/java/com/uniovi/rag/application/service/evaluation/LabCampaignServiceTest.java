package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.evaluation.LabCampaignHumanExportBuilder;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabCampaignServiceTest {

    @Test
    void exportCampaignMvpItemsJson_containsCampaignAndRunIds() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = campaign(userId, campaignId, "LLM_MODEL_BASELINE");
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity r = run("m1", "LLM_JUDGE_QA", null);
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(r));

        EvaluationResultEntity item = item("EXECUTED");
        when(results.findByRun_IdOrderByEvaluatedAtAsc(r.getId())).thenReturn(List.of(item));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results);
        Map<String, Object> out = svc.exportCampaignMvpItemsJson(userId, campaignId);
        assertThat(out.get("campaignId")).isEqualTo(campaignId);
        assertThat(out.get("exportKind")).isEqualTo(LabCampaignHumanExportBuilder.EXPORT_KIND_ITEMS);
        assertThat(out.get("comparisonAxis")).isEqualTo(LabCampaignService.COMPARISON_AXIS_LLM);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("items");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("campaignId")).isEqualTo(campaignId);
        assertThat(rows.getFirst().get("runId")).isEqualTo(r.getId());
        assertThat(rows.getFirst()).containsKey("presetCode");
        assertThat(rows.getFirst()).containsKey("sources");
        assertThat(rows.getFirst()).containsKey("mvp");
    }

    @Test
    void exportCampaignSummaryJson_containsComparisonRows() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = campaign(userId, campaignId, "RAG_PRESET_BENCHMARK");
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity run = ragPresetRun("P0");
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(run));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(run.getId())).thenReturn(List.of(item("EXECUTED")));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results);
        Map<String, Object> out = svc.exportCampaignSummaryJson(userId, campaignId);
        assertThat(out.get("exportKind")).isEqualTo(LabCampaignHumanExportBuilder.EXPORT_KIND_SUMMARY);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("presetLabel")).isEqualTo("P0");
    }

    @Test
    void campaignComparison_llmCampaign_rollsUpThreeModels() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = campaign(userId, campaignId, "LLM_MODEL_BASELINE");
        c.setMetaJson(Map.of("comparativeMode", true, "llmModelIds", List.of("m1", "m2", "m3")));
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity r1 = run("m1", "LLM_JUDGE_QA", null);
        EvaluationRunEntity r2 = run("m2", "LLM_JUDGE_QA", null);
        EvaluationRunEntity r3 = run("m3", "LLM_JUDGE_QA", null);
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(r1, r2, r3));

        when(results.findByRun_IdOrderByEvaluatedAtAsc(r1.getId())).thenReturn(List.of(item("EXECUTED")));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(r2.getId())).thenReturn(List.of(item("NOT_SUPPORTED")));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(r3.getId())).thenReturn(List.of(item("EXECUTED"), item("FAILED")));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results);
        Map<String, Object> out = svc.campaignComparison(userId, campaignId);
        assertThat(out.get("campaignType")).isEqualTo("LLM");
        assertThat(out.get("comparisonAxis")).isEqualTo(LabCampaignService.COMPARISON_AXIS_LLM);
        assertThat(out.get("comparativeMode")).isEqualTo(true);
        assertThat(out.get("axisCount")).isEqualTo(3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("axisValue")).toList()).containsExactlyInAnyOrder("m1", "m2", "m3");
    }

    @Test
    void campaignComparison_ragPresetCampaign_oneRowPerPresetRun() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = campaign(userId, campaignId, "RAG_PRESET_BENCHMARK");
        c.setMetaJson(Map.of("comparativeMode", true, "experimentalPresetCodes", List.of("P0", "P1", "P2")));
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity p0 = ragPresetRun("P0");
        EvaluationRunEntity p1 = ragPresetRun("P1");
        EvaluationRunEntity p2 = ragPresetRun("P2");
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(p0, p1, p2));

        when(results.findByRun_IdOrderByEvaluatedAtAsc(p0.getId())).thenReturn(List.of(item("EXECUTED")));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(p1.getId())).thenReturn(List.of(item("EXECUTED")));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(p2.getId())).thenReturn(List.of(item("NOT_SUPPORTED")));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results);
        Map<String, Object> out = svc.campaignComparison(userId, campaignId);
        assertThat(out.get("campaignType")).isEqualTo("RAG_PRESET");
        assertThat(out.get("comparisonAxis")).isEqualTo(LabCampaignService.COMPARISON_AXIS_PRESET);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("presetLabel")).toList()).containsExactlyInAnyOrder("P0", "P1", "P2");
    }

    @Test
    void exportCampaignSummaryCsv_includesComparisonAxisColumn() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = campaign(userId, campaignId, "EMBEDDING_MODEL_BASELINE");
        c.setMetaJson(Map.of("comparativeMode", true));
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity r1 = run(null, "EMBEDDING_RETRIEVAL", "emb-a");
        EvaluationRunEntity r2 = run(null, "EMBEDDING_RETRIEVAL", "emb-b");
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(r1, r2));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(r1.getId())).thenReturn(List.of(item("EXECUTED")));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(r2.getId())).thenReturn(List.of(item("EXECUTED")));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results);
        String csv = svc.exportCampaignSummaryCsv(userId, campaignId);
        assertThat(csv.split("\n")[0]).contains("comparison_axis");
        assertThat(csv).contains("EMBEDDING_MODEL");
    }

    @Test
    void campaignComparison_singleModel_notComparative() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = campaign(userId, campaignId, "LLM_MODEL_BASELINE");
        c.setMetaJson(Map.of("comparativeMode", false, "llmModelIds", List.of("m1")));
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity r = run("m1", "LLM_JUDGE_QA", null);
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(r));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(r.getId())).thenReturn(List.of(item("EXECUTED")));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results);
        Map<String, Object> out = svc.campaignComparison(userId, campaignId);
        assertThat(out.get("comparativeMode")).isEqualTo(false);
        assertThat(out.get("axisCount")).isEqualTo(1);
    }

    private static EvaluationCampaignEntity campaign(UUID userId, UUID campaignId, String studyType) {
        EvaluationCampaignEntity c = new EvaluationCampaignEntity();
        c.setId(campaignId);
        UserEntity u = mock(UserEntity.class);
        when(u.getId()).thenReturn(userId);
        c.setUser(u);
        c.setStudyType(studyType);
        c.setCreatedAt(Instant.now());
        return c;
    }

    private static EvaluationRunEntity run(String llmModelId, String benchmarkKind, String embeddingModelId) {
        EvaluationRunEntity r = new EvaluationRunEntity();
        UUID runId = UUID.randomUUID();
        r.setId(runId);
        r.setLlmModelId(llmModelId);
        r.setEmbeddingModelId(embeddingModelId);
        r.setBenchmarkKind(benchmarkKind);
        r.setStatus(EvaluationRunStatus.DONE);
        return r;
    }

    private static EvaluationRunEntity ragPresetRun(String presetCode) {
        EvaluationRunEntity r = run(null, "RAG_PRESET_END_TO_END", null);
        r.setAggregatesJson(Map.of(BenchmarkRunOrchestrator.AGG_KEY_REQUESTED_PRESET_CODES, List.of(presetCode)));
        return r;
    }

    private static EvaluationResultEntity item(String outcome) {
        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setBenchmarkKind("LLM_JUDGE_QA");
        item.setEvaluatedAt(Instant.now());
        item.setMetricsPayload(Map.of("item_outcome", outcome));
        return item;
    }
}
