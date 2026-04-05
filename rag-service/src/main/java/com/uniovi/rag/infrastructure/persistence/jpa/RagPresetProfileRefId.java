package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RagPresetProfileRefId implements Serializable {

    @Column(name = "preset_id", nullable = false)
    private UUID presetId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    protected RagPresetProfileRefId() {
    }

    public RagPresetProfileRefId(UUID presetId, UUID profileId) {
        this.presetId = presetId;
        this.profileId = profileId;
    }

    public UUID getPresetId() {
        return presetId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RagPresetProfileRefId that = (RagPresetProfileRefId) o;
        return Objects.equals(presetId, that.presetId) && Objects.equals(profileId, that.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(presetId, profileId);
    }
}
