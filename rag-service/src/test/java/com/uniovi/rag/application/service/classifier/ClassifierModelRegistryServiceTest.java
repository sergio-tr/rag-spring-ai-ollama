package com.uniovi.rag.application.service.classifier;

import com.uniovi.rag.domain.ClassifierModelStatus;
import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.infrastructure.persistence.ClassifierModelRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ClassifierModelEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ClassifierModelResponseDto;
import com.uniovi.rag.application.service.config.UserProjectConfigurationService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
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

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private ClassifierLabPort classifierLabPort;

    private ClassifierModelRegistryService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClassifierModelRegistryService(
                classifierModelRepository,
                userRepository,
                projectAccessService,
                userProjectConfigurationService,
                classifierLabPort,
                "default");
    }

    @Test
    void registerAfterSuccessfulTrain_persistsWhenModelIdPresent() {
        when(classifierModelRepository.findByOwnerIdAndSourceTaskId(userId, taskId.toString()))
                .thenReturn(Optional.empty());
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(classifierModelRepository.existsByOwner_IdAndNameIgnoreCase(userId, "m1")).thenReturn(false);
        when(classifierModelRepository.existsByOwner_IdAndArtifactPath(userId, "my-tag-1")).thenReturn(false);

        Map<String, Object> res = Map.of("modelId", "my-tag-1", "name", "m1", "metrics", Map.of("loss", 0.1));

        service.registerAfterSuccessfulTrain(userId, taskId, "lab", res, 50, 8);

        ArgumentCaptor<ClassifierModelEntity> cap = ArgumentCaptor.forClass(ClassifierModelEntity.class);
        verify(classifierModelRepository).save(cap.capture());
        assertThat(cap.getValue().getArtifactPath()).isEqualTo("my-tag-1");
        assertThat(cap.getValue().getStatus()).isEqualTo(ClassifierModelStatus.READY);
        assertThat(cap.getValue().getHyperparams()).containsEntry(ClassifierModelRegistryService.HP_SOURCE_TASK_ID, taskId.toString());
        assertThat(cap.getValue().getHyperparams()).containsEntry(ClassifierModelRegistryService.HP_OWNER_ID, userId.toString());
    }

    @Test
    void registerAfterSuccessfulTrain_skipsWhenDuplicateNameExists() {
        when(classifierModelRepository.findByOwnerIdAndSourceTaskId(userId, taskId.toString()))
                .thenReturn(Optional.empty());
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(classifierModelRepository.existsByOwner_IdAndNameIgnoreCase(userId, "m1")).thenReturn(true);

        service.registerAfterSuccessfulTrain(
                userId, taskId, "lab", Map.of("modelId", "my-tag-1", "name", "m1"), 50, 8);

        verify(classifierModelRepository, never()).save(any());
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
    void listForUserWithSync_excludesDbRowWhenArtifactRemovedFromDisk() {
        when(classifierLabPort.isConfigured()).thenReturn(true);
        when(classifierLabPort.listModels()).thenReturn(List.of(Map.of("id", "default", "name", "Default model")));

        ClassifierModelEntity stale = new ClassifierModelEntity();
        stale.setId(UUID.randomUUID());
        stale.setName("gone");
        stale.setArtifactPath("deleted-tag");
        stale.setStatus(ClassifierModelStatus.READY);
        stale.setHyperparams(Map.of(ClassifierModelRegistryService.HP_SOURCE_TASK_ID, "task-gone"));
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of(stale));

        List<ClassifierModelResponseDto> list = service.listForUserWithSync(userId);

        assertThat(list).extracting(ClassifierModelResponseDto::inferenceTag).containsExactly("default");
    }

    @Test
    void listForUserWithSync_includesGlobalCustomModelsFromClassifierService() {
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(classifierLabPort.isConfigured()).thenReturn(true);
        when(classifierLabPort.listModels())
                .thenReturn(
                        List.of(
                                Map.of("id", "default", "name", "Default model"),
                                Map.of(
                                        "id",
                                        "a1b2c3d4",
                                        "name",
                                        "shared-custom",
                                        "createdAt",
                                        "2020-01-01T00:00:00Z",
                                        "metrics",
                                        Map.of("accuracy", 0.9, "macro_avg_f1", 0.88))));
        when(classifierModelRepository.findByOwner_IdAndArtifactPath(userId, "default")).thenReturn(Optional.empty());
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of());

        List<ClassifierModelResponseDto> list = service.listForUserWithSync(userId);

        assertThat(list).extracting(ClassifierModelResponseDto::inferenceTag).contains("default", "a1b2c3d4");
        verify(classifierModelRepository, atLeastOnce()).save(any());
    }

    @Test
    void listForUserWithSync_doesNotRegisterOtherUsersDiskModelTags() {
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(classifierLabPort.isConfigured()).thenReturn(true);
        when(classifierLabPort.listModels())
                .thenReturn(
                        List.of(
                                Map.of("id", "a1b2c3d4", "name", "stolen-from-disk", "createdAt", "2020-01-01T00:00:00Z")));
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of());

        service.listForUserWithSync(userId);

        verify(classifierModelRepository, never()).save(any());
    }

    @Test
    void activateForProject_throwsForbiddenWhenRowOwnedByAnotherUser() {
        UUID projectId = UUID.randomUUID();
        UUID modelRowId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UserEntity otherOwner = mock(UserEntity.class);
        when(otherOwner.getId()).thenReturn(otherUser);

        ClassifierModelEntity model = new ClassifierModelEntity();
        model.setId(modelRowId);
        model.setOwner(otherOwner);
        model.setStatus(ClassifierModelStatus.READY);
        model.setArtifactPath("tag-x");
        model.setHyperparams(Map.of(ClassifierModelRegistryService.HP_SOURCE_TASK_ID, "task"));

        when(classifierModelRepository.findById(modelRowId)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> service.activateForProject(userId, projectId, modelRowId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void activateForProject_throwsBadRequestWhenOrphanExternalRow() {
        UUID projectId = UUID.randomUUID();
        UUID modelRowId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(userId);

        ClassifierModelEntity model = new ClassifierModelEntity();
        model.setId(modelRowId);
        model.setOwner(owner);
        model.setStatus(ClassifierModelStatus.READY);
        model.setArtifactPath("foreign-uuid");
        model.setHyperparams(Map.of("external", true, "source", "classifier-service"));

        when(classifierModelRepository.findById(modelRowId)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> service.activateForProject(userId, projectId, modelRowId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
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
    void listForUserWithSync_upsertsSystemDefaultWhenMissing() {
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(classifierLabPort.isConfigured()).thenReturn(true);
        when(classifierLabPort.listModels()).thenReturn(List.of(Map.of("id", "default", "name", "Default model")));
        when(classifierModelRepository.findByOwner_IdAndArtifactPath(userId, "default")).thenReturn(Optional.empty());
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of());

        service.listForUserWithSync(userId);

        ArgumentCaptor<ClassifierModelEntity> cap = ArgumentCaptor.forClass(ClassifierModelEntity.class);
        verify(classifierModelRepository, atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues().stream().anyMatch(e -> "default".equals(e.getArtifactPath()))).isTrue();
        ClassifierModelEntity savedDefault =
                cap.getAllValues().stream().filter(e -> "default".equals(e.getArtifactPath())).findFirst().orElseThrow();
        assertThat(savedDefault.getHyperparams()).containsEntry(ClassifierModelRegistryService.HP_SYSTEM_CATALOG, true);
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
        model.setHyperparams(Map.of(ClassifierModelRegistryService.HP_SOURCE_TASK_ID, "task-1"));

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
    void activateForProject_allowsSystemInferenceTagWithoutHyperparams() {
        UUID projectId = UUID.randomUUID();
        UUID modelRowId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(userId);

        ClassifierModelEntity model = new ClassifierModelEntity();
        model.setId(modelRowId);
        model.setOwner(owner);
        model.setStatus(ClassifierModelStatus.READY);
        model.setArtifactPath("default");
        model.setActive(false);
        model.setHyperparams(Map.of());

        when(classifierModelRepository.findById(modelRowId)).thenReturn(Optional.of(model));
        when(classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId)).thenReturn(List.of(model));

        service.activateForProject(userId, projectId, modelRowId);

        verify(userProjectConfigurationService).mergeProjectConfig(userId, projectId, Map.of("classifierModelId", "default"));
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
        other.setHyperparams(Map.of(ClassifierModelRegistryService.HP_SOURCE_TASK_ID, "task-old"));

        ClassifierModelEntity model = new ClassifierModelEntity();
        model.setId(modelRowId);
        model.setOwner(owner);
        model.setStatus(ClassifierModelStatus.READY);
        model.setArtifactPath("new-tag");
        model.setActive(false);
        model.setHyperparams(Map.of(ClassifierModelRegistryService.HP_SOURCE_TASK_ID, "task-new"));

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
