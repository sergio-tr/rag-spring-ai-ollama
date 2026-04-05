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

    @Query(
            "SELECT DISTINCT p FROM RagPresetEntity p LEFT JOIN FETCH p.profileRefs pr LEFT JOIN FETCH pr.profile WHERE p.system = true OR (p.owner IS NOT NULL AND p.owner.id = :userId)")
    List<RagPresetEntity> findVisibleForUserWithProfileRefs(@Param("userId") UUID userId);

    @Query(
            "SELECT DISTINCT p FROM RagPresetEntity p LEFT JOIN FETCH p.profileRefs pr LEFT JOIN FETCH pr.profile WHERE p.id = :id")
    Optional<RagPresetEntity> findByIdWithProfileRefs(@Param("id") UUID id);

    Optional<RagPresetEntity> findByIdAndOwner_Id(UUID id, UUID ownerId);
}
