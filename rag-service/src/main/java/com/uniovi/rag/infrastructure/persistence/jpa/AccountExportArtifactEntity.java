package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.AccountExportArtifactStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_export_artifact")
public class AccountExportArtifactEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "async_task_id")
    private AsyncTaskEntity asyncTask;

    @Column(name = "storage_uri", nullable = false, columnDefinition = "text")
    private String storageUri;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountExportArtifactStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;

    protected AccountExportArtifactEntity() {
    }

    public static AccountExportArtifactEntity newArtifact() {
        return new AccountExportArtifactEntity();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public AsyncTaskEntity getAsyncTask() {
        return asyncTask;
    }

    public void setAsyncTask(AsyncTaskEntity asyncTask) {
        this.asyncTask = asyncTask;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public long getByteSize() {
        return byteSize;
    }

    public void setByteSize(long byteSize) {
        this.byteSize = byteSize;
    }

    public AccountExportArtifactStatus getStatus() {
        return status;
    }

    public void setStatus(AccountExportArtifactStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getDownloadedAt() {
        return downloadedAt;
    }

    public void setDownloadedAt(Instant downloadedAt) {
        this.downloadedAt = downloadedAt;
    }
}
