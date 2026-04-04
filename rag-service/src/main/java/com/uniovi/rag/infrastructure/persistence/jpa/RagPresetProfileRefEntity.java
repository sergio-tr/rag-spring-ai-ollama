package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "rag_preset_profile_ref")
public class RagPresetProfileRefEntity {

    @EmbeddedId
    private RagPresetProfileRefId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("presetId")
    @JoinColumn(name = "preset_id", nullable = false)
    private RagPresetEntity preset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("profileId")
    @JoinColumn(name = "profile_id", nullable = false)
    private ConfigProfileEntity profile;

    @Column(nullable = false)
    private int ordinal;

    @Column(length = 64)
    private String role;

    protected RagPresetProfileRefEntity() {
    }

    public RagPresetProfileRefId getId() {
        return id;
    }

    public void setId(RagPresetProfileRefId id) {
        this.id = id;
    }

    public RagPresetEntity getPreset() {
        return preset;
    }

    public void setPreset(RagPresetEntity preset) {
        this.preset = preset;
    }

    public ConfigProfileEntity getProfile() {
        return profile;
    }

    public void setProfile(ConfigProfileEntity profile) {
        this.profile = profile;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public static RagPresetProfileRefEntity link(RagPresetEntity preset, ConfigProfileEntity profile, int ordinal, String role) {
        RagPresetProfileRefEntity e = new RagPresetProfileRefEntity();
        e.id = new RagPresetProfileRefId(preset.getId(), profile.getId());
        e.preset = preset;
        e.profile = profile;
        e.ordinal = ordinal;
        e.role = role;
        return e;
    }
}
