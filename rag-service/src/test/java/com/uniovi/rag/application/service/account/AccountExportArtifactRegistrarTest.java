package com.uniovi.rag.application.service.account;

import com.uniovi.rag.domain.AccountExportArtifactStatus;
import com.uniovi.rag.infrastructure.persistence.AccountExportArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AccountExportArtifactEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountExportArtifactRegistrarTest {

    @Mock
    private AccountExportArtifactRepository accountExportArtifactRepository;

    @Mock
    private AsyncTaskMutationService mutation;

    @InjectMocks
    private AccountExportArtifactRegistrar registrar;

    @Test
    void saveAndCompleteTask_persistsArtifact_andMarksTaskSucceeded() {
        UUID taskId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UserEntity user = Mockito.mock(UserEntity.class);
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        Path zip = Path.of("/tmp/export.zip");
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant expires = Instant.parse("2026-01-02T00:00:00Z");

        String sha256Hex = "a".repeat(64);
        AccountExportCompletion completion =
                new AccountExportCompletion(task, taskId, artifactId, user, zip, sha256Hex, 10L, created, expires, mutation);

        registrar.saveAndCompleteTask(completion);

        ArgumentCaptor<AccountExportArtifactEntity> entity = ArgumentCaptor.forClass(AccountExportArtifactEntity.class);
        verify(accountExportArtifactRepository).save(entity.capture());
        assertThat(entity.getValue().getId()).isEqualTo(artifactId);
        assertThat(entity.getValue().getUser()).isSameAs(user);
        assertThat(entity.getValue().getStatus()).isEqualTo(AccountExportArtifactStatus.READY);
        assertThat(entity.getValue().getSha256()).hasSize(64);

        ArgumentCaptor<Map<String, Object>> res = ArgumentCaptor.forClass(Map.class);
        verify(mutation).markSucceeded(eq(taskId), res.capture());
        assertThat(res.getValue())
                .containsEntry(AccountJobPayloadKeys.EXPORT_ARTIFACT_ID, artifactId.toString())
                .containsEntry(AccountJobPayloadKeys.TASK_TYPE, "ACCOUNT_EXPORT");
    }
}
