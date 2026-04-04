package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_index_snapshot")
public class KnowledgeIndexSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "signature_hash", nullable = false, length = 128)
    private String signatureHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private KnowledgeSnapshotScopeType scopeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private ConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IndexSnapshotStatus status;

    @Column(name = "resolved_config_snapshot_id")
    private UUID resolvedConfigSnapshotId;

    @Column(name = "resolved_config_hash", length = 128)
    private String resolvedConfigHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public KnowledgeIndexSnapshotEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getSignatureHash() {
        return signatureHash;
    }

    public void setSignatureHash(String signatureHash) {
        this.signatureHash = signatureHash;
    }

    public KnowledgeSnapshotScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(KnowledgeSnapshotScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public ProjectEntity getProject() {
        return project;
    }

    public void setProject(ProjectEntity project) {
        this.project = project;
    }

    public ConversationEntity getConversation() {
        return conversation;
    }

    public void setConversation(ConversationEntity conversation) {
        this.conversation = conversation;
    }

    public IndexSnapshotStatus getStatus() {
        return status;
    }

    public void setStatus(IndexSnapshotStatus status) {
        this.status = status;
    }

    public UUID getResolvedConfigSnapshotId() {
        return resolvedConfigSnapshotId;
    }

    public void setResolvedConfigSnapshotId(UUID resolvedConfigSnapshotId) {
        this.resolvedConfigSnapshotId = resolvedConfigSnapshotId;
    }

    public String getResolvedConfigHash() {
        return resolvedConfigHash;
    }

    public void setResolvedConfigHash(String resolvedConfigHash) {
        this.resolvedConfigHash = resolvedConfigHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
