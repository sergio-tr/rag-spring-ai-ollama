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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "allowed_model")
public class AllowedModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AllowedModelType type;

    @Column(name = "in_allowlist", nullable = false)
    private boolean inAllowlist;

    @Column(name = "installed_at")
    private Instant installedAt;

    protected AllowedModelEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
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

    public void setName(String name) {
        this.name = name;
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

    public static AllowedModelEntity newRow(
            String name, AllowedModelType type, boolean inAllowlist, Instant installedAt) {
        AllowedModelEntity e = new AllowedModelEntity();
        e.name = name;
        e.type = type;
        e.inAllowlist = inAllowlist;
        e.installedAt = installedAt;
        return e;
    }
}
