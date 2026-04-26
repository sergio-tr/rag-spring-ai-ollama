package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition;

import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySpec;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.UpdateDefinitionCommand;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntity;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntryRepository;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntryTraceRepository;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionPersistenceMapper;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionRepository;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceRegressionSuiteDefinitionServiceMockTest {

    @Mock private RuntimeTraceRegressionSuiteDefinitionRepository definitionRepository;
    @Mock private RuntimeTraceRegressionSuiteDefinitionEntryRepository entryRepository;
    @Mock private RuntimeTraceRegressionSuiteDefinitionEntryTraceRepository traceRepository;

    private RuntimeTraceRegressionSuiteDefinitionPersistenceMapper mapper;
    private RuntimeTraceRegressionSuiteDefinitionService service;

    @BeforeEach
    void setUp() {
        mapper = new RuntimeTraceRegressionSuiteDefinitionPersistenceMapper();
        service = new RuntimeTraceRegressionSuiteDefinitionService(
                definitionRepository, entryRepository, traceRepository, mapper);
    }

    @Test
    void create_nullUserId_throws() {
        var cmd =
                new CreateDefinitionCommand(
                        "n", Optional.empty(), List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of())));
        assertThatThrownBy(() -> service.create(null, cmd)).isInstanceOf(IllegalArgumentException.class);
        verify(definitionRepository, never()).save(any());
    }

    @Test
    void update_wrongUser_throwsNotFound() {
        UUID defId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(definitionRepository.findByIdAndUserId(defId, userId)).thenReturn(Optional.empty());
        var cmd =
                new UpdateDefinitionCommand(
                        "n", Optional.empty(), List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of())));
        assertThatThrownBy(() -> service.update(defId, userId, cmd)).isInstanceOf(NotFoundException.class);
        verify(entryRepository, never()).deleteByDefinition_Id(any());
    }

    @Test
    void delete_wrongUser_throwsNotFound() {
        UUID defId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(definitionRepository.findByIdAndUserId(defId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(defId, userId)).isInstanceOf(NotFoundException.class);
        verify(definitionRepository, never()).delete(any());
    }

    @Test
    void materialize_wrongUser_throwsNotFound() {
        UUID defId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(definitionRepository.findByIdAndUserId(defId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.materializeToSuiteRequest(defId, userId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_duplicateName_mapsToIllegalState() {
        UUID userId = UUID.randomUUID();
        when(definitionRepository.saveAndFlush(any(RuntimeTraceRegressionSuiteDefinitionEntity.class)))
                .thenThrow(new DataIntegrityViolationException("unique", null));
        var cmd =
                new CreateDefinitionCommand(
                        "unique-name",
                        Optional.empty(),
                        List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of(UUID.randomUUID()))));
        assertThatThrownBy(() -> service.create(userId, cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        ArgumentCaptor<RuntimeTraceRegressionSuiteDefinitionEntity> cap =
                ArgumentCaptor.forClass(RuntimeTraceRegressionSuiteDefinitionEntity.class);
        verify(definitionRepository).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("unique-name");
    }

}
