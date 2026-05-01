package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.ReindexEventStatus;
import com.uniovi.rag.infrastructure.persistence.ReindexEventRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ReindexEventEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReindexEventStatusUpdaterTest {

    @Mock
    private ReindexEventRepository reindexEventRepository;

    @InjectMocks
    private ReindexEventStatusUpdater updater;

    @Test
    void update_persistsStatus() {
        UUID id = UUID.randomUUID();
        ReindexEventEntity ev = mock(ReindexEventEntity.class);
        when(reindexEventRepository.findById(id)).thenReturn(Optional.of(ev));
        when(reindexEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        updater.update(id, ReindexEventStatus.COMPLETED);

        verify(ev).setStatus(ReindexEventStatus.COMPLETED);
        ArgumentCaptor<ReindexEventEntity> cap = ArgumentCaptor.forClass(ReindexEventEntity.class);
        verify(reindexEventRepository).save(cap.capture());
        assertThat(cap.getValue()).isSameAs(ev);
    }
}
