package com.uniovi.rag.service.account;

import com.uniovi.rag.domain.AccountExportArtifactStatus;
import com.uniovi.rag.infrastructure.persistence.AccountExportArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AccountExportArtifactEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountExportCleanupSchedulerTest {

    @Mock
    private AccountExportArtifactRepository accountExportArtifactRepository;

    @InjectMocks
    private AccountExportCleanupScheduler scheduler;

    @Test
    void purgeExpiredExports_deletesFile_andMarksExpired(@TempDir Path dir) throws Exception {
        Path zip = dir.resolve("e.zip");
        Files.writeString(zip, "z");
        AccountExportArtifactEntity e = AccountExportArtifactEntity.newArtifact();
        e.setId(UUID.randomUUID());
        e.setStorageUri(zip.toString());
        when(accountExportArtifactRepository.findByStatusAndExpiresAtBefore(
                        eq(AccountExportArtifactStatus.READY), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(e));

        scheduler.purgeExpiredExports();

        assertThat(Files.exists(zip)).isFalse();
        ArgumentCaptor<AccountExportArtifactEntity> cap = ArgumentCaptor.forClass(AccountExportArtifactEntity.class);
        verify(accountExportArtifactRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(AccountExportArtifactStatus.EXPIRED);
    }
}
