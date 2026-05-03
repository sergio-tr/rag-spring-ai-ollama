package com.uniovi.rag.service.account;

import com.uniovi.rag.domain.AccountExportArtifactStatus;
import com.uniovi.rag.infrastructure.persistence.AccountExportArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AccountExportArtifactEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Removes expired export ZIP files from disk and marks rows expired.
 */
@Component
public class AccountExportCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountExportCleanupScheduler.class);

    private final AccountExportArtifactRepository accountExportArtifactRepository;

    public AccountExportCleanupScheduler(AccountExportArtifactRepository accountExportArtifactRepository) {
        this.accountExportArtifactRepository = accountExportArtifactRepository;
    }

    @Scheduled(fixedDelayString = "${rag.account.cleanup-interval-ms:3600000}", initialDelayString = "60000")
    @Transactional
    public void purgeExpiredExports() {
        Instant now = Instant.now();
        List<AccountExportArtifactEntity> expired =
                accountExportArtifactRepository.findByStatusAndExpiresAtBefore(
                        AccountExportArtifactStatus.READY, now);
        for (AccountExportArtifactEntity e : expired) {
            try {
                Path p = Path.of(e.getStorageUri());
                Files.deleteIfExists(p);
            } catch (Exception ex) {
                log.warn("Could not delete export file {}: {}", e.getStorageUri(), ex.getMessage());
            }
            e.setStatus(AccountExportArtifactStatus.EXPIRED);
            accountExportArtifactRepository.save(e);
        }
    }
}
