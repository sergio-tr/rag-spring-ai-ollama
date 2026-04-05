package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeIndexSnapshotRepository extends JpaRepository<KnowledgeIndexSnapshotEntity, UUID> {

    Optional<KnowledgeIndexSnapshotEntity> findByProject_IdAndSignatureHashAndStatus(
            UUID projectId, String signatureHash, IndexSnapshotStatus status);
}
