package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.RagConfigurationLevel;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RagConfigurationRepository extends JpaRepository<RagConfigurationEntity, UUID> {

    List<RagConfigurationEntity> findByUser_IdOrderByUpdatedAtDesc(UUID userId);

    Optional<RagConfigurationEntity> findFirstByUser_IdAndLevelAndProjectIsNullAndActiveIsTrue(
            UUID userId, RagConfigurationLevel level);

    Optional<RagConfigurationEntity> findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
            UUID userId, UUID projectId, RagConfigurationLevel level);
}
