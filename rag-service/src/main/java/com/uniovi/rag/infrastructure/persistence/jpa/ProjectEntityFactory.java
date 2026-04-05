package com.uniovi.rag.infrastructure.persistence.jpa;

import java.time.Instant;

public final class ProjectEntityFactory {

    private ProjectEntityFactory() {
    }

    public static ProjectEntity newOwnedProject(UserEntity owner, String name, String description) {
        ProjectEntity p = new ProjectEntity();
        p.setOwner(owner);
        p.setName(name);
        p.setDescription(description);
        Instant now = Instant.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }
}
