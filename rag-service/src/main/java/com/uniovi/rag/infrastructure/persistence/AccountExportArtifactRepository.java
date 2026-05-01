package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.AccountExportArtifactStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.AccountExportArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountExportArtifactRepository extends JpaRepository<AccountExportArtifactEntity, UUID> {

    Optional<AccountExportArtifactEntity> findByIdAndUser_Id(UUID id, UUID userId);

    List<AccountExportArtifactEntity> findByStatusAndExpiresAtBefore(
            AccountExportArtifactStatus status, Instant before);
}
