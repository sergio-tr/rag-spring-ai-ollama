package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "resolved_config_snapshot")
public class ResolvedConfigSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "job_id")
    private UUID jobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_jsonb", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payloadJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capability_set_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> capabilitySetJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compatibility_result_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> compatibilityResultJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prompt_stack_preview_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> promptStackPreviewJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provenance_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> provenanceJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reindex_impact_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> reindexImpactJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "system_prompt_layers_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> systemPromptLayersJsonb;

    @Column(name = "effective_system_prompt", columnDefinition = "TEXT")
    private String effectiveSystemPrompt;

    @Column(name = "config_hash", length = 128)
    private String configHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ResolvedConfigSnapshotEntity() {
    }

    /** Factory for mappers outside this package (JPA requires a no-arg constructor). */
    public static ResolvedConfigSnapshotEntity newForInsert() {
        return new ResolvedConfigSnapshotEntity();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public Map<String, Object> getPayloadJsonb() {
        return payloadJsonb;
    }

    public void setPayloadJsonb(Map<String, Object> payloadJsonb) {
        this.payloadJsonb = payloadJsonb;
    }

    public Map<String, Object> getCapabilitySetJsonb() {
        return capabilitySetJsonb;
    }

    public void setCapabilitySetJsonb(Map<String, Object> capabilitySetJsonb) {
        this.capabilitySetJsonb = capabilitySetJsonb;
    }

    public Map<String, Object> getCompatibilityResultJsonb() {
        return compatibilityResultJsonb;
    }

    public void setCompatibilityResultJsonb(Map<String, Object> compatibilityResultJsonb) {
        this.compatibilityResultJsonb = compatibilityResultJsonb;
    }

    public Map<String, Object> getPromptStackPreviewJsonb() {
        return promptStackPreviewJsonb;
    }

    public void setPromptStackPreviewJsonb(Map<String, Object> promptStackPreviewJsonb) {
        this.promptStackPreviewJsonb = promptStackPreviewJsonb;
    }

    public Map<String, Object> getProvenanceJsonb() {
        return provenanceJsonb;
    }

    public void setProvenanceJsonb(Map<String, Object> provenanceJsonb) {
        this.provenanceJsonb = provenanceJsonb;
    }

    public Map<String, Object> getReindexImpactJsonb() {
        return reindexImpactJsonb;
    }

    public void setReindexImpactJsonb(Map<String, Object> reindexImpactJsonb) {
        this.reindexImpactJsonb = reindexImpactJsonb;
    }

    public Map<String, Object> getSystemPromptLayersJsonb() {
        return systemPromptLayersJsonb;
    }

    public void setSystemPromptLayersJsonb(Map<String, Object> systemPromptLayersJsonb) {
        this.systemPromptLayersJsonb = systemPromptLayersJsonb;
    }

    public String getEffectiveSystemPrompt() {
        return effectiveSystemPrompt;
    }

    public void setEffectiveSystemPrompt(String effectiveSystemPrompt) {
        this.effectiveSystemPrompt = effectiveSystemPrompt;
    }

    public String getConfigHash() {
        return configHash;
    }

    public void setConfigHash(String configHash) {
        this.configHash = configHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
