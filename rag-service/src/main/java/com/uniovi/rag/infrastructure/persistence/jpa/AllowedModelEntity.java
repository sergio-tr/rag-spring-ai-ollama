package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.AllowedModelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Entity
@Table(name = "allowed_model")
public class AllowedModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AllowedModelType type;

    @Column(name = "in_allowlist", nullable = false)
    private boolean inAllowlist;

    @Column(name = "installed_at")
    private Instant installedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags_json", columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_pull_status", length = 64)
    private String lastPullStatus;

    @Column(name = "last_pull_error", columnDefinition = "text")
    private String lastPullError;

    protected AllowedModelEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AllowedModelType getType() {
        return type;
    }

    public boolean isInAllowlist() {
        return inAllowlist;
    }

    public Instant getInstalledAt() {
        return installedAt;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean isAvailable() {
        return available;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public String getLastPullStatus() {
        return lastPullStatus;
    }

    public String getLastPullError() {
        return lastPullError;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setType(AllowedModelType type) {
        this.type = type;
    }

    public void setInAllowlist(boolean inAllowlist) {
        this.inAllowlist = inAllowlist;
    }

    public void setInstalledAt(Instant installedAt) {
        this.installedAt = installedAt;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public void setLastPullStatus(String lastPullStatus) {
        this.lastPullStatus = lastPullStatus;
    }

    public void setLastPullError(String lastPullError) {
        this.lastPullError = lastPullError;
    }

    public static AllowedModelEntity newRow(
            String name, AllowedModelType type, boolean inAllowlist, Instant installedAt) {
        AllowedModelEntity e = new AllowedModelEntity();
        e.name = name;
        e.type = type;
        e.inAllowlist = inAllowlist;
        e.installedAt = installedAt;
        e.available = installedAt != null;
        return e;
    }
}
