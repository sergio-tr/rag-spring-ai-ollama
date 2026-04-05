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

    @Column(name = "config_hash", length = 128)
    private String configHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ResolvedConfigSnapshotEntity() {
    }

    public UUID getId() {
        return id;
    }

    public Map<String, Object> getPayloadJsonb() {
        return payloadJsonb;
    }

    public void setPayloadJsonb(Map<String, Object> payloadJsonb) {
        this.payloadJsonb = payloadJsonb;
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
