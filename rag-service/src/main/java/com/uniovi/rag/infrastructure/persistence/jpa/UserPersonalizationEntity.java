package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_personalization")
public class UserPersonalizationEntity {

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "personalization_jsonb", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> personalization;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPersonalizationEntity() {
    }

    /** New row for the given user (same persistence unit). */
    public static UserPersonalizationEntity newForUser(UserEntity user) {
        UserPersonalizationEntity e = new UserPersonalizationEntity();
        e.setUser(user);
        e.setPersonalization(new java.util.LinkedHashMap<>());
        e.setSchemaVersion(1);
        e.setUpdatedAt(Instant.now());
        return e;
    }

    public UUID getUserId() {
        return userId;
    }

    public Map<String, Object> getPersonalization() {
        return personalization;
    }

    public void setPersonalization(Map<String, Object> personalization) {
        this.personalization = personalization;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }
}
