package com.uniovi.rag.application.service.account;

import com.uniovi.rag.domain.AccountExportArtifactStatus;
import com.uniovi.rag.infrastructure.persistence.AccountExportArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AccountExportArtifactEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AccountExportArtifactRegistrar {

    private final AccountExportArtifactRepository accountExportArtifactRepository;

    public AccountExportArtifactRegistrar(AccountExportArtifactRepository accountExportArtifactRepository) {
        this.accountExportArtifactRepository = accountExportArtifactRepository;
    }

    @Transactional
    public void saveAndCompleteTask(AccountExportCompletion completion) {
        AccountExportArtifactEntity artifact = AccountExportArtifactEntity.newArtifact();
        artifact.setId(completion.artifactId());
        artifact.setUser(completion.user());
        artifact.setAsyncTask(completion.task());
        artifact.setStorageUri(completion.zipPath().toAbsolutePath().toString());
        artifact.setSha256(completion.sha256());
        artifact.setByteSize(completion.byteSize());
        artifact.setStatus(AccountExportArtifactStatus.READY);
        artifact.setCreatedAt(completion.createdAt());
        artifact.setExpiresAt(completion.expiresAt());
        accountExportArtifactRepository.save(artifact);

        completion
                .mutation()
                .markSucceeded(
                        completion.taskId(),
                        Map.of(
                                AccountJobPayloadKeys.EXPORT_ARTIFACT_ID,
                                completion.artifactId().toString(),
                                AccountJobPayloadKeys.TASK_TYPE,
                                "ACCOUNT_EXPORT"));
    }
}
