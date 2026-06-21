package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Campaign-axis integration: comparison rows grouped by preset, model id kept as metadata only.
 */
@ExtendWith(MockitoExtension.class)
class RagPresetCampaignAxisIntegrationTest {

    @Mock private EvaluationCampaignRepository evaluationCampaignRepository;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private EvaluationResultRepository evaluationResultRepository;

    private LabPresetAxisSupport labPresetAxisSupport;
    private LabCampaignService labCampaignService;

    @BeforeEach
    void setUp() {
        labPresetAxisSupport = new LabPresetAxisSupport(new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser()));
        labCampaignService =
                new LabCampaignService(
                        evaluationCampaignRepository, evaluationRunRepository, evaluationResultRepository, labPresetAxisSupport);
    }

    @Test
    void ragPresetCampaign_comparisonGroupedByPreset_p0AndRetrievalPreset() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignEntity camp = new EvaluationCampaignEntity();
        camp.setId(campaignId);
        UserEntity user = mock(UserEntity.class);
        camp.setUser(user);
        camp.setStudyType("RAG_PRESET_BENCHMARK");
        camp.setMetaJson(Map.of("comparativeMode", true, "experimentalPresetCodes", List.of("P0", "P2")));

        when(evaluationCampaignRepository.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(camp));

        EvaluationRunEntity p0 = childRun(campaignId, "P0", "gemma3:4b");
        EvaluationRunEntity p2 = childRun(campaignId, "P2", "gemma3:4b");
        when(evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(p0, p2));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(p0.getId()))
                .thenReturn(List.of(item("EXECUTED"), item("EXECUTED")));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(p2.getId()))
                .thenReturn(List.of(item("SKIPPED"), item("SKIPPED")));

        Map<String, Object> cmp = labCampaignService.campaignComparison(userId, campaignId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) cmp.get("rows");

        assertThat(cmp.get("comparisonAxis")).isEqualTo(LabCampaignService.COMPARISON_AXIS_PRESET);
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().map(r -> r.get("axisValue")).toList()).containsExactlyInAnyOrder("P0", "P2");
        assertThat(rows.stream().map(r -> String.valueOf(r.get("comparisonLabel"))).toList())
                .allMatch(label -> label.startsWith("P0") || label.startsWith("P2"));
        assertThat(rows.stream().map(r -> String.valueOf(r.get("comparisonLabel"))).toList())
                .noneMatch(label -> label.contains("gemma3:4b"));
        assertThat(rows.stream().filter(r -> "P0".equals(r.get("axisValue"))).findFirst().orElseThrow().get("executed"))
                .isEqualTo(2L);
        assertThat(rows.stream().filter(r -> "P2".equals(r.get("axisValue"))).findFirst().orElseThrow().get("skipped"))
                .isEqualTo(2L);
        assertThat(p0.getAggregatesJson().get(LabPresetAxisSupport.AGG_KEY_COMPARISON_AXIS))
                .isEqualTo(LabPresetAxisSupport.COMPARISON_AXIS_PRESET);
    }

    private EvaluationRunEntity childRun(UUID campaignId, String presetCode, String llmModelId) {
        EvaluationRunEntity r = new EvaluationRunEntity();
        r.setId(UUID.randomUUID());
        r.setBenchmarkKind("RAG_PRESET_END_TO_END");
        r.setLlmModelId(llmModelId);
        r.setStatus(EvaluationRunStatus.DONE);
        r.setCampaign(new EvaluationCampaignEntity());
        labPresetAxisSupport.enrichRagPresetChildRun(r, campaignId, presetCode);
        return r;
    }

    private static EvaluationResultEntity item(String outcome) {
        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setBenchmarkKind("RAG_PRESET_END_TO_END");
        item.setEvaluatedAt(Instant.now());
        item.setMetricsPayload(Map.of("item_outcome", outcome));
        return item;
    }
}
