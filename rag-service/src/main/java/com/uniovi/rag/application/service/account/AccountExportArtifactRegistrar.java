package com.uniovi.rag.application.service.account;

import com.uniovi.rag.domain.AccountExportArtifactStatus;
import com.uniovi.rag.infrastructure.persistence.AccountExportArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AccountExportArtifactEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountExportArtifactRegistrar {

    private final AccountExportArtifactRepository accountExportArtifactRepository;

    public AccountExportArtifactRegistrar(AccountExportArtifactRepository accountExportArtifactRepository) {
        this.accountExportArtifactRepository = accountExportArtifactRepository;
    }

    @Transactional
    public void saveAndCompleteTask(
            AsyncTaskEntity task,
            UUID taskId,
            UUID artifactId,
            UserEntity user,
            Path zipPath,
            String sha256,
            long byteSize,
            Instant createdAt,
            Instant expiresAt,
            AsyncTaskMutationService mutation) {

        AccountExportArtifactEntity artifact = AccountExportArtifactEntity.newArtifact();
        artifact.setId(artifactId);
        artifact.setUser(user);
        artifact.setAsyncTask(task);
        artifact.setStorageUri(zipPath.toAbsolutePath().toString());
        artifact.setSha256(sha256);
        artifact.setByteSize(byteSize);
        artifact.setStatus(AccountExportArtifactStatus.READY);
        artifact.setCreatedAt(createdAt);
        artifact.setExpiresAt(expiresAt);
        accountExportArtifactRepository.save(artifact);

        mutation.markSucceeded(
                taskId,
                Map.of(
                        AccountJobPayloadKeys.EXPORT_ARTIFACT_ID,
                        artifactId.toString(),
                        AccountJobPayloadKeys.TASK_TYPE,
                        "ACCOUNT_EXPORT"));
    }
}
