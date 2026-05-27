package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.LabJobEventDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class LabJobProgressTrackerTest {

    @Mock
    private LabJobEventService labJobEventService;

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private EvaluationCampaignRepository evaluationCampaignRepository;

    @Mock
    private ObjectProvider<LabJobProgressTracker> selfProvider;

    private LabJobProgressTracker tracker;

    @BeforeEach
    void setUp() {
        tracker =
                new LabJobProgressTracker(
                        labJobEventService, evaluationRunRepository, evaluationCampaignRepository, selfProvider);
        lenient().when(selfProvider.getObject()).thenReturn(tracker);
        when(labJobEventService.record(any())).thenAnswer(
                inv -> {
                    LabJobEventRequest req = inv.getArgument(0);
                    return new LabJobEventDto(
                            1L,
                            req.taskId(),
                            req.type().name(),
                            "RUNNING",
                            null,
                            req.message(),
                            Instant.now(),
                            req.payload(),
                            req.campaignId(),
                            req.runId(),
                            req.itemId(),
                            req.globalCompletedItems(),
                            req.globalTotalItems(),
                            req.runCompletedItems(),
                            req.runTotalItems(),
                            req.currentModelId(),
                            req.currentPresetCode());
                });
    }

    @Test
    void itemProgress_emitsStartedAndCompletedPerItem() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        var callback = tracker.itemProgressCallback(taskId, runId, 3, null, "llama3", null, null);
        callback.accept(1, 3);
        callback.accept(2, 3);

        ArgumentCaptor<LabJobEventRequest> captor = ArgumentCaptor.forClass(LabJobEventRequest.class);
        verify(labJobEventService, atLeastOnce()).record(captor.capture());
        List<LabJobEventType> types =
                captor.getAllValues().stream().map(LabJobEventRequest::type).toList();
        assertThat(types).contains(LabJobEventType.ITEM_STARTED, LabJobEventType.ITEM_COMPLETED);
    }

    @Test
    void campaignRun_setsGlobalTotal108_for36itemsTimes3Models() {
        UUID campaignId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        UserEntity user = Mockito.mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);

        EvaluationCampaignEntity camp = new EvaluationCampaignEntity();
        camp.setId(campaignId);
        camp.setMetaJson(new LinkedHashMap<>());

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        run.setUser(user);
        run.setCampaign(camp);

        List<EvaluationRunEntity> runs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            runs.add(run);
        }

        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(evaluationRunRepository.findCampaignIdByRunId(runId)).thenReturn(Optional.of(campaignId));
        when(evaluationRunRepository.findByCampaign_IdOrderByCreatedAtAsc(campaignId)).thenReturn(runs);
        when(evaluationCampaignRepository.findById(campaignId)).thenReturn(Optional.of(camp));

        tracker.emitRunStarted(taskId, runId, 36, null, "model-a", "P0");

        ArgumentCaptor<LabJobEventRequest> captor = ArgumentCaptor.forClass(LabJobEventRequest.class);
        verify(labJobEventService, atLeastOnce()).record(captor.capture());
        LabJobEventRequest runStarted =
                captor.getAllValues().stream()
                        .filter(r -> r.type() == LabJobEventType.RUN_STARTED)
                        .findFirst()
                        .orElseThrow();
        assertThat(runStarted.globalTotalItems()).isEqualTo(108);
        assertThat(runStarted.runTotalItems()).isEqualTo(36);
    }
}
