package com.uniovi.rag.service.classifier;

import com.uniovi.rag.domain.ClassifierModelStatus;
import com.uniovi.rag.infrastructure.persistence.ClassifierModelRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ClassifierModelEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ClassifierModelResponseDto;
import com.uniovi.rag.service.config.UserProjectConfigurationService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassifierModelRegistryServiceTest {

    @Mock
    private ClassifierModelRepository classifierModelRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private UserProjectConfigurationService userProjectConfigurationService;

    private ClassifierModelRegistryService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClassifierModelRegistryService(
                classifierModelRepository, userRepository, projectAccessService, userProjectConfigurationService);
    }

    @Test
    void registerAfterSuccessfulTrain_persistsWhenModelIdPresent() {
        when(classifierModelRepository.findByOwnerIdAndSourceTaskId(userId, taskId.toString()))
                .thenReturn(Optional.empty());
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));

        Map<String, Object> res = Map.of("modelId", "my-tag-1", "name", "m1", "metrics", Map.of("loss", 0.1));

        service.registerAfterSuccessfulTrain(userId, taskId, "lab", res, 50, 8);

        ArgumentCaptor<ClassifierModelEntity> cap = ArgumentCaptor.forClass(ClassifierModelEntity.class);
        verify(classifierModelRepository).save(cap.capture());
        assertThat(cap.getValue().getArtifactPath()).isEqualTo("my-tag-1");
        assertThat(cap.getValue().getStatus()).isEqualTo(ClassifierModelStatus.READY);
        assertThat(cap.getValue().getHyperparams()).containsEntry(ClassifierModelRegistryService.HP_SOURCE_TASK_ID, taskId.toString());
    }

    @Test
    void registerAfterSuccessfulTrain_skipsWhenAlreadyRegisteredForTask() {
        ClassifierModelEntity existing = new ClassifierModelEntity();
        when(classifierModelRepository.findByOwnerIdAndSourceTaskId(userId, taskId.toString()))
                .thenReturn(Optional.of(existing));

        service.registerAfterSuccessfulTrain(
                userId, taskId, "lab", Map.of("modelId", "x"), 50, 8);

        verify(classifierModelRepository, never()).save(any());
    }

    @Test
    void listForUser_mapsRows() {
        ClassifierModelEntity e = new ClassifierModelEntity();
        e.setId(UUID.randomUUID());
        e.setName("n");
        e.setArtifactPath("tag");
        e.setStatus(ClassifierModelStatus.READY);
        e.setHyperparams(Map.of("k", "v"));
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of(e));

        List<ClassifierModelResponseDto> list = service.listForUser(userId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).inferenceTag()).isEqualTo("tag");
    }

    @Test
    void registerAfterSuccessfulTrain_acceptsModelIdSnakeCase() {
        when(classifierModelRepository.findByOwnerIdAndSourceTaskId(userId, taskId.toString()))
                .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(mock(UserEntity.class)));

        service.registerAfterSuccessfulTrain(
                userId, taskId, "lab", Map.of("model_id", "snake-tag", "name", "n"), 3, 4);

        ArgumentCaptor<ClassifierModelEntity> cap = ArgumentCaptor.forClass(ClassifierModelEntity.class);
        verify(classifierModelRepository).save(cap.capture());
        assertThat(cap.getValue().getArtifactPath()).isEqualTo("snake-tag");
    }

    @Test
    void registerAfterSuccessfulTrain_skipsWhenNoInferenceTag() {
        when(classifierModelRepository.findByOwnerIdAndSourceTaskId(userId, taskId.toString()))
                .thenReturn(Optional.empty());

        service.registerAfterSuccessfulTrain(userId, taskId, "lab", Map.of("foo", "bar"), 1, 2);

        verify(classifierModelRepository, never()).save(any());
    }

    @Test
    void registerAfterSuccessfulTrain_skipsWhenUserMissing() {
        when(classifierModelRepository.findByOwnerIdAndSourceTaskId(userId, taskId.toString()))
                .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.registerAfterSuccessfulTrain(userId, taskId, "lab", Map.of("modelId", "x"), 1, 2);

        verify(classifierModelRepository, never()).save(any());
    }

    @Test
    void activateForProject_mergesClassifierModelIdAndSetsActive() {
        UUID projectId = UUID.randomUUID();
        UUID modelRowId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(userId);

        ClassifierModelEntity model = new ClassifierModelEntity();
        model.setId(modelRowId);
        model.setOwner(owner);
        model.setStatus(ClassifierModelStatus.READY);
        model.setArtifactPath("infer-tag");
        model.setActive(false);

        when(classifierModelRepository.findById(modelRowId)).thenReturn(Optional.of(model));
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of(model));

        ClassifierModelResponseDto dto = service.activateForProject(userId, projectId, modelRowId);

        verify(projectAccessService).requireOwnedProject(userId, projectId);
        verify(userProjectConfigurationService)
                .mergeProjectConfig(userId, projectId, Map.of("classifierModelId", "infer-tag"));
        assertThat(model.isActive()).isTrue();
        assertThat(dto.inferenceTag()).isEqualTo("infer-tag");
        verify(classifierModelRepository, atLeastOnce()).save(any());
    }

    @Test
    void activateForProject_deactivatesOtherActiveRows() {
        UUID projectId = UUID.randomUUID();
        UUID modelRowId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(userId);

        ClassifierModelEntity other = new ClassifierModelEntity();
        other.setId(otherId);
        other.setOwner(owner);
        other.setActive(true);
        other.setStatus(ClassifierModelStatus.READY);
        other.setArtifactPath("old");

        ClassifierModelEntity model = new ClassifierModelEntity();
        model.setId(modelRowId);
        model.setOwner(owner);
        model.setStatus(ClassifierModelStatus.READY);
        model.setArtifactPath("new-tag");
        model.setActive(false);

        when(classifierModelRepository.findById(modelRowId)).thenReturn(Optional.of(model));
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of(other, model));

        service.activateForProject(userId, projectId, modelRowId);

        assertThat(other.isActive()).isFalse();
        assertThat(model.isActive()).isTrue();
    }

    @Test
    void enrichAfterEval_updatesMetricsWhenRowFound() {
        ClassifierModelEntity e = new ClassifierModelEntity();
        e.setId(UUID.randomUUID());
        when(classifierModelRepository.findByOwner_IdAndArtifactPath(userId, "eval-tag"))
                .thenReturn(Optional.of(e));

        Map<String, Object> evalResult =
                Map.of(
                        "metrics",
                        Map.of(
                                "classificationReport",
                                Map.of(
                                        "accuracy",
                                        0.91,
                                        "macro avg",
                                        Map.of("f1-score", 0.88))));

        service.enrichAfterEval(userId, "eval-tag", evalResult);

        verify(classifierModelRepository).save(e);
        assertThat(e.getAccuracy()).isEqualTo(0.91);
        assertThat(e.getF1Macro()).isEqualTo(0.88);
    }

    @Test
    void enrichAfterEval_noOpWhenNoMatchingRow() {
        when(classifierModelRepository.findByOwner_IdAndArtifactPath(userId, "missing"))
                .thenReturn(Optional.empty());

        service.enrichAfterEval(userId, "missing", Map.of("metrics", Map.of()));

        verify(classifierModelRepository, never()).save(any());
    }

    @Test
    void extractInferenceTag_handlesNullResult() {
        assertThat(ClassifierModelRegistryService.extractInferenceTag(null)).isNull();
    }
}
