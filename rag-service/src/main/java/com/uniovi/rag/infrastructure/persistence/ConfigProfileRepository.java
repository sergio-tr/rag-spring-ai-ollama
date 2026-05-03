package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.infrastructure.persistence.jpa.ConfigProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConfigProfileRepository extends JpaRepository<ConfigProfileEntity, UUID> {

    @Query(
            "SELECT p FROM ConfigProfileEntity p WHERE p.owner IS NULL OR p.owner.id = :userId ORDER BY p.createdAt DESC")
    List<ConfigProfileEntity> findVisibleForUser(@Param("userId") UUID userId);

    List<ConfigProfileEntity> findByProfileType(ConfigProfileType type);
}
