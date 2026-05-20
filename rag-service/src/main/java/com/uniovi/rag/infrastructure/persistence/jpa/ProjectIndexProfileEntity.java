package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_index_profile")
public class ProjectIndexProfileEntity {

    @Id
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "materialization_strategy", nullable = false, length = 32)
    private String materializationStrategy;

    @Column(name = "metadata_enabled", nullable = false)
    private boolean metadataEnabled;

    @Column(name = "metadata_profile", length = 64)
    private String metadataProfile;

    @Column(name = "embedding_model_id", nullable = false, length = 128)
    private String embeddingModelId;

    @Column(name = "chunk_max_chars", nullable = false)
    private int chunkMaxChars;

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap;

    @Column(name = "profile_hash", nullable = false, length = 128)
    private String profileHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getMaterializationStrategy() {
        return materializationStrategy;
    }

    public void setMaterializationStrategy(String materializationStrategy) {
        this.materializationStrategy = materializationStrategy;
    }

    public boolean isMetadataEnabled() {
        return metadataEnabled;
    }

    public void setMetadataEnabled(boolean metadataEnabled) {
        this.metadataEnabled = metadataEnabled;
    }

    public String getMetadataProfile() {
        return metadataProfile;
    }

    public void setMetadataProfile(String metadataProfile) {
        this.metadataProfile = metadataProfile;
    }

    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    public int getChunkMaxChars() {
        return chunkMaxChars;
    }

    public void setChunkMaxChars(int chunkMaxChars) {
        this.chunkMaxChars = chunkMaxChars;
    }

    public Integer getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(Integer chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public String getProfileHash() {
        return profileHash;
    }

    public void setProfileHash(String profileHash) {
        this.profileHash = profileHash;
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

