package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
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

/**
 * Canonical JPA mapping for {@code project_documents} (single entity per table).
 */
@Entity
@Table(name = "project_documents")
public class KnowledgeDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private ConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "corpus_scope", nullable = false, length = 32)
    private CorpusScope corpusScope = CorpusScope.PROJECT_SHARED;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectDocumentStatus status;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "reindexed_at")
    private Instant reindexedAt;

    @Column(name = "storage_uri")
    private String storageUri;

    @Column(name = "content_checksum", length = 64)
    private String contentChecksum;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "byte_size")
    private Long byteSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_index_snapshot_id")
    private KnowledgeIndexSnapshotEntity currentIndexSnapshot;

    @Column(name = "requires_reindex", nullable = false)
    private boolean requiresReindex;

    protected KnowledgeDocumentEntity() {
    }

    public UUID getId() {
        return id;
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

    public CorpusScope getCorpusScope() {
        return corpusScope;
    }

    public void setCorpusScope(CorpusScope corpusScope) {
        this.corpusScope = corpusScope;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public ProjectDocumentStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectDocumentStatus status) {
        this.status = status;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getReindexedAt() {
        return reindexedAt;
    }

    public void setReindexedAt(Instant reindexedAt) {
        this.reindexedAt = reindexedAt;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public String getContentChecksum() {
        return contentChecksum;
    }

    public void setContentChecksum(String contentChecksum) {
        this.contentChecksum = contentChecksum;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getByteSize() {
        return byteSize;
    }

    public void setByteSize(Long byteSize) {
        this.byteSize = byteSize;
    }

    public KnowledgeIndexSnapshotEntity getCurrentIndexSnapshot() {
        return currentIndexSnapshot;
    }

    public void setCurrentIndexSnapshot(KnowledgeIndexSnapshotEntity currentIndexSnapshot) {
        this.currentIndexSnapshot = currentIndexSnapshot;
    }

    public boolean isRequiresReindex() {
        return requiresReindex;
    }

    public void setRequiresReindex(boolean requiresReindex) {
        this.requiresReindex = requiresReindex;
    }
}
