package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.interfaces.rest.dto.StartCampaignRequestDto;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.infrastructure.persistence.evaluation.LabCampaignHumanExportBuilder;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabCampaignServiceTest {

    private final LabPresetAxisSupport labPresetAxisSupport =
            new LabPresetAxisSupport(new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser()));

    @Test
    void startCampaign_passesAutoReindexFlagsFromBaseConfig() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        BenchmarkRunOrchestrator orchestrator = mock(BenchmarkRunOrchestrator.class);
        when(orchestrator.startJsonBenchmark(
                        eq(userId),
                        eq("USER"),
                        eq(BenchmarkKind.RAG_PRESET_END_TO_END),
                        any(StartBenchmarkRunRequest.class)))
                .thenReturn(BenchmarkJobAccepted.ofCampaign(runId, taskId, campaignId, 4));

        LabCampaignService svc =
                new LabCampaignService(
                        mock(EvaluationCampaignRepository.class),
                        mock(EvaluationRunRepository.class),
                        mock(EvaluationResultRepository.class),
                        labPresetAxisSupport);

        StartCampaignRequestDto req =
                new StartCampaignRequestDto(
                        "r2r1-min",
                        "RAG_PRESET_BENCHMARK",
                        datasetId,
                        corpusId,
                        projectId,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("P0", "P1"),
                        Map.of("autoReindex", true, "allowActiveSnapshotMutation", true));

        svc.startCampaign(userId, BenchmarkKind.RAG_PRESET_END_TO_END, req, orchestrator);

        org.mockito.ArgumentCaptor<StartBenchmarkRunRequest> captor =
                org.mockito.ArgumentCaptor.forClass(StartBenchmarkRunRequest.class);
        verify(orchestrator)
                .startJsonBenchmark(
                        eq(userId), eq("USER"), eq(BenchmarkKind.RAG_PRESET_END_TO_END), captor.capture());
        assertThat(captor.getValue().autoReindex()).isTrue();
        assertThat(captor.getValue().allowActiveSnapshotMutation()).isTrue();
    }

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

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results, labPresetAxisSupport);
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
        labPresetAxisSupport.enrichRagPresetChildRun(run, campaignId, "P0");
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(run));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(run.getId())).thenReturn(List.of(item("EXECUTED")));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results, labPresetAxisSupport);
        Map<String, Object> comparison = svc.campaignComparison(userId, campaignId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comparisonRows = (List<Map<String, Object>>) comparison.get("rows");
        String expectedLabel = String.valueOf(comparisonRows.getFirst().get("comparisonLabel"));

        Map<String, Object> out = svc.exportCampaignSummaryJson(userId, campaignId);
        assertThat(out.get("exportKind")).isEqualTo(LabCampaignHumanExportBuilder.EXPORT_KIND_SUMMARY);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("presetLabel")).isNotEqualTo("gemma3:4b");
        assertThat(String.valueOf(rows.getFirst().get("comparisonLabel"))).isEqualTo(expectedLabel);
        assertThat(expectedLabel).contains(" — ");
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

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results, labPresetAxisSupport);
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

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results, labPresetAxisSupport);
        Map<String, Object> out = svc.campaignComparison(userId, campaignId);
        assertThat(out.get("campaignType")).isEqualTo("RAG_PRESET");
        assertThat(out.get("comparisonAxis")).isEqualTo(LabCampaignService.COMPARISON_AXIS_PRESET);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("axisValue")).toList()).containsExactlyInAnyOrder("P0", "P1", "P2");
        assertThat(rows.stream().map(r -> String.valueOf(r.get("comparisonLabel"))).toList())
                .allMatch(label -> label.startsWith("P0") || label.startsWith("P1") || label.startsWith("P2"));
        assertThat(rows.stream().map(r -> String.valueOf(r.get("comparisonLabel"))).toList())
                .noneMatch(label -> label.contains("gemma3:4b"));
        assertThat(rows.getFirst()).containsKeys("scoreGlobal", "scoreAnswerable", "finalScoreSampleCount");
    }

    @Test
    void campaignComparison_ragPreset_usesPresetLabelsNotModelId() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = campaign(userId, campaignId, "RAG_PRESET_BENCHMARK");
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity p0 = ragPresetRun("P0");
        p0.setLlmModelId("gemma3:4b");
        labPresetAxisSupport.enrichRagPresetChildRun(p0, campaignId, "P0");
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(p0));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(p0.getId())).thenReturn(List.of(item("EXECUTED")));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results, labPresetAxisSupport);
        Map<String, Object> out = svc.campaignComparison(userId, campaignId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("modelLabel")).isEqualTo("gemma3:4b");
        assertThat(rows.getFirst().get("axisValue")).isEqualTo("P0");
        assertThat(String.valueOf(rows.getFirst().get("comparisonLabel"))).startsWith("P0");
        assertThat(String.valueOf(rows.getFirst().get("comparisonLabel"))).doesNotContain("gemma3:4b");
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

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results, labPresetAxisSupport);
        String csv = svc.exportCampaignSummaryCsv(userId, campaignId);
        assertThat(csv.split("\n")[0]).contains("comparison_axis");
        assertThat(csv).contains("EMBEDDING_MODEL");
    }

    @Test
    void exportCampaignItemsJson_fourPresetMiniCampaign_includesAllPresetRows() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = campaign(userId, campaignId, "RAG_PRESET_BENCHMARK");
        c.setMetaJson(Map.of("comparativeMode", true, "experimentalPresetCodes", List.of("P0", "P1", "P3", "P4")));
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity p0 = ragPresetRun("P0");
        EvaluationRunEntity p1 = ragPresetRun("P1");
        EvaluationRunEntity p3 = ragPresetRun("P3");
        EvaluationRunEntity p4 = ragPresetRun("P4");
        labPresetAxisSupport.enrichRagPresetChildRun(p0, campaignId, "P0");
        labPresetAxisSupport.enrichRagPresetChildRun(p1, campaignId, "P1");
        labPresetAxisSupport.enrichRagPresetChildRun(p3, campaignId, "P3");
        labPresetAxisSupport.enrichRagPresetChildRun(p4, campaignId, "P4");
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(p0, p1, p3, p4));

        when(results.findByRun_IdOrderByEvaluatedAtAsc(p0.getId())).thenReturn(repeatOutcomeItems(60, "P0"));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(p1.getId())).thenReturn(repeatOutcomeItems(60, "P1"));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(p3.getId())).thenReturn(repeatOutcomeItems(60, "P3"));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(p4.getId())).thenReturn(repeatOutcomeItems(60, "P4"));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results, labPresetAxisSupport);
        Map<String, Object> out = svc.exportCampaignItemsJson(userId, campaignId);

        assertThat(out.get("totalPersistedItems")).isEqualTo(240);
        @SuppressWarnings("unchecked")
        List<String> presetCodes = (List<String>) out.get("presetCodes");
        assertThat(presetCodes).containsExactlyInAnyOrder("P0", "P1", "P3", "P4");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");
        assertThat(items).hasSize(240);
        assertThat(items.stream().map(row -> row.get("presetCode")).distinct().toList())
                .containsExactlyInAnyOrder("P0", "P1", "P3", "P4");
        assertThat(items.stream().map(row -> row.get("presetLabel")).distinct().count()).isGreaterThanOrEqualTo(4);
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

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results, labPresetAxisSupport);
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

    private static List<EvaluationResultEntity> repeatOutcomeItems(int count, String presetCode) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(
                        i -> {
                            EvaluationResultEntity row = new EvaluationResultEntity();
                            row.setId(UUID.randomUUID());
                            row.setBenchmarkKind("RAG_PRESET_END_TO_END");
                            row.setEvaluatedAt(Instant.now());
                            row.setMetricsPayload(
                                    Map.of(
                                            "item_outcome",
                                            "EXECUTED",
                                            "preset_code",
                                            presetCode,
                                            "preset_label",
                                            presetCode + " label"));
                            return row;
                        })
                .toList();
    }
}
