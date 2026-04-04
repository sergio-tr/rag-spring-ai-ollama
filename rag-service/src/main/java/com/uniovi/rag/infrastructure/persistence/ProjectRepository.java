package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    Page<ProjectEntity> findByOwner_IdOrderByUpdatedAtDesc(UUID ownerId, Pageable pageable);

    Optional<ProjectEntity> findByIdAndOwner_Id(UUID id, UUID ownerId);
}
