package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.DocumentArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentArtifactRepository extends JpaRepository<DocumentArtifactEntity, UUID> {

    List<DocumentArtifactEntity> findByDocument_IdOrderByCreatedAtAsc(UUID documentId);
}
