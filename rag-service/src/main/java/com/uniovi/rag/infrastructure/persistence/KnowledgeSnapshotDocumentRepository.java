package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeSnapshotDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeSnapshotDocumentPk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KnowledgeSnapshotDocumentRepository extends JpaRepository<KnowledgeSnapshotDocumentEntity, KnowledgeSnapshotDocumentPk> {

    void deleteByDocument_Id(UUID documentId);

    long countBySnapshot_Id(UUID snapshotId);
}
