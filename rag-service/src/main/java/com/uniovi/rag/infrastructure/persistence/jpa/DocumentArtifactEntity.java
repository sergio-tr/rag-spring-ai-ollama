package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "document_artifact")
public class DocumentArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocumentEntity document;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 32)
    private DocumentArtifactType artifactType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_jsonb", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payloadJsonb;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentArtifactEntity() {
    }

    public static DocumentArtifactEntity newRow() {
        return new DocumentArtifactEntity();
    }

    public UUID getId() {
        return id;
    }

    public KnowledgeDocumentEntity getDocument() {
        return document;
    }

    public void setDocument(KnowledgeDocumentEntity document) {
        this.document = document;
    }

    public DocumentArtifactType getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(DocumentArtifactType artifactType) {
        this.artifactType = artifactType;
    }

    public Map<String, Object> getPayloadJsonb() {
        return payloadJsonb;
    }

    public void setPayloadJsonb(Map<String, Object> payloadJsonb) {
        this.payloadJsonb = payloadJsonb;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
