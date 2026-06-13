package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabCampaignTerminalPayloadBuilderTest {

    private final LabPresetAxisSupport labPresetAxisSupport =
            new LabPresetAxisSupport(new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser()));

    @Test
    void build_toleratesNullLlmModelIdOnChildRun() {
        UUID campaignId = UUID.randomUUID();
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);
        EvaluationRunEntity run = childRun("P0");
        run.setLlmModelId(null);
        labPresetAxisSupport.enrichRagPresetChildRun(run, campaignId, "P0");
        when(results.findByRun_IdOrderByEvaluatedAtAsc(run.getId())).thenReturn(repeatItems(60));

        Map<String, Object> payload =
                LabCampaignTerminalPayloadBuilder.build(campaignId, List.of(run), results, labPresetAxisSupport);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) payload.get(LabCampaignTerminalPayloadBuilder.KEY_CHILD_RUNS);
        assertThat(children).hasSize(1);
        assertThat(children.getFirst().get("modelId")).isNull();
        assertThat(payload.get(LabCampaignTerminalPayloadBuilder.KEY_PERSISTED_ITEM_COUNT)).isEqualTo(60);
    }

    @Test
    void build_aggregatesPersistedCountsPerPresetChild() {
        UUID campaignId = UUID.randomUUID();
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationRunEntity p0 = childRun("P0");
        EvaluationRunEntity p1 = childRun("P1");
        EvaluationRunEntity p3 = childRun("P3");
        EvaluationRunEntity p4 = childRun("P4");
        labPresetAxisSupport.enrichRagPresetChildRun(p0, campaignId, "P0");
        labPresetAxisSupport.enrichRagPresetChildRun(p1, campaignId, "P1");
        labPresetAxisSupport.enrichRagPresetChildRun(p3, campaignId, "P3");
        labPresetAxisSupport.enrichRagPresetChildRun(p4, campaignId, "P4");

        when(results.findByRun_IdOrderByEvaluatedAtAsc(p0.getId())).thenReturn(repeatItems(60));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(p1.getId())).thenReturn(repeatItems(60));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(p3.getId())).thenReturn(repeatItems(60));
        when(results.findByRun_IdOrderByEvaluatedAtAsc(p4.getId())).thenReturn(repeatItems(60));

        Map<String, Object> payload =
                LabCampaignTerminalPayloadBuilder.build(
                        campaignId, List.of(p0, p1, p3, p4), results, labPresetAxisSupport);

        assertThat(payload.get(LabCampaignTerminalPayloadBuilder.KEY_RESULTS_SOURCE)).isEqualTo("DATABASE");
        assertThat(payload.get(LabCampaignTerminalPayloadBuilder.KEY_PERSISTED_ITEM_COUNT)).isEqualTo(240);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) payload.get(LabCampaignTerminalPayloadBuilder.KEY_CHILD_RUNS);
        assertThat(children).hasSize(4);
        assertThat(children.stream().map(c -> c.get("presetKey")).toList()).containsExactly("P0", "P1", "P3", "P4");
        assertThat(children.stream().map(c -> c.get("itemCount")).toList()).containsOnly(60);
        assertThat(String.valueOf(children.getFirst().get("comparisonLabel"))).startsWith("P0");
        assertThat(String.valueOf(children.getFirst().get("comparisonLabel"))).doesNotContain("gemma3:4b");
        @SuppressWarnings("unchecked")
        Map<String, Object> recovery = (Map<String, Object>) payload.get(LabCampaignTerminalPayloadBuilder.KEY_RECOVERY);
        assertThat(recovery.get("comparisonPath")).isEqualTo("/lab/campaigns/" + campaignId + "/comparison");
    }

    private static EvaluationRunEntity childRun(String presetCode) {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());
        run.setLlmModelId("gemma3:4b");
        run.setBenchmarkKind("RAG_PRESET_END_TO_END");
        run.setStatus(EvaluationRunStatus.DONE);
        run.setAggregatesJson(Map.of(BenchmarkRunOrchestrator.AGG_KEY_REQUESTED_PRESET_CODES, List.of(presetCode)));
        return run;
    }

    private static List<EvaluationResultEntity> repeatItems(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(
                        i -> {
                            EvaluationResultEntity e = new EvaluationResultEntity();
                            e.setId(UUID.randomUUID());
                            e.setEvaluatedAt(Instant.now());
                            e.setBenchmarkKind("RAG_PRESET_END_TO_END");
                            return e;
                        })
                .toList();
    }
}
