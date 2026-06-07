package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LabJobPhaseEmitterTest {

    @Mock
    private AsyncTaskRepository asyncTaskRepository;

    @Mock
    private LabJobEventService labJobEventService;

    private LabJobPhaseEmitter emitter;
    private UUID taskId;
    private AsyncTaskEntity task;

    @BeforeEach
    void setUp() {
        emitter = new LabJobPhaseEmitter(asyncTaskRepository, labJobEventService);
        taskId = UUID.randomUUID();
        task = Mockito.mock(AsyncTaskEntity.class);
        Map<String, Object> eventLog = new LinkedHashMap<>();
        when(task.getId()).thenReturn(taskId);
        when(task.getEventLogJson()).thenAnswer(inv -> eventLog);
        Mockito.doAnswer(
                        (Answer<Void>)
                                inv -> {
                                    eventLog.clear();
                                    eventLog.putAll((Map<String, Object>) inv.getArgument(0));
                                    return null;
                                })
                .when(task)
                .setEventLogJson(Mockito.any());
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(task));
    }

    @Test
    void emitDatasetResolved_onlyOncePerPhaseKey() {
        UUID runId = UUID.randomUUID();
        emitter.emitDatasetResolved(taskId, runId, UUID.randomUUID(), "RAG_PRESET_END_TO_END", 5, 2);
        emitter.emitDatasetResolved(taskId, runId, UUID.randomUUID(), "RAG_PRESET_END_TO_END", 5, 2);

        ArgumentCaptor<LabJobEventRequest> captor = ArgumentCaptor.forClass(LabJobEventRequest.class);
        verify(labJobEventService, times(1)).record(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(LabJobEventType.DATASET_RESOLVED);
        verify(asyncTaskRepository, times(1)).save(task);
    }
}
