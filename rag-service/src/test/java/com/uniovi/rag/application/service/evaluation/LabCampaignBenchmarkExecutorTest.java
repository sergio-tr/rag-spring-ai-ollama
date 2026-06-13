package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabCampaignBenchmarkExecutorTest {

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private EvaluationCampaignRepository evaluationCampaignRepository;

    @Mock
    private EvaluationResultRepository evaluationResultRepository;

    @Mock
    private LabJobEventService labJobEventService;

    @Mock
    private LabBenchmarkCompletionService labBenchmarkCompletionService;

    @Mock
    private LabPresetAxisSupport labPresetAxisSupport;

    @InjectMocks
    private LabCampaignBenchmarkExecutor executor;

    @Test
    void runCampaign_completesWithDatabaseBackedTerminalPayload() {
        UUID campaignId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID p0Id = UUID.randomUUID();
        UUID p1Id = UUID.randomUUID();

        UserEntity user = mock(UserEntity.class);
        AsyncTaskEntity task =
                Mockito.spy(
                        AsyncTaskEntity.queued(
                                user, com.uniovi.rag.domain.AsyncTaskType.EVAL_RAG, Map.of(), Instant.now()));
        Mockito.lenient().when(task.getId()).thenReturn(taskId);

        EvaluationRunEntity p0 = childRun(p0Id, "P0");
        EvaluationRunEntity p1 = childRun(p1Id, "P1");

        EvaluationCampaignEntity campaign = new EvaluationCampaignEntity();
        campaign.setId(campaignId);
        campaign.setMetaJson(Map.of("totalItems", 120, "axisCount", 2));

        when(evaluationRunRepository.findByCampaign_IdOrderByCreatedAtAsc(campaignId)).thenReturn(List.of(p0, p1));
        when(evaluationCampaignRepository.findById(campaignId)).thenReturn(java.util.Optional.of(campaign));
        when(labPresetAxisSupport.resolvePresetCode(p0)).thenReturn("P0");
        when(labPresetAxisSupport.resolvePresetCode(p1)).thenReturn("P1");
        when(labPresetAxisSupport.resolvePresetLabel(any())).thenReturn("label");
        when(labPresetAxisSupport.comparisonLabel(any())).thenReturn("P0 — label");
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(p0Id)).thenReturn(repeatItems(60));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(p1Id)).thenReturn(repeatItems(60));

        AsyncTaskMutationService mutation = mock(AsyncTaskMutationService.class);
        executor.runCampaign(
                task,
                mutation,
                campaignId,
                (t, m, runId) -> {
                    Map<String, Object> slice = new LinkedHashMap<>();
                    slice.put("results", List.of(Map.of("runId", runId.toString())));
                    return slice;
                });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(labBenchmarkCompletionService)
                .completeCampaign(eq(mutation), eq(taskId), eq(List.of(p0Id, p1Id)), payloadCaptor.capture());

        Map<String, Object> terminal = payloadCaptor.getValue();
        assertThat(terminal.get(LabCampaignTerminalPayloadBuilder.KEY_RESULTS_SOURCE)).isEqualTo("DATABASE");
        assertThat(terminal.get(LabCampaignTerminalPayloadBuilder.KEY_PERSISTED_ITEM_COUNT)).isEqualTo(120);
        assertThat(terminal).doesNotContainKey("results");
    }

    private static EvaluationRunEntity childRun(UUID id, String presetCode) {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(id);
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
                            return e;
                        })
                .toList();
    }
}
