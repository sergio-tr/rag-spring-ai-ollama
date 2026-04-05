package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "rag_preset")
public class RagPresetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Column(nullable = false)
    private String name;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> values;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "composition_version", nullable = false)
    private int compositionVersion;

    @OneToMany(mappedBy = "preset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RagPresetProfileRefEntity> profileRefs = new ArrayList<>();

    protected RagPresetEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
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

    public int getCompositionVersion() {
        return compositionVersion;
    }

    public void setCompositionVersion(int compositionVersion) {
        this.compositionVersion = compositionVersion;
    }

    public List<RagPresetProfileRefEntity> getProfileRefs() {
        return profileRefs;
    }

    public void setProfileRefs(List<RagPresetProfileRefEntity> profileRefs) {
        this.profileRefs = profileRefs != null ? profileRefs : new ArrayList<>();
    }

    public static RagPresetEntity newUserOwned(
            UserEntity owner,
            String name,
            String description,
            List<String> tags,
            Map<String, Object> values,
            Instant createdAt,
            Instant updatedAt) {
        RagPresetEntity e = new RagPresetEntity();
        e.owner = owner;
        e.name = name;
        e.description = description;
        e.tags = tags != null ? tags : new java.util.ArrayList<>();
        e.values = values != null ? values : Map.of();
        e.system = false;
        e.createdAt = createdAt;
        e.updatedAt = updatedAt;
        return e;
    }
}
