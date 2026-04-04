package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.ProjectDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocumentEntity, UUID> {

    List<ProjectDocumentEntity> findByProject_IdOrderByUploadedAtDesc(UUID projectId);

    Optional<ProjectDocumentEntity> findByIdAndProject_Id(UUID id, UUID projectId);

    long countByProject_Id(UUID projectId);
}
