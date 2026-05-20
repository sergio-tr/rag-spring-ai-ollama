package com.uniovi.rag.application.service.async;

import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.port.AfterCommitTaskScheduler;
import com.uniovi.rag.application.service.evaluation.async.LabJobPayloadKeys;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncTaskServiceTest {

    @Mock
    private AsyncTaskRepository asyncTaskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private AsyncLabTaskRunner asyncLabTaskRunner;

    @Mock
    private AfterCommitTaskScheduler afterCommitTaskScheduler;

    @InjectMocks
    private AsyncTaskService asyncTaskService;

    @Test
    void submitEvalLlm_schedulesRunnerAfterCommit_thenRunnableStartsLabRunner() {
        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(asyncTaskRepository.save(any(AsyncTaskEntity.class)))
                .thenAnswer(
                        inv -> {
                            AsyncTaskEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });

        UUID taskId = asyncTaskService.submitEvalLlm(userId);

        assertThat(taskId).isNotNull();
        verify(asyncLabTaskRunner, never()).execute(any());
        ArgumentCaptor<Runnable> runCap = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitTaskScheduler).scheduleAfterCommit(runCap.capture());
        runCap.getValue().run();
        verify(asyncLabTaskRunner).execute(taskId);
        ArgumentCaptor<AsyncTaskEntity> cap = ArgumentCaptor.forClass(AsyncTaskEntity.class);
        verify(asyncTaskRepository).save(cap.capture());
        assertThat(cap.getValue().getTaskType()).isEqualTo(AsyncTaskType.EVAL_LLM);
    }

    @Test
    void submitEvalLlm_withOwnedProjectId_persistsProjectOnTask() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(asyncTaskRepository.save(any(AsyncTaskEntity.class)))
                .thenAnswer(
                        inv -> {
                            AsyncTaskEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });

        asyncTaskService.submitEvalLlm(userId, projectId);

        ArgumentCaptor<AsyncTaskEntity> cap = ArgumentCaptor.forClass(AsyncTaskEntity.class);
        verify(asyncTaskRepository).save(cap.capture());
        assertThat(cap.getValue().getProject()).isSameAs(project);
    }

    @Test
    void submitOllamaPull_buildsPayload() {
        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(asyncTaskRepository.save(any(AsyncTaskEntity.class)))
                .thenAnswer(
                        inv -> {
                            AsyncTaskEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });

        asyncTaskService.submitOllamaPull(userId, "  llama3  ");

        ArgumentCaptor<AsyncTaskEntity> cap = ArgumentCaptor.forClass(AsyncTaskEntity.class);
        verify(asyncTaskRepository).save(cap.capture());
        assertThat(cap.getValue().getRequestPayload())
                .containsEntry(LabJobPayloadKeys.OLLAMA_MODEL, "llama3");
    }

    @Test
    void getStatus_throwsWhenMissing() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(asyncTaskRepository.findByIdAndUser_Id(taskId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> asyncTaskService.getStatus(taskId, userId))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void submitEvalRag_enqueuesEvalRag() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(asyncTaskRepository.save(any(AsyncTaskEntity.class)))
                .thenAnswer(
                        inv -> {
                            AsyncTaskEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });

        asyncTaskService.submitEvalRag(userId);

        ArgumentCaptor<AsyncTaskEntity> cap = ArgumentCaptor.forClass(AsyncTaskEntity.class);
        verify(asyncTaskRepository).save(cap.capture());
        assertThat(cap.getValue().getTaskType()).isEqualTo(AsyncTaskType.EVAL_RAG);
    }

    @Test
    void submitClassifierTrain_withLabelsFile_addsLabelsPath() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(asyncTaskRepository.save(any(AsyncTaskEntity.class)))
                .thenAnswer(
                        inv -> {
                            AsyncTaskEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });
        MockMultipartFile train = new MockMultipartFile("file", "t.xlsx", null, new byte[] {1, 2});
        MockMultipartFile labels = new MockMultipartFile("labels", "lab.csv", null, new byte[] {3});

        asyncTaskService.submitClassifierTrain(userId, train, "m1", null, labels, 2, 8);

        ArgumentCaptor<AsyncTaskEntity> cap = ArgumentCaptor.forClass(AsyncTaskEntity.class);
        verify(asyncTaskRepository).save(cap.capture());
        assertThat(cap.getValue().getRequestPayload()).containsKeys(LabJobPayloadKeys.LABELS_PATH);
    }

    @Test
    void submitClassifierEval_withDataset_setsEvalPaths() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(asyncTaskRepository.save(any(AsyncTaskEntity.class)))
                .thenAnswer(
                        inv -> {
                            AsyncTaskEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });
        MockMultipartFile eval = new MockMultipartFile("f", "eval.xlsx", null, new byte[] {9});

        asyncTaskService.submitClassifierEval(userId, "mid", true, eval);

        ArgumentCaptor<AsyncTaskEntity> cap = ArgumentCaptor.forClass(AsyncTaskEntity.class);
        verify(asyncTaskRepository).save(cap.capture());
        assertThat(cap.getValue().getRequestPayload())
                .containsEntry(LabJobPayloadKeys.INCLUDE_IMAGES, true)
                .containsEntry(LabJobPayloadKeys.MODEL_ID, "mid");
        assertThat(cap.getValue().getRequestPayload()).containsKeys(LabJobPayloadKeys.EVAL_PATH);
    }

    @Test
    void getStatus_returnsDto() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        AsyncTaskEntity e = AsyncTaskEntity.queued(owner, AsyncTaskType.EVAL_LLM, Map.of(), Instant.now());
        ReflectionTestUtils.setField(e, "id", taskId);
        e.setStatus(AsyncTaskStatus.SUCCEEDED);
        when(asyncTaskRepository.findByIdAndUser_Id(taskId, userId)).thenReturn(Optional.of(e));

        AsyncTaskStatusDto dto = asyncTaskService.getStatus(taskId, userId);

        assertThat(dto.id()).isEqualTo(taskId);
        assertThat(dto.status()).isEqualTo("SUCCEEDED");
        assertThat(dto.terminal()).isTrue();
    }
}
