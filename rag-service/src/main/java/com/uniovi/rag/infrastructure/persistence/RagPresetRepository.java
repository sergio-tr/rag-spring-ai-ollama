package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RagPresetRepository extends JpaRepository<RagPresetEntity, UUID> {

    @Query(
            "SELECT p FROM RagPresetEntity p WHERE p.system = true OR (p.owner IS NOT NULL AND p.owner.id = :userId) ORDER BY p.system ASC, p.updatedAt DESC")
    List<RagPresetEntity> findVisibleForUser(@Param("userId") UUID userId);

    Optional<RagPresetEntity> findByIdAndOwner_Id(UUID id, UUID ownerId);
}
