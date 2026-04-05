package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class KnowledgeSnapshotDocumentPk implements Serializable {

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    protected KnowledgeSnapshotDocumentPk() {
    }

    public KnowledgeSnapshotDocumentPk(UUID snapshotId, UUID documentId) {
        this.snapshotId = snapshotId;
        this.documentId = documentId;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(UUID snapshotId) {
        this.snapshotId = snapshotId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KnowledgeSnapshotDocumentPk that = (KnowledgeSnapshotDocumentPk) o;
        return Objects.equals(snapshotId, that.snapshotId) && Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotId, documentId);
    }
}
