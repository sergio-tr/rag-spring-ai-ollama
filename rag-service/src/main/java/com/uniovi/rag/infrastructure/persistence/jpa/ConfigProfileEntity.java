package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
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
@Table(name = "config_profile")
public class ConfigProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type", nullable = false, length = 32)
    private ConfigProfileType profileType;

    @Column(nullable = false)
    private int version = 1;

    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Column(nullable = false)
    private boolean immutable;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    protected ConfigProfileEntity() {
    }

    public UUID getId() {
        return id;
    }

    public ConfigProfileType getProfileType() {
        return profileType;
    }

    public void setProfileType(ConfigProfileType profileType) {
        this.profileType = profileType;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UserEntity getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserEntity createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * New editable profile row (immutability is enforced when linked from published presets).
     */
    public static ConfigProfileEntity newDraft(
            ConfigProfileType type,
            int version,
            String label,
            Map<String, Object> payload,
            UserEntity owner,
            UserEntity createdBy,
            Instant createdAt) {
        ConfigProfileEntity e = new ConfigProfileEntity();
        e.profileType = type;
        e.version = version;
        e.label = label;
        e.payload = payload != null ? payload : Map.of();
        e.owner = owner;
        e.immutable = false;
        e.createdAt = createdAt;
        e.createdBy = createdBy;
        return e;
    }
}
