package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_snapshot_document")
public class KnowledgeSnapshotDocumentEntity {

    @EmbeddedId
    private KnowledgeSnapshotDocumentPk id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("snapshotId")
    @JoinColumn(name = "snapshot_id", nullable = false)
    private KnowledgeIndexSnapshotEntity snapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("documentId")
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocumentEntity document;

    public KnowledgeSnapshotDocumentEntity() {
        // JPA requires a no-arg constructor for entity instantiation.
    }

    public KnowledgeSnapshotDocumentPk getId() {
        return id;
    }

    public void setId(KnowledgeSnapshotDocumentPk id) {
        this.id = id;
    }

    public KnowledgeIndexSnapshotEntity getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(KnowledgeIndexSnapshotEntity snapshot) {
        this.snapshot = snapshot;
    }

    public KnowledgeDocumentEntity getDocument() {
        return document;
    }

    public void setDocument(KnowledgeDocumentEntity document) {
        this.document = document;
    }
}
