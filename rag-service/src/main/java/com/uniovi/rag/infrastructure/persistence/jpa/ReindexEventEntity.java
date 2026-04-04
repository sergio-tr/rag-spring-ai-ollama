package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.knowledge.ReindexEventStatus;
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
@Table(name = "reindex_event")
public class ReindexEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private KnowledgeDocumentEntity document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private ConversationEntity conversation;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(name = "target_signature_hash", nullable = false, length = 128)
    private String targetSignatureHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReindexEventStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "async_task_id")
    private AsyncTaskEntity asyncTask;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReindexEventEntity() {
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTargetSignatureHash() {
        return targetSignatureHash;
    }

    public void setTargetSignatureHash(String targetSignatureHash) {
        this.targetSignatureHash = targetSignatureHash;
    }

    public ReindexEventStatus getStatus() {
        return status;
    }

    public void setStatus(ReindexEventStatus status) {
        this.status = status;
    }

    public AsyncTaskEntity getAsyncTask() {
        return asyncTask;
    }

    public void setAsyncTask(AsyncTaskEntity asyncTask) {
        this.asyncTask = asyncTask;
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
