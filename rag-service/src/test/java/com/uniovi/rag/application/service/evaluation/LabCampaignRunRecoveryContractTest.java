package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.CampaignChildRunSummaryDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationRunDetailDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Recovery contract: coordinator run id resolves campaign-wide persisted rows and metadata.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LabCampaignRunRecoveryContractTest {

    private static final List<String> PRESETS = List.of("P0", "P1", "P3", "P4");
    private static final int ITEMS_PER_PRESET = 60;

    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private EvaluationResultRepository evaluationResultRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private RagApiPathProperties apiPathProperties;

    private LabPresetAxisSupport labPresetAxisSupport;
    private LabEvaluationRunService service;

    @BeforeEach
    void setUp() {
        labPresetAxisSupport = new LabPresetAxisSupport(new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser()));
        service =
                new LabEvaluationRunService(
                        evaluationRunRepository,
                        evaluationResultRepository,
                        labPresetAxisSupport,
                        objectMapper,
                        apiPathProperties);
    }

    @Test
    void coordinatorRunId_listItems_returnsAllFourPresetBatches() {
        CampaignFixture fixture = stubFourPresetCampaign();

        assertThat(service.listItems(fixture.userId(), fixture.coordinatorRunId())).hasSize(240);
    }

    @Test
    void coordinatorRunId_getRun_exposesCampaignRecoveryMetadata() {
        CampaignFixture fixture = stubFourPresetCampaign();

        EvaluationRunDetailDto detail = service.getRun(fixture.userId(), fixture.coordinatorRunId());

        assertThat(detail.campaignId()).isEqualTo(fixture.campaignId());
        assertThat(detail.campaignMode()).isTrue();
        assertThat(detail.presetKey()).isEqualTo("P0");
        assertThat(detail.comparisonAxis()).isEqualTo(LabPresetAxisSupport.COMPARISON_AXIS_PRESET);
        assertThat(detail.persistedItemCount()).isEqualTo(ITEMS_PER_PRESET);
        assertThat(detail.campaignPersistedItemCount()).isEqualTo(240);
        assertThat(detail.campaignChildRuns()).hasSize(4);
        assertThat(detail.campaignChildRuns().stream().map(CampaignChildRunSummaryDto::presetKey).toList())
                .containsExactlyInAnyOrder("P0", "P1", "P3", "P4");
        assertThat(detail.campaignChildRuns().stream().mapToInt(CampaignChildRunSummaryDto::persistedItemCount).sum())
                .isEqualTo(240);
    }

    @Test
    void coordinatorRunId_exportMvpItemsBundle_includesAllPresetRows() {
        CampaignFixture fixture = stubFourPresetCampaign();

        Map<String, Object> bundle = service.exportMvpItemsJsonBundle(fixture.userId(), fixture.coordinatorRunId());

        assertThat(bundle.get("campaignId")).isEqualTo(fixture.campaignId());
        assertThat(bundle.get("campaignMode")).isEqualTo(true);
        assertThat(bundle.get("campaignPersistedItemCount")).isEqualTo(240);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) bundle.get("items");
        assertThat(items).hasSize(240);
    }

    private CampaignFixture stubFourPresetCampaign() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);

        EvaluationCampaignEntity campaign = new EvaluationCampaignEntity();
        campaign.setId(campaignId);
        campaign.setUser(user);
        campaign.setStudyType("RAG_PRESET_BENCHMARK");
        campaign.setMetaJson(Map.of("experimentalPresetCodes", PRESETS));

        List<EvaluationRunEntity> children = new ArrayList<>();
        List<EvaluationResultEntity> allItems = new ArrayList<>();
        UUID coordinatorRunId = null;

        for (String preset : PRESETS) {
            EvaluationRunEntity run = new EvaluationRunEntity();
            UUID runId = UUID.randomUUID();
            run.setId(runId);
            run.setUser(user);
            run.setCampaign(campaign);
            run.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
            run.setLlmModelId("gemma3:4b");
            run.setStatus(EvaluationRunStatus.DONE);
            run.setCreatedAt(Instant.now());
            labPresetAxisSupport.enrichRagPresetChildRun(run, campaignId, preset);
            children.add(run);
            if ("P0".equals(preset)) {
                coordinatorRunId = runId;
            }

            List<EvaluationResultEntity> presetItems = presetItems(preset, ITEMS_PER_PRESET);
            allItems.addAll(presetItems);
            when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId)).thenReturn(presetItems);
        }

        List<UUID> childIds = children.stream().map(EvaluationRunEntity::getId).toList();
        when(evaluationRunRepository.findByIdAndUser_Id(coordinatorRunId, userId))
                .thenReturn(Optional.of(children.getFirst()));
        when(evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(children);
        when(evaluationResultRepository.findByRun_IdInOrderByEvaluatedAtAsc(any()))
                .thenReturn(allItems);

        return new CampaignFixture(userId, campaignId, coordinatorRunId);
    }

    private static List<EvaluationResultEntity> presetItems(String presetCode, int count) {
        List<EvaluationResultEntity> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EvaluationResultEntity row = new EvaluationResultEntity();
            row.setId(UUID.randomUUID());
            row.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
            row.setEvaluatedAt(Instant.now());
            row.setMetricsPayload(
                    Map.of(
                            BenchmarkResultRowKeys.PRESET_CODE,
                            presetCode,
                            BenchmarkResultRowKeys.PRESET_LABEL,
                            presetCode + " label",
                            BenchmarkResultRowKeys.ITEM_OUTCOME,
                            "EXECUTED"));
            out.add(row);
        }
        return out;
    }

    private record CampaignFixture(UUID userId, UUID campaignId, UUID coordinatorRunId) {}
}
